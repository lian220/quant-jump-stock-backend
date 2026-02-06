# Quantiq 프로젝트 개요

## 프로젝트 소개

**Quantiq**는 주식 거래 시스템으로, **stock-trading 프로젝트의 개선 버전**입니다.
Python의 장점을 활용한 데이터 처리와 Spring Boot 기반의 자동매매 엔진으로 구성된 **하이브리드 아키텍처**입니다.

## 아키텍처 다이어그램

```
┌──────────────────────────────────────────────────────┐
│      Quantiq Data Engine (Python)                    │
│  ┌────────────────────────────────────────────────┐  │
│  │  FastAPI Server (Port 8000)                    │  │
│  │  - yfinance 주식 데이터 수집                    │  │
│  │  - Pandas/NumPy 기술적 분석                    │  │
│  │  - 거래 신호 생성                              │  │
│  └────────────────────────────────────────────────┘  │
└────────────────┬─────────────────────────────────────┘
                 │ Kafka Topic: stock-recommendations
                 ▼
┌──────────────────────────────────────────────────────┐
│      Quantiq Core (Spring Boot)                      │
│  ┌────────────────────────────────────────────────┐  │
│  │  REST API Server (Port 8080)                   │  │
│  │  - Kafka 메시지 리스너                          │  │
│  │  - 자동 거래 실행                              │  │
│  │  - KIS API 연동                                │  │
│  │  - 잔액/거래 기록 관리                          │  │
│  └────────────────────────────────────────────────┘  │
└────────────────┬──────────────┬──────────────────────┘
                 │              │
        ┌────────▼──────┐  ┌───▼──────────┐
        │   MongoDB     │  │ Kafka Broker │
        │  (Port 27017) │  │ (Port 9092)  │
        │  - Users      │  │ - Zookeeper  │
        │  - Trades     │  │ - Topics     │
        └───────────────┘  └──────────────┘
```

## 데이터 흐름

1. **데이터 수집** (Python)
   - yfinance에서 실시간 주식 데이터 수집
   - MongoDB에 저장

2. **기술적 분석** (Python)
   - Pandas/NumPy로 기술적 지표 계산
   - 이동평균, RSI, MACD 등 분석

3. **신호 생성 및 발행** (Python → Kafka)
   - 거래 신호 생성
   - Kafka 주제로 발행

4. **신호 수신** (Spring Boot)
   - Kafka 메시지 리스닝
   - StockRecommendation 도메인 모델 생성

5. **자동 거래 실행** (Spring Boot → KIS)
   - KIS (한국투자증권) API 호출
   - 매매 주문 실행

6. **기록 저장** (Spring Boot → MongoDB)
   - 거래 결과 저장
   - 사용자 잔액 업데이트

## 기술 스택

### Backend Services

#### Quantiq Core (Spring Boot + Kotlin)
- **Java Version**: JDK 21
- **Spring Boot**: 3.2.2
- **Build Tool**: Gradle
- **데이터 접근**: Spring Data JPA, MongoDB
- **메시징**: Spring Kafka
- **비동기**: Spring WebFlux
- **외부 API**: KIS (한국투자증권)

**주요 의존성**:
```
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-data-mongodb
- spring-boot-starter-webflux
- spring-kafka
- jackson-module-kotlin
- h2 (개발용)
```

#### Quantiq Data Engine (Python)
- **Python**: 3.11+
- **Package Manager**: Poetry
- **Web Framework**: FastAPI + Uvicorn
- **데이터 처리**: Pandas, NumPy
- **주식 데이터**: yfinance
- **메시징**: Confluent Kafka
- **데이터베이스**: PyMongo

**주요 의존성**:
```
- pandas ^2.2.0
- numpy ^1.26.0
- yfinance ^0.2.36
- pymongo ^4.6.1
- confluent-kafka ^2.3.0
- fastapi ^0.109.0
- uvicorn ^0.27.0
- requests ^2.31.0
- python-dotenv ^1.0.1
```

### Infrastructure

- **Message Broker**: Apache Kafka 7.5.0
- **Coordination**: Apache Zookeeper 7.5.0
- **Database**: MongoDB (latest)
- **Containerization**: Docker & Docker Compose
- **Network**: Docker Compose networking

## 프로젝트 구조

```
quantiq/
├── quantiq-core/                          # Spring Boot 애플리케이션
│   ├── src/main/kotlin/com/quantiq/core/
│   │   ├── QuantiqCoreApplication.kt     # 진입점
│   │   ├── domain/                        # 도메인 모델
│   │   │   ├── User.kt                   # 사용자
│   │   │   └── StockRecommendation.kt    # 거래 추천
│   │   ├── repository/                    # 데이터 접근
│   │   │   ├── UserRepository.kt
│   │   │   └── StockRecommendationRepository.kt
│   │   ├── service/                       # 비즈니스 로직
│   │   │   ├── AutoTradingService.kt     # 자동 거래
│   │   │   ├── BalanceService.kt         # 잔액 관리
│   │   │   ├── MockBalanceService.kt     # 모의 거래
│   │   │   └── KafkaMessageListener.kt   # 메시지 수신
│   │   └── config/                        # 설정
│   │       ├── KisConfig.kt              # KIS API 설정
│   │       └── KafkaConfig.kt            # Kafka 설정
│   ├── src/main/resources/
│   │   └── application.yml                # Spring 설정
│   ├── build.gradle.kts                   # Gradle 설정
│   ├── settings.gradle.kts
│   ├── Dockerfile
│   └── .gradle/                           # 빌드 캐시
│
├── quantiq-data-engine/                   # Python 데이터 엔진
│   ├── src/
│   │   ├── main.py                       # FastAPI 진입점
│   │   ├── config.py                     # 환경 설정
│   │   ├── db.py                         # MongoDB 연결
│   │   ├── services/
│   │   │   ├── __init__.py
│   │   │   ├── data_collector.py         # 주식 데이터 수집
│   │   │   └── technical_analysis.py     # 기술적 분석
│   │   └── events/
│   │       ├── __init__.py
│   │       └── publisher.py              # Kafka 메시지 발행
│   ├── pyproject.toml                    # Poetry 설정
│   ├── poetry.lock                       # 의존성 lock
│   ├── README.md
│   ├── Dockerfile
│   └── src/__pycache__/                  # Python 캐시
│
├── docs/                                  # 프로젝트 문서
│   ├── PROJECT_OVERVIEW.md               # 이 파일
│   ├── ARCHITECTURE.md
│   ├── SETUP_GUIDE.md
│   ├── COMMANDS.md
│   └── CODE_STYLE.md
│
├── scripts/                               # 유틸리티 스크립트
│
├── docker-compose.yml                    # 컨테이너 오케스트레이션
├── .env                                  # 환경 변수 (Git 제외)
└── .env.example                          # 환경 변수 템플릿
```

