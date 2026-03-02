# Core API Cloud Run 전환 계획 및 진행 상태

> **목표**: Native Image cold start 6초+ → ~2초 / VM → Cloud Run 완전 전환
> **최종 업데이트**: 2026-03-01
> **현재 상태**: Phase 1 대부분 완료 (**6.2초 → 2.5초 달성**), Phase 2-4 코드 준비 완료. Terraform 적용 및 프로덕션 전환 대기.

## 진행 상태 요약

| Phase | 항목 | 상태 |
|-------|------|------|
| 1-1 | WebFlux → RestClient 전환 | **완료** (RestClient 전환 완료, WebFlux 제거) |
| 1-2 | Prod 프로파일 생성 | **완료** (application.yml 단일화, ddl-auto:none, lazy-init:true) |
| 1-3 | Pub/Sub 구독 지연 초기화 | **완료** (PubSubEventListenerAdapter 삭제, Push 전환) |
| 1-4 | spring-cloud-gcp-starter-pubsub 제거 | **완료** (google-cloud-pubsub 직접 사용) |
| 1-5 | 추가 부팅 최적화 | **완료** (SpringDoc 비활성화, JPA lazy bootstrap, Hibernate 메타데이터 스킵, JMX 비활성화) |
| 2 | Pub/Sub Pull → Push 전환 | **코드 완료** (PubSubPushController, LightweightPubSubPublisher, Pull 어댑터 삭제) |
| 3 | Quartz → Cloud Scheduler 전환 | **코드 완료** (CloudSchedulerController 6개 엔드포인트, QuartzConfig 조건부) |
| 4 | Cloud Run 배포 준비 | **완료** (deploy-core.yml, Dockerfile.native, entrypoint.sh, Terraform 리소스) |
| - | Config 정리 | **완료** (application*.yml 3→1, docker-compose*.yml 3→1, start.sh gcp deprecated) |

---

## Phase 1: 부팅 시간 최적화

---

## 1-1. WebFlux 제거 → RestClient 전환 ✅ 완료

### 왜 제거하는가?

`spring-boot-starter-webflux`를 쓰는 이유는 **WebClient** 하나뿐인데, 실제 사용처 9개 중 **7개가 `.block()`으로 동기 호출**입니다.

| 문제 | 설명 |
|------|------|
| Netty 부팅 오버헤드 | WebFlux 의존 → Netty reactor 런타임 초기화 (~0.3-0.5초) |
| 이중 웹 서버 | Tomcat(MVC) + Netty(WebFlux) 동시 초기화 |
| `.block()` 안티패턴 | 리액티브 스트림을 만들고 바로 블로킹 → RestClient와 동일 |
| Native Image 비용 | Netty 리플렉션/바이트코드가 GraalVM에서 추가 오버헤드 |

### 대안: Spring 6.1 `RestClient`

`spring-boot-starter-web`(Tomcat)에 이미 포함. 동기 호출이 기본이므로 `.block()` 불필요.

```kotlin
// Before (WebClient)
webClient.post()
    .uri("/api")
    .bodyValue(request)
    .retrieve()
    .bodyToMono(Response::class.java)
    .block()

// After (RestClient)
restClient.post()
    .uri("/api")
    .body(request)
    .retrieve()
    .body(Response::class.java)
```

---

### 현재 WebClient 사용처 전수 조사

#### 사용 현황 요약

| # | 파일 | 메서드 | 패턴 | 타임아웃 | 전환 방식 |
|---|------|--------|------|----------|-----------|
| 1 | `DataEngineClient.kt` | `uploadMlPackage()` | `.block()` | 5분 | RestClient + timeout |
| 2 | `DataEngineClient.kt` | `getPackageStatus()` | `.block()` | 30초 | RestClient + timeout |
| 3 | `KisApiAdapter.kt` | `refreshToken()` | `.block()` | **없음** | RestClient + timeout 추가 |
| 4 | `KisApiAdapter.kt` | `getOverseasBalance()` | `.block()` | **없음** | RestClient + timeout 추가 |
| 5 | `KisApiAdapter.kt` | `placeOrder()` | `.block()` | **없음** | RestClient + timeout 추가 |
| 6 | `SlackApiClient.kt` | `sendToSlackApi()` | `.block()` | **없음** | RestClient |
| 7 | `SlackApiClient.kt` | `sendToSlackWebhook()` | `.block()` | **없음** | RestClient |
| 8 | `WebClientRestApiAdapter.kt` | `callEconomicDataCollectionApi()` | `.toFuture()` | **없음** | RestClient + CompletableFuture |
| 9 | `NewsSlackAlertAdapter.kt` | `sendAlert()` | `.subscribe()` | **없음** | `CompletableFuture.runAsync` |

