# CLAUDE.md - Backend

이 파일은 Backend 프로젝트 작업 시 Claude Code가 참고하는 가이드입니다.

## 프로젝트 구조

Backend는 2개의 서비스로 구성됩니다:
- **quant-jump-stock-core**: Spring Boot + Kotlin 메인 API 서버
- **quant-jump-stock-data-engine**: Python + FastAPI 데이터 수집 엔진

## 명령어

### Core API (Kotlin/Spring Boot)
```bash
cd quant-jump-stock-core

# 개발 서버 실행
./gradlew bootRun

# 빌드 (테스트 제외)
./gradlew build -x test

# 테스트 실행
./gradlew test

# 클린 빌드
./gradlew clean build
```

### Data Engine (Python/FastAPI)
```bash
cd quant-jump-stock-data-engine

# Poetry로 의존성 설치
poetry install

# 개발 서버 실행
python src/main.py
# 또는
uvicorn src.main:app --reload --port 10020
```

### Makefile
```bash
make local          # .env.local로 Docker 서비스 시작
make prod           # .env.prod로 Docker 서비스 시작
make logs           # 로그 확인
make down           # 서비스 종료
make clean          # 볼륨 포함 전체 삭제
```

## Core API 아키텍처 (Hexagonal)

```
src/main/kotlin/com/quantjumpstock/core/
├── adapter/                    # 어댑터 레이어 (외부 연동)
│   ├── input/                  # 인바운드 어댑터
│   │   ├── api/                # 내부 API 인터페이스
│   │   ├── rest/               # REST 컨트롤러 ⭐ 신규 API는 여기에
│   │   │   ├── admin/          # 관리자 API
│   │   │   ├── analysis/       # 주식 분석 API
│   │   │   ├── auth/           # 인증 API
│   │   │   ├── category/       # 카테고리 API
│   │   │   ├── economic/       # 경제 지표 API
│   │   │   ├── marketplace/    # 마켓플레이스 API
│   │   │   ├── ml/             # ML 모델 API
│   │   │   ├── prediction/     # 예측 API
│   │   │   ├── scheduler/      # 스케줄러 API
│   │   │   ├── strategy/       # 전략 API
│   │   │   ├── user/           # 사용자 API
│   │   │   └── vertexai/       # Vertex AI API
│   │   ├── messaging/          # Pub/Sub 컨슈머
│   │   └── scheduler/          # Cloud Scheduler HTTP 엔드포인트
│   └── output/                 # 아웃바운드 어댑터
│       ├── persistence/
│       │   ├── jpa/            # PostgreSQL (JPA Entity/Repository)
│       │   └── mongodb/        # MongoDB Repository
│       ├── external/           # 외부 API 클라이언트 (KIS, FRED)
│       ├── gcp/                # GCP 서비스
│       │   └── vertexai/       # Vertex AI 클라이언트
│       ├── messaging/          # Pub/Sub 프로듀서
│       └── notification/       # Slack 알림
│
├── application/                # 애플리케이션 레이어 (유스케이스)
│   ├── admin/                  # 관리자 서비스
│   ├── analysis/               # 분석 서비스
│   ├── auth/                   # 인증 서비스
│   ├── balance/                # 잔고 서비스
│   ├── category/               # 카테고리 서비스
│   ├── economic/               # 경제 지표 서비스
│   ├── marketplace/            # 마켓플레이스 서비스
│   ├── ml/                     # ML 서비스
│   ├── prediction/             # 예측 서비스
│   ├── scheduler/              # 스케줄러 서비스
│   ├── stock/                  # 주식 데이터 서비스
│   ├── strategy/               # 전략 서비스
│   ├── trading/                # 트레이딩 서비스
│   ├── user/                   # 사용자 서비스
│   └── vertexai/               # Vertex AI 서비스
│
├── domain/                     # 도메인 레이어 (비즈니스 로직)
│   ├── model/                  # 핵심 도메인 엔티티
│   ├── port/                   # 포트 인터페이스 (헥사고날 아키텍처)
│   ├── analysis/               # 분석 도메인
│   ├── economic/               # 경제 도메인
│   ├── ml/                     # ML 도메인
│   ├── prediction/             # 예측 도메인
│   ├── scheduler/              # 스케줄러 도메인
│   ├── trading/                # 트레이딩 도메인
│   └── vertexai/               # Vertex AI 도메인
│
├── config/                     # Spring 설정
├── events/                     # 이벤트 정의
├── infrastructure/             # 인프라 공통 유틸
└── scheduler/                  # Cloud Scheduler HTTP 엔드포인트 (Quartz 완전 제거됨)
```

