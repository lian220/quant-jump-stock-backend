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
│   │   ├── messaging/          # Kafka 컨슈머
│   │   └── scheduler/          # Quartz 잡 스케줄러
│   └── output/                 # 아웃바운드 어댑터
│       ├── persistence/
│       │   ├── jpa/            # PostgreSQL (JPA Entity/Repository)
│       │   └── mongodb/        # MongoDB Repository
│       ├── external/           # 외부 API 클라이언트 (KIS, FRED)
│       ├── gcp/                # GCP 서비스
│       │   └── vertexai/       # Vertex AI 클라이언트
│       ├── messaging/          # Kafka 프로듀서
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
└── scheduler/                  # Quartz 설정
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
- **daily_stock_data**: 일별 주식 가격
  ```json
  {
    "date": "2025-02-05",
    "stocks": {
      "AAPL": { "close_price": 231.42 },
      "NVDA": { "close_price": 124.79 },
      ...
    },
    "yfinance_indicators": { "S&P 500 ETF": 597.20, ... },
    "fred_indicators": { ... }
  }
  ```
  - 기간: 2025-02-05 ~ 현재 (약 260일)
  - 종목: 35개 (미국 주식)
  - **주의**: close_price만 있음 (OHLCV 중 O/H/L/V 없음)
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
| Kafka | 이벤트 스트리밍 | `adapter/input/messaging/`, `adapter/output/messaging/` |

## 스케줄러 (Quartz)

- 설정: `scheduler/` 디렉토리
- 잡 정의: `adapter/input/scheduler/`
- Quartz 테이블: `V3__Create_Quartz_Tables.sql`

## 테스트

```bash
# 전체 테스트
./gradlew test

# 특정 테스트 클래스
./gradlew test --tests "com.quantjumpstock.core.SomeTest"
```

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

1. **[BACKEND_ARCHITECTURE.md](./quant-jump-stock-core/refactorplan/claude/architecture/BACKEND_ARCHITECTURE.md)**
   - 현재 아키텍처 분석 (점수: 65/100)
   - 주요 위반 사항 및 개선 방향
   - 14주 로드맵 (Phase 1-3)
   - 목표: Hexagonal Architecture 완성도 100/100

2. **[ADAPTER_PATTERNS.md](./quant-jump-stock-core/refactorplan/claude/patterns/ADAPTER_PATTERNS.md)**
   - Persistence Adapter 패턴 가이드
   - JPA/MongoDB Adapter 구현 예시
   - 매핑 전략 (toEntity/toDomain)
   - 실전 코드 예제

3. **[TESTING_GUIDE.md](./quant-jump-stock-core/refactorplan/claude/testing/TESTING_GUIDE.md)**
   - Kotest + MockK 단위 테스트
   - Testcontainers 통합 테스트
   - Property-Based Testing
   - 테스트 픽스처 패턴

4. **[MIGRATION_LOG.md](./quant-jump-stock-core/refactorplan/MIGRATION_LOG.md)**
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

1. **[BACKEND_ARCHITECTURE.md](./quant-jump-stock-core/refactorplan/claude/architecture/BACKEND_ARCHITECTURE.md)** 읽기 - 전체 계획 이해
2. **[ADAPTER_PATTERNS.md](./quant-jump-stock-core/refactorplan/claude/patterns/ADAPTER_PATTERNS.md)** 읽기 - 구현 패턴 학습
3. **[TESTING_GUIDE.md](./quant-jump-stock-core/refactorplan/claude/testing/TESTING_GUIDE.md)** 읽기 - 테스트 작성 방법
4. Week 1부터 단계적 진행