**`.block()` 7개 / 비동기 2개** → 전부 RestClient로 전환 가능

---

#### 파일별 상세

##### 1) `config/WebClientConfig.kt` → `RestClientConfig.kt`로 변환

```kotlin
// Before
@Bean
fun webClient(): WebClient {
    return WebClient.builder()
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.USER_AGENT, "quantiq-core/1.0")
        .build()
}

// After
@Bean
fun restClient(): RestClient {
    return RestClient.builder()
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.USER_AGENT, "quantiq-core/1.0")
        .build()
}
```

##### 2) `adapter/output/external/DataEngineClient.kt`

현재: WebClient + `.block()` + timeout
전환: RestClient (timeout은 `ClientHttpRequestFactory`에서 설정)

```kotlin
// Before
webClient.post()
    .uri("$baseUrl/api/v1/ml/upload")
    .retrieve()
    .bodyToMono(PackageUploadResponse::class.java)
    .timeout(Duration.ofMinutes(5))
    .block()

// After
restClient.post()
    .uri("$baseUrl/api/v1/ml/upload")
    .retrieve()
    .body(PackageUploadResponse::class.java)
```

> **참고**: RestClient의 timeout은 `RestClient.builder().requestFactory(factory)` 에서 `SimpleClientHttpRequestFactory`로 설정.
> Data Engine 호출용 별도 RestClient 빈 생성 (5분 timeout)

##### 3) `adapter/output/external/KisApiAdapter.kt`

현재: 유저별 WebClient 캐시 (`ConcurrentHashMap<String, WebClient>`)
전환: 유저별 RestClient 캐시로 동일 패턴 유지

```kotlin
// Before
private val webClientCache = ConcurrentHashMap<String, WebClient>()
private fun getWebClientForUser(userId: String): WebClient { ... }

// After
private val restClientCache = ConcurrentHashMap<String, RestClient>()
private fun getRestClientForUser(userId: String): RestClient { ... }
```

**추가 개선**: 현재 KIS API 호출에 **timeout이 없음** → 30초 timeout 추가

##### 4) `adapter/output/external/WebClientRestApiAdapter.kt`

현재: `.toFuture()` 로 비동기
전환: RestClient는 동기이므로 `CompletableFuture.supplyAsync` 로 감싸기

```kotlin
// Before
webClient.post().uri(url).bodyValue(requestBody)
    .retrieve().bodyToMono(String::class.java).toFuture()

// After
CompletableFuture.supplyAsync {
    restClient.post().uri(url).body(requestBody)
        .retrieve().body(String::class.java)
}
```

##### 5) `adapter/output/notification/slack/SlackApiClient.kt`

현재: `.block()` (2곳)
전환: RestClient 직접 호출 (동기, Slack 알림은 블로킹 OK)

##### 6) `adapter/output/notification/NewsSlackAlertAdapter.kt`

현재: `.subscribe()` (fire-and-forget 비동기)
전환: `CompletableFuture.runAsync` 로 비동기 유지

```kotlin
// Before
webClient.post().uri(slackWebhookUrl)
    .bodyValue(SlackMessage(text = message))
    .retrieve().bodyToMono(String::class.java)
    .subscribe({ }, { e -> logger.warn("실패: {}", e.message) })

// After
CompletableFuture.runAsync {
    try {
        restClient.post().uri(slackWebhookUrl)
            .body(SlackMessage(text = message))
            .retrieve().body(String::class.java)
    } catch (e: Exception) {
        logger.warn("Slack 뉴스 알림 발송 실패: {}", e.message)
    }
}
```

---

### 수정 파일 목록

| # | 파일 | 변경 내용 |
|---|------|----------|
| 1 | `build.gradle.kts` | `spring-boot-starter-webflux` 제거 |
| 2 | `config/WebClientConfig.kt` | → `RestClientConfig.kt` 변환 (파일명 변경 + RestClient 빈) |
| 3 | `adapter/output/external/KisApiAdapter.kt` | WebClient → RestClient, timeout 30초 추가 |
| 4 | `adapter/output/external/DataEngineClient.kt` | WebClient → RestClient |
| 5 | `adapter/output/external/WebClientRestApiAdapter.kt` | WebClient → RestClient + CompletableFuture |
| 6 | `adapter/output/notification/slack/SlackApiClient.kt` | WebClient → RestClient |
| 7 | `adapter/output/notification/NewsSlackAlertAdapter.kt` | `.subscribe()` → `CompletableFuture.runAsync` |