## Data Engine 구조 (Python)

```
quant-jump-stock-data-engine/src/
├── main.py                     # FastAPI 진입점
├── core/                       # 핵심 유틸리티
├── events/                     # 이벤트 핸들러
├── features/                   # 피처 모듈
└── services/                   # 비즈니스 서비스
```

## 데이터베이스

### PostgreSQL (관계형 데이터)
- **stocks**: 종목 마스터 (ticker, name, sector, industry)
- **strategies**: 전략 정의 (DSL JSON)
- **backtest_results**: 백테스트 결과
- **users, user_roles**: 사용자 및 RBAC
- JPA Entity: `adapter/output/persistence/jpa/`
- Flyway 마이그레이션: `src/main/resources/db/migration/`

### MongoDB (시계열 데이터)
- **DB명**: `stock_trading`
- **daily_stock_data**: 일별 주식 가격 + 경제 지표
  ```json
  {
    "date": "2026-02-12",
    "stocks": {
      "AAPL": { "open": 228.5, "high": 233.1, "low": 227.8, "close": 231.42, "volume": 48523100, "close_price": 231.42, "info": { "trailingPE": 28.5, "marketCap": 3560000000000, ... } }
    },
    "yfinance_indicators": {
      "^GSPC": { "open": 5950.2, "high": 5980.5, "low": 5940.1, "close": 5970.8, "volume": 3800000000, "close_price": 5970.8, "name": "S&P 500 지수" }
    },
    "fred_indicators": {
      "FEDFUNDS": { "value": 4.33, "name": "기준금리" }
    }
  }
  ```
  - 기간: 2025-02-05 ~ 현재 (약 260일)
  - 종목: 35개 (미국 주식)
  - stocks: OHLCV + 펀더멘탈(info), yfinance_indicators: ticker 키 + OHLCV, fred_indicators: code 키 + {value, name}
- Repository: `adapter/output/persistence/mongodb/`

### Flyway 마이그레이션 규칙
```
V{버전}__설명.sql
예: V12__Create_User_Subscription_Table.sql
```
- 버전은 순차적으로 증가
- 기존 마이그레이션 수정 금지 (새 버전 생성)

## 코딩 규칙

### Hexagonal 아키텍처 준수
```
✅ adapter → application → domain
❌ domain → adapter (의존성 역전 위반)
❌ adapter.input → adapter.output 직접 참조
```

### 새 API 엔드포인트 추가 시
1. `adapter/input/rest/{도메인}/` 에 Controller 생성
2. `application/{도메인}/` 에 Service 생성
3. 필요시 `domain/model/` 에 도메인 모델 추가
4. 영속성 필요시 `adapter/output/persistence/jpa/` 에 Entity/Repository 추가

### JPA Entity 규칙
```kotlin
@Entity
@Table(name = "table_name")
class EntityName(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // 다른 필드들...
)
```

### 도메인 모델 규칙
- 영속성 어노테이션 사용 금지 (순수 Kotlin 클래스)
- 비즈니스 로직은 도메인 모델에 포함

## 외부 연동

| 서비스 | 용도 | 설정 위치 |
|--------|------|-----------|
| KIS API | 한국투자증권 실시간 시세 | `adapter/output/external/` |
| FRED API | 미국 경제 지표 | `adapter/output/external/` |
| Vertex AI | AI 예측 모델 | `adapter/output/gcp/vertexai/` |
| Slack | 알림 전송 | `adapter/output/notification/slack/` |
| Pub/Sub | 이벤트 메시징 (GCP) | `adapter/input/messaging/`, `adapter/output/messaging/` |