## 데이터베이스 스키마

### MongoDB Collections

#### users
```json
{
  "_id": "ObjectId",
  "email": "user@example.com",
  "password": "hashed_password",
  "name": "User Name",
  "created_at": "2024-01-01T00:00:00Z",
  "updated_at": "2024-01-01T00:00:00Z"
}
```

#### stock_recommendations
```json
{
  "_id": "ObjectId",
  "ticker": "AAPL",
  "signal": "BUY",
  "price": 150.25,
  "confidence": 0.85,
  "timestamp": "2024-01-01T10:00:00Z",
  "executed": false,
  "execution_price": null,
  "execution_time": null
}
```

#### balance (자동 거래 잔액)
```json
{
  "_id": "ObjectId",
  "user_id": "ObjectId",
  "cash": 1000000,
  "holdings": {
    "AAPL": 100,
    "MSFT": 50
  },
  "total_value": 1500000,
  "updated_at": "2024-01-01T10:00:00Z"
}
```

## 환경 설정

### 필수 환경 변수 (.env)

```env
# KIS (한국투자증권) API
KIS_APP_KEY=your_kis_app_key
KIS_APP_SECRET=your_kis_app_secret
KIS_ACCOUNT_NO=your_kis_account_number

# FRED API (선택사항 - 경제지표용)
FRED_API_KEY=your_fred_api_key

# MongoDB
MONGODB_URI=mongodb://mongodb:27017/stock_trading

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:29092

# Spring Boot
SPRING_PROFILES_ACTIVE=dev
```

## 서비스 포트

| 서비스 | 컨테이너 포트 | 호스트 포트 | 설명 |
|--------|--------------|-----------|------|
| Quantiq Core | 8080 | 10010 | Spring Boot REST API |
| Quantiq Data Engine | 8000 | 10020 | Python FastAPI |
| Kafka | 9092 | 9092 | 메시지 브로커 |
| Zookeeper | 2181 | 2181 | 리더십/조율 |
| MongoDB | 27017 | 27017 | 데이터베이스 |

## API 엔드포인트 (Spring Boot)

### 기본 API

```
GET  /health                    # 서비스 헬스 체크
POST /api/users                 # 사용자 생성
GET  /api/users/{id}            # 사용자 조회
GET  /api/recommendations       # 거래 추천 목록
POST /api/trading/execute       # 거래 실행
GET  /api/trading/history       # 거래 이력
```

### Python FastAPI

```
GET  /docs                      # Swagger UI
GET  /redoc                     # ReDoc
GET  /recommendations           # 추천 신호 조회
POST /analyze/{ticker}          # 기술적 분석
```

## 개발 환경 요구사항

- Docker & Docker Compose
- JDK 21 (로컬 Spring 개발)
- Python 3.11+ (로컬 Python 개발)
- Poetry (Python 패키지 관리)
- Gradle (Java 빌드)

## 다음 단계

1. [환경설정 가이드](../setup/환경설정_가이드.md) - 개발 환경 설정
2. [명령어 가이드](../setup/명령어_가이드.md) - 주요 개발 명령어
3. [코드 스타일 가이드](./CODE_STYLE.md) - 코딩 컨벤션
4. [시스템 아키텍처](../architecture/시스템_아키텍처.md) - 상세 아키텍처

---

## 📚 관련 문서

### 가이드라인
- [코드 스타일 가이드](./CODE_STYLE.md) - 코딩 컨벤션
- [문서 작성 가이드](./문서_작성_가이드.md) - 문서 작성 규칙

### 아키텍처
- [시스템 아키텍처](../architecture/시스템_아키텍처.md) - 전체 시스템 구조
- [이벤트 기반 아키텍처](../architecture/이벤트_기반_아키텍처.md) - Kafka 이벤트
- [하이브리드 데이터베이스 전략](../architecture/하이브리드_데이터베이스_전략.md) - DB 전략

### 설정 가이드
- [환경설정 가이드](../setup/환경설정_가이드.md) - 개발 환경 설정
- [배포 운영 가이드](../setup/배포_운영_가이드.md) - 프로덕션 배포

### 데이터베이스
- [데이터베이스 스키마](../database/SCHEMA.md) - 전체 스키마
- [데이터베이스 관계](../database/RELATIONSHIPS.md) - ERD 및 관계

### TODO 및 로드맵
- [기능 로드맵](../todo/기능_로드맵.md) - 전체 개발 로드맵
- [다음 작업](../todo/다음_작업.md) - 현재 진행 중인 작업