---

### 리스크 분석

| 리스크 | 확률 | 영향 | 대응 |
|--------|------|------|------|
| API 호출 동작 변경 | LOW | 없음 (이미 동기) | RestClient는 동일한 HTTP 호출 |
| KIS 유저별 캐시 동작 변경 | LOW | LOW | ConcurrentHashMap 패턴 동일 유지 |
| 비동기 호출 (2곳) 동작 변경 | LOW | LOW | CompletableFuture로 동일 패턴 |
| 빌드 실패 | LOW | - | WebFlux import 전부 제거 확인 |

---

## 1-2. Prod 프로파일 → application.yml 단일화 ✅ 완료

### 변경 내용 (2026-03-01)

**결정**: `application-prod.yml` 별도 생성 대신, `application.yml`에 직접 적용하여 단일 파일로 관리.

```yaml
# application.yml에 직접 적용 (환경 무관하게 동일)
spring:
  main:
    lazy-initialization: true    # cold start 최적화
  jpa:
    hibernate:
      ddl-auto: none             # Flyway가 스키마 관리, validate 불필요
```

- **Swagger**: 환경변수 `${SWAGGER_ENABLED:true}`로 제어 (프로덕션에서 Cloud Run env로 false 설정)
- **포트**: `${SERVER_PORT:10010}`로 제어 (Cloud Run은 `PORT` env로 8080 주입)

### 삭제된 파일
- `application-local.yml` — 모든 설정이 application.yml + env_file + docker-compose 환경변수로 커버
- `application-prod.yml` — gitignored 상태로 CI/CD에서 적용 불가했음, 유용한 설정은 application.yml로 이동

> **원칙**: 환경별 차이는 환경변수로 제어, 코드 파일은 하나로 통일.

---

## 1-3. Pub/Sub Pull 구독 제거 + spring-cloud-gcp 제거 ✅ 완료

### 변경 내용 (2026-03-01)

**결정**: Phase 2의 Pull → Push 전환을 선행 적용하여, Pull 구독 코드를 완전히 제거함.
지연 초기화 대신 Pull 자체를 없애는 것이 부팅 시간에 더 효과적.

**삭제된 파일/의존성:**
- `PubSubEventListenerAdapter.kt` — Pull 구독 어댑터 전체 삭제
- `spring-cloud-gcp-starter-pubsub` + Spring Cloud GCP BOM — 의존성 완전 제거 (gRPC/Netty 자동 초기화 오버헤드 제거)

**대체:**
- `PubSubPushController.kt` — `/_ah/push-handler/{topicName}` 단일 엔드포인트로 6개 토픽 수신
- `PubSubMessageHandlerService.kt` — 핸들러 로직 분리 (Push 컨트롤러에서 호출)
- `LightweightPubSubPublisher.kt` — `google-cloud-pubsub:1.149.0` 직접 사용 (경량 퍼블리셔)
- `gcp.project-id` — `spring.cloud.gcp.project-id` 대신 커스텀 프로퍼티로 이동

### 효과

| 항목 | Before | After |
|------|--------|-------|
| 부팅 블로킹 | 6개 Pull 구독 순차 생성 (~1-3초) | **제거** (Push는 HTTP 요청으로 수신) |
| gRPC/Netty 오버헤드 | spring-cloud-gcp 전체 스택 (auto-config) | **제거** (경량 Publisher만 사용) |
| 구독 실패 시 | 부팅 실패 | 해당 없음 (Push는 Pub/Sub이 HTTP로 전달) |
| 부팅 시간 (실측) | 6.235초 | **5.459초** (-0.8초) |

---

## Phase 1 전체 실측 결과

### Cloud Run 부팅 시간 변천 (실측)

| 단계 | 변경사항 | 부팅 시간 | 비고 |
|------|----------|-----------|------|
| 기준선 | spring-cloud-gcp 있음 | **6.235초** | |
| 1차 | spring-cloud-gcp → google-cloud-pubsub 직접 사용 | **5.459초** | -0.8초 |
| 2차 | + HikariPool minimum-idle=0 | 7.041초 | 악화 → 원복 |
| 3차 | minimum-idle=2 원복 | 5.768초 | |
| 4차 | + SpringDoc 비활성화 + JPA lazy bootstrap + Hibernate 메타데이터 스킵 | **2.132초** | 핵심 최적화 |
| 5차 | + JMX 비활성화 + HikariPool init-fail-timeout + dialect 제거 | 부팅 실패 | dialect 필수 |
| **최종** | **dialect 복원 + 전체 최적화 적용** | **2.554초** | **6.2초 → 2.5초 (59% 개선)** |

