# Phase 1: WebFlux 제거 및 부팅 시간 최적화

> **목표**: Native Image cold start 4-14초 → ~1.5초
> **Phase 1 예상 절감**: ~2-4초
> **리스크**: LOW (VM에서 먼저 적용, 기능 변경 없음)

---

## 1-1. WebFlux 제거 → RestClient 전환

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

## 1-2. Prod 프로파일 생성

### 변경 내용

**새 파일**: `src/main/resources/application-prod.yml`

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: none          # validate 대신 none (35개 Entity 검증 제거, ~0.3-0.5초 절감)
springdoc:
  api-docs.enabled: false     # Swagger 비활성화 (~0.2초 절감)
  swagger-ui.enabled: false
logging:
  level:
    org.hibernate.SQL: WARN
    org.springframework.data.mongodb: WARN
```

### 왜 필요한가?

| 항목 | 현재 | prod 프로파일 | 절감 |
|------|------|--------------|------|
| Hibernate ddl-auto | validate (35개 Entity 메타데이터 검증) | none | ~0.3-0.5초 |
| Swagger/SpringDoc | 항상 활성화 | 비활성화 | ~0.2초 |
| SQL 로깅 | DEBUG | WARN | 미미하지만 로그 I/O 감소 |

> **Flyway가 스키마를 관리하므로** Hibernate validate는 프로덕션에서 불필요합니다.

---

## 1-3. Pub/Sub 구독 지연 초기화

### 변경 내용

**수정 파일**: `adapter/input/messaging/PubSubEventListenerAdapter.kt`

```kotlin
// Before: @PostConstruct (부팅 시 블로킹, 6개 구독 순차 생성)
@PostConstruct
fun init() {
    subscribe(EventTopics.ANALYSIS_COMPLETED, ::handleAnalysisCompleted)
    subscribe(EventTopics.ECONOMIC_DATA_COLLECTED, ::handleEconomicDataCollected)
    // ... 4개 더
}

// After: ApplicationReadyEvent + 백그라운드 스레드
@EventListener(ApplicationReadyEvent::class)
fun subscribeAll() {
    Thread.startVirtualThread {
        subscribe(EventTopics.ANALYSIS_COMPLETED, ::handleAnalysisCompleted)
        subscribe(EventTopics.ECONOMIC_DATA_COLLECTED, ::handleEconomicDataCollected)
        // ... 4개 더
        logger.info("Pub/Sub 구독 초기화 완료")
    }
}
```

### 효과

| 항목 | Before | After |
|------|--------|-------|
| 부팅 블로킹 | 6개 Pull 구독 순차 생성 (~1-3초) | 부팅 완료 후 백그라운드 실행 |
| 서버 가용성 | 구독 완료까지 대기 | 즉시 HTTP 요청 수신 가능 |
| 구독 실패 시 | 부팅 실패 | 부팅 성공, 구독만 재시도 가능 |

> **주의**: Phase 2에서 Pull → Push 전환하면 이 코드는 제거됩니다.
> Phase 1에서는 기존 Pull 방식 유지하되 부팅 블로킹만 제거합니다.

---

## Phase 1 전체 예상 효과

| 최적화 | 절감 시간 |
|--------|----------|
| WebFlux(Netty) 제거 | ~0.3-0.5초 |
| Hibernate validate → none | ~0.3-0.5초 |
| Swagger 비활성화 | ~0.2초 |
| Pub/Sub 구독 지연 | ~1-3초 (부팅 블로킹 → 백그라운드) |
| **합계** | **~2-4초** |

---

## 검증 방법

```bash
# 1. 빌드
cd quant-jump-stock-core
./gradlew build -x test

# 2. 기존 프로파일로 부팅 시간 측정 (baseline)
java -jar build/libs/quant-jump-stock-core-*.jar 2>&1 | grep "Started"

# 3. prod 프로파일로 부팅 시간 측정
java -jar build/libs/quant-jump-stock-core-*.jar --spring.profiles.active=prod 2>&1 | grep "Started"

# 4. API 동작 확인
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