## 스케줄러 (Cloud Scheduler)

> Quartz는 완전 제거됨 (2026-02-28). 모든 스케줄링은 GCP Cloud Scheduler로 운영.

- Cloud Scheduler 정의: `terraform/scheduler.tf`
- HTTP 엔드포인트: `adapter/input/rest/scheduler/CloudSchedulerController.kt`
- 데이터 파이프라인: Cloud Scheduler → Pub/Sub → Data Engine (체이닝)
- 운영 가이드: [스케줄러_운영_가이드.md](./docs/setup/스케줄러_운영_가이드.md)

## 테스트 규칙

상세: **[테스트_규칙.md](./테스트_규칙.md)** 참조

- 항상 Docker 실행 (Core 재빌드 필수, Data Engine 자동 반영)
- 단위 테스트: `cd quant-jump-stock-core && ./gradlew test`
- 통합 테스트: 사용자 요청 시에만 수행 (플랜 → API → E2E → 결과 보고)

### PR 전 테스트 필수
- **PR 생성 전 반드시 관련 테스트를 로컬에서 실행하고 통과 확인**
- Core API: `./gradlew test` (변경된 모듈)
- Data Engine: `cd quant-jump-stock-data-engine && python -m pytest tests/` (변경된 테스트)
- 테스트 실패 시 원인 파악 후 수정 → 재실행 → 통과 확인 후 PR 생성

## 환경 변수

`.env.local` 파일에서 관리 (`.env.example` 참조):
```bash
# 필수
FRED_API_KEY=
KIS_APP_KEY=
KIS_APP_SECRET=
DB_HOST=localhost
DB_PORT=5432
MONGODB_URI=

# 선택
SLACK_WEBHOOK_URL_TRADING=
GCP_ENABLED=false
```

## 아키텍처 개선 계획

Backend 아키텍처 개선을 위한 상세 문서가 준비되어 있습니다:

### 📚 핵심 문서

1. **[백엔드_아키텍처.md](./docs/architecture/refactor/백엔드_아키텍처.md)**
   - 현재 아키텍처 분석 (점수: 65/100)
   - 주요 위반 사항 및 개선 방향
   - 14주 로드맵 (Phase 1-3)
   - 목표: Hexagonal Architecture 완성도 100/100

2. **[어댑터_패턴.md](./docs/architecture/refactor/어댑터_패턴.md)**
   - Persistence Adapter 패턴 가이드
   - JPA/MongoDB Adapter 구현 예시
   - 매핑 전략 (toEntity/toDomain)
   - 실전 코드 예제

3. **[테스트_가이드.md](./docs/architecture/refactor/테스트_가이드.md)**
   - Kotest + MockK 단위 테스트
   - Testcontainers 통합 테스트
   - Property-Based Testing
   - 테스트 픽스처 패턴

4. **[마이그레이션_로그.md](./docs/architecture/refactor/마이그레이션_로그.md)**
   - 주차별 진행 상황 기록 템플릿
   - 작업 항목 체크리스트
   - 성공 지표 추적

### 🎯 개선 목표

| 항목 | 현재 | 목표 |
|------|------|------|
| ArchUnit 위반 | ~27건 | 0건 |
| 단위 테스트 커버리지 | 0% | 80%+ |
| Application → JPA 의존성 | 11개 서비스 | 0개 |
| Domain 모델 오염 | 5개 파일 | 0개 |

### 🚀 시작하기

1. **[백엔드_아키텍처.md](./docs/architecture/refactor/백엔드_아키텍처.md)** 읽기 - 전체 계획 이해
2. **[어댑터_패턴.md](./docs/architecture/refactor/어댑터_패턴.md)** 읽기 - 구현 패턴 학습
3. **[테스트_가이드.md](./docs/architecture/refactor/테스트_가이드.md)** 읽기 - 테스트 작성 방법
4. Week 1부터 단계적 진행