### 최적화 항목별 상태

| 최적화 | 상태 | 설정 |
|--------|------|------|
| WebFlux(Netty) 제거 | ✅ 완료 | RestClient 전환 |
| spring-cloud-gcp-starter-pubsub 제거 | ✅ 완료 | `google-cloud-pubsub:1.149.0` 직접 사용 |
| Pub/Sub Pull 구독 제거 | ✅ 완료 | Push 전환 + Pull 삭제 |
| SpringDoc 프로덕션 비활성화 | ✅ 완료 | `SWAGGER_ENABLED=false` (Cloud Run env) |
| JPA repository lazy bootstrap | ✅ 완료 | `spring.data.jpa.repositories.bootstrap-mode=lazy` |
| Hibernate JDBC 메타데이터 스킵 | ✅ 완료 | `hibernate.temp.use_jdbc_metadata_defaults=false` + dialect 명시 필수 |
| Hibernate ddl-auto: none | ✅ 완료 | Flyway가 스키마 관리 |
| lazy-initialization | ✅ 완료 | `spring.main.lazy-initialization=true` |
| JMX 비활성화 | ✅ 완료 | `spring.jmx.enabled=false` |
| HikariPool init-fail-timeout | ✅ 완료 | `initialization-fail-timeout=-1` |
| 로깅 레벨 축소 | ✅ 완료 | `${LOG_LEVEL_APP:DEBUG}`, `${LOG_LEVEL_MONGO:INFO}`, `${LOG_LEVEL_HIBERNATE:INFO}` |

### 시도했으나 효과 없었던 최적화

| 최적화 | 결과 | 이유 |
|--------|------|------|
| HikariPool minimum-idle=0 | **악화** (5.7초 → 7.0초) | Hibernate EntityManagerFactory가 초기화 시 DB 연결을 강제함 |
| Hibernate dialect 자동 감지 (dialect 제거) | **부팅 실패** | `use_jdbc_metadata_defaults=false`와 함께 사용 시 dialect 감지 불가 |

---

## 검증 방법

```bash
# 1. 빌드 (application.yml 단일 파일, 프로파일 불필요)
cd quant-jump-stock-core
./gradlew build -x test

# 2. JVM 부팅 시간 측정
java -jar build/libs/quant-jump-stock-core-*.jar 2>&1 | grep "Started"

# 3. Native Image 빌드 + 부팅 시간 측정
./gradlew nativeCompile
./build/native/nativeCompile/quant-jump-stock-core 2>&1 | grep "Started"

# 4. Docker Native 빌드 (docker-compose 통합)
CORE_DOCKERFILE=Dockerfile.native CORE_CONTAINER_PORT=8080 docker compose up quant-jump-stock-core

# 5. API 동작 확인
curl http://localhost:10010/actuator/health
curl http://localhost:10010/api/v1/stocks
```

---

---

## Phase 2: Pub/Sub Pull → Push 전환 (완료)

### 2-1. Push 엔드포인트 생성

**새 파일**: `adapter/input/messaging/PubSubPushController.kt`
- `/_ah/push-handler/{topicName}` 엔드포인트
- Base64 메시지 디코딩 → `PubSubMessageHandlerService`에 위임
- 에러 시에도 200 응답 (Pub/Sub 무한 재시도 방지)

### 2-2. 핸들러 로직 추출

**새 파일**: `application/messaging/PubSubMessageHandlerService.kt`
- Pull(`PubSubEventListenerAdapter`)과 Push(`PubSubPushController`) 양쪽에서 공유
- 6개 토픽 핸들러: `handleAnalysisCompleted`, `handleEconomicDataUpdated`, `handleBacktestCompleted` 등

### 2-3. 경량 Pub/Sub 퍼블리셔

**새 파일**: `infrastructure/messaging/LightweightPubSubPublisher.kt`
- `spring-cloud-gcp-starter-pubsub`의 `PubSubTemplate` 대신 `com.google.cloud.pubsub.v1.Publisher` 직접 사용
- `PUBSUB_EMULATOR_HOST` 자동 감지로 로컬/프로덕션 호환
- `ConcurrentHashMap`으로 토픽별 Publisher 캐시
- gRPC/Netty 오버헤드 대폭 감소

### 2-4. Pull 구독 조건부 비활성화

**수정**: `adapter/input/messaging/PubSubEventListenerAdapter.kt`
- `@ConditionalOnProperty(pubsub.pull.enabled)` 추가
- Cloud Run: `pubsub.pull.enabled=false` → Push만 사용
- VM: 기본값 `true` → 기존 Pull 구독 유지

---

## Phase 3: Quartz → Cloud Scheduler (완료)

### 3-1. HTTP 엔드포인트

**새 파일**: `adapter/input/rest/scheduler/CloudSchedulerController.kt`

| 엔드포인트 | 스케줄 | 서비스 호출 |
|-----------|--------|-----------|
| `POST /api/internal/scheduler/auto-buy` | 00:30 KST | `AutoTradingService.executeAutoTrading()` |
| `POST /api/internal/scheduler/auto-sell` | 매 1분 | 미국 장 시간 체크 + TODO |
| `POST /api/internal/scheduler/cleanup-orders` | 06:30 KST | TODO |
| `POST /api/internal/scheduler/portfolio-report` | 07:00 KST | TODO |
| `POST /api/internal/scheduler/canonical-backtest` | 일 02:00 KST | `CanonicalBacktestService` + 캐시 초기화 |
| `POST /api/internal/scheduler/backtest-cleanup` | 03:00 KST | `BacktestCleanupService.runCleanup()` |

### 3-2. Quartz 조건부 비활성화

**수정**: `scheduler/QuartzConfig.kt`
- `@ConditionalOnProperty(scheduler.quartz.enabled, matchIfMissing=true)` 추가
- Cloud Run: `scheduler.quartz.enabled=false`
- VM: 기본값 `true` → 기존 Quartz 유지

---

## Phase 4: Cloud Run 배포 준비 (완료)

### 4-1. Dockerfile.native 수정

- `PORT` 환경변수 사용 (Cloud Run 규약, 기본 8080)
- `HEALTHCHECK` 제거 (Cloud Run이 프로브 관리)
- `ENTRYPOINT`에 `--server.port=${PORT}` 추가

### 4-2. Cloud Run 프로파일

**새 파일**: `src/main/resources/application-cloudrun.yml`
- `server.port: ${PORT:8080}`
- `spring.quartz.auto-startup: false`
- `scheduler.quartz.enabled: false`
- `pubsub.pull.enabled: false`

### 4-3. Terraform

**수정 파일:**
- `cloudrun.tf`: Core API Cloud Run v2 서비스 (`core_api`) + IAM  
  - Data Engine용: `pubsub_invoker_data_engine`  
  - Core API용: `pubsub_invoker_core_api`, `scheduler_invoker_core_api`
- `iam.tf`: `qjs-core-api` SA (`core_api_sa`) 생성 — Pub/Sub publisher, Secret Manager accessor
- `pubsub.tf`: `core_push_topics` 로컬(현재 빈 배열) + `core_push_subscriptions` 리소스 추가. 전환 시 토픽을 `core_push_topics`로 이동하면 Push 구독 자동 생성
- `scheduler.tf`: 6개 Cloud Scheduler HTTP 잡 (주석 처리, 전환 시 활성화): auto-buy, auto-sell, cleanup-orders, portfolio-report, canonical-backtest, backtest-cleanup
- `outputs.tf`: `core_api_url`, `core_api_sa_email` 출력 추가

### 4-4. GitHub Actions

**수정 파일**: `.github/workflows/deploy-core.yml`
- Cloud Run 배포 잡 추가 (주석 처리, 전환 시 활성화)
- `google-github-actions/deploy-cloudrun@v2` 사용
- Data Engine 워크플로우 패턴 따름

---

## 전환 순서 (실행 가이드)

```
1. 현재 상태: VM에서 Phase 1-3 적용 → 부팅 시간 개선
   SPRING_PROFILES_ACTIVE=prod

2. Cloud Run 첫 배포 (트래픽 0%):
   - terraform apply (core_push_topics는 비워둠)
   - deploy-core.yml에서 Cloud Run 잡 주석 해제
   - 내부 테스트만 진행

3. Pub/Sub Push 전환:
   - pubsub.tf: core_pull_topics → core_push_topics로 토픽 이동
   - terraform apply

4. Cloud Scheduler 활성화:
   - scheduler.tf: Core 잡 주석 해제
   - terraform apply

5. Cloudflare 트래픽 분할:
   - Workers로 10% → 30% → 50% → 100%
   - 문제없으면 VM 종료
```
