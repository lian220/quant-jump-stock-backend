# [Implemented] Kafka Architecture & Event System
**Status**: Implemented / Active
**System Overview**: This document details the currently running Event-Driven Architecture using Kafka, implemented in `quant-jump-stock-backend/quant-jump-stock-core` and `quant-jump-stock-data-engine`.

---

# Kafka 기반 이벤트 드리븐 아키텍처

## 🏗️ 전체 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Frontend / Backoffice                            │
│                    (Next.js - Port 3000/4000)                           │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ HTTP REST API
                                │
┌───────────────────────────────▼─────────────────────────────────────────┐
│                         Core API (Spring Boot)                           │
│                            Port 10010                                    │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ REST Controllers                                                  │  │
│  │  - StrategyController: 전략 CRUD                                 │  │
│  │  - BacktestController: 백테스트 요청/결과 조회                  │  │
│  │  - TradingController: 실시간 트레이딩 제어                      │  │
│  └────────────┬────────────────────────────┬───────────────────────┘  │
│               │                            │                           │
│  ┌────────────▼────────────┐  ┌───────────▼───────────────┐          │
│  │ Application Services    │  │ Kafka Producers           │          │
│  │  - StrategyService      │  │  - BacktestRequestProducer│          │
│  │  - BacktestService      │  │  - TradingSignalProducer  │          │
│  │  - TradingService       │  │  - RiskAlertProducer      │          │
│  └─────────────────────────┘  └───────────┬───────────────┘          │
│                                            │                            │
└────────────────────────────────────────────┼────────────────────────────┘
                                             │
                ┌────────────────────────────┼──────────────────────────┐
                │        Apache Kafka        │                          │
                │        (Port 9092)         │                          │
                │  ┌─────────────────────────▼───────────────────────┐ │
                │  │ Topics:                                          │ │
                │  │                                                  │ │
                │  │  📬 backtest-requests                            │ │
                │  │     - partition: 3                               │ │
                │  │     - replication: 1                             │ │
                │  │     - retention: 7 days                          │ │
                │  │                                                  │ │
                │  │  📬 backtest-results                             │ │
                │  │     - partition: 3                               │ │
                │  │     - replication: 1                             │ │
                │  │     - retention: 30 days                         │ │
                │  │                                                  │ │
                │  │  📬 trading-signals                              │ │
                │  │     - partition: 5                               │ │
                │  │     - replication: 2                             │ │
                │  │     - retention: 365 days (분석용)               │ │
                │  │                                                  │ │
                │  │  📬 risk-alerts                                  │ │
                │  │     - partition: 2                               │ │
                │  │     - replication: 1                             │ │
                │  │     - retention: 90 days                         │ │
                │  │                                                  │ │
                │  │  📬 market-data                                  │ │
                │  │     - partition: 10                              │ │
                │  │     - replication: 1                             │ │
                │  │     - retention: 1 day (실시간 데이터)           │ │
                │  │                                                  │ │
                │  │  📬 order-executions                             │ │
                │  │     - partition: 3                               │ │
                │  │     - replication: 2                             │ │
                │  │     - retention: permanent (감사용)              │ │
                │  └──────────────────────────────────────────────────┘ │
                └──────────┬──────────────────┬────────────────────────┘
                           │                  │
         ┌─────────────────▼──────┐    ┌─────▼──────────────────┐
         │   Data Engine          │    │   Core API             │
         │   (Python FastAPI)     │    │   (Kafka Consumers)    │
         │   Port 10020           │    │                        │
         │                        │    │  - BacktestResult      │
         │  Kafka Consumers:      │    │    Consumer            │
         │  ┌──────────────────┐ │    │  - TradingSignal       │
         │  │ BacktestRequest  │ │    │    Consumer            │
         │  │ Consumer         │ │    │  - RiskAlert           │
         │  └────────┬─────────┘ │    │    Consumer            │
         │           │            │    └────────────────────────┘
         │  ┌────────▼─────────┐ │
         │  │ Backtest Engine  │ │
         │  │  1. Load Data    │ │
         │  │  2. Apply Logic  │ │
         │  │  3. Simulate     │ │
         │  │  4. Calculate    │ │
         │  └────────┬─────────┘ │
         │           │            │
         │  ┌────────▼─────────┐ │
         │  │ Kafka Producers  │ │
         │  │  - Backtest      │ │
         │  │    Results       │ │
         │  │  - Trading       │ │
         │  │    Signals       │ │
         │  └──────────────────┘ │
         └────────────────────────┘
                    │
        ┌───────────┴───────────┐
        │                       │
┌───────▼──────┐       ┌────────▼────────┐
│  PostgreSQL  │       │    MongoDB      │
│  (Port 5432) │       │  (Port 27017)   │
│              │       │                 │
│ - strategies │       │ - stock_prices  │
│ - backtest_  │       │ - indicators    │
│   results    │       │ - predictions   │
│ - users      │       │                 │
└──────────────┘       └─────────────────┘
```

---

## 🤔 왜 Kafka 아키텍처를 선택했는가?

### 1. **비동기 처리 (Async Processing)**
백테스트는 10초에서 5분까지 소요되는 **장시간 실행 작업**입니다.
- ❌ **동기 방식**: HTTP 요청이 5분간 블로킹 → 타임아웃, 사용자 경험 저하
- ✅ **Kafka 방식**: 요청 즉시 응답(~100ms) → 백그라운드 처리 → 완료 시 결과 전달

```
동기 처리:
[Frontend] --5분 대기--> [Core API] --5분 대기--> [Data Engine]
❌ 문제: 타임아웃, 리소스 낭비, UI 블로킹

Kafka 처리:
[Frontend] --즉시 응답--> [Core API] --메시지 발행--> [Kafka Queue]
                                                        ↓
                                        [Data Engine] 백그라운드 처리
                                                        ↓
[Frontend] <--결과 폴링-- [Core API] <--결과 컨슘-- [Kafka Queue]
✅ 장점: 즉시 응답, 병렬 처리, 큐잉
```

### 2. **서비스 간 결합도 낮춤 (Loose Coupling)**
Core API(Spring Boot)와 Data Engine(Python)을 **독립적으로 개발/배포/확장** 가능합니다.
- Core API가 다운되어도 Data Engine은 메시지를 계속 처리
- Data Engine 업데이트 시 Core API 영향 없음
- 언어/프레임워크 독립성 유지 (Kotlin ↔ Python)

### 3. **수평 확장성 (Horizontal Scalability)**
처리량 증가 시 **Consumer만 추가**하면 자동으로 부하 분산됩니다.

```
초기 구성:
[Kafka Topic: backtest-requests (3 partitions)]
       ↓
[Data Engine #1] → Partition 0, 1, 2 처리

트래픽 증가 후:
[Kafka Topic: backtest-requests (3 partitions)]
   ↓         ↓         ↓
[Engine #1] [Engine #2] [Engine #3]
Partition 0 Partition 1 Partition 2
→ 처리 능력 3배 증가
```

### 4. **신뢰성 및 복구 (Reliability & Recovery)**
- **메시지 영속성**: 디스크 저장 (retention 7~365일)
- **재처리 가능**: Consumer 재시작 시 오프셋부터 재처리
- **At-Least-Once 보장**: 메시지 손실 없음
- **Dead Letter Queue**: 실패한 메시지 별도 관리

### 5. **이벤트 소싱 및 감사 추적 (Event Sourcing & Audit Trail)**
모든 이벤트가 Kafka에 기록되어 **완벽한 감사 추적**이 가능합니다.
- `order-executions` 토픽: 영구 보관 (법적 요구사항)
- 과거 데이터 재처리 (replay)
- 디버깅 및 분석 용이

### 6. **발행-구독 패턴 (Pub-Sub Pattern)**
하나의 이벤트를 **여러 Consumer가 독립적으로 처리**할 수 있습니다.

```
[trading-signals 토픽]
       ↓
   ┌───┴───┬───────┬──────┐
   ↓       ↓       ↓      ↓
[Core API] [Backoffice] [Slack] [ML Model]
주문 실행   대시보드 표시  알림   학습 데이터
```

### 7. **부하 평준화 (Load Leveling)**
급격한 트래픽 증가 시 **큐에 쌓아두고 천천히 처리**합니다.
- 피크 시간대: 초당 100개 요청 → 큐에 저장
- 백그라운드: 초당 10개씩 안정적으로 처리
- 시스템 과부하 방지

---

## 🗄️ 왜 MongoDB와 PostgreSQL에 다른 데이터를 저장하는가?

### 데이터 특성에 따른 최적 데이터베이스 선택

| 특성 | PostgreSQL | MongoDB |
|------|------------|---------|
| **데이터 타입** | 구조화된 관계형 데이터 | 비구조화된 시계열 데이터 |
| **스키마** | 고정 스키마 (테이블 설계) | 유연한 스키마 (Document) |
| **트랜잭션** | ACID 보장 필수 | Eventual Consistency 허용 |
| **쿼리 패턴** | 복잡한 JOIN, 집계 | 시간 범위 검색, 단순 조회 |
| **데이터 볼륨** | 중간 규모 (수백만 건) | 대용량 (수천만~수억 건) |
| **변경 빈도** | 낮음 (메타데이터) | 높음 (실시간 스트림) |
| **인덱싱** | B-Tree, GIN (JSONB) | 시계열 인덱스 최적화 |

---

### PostgreSQL 저장 데이터

#### 1. **전략 메타데이터 (strategies 테이블)**
```sql
CREATE TABLE strategies (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100),
    category VARCHAR(50),
    conditions JSONB,  -- 매매 조건 (유연성 + 인덱싱)
    risk_settings JSONB,
    status VARCHAR(20),
    last_cagr DECIMAL(5,2),
    created_at TIMESTAMP
);
```

**선택 이유**:
- ✅ **ACID 트랜잭션**: 전략 수정 시 데이터 일관성 보장
- ✅ **복잡한 JOIN**: `strategies ↔ backtest_results ↔ users` 조인 필요
- ✅ **JSONB 인덱싱**: `conditions` 필드에 GIN 인덱스로 빠른 검색
- ✅ **외래키 제약**: 참조 무결성 보장

#### 2. **백테스트 결과 요약 (backtest_results 테이블)**
```sql
CREATE TABLE backtest_results (
    id SERIAL PRIMARY KEY,
    job_id UUID UNIQUE,
    strategy_id INTEGER REFERENCES strategies(id),
    start_date DATE,
    end_date DATE,
    cagr DECIMAL(5,2),
    mdd DECIMAL(5,2),
    sharpe_ratio DECIMAL(5,3),
    total_trades INTEGER,
    status VARCHAR(20),
    created_at TIMESTAMP
);
```

**선택 이유**:
- ✅ **관계형 데이터**: strategy_id로 전략과 1:N 관계
- ✅ **집계 쿼리**: 전략별 평균 CAGR, MDD 계산
- ✅ **정렬 및 필터링**: 날짜 범위, 성과 지표 기준 조회
- ✅ **작은 데이터 크기**: 백테스트 1건당 1행 (메타데이터만)

#### 3. **사용자 및 RBAC (users, roles, permissions)**
```sql
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE,
    role_id INTEGER REFERENCES roles(id),
    subscription_tier VARCHAR(20),
    created_at TIMESTAMP
);
```

**선택 이유**:
- ✅ **ACID 필수**: 사용자 인증, 권한 관리는 데이터 일관성 중요
- ✅ **복잡한 권한 체계**: users ↔ roles ↔ permissions 다중 JOIN
- ✅ **트랜잭션 보장**: 결제 처리, 구독 변경 등 원자성 보장

---

### MongoDB 저장 데이터

#### 1. **시계열 주식 가격 데이터 (stock_prices 컬렉션)**
```javascript
{
  "_id": ObjectId("..."),
  "symbol": "005930",
  "date": ISODate("2024-01-15T00:00:00Z"),
  "open": 50000,
  "high": 51000,
  "low": 49500,
  "close": 50500,
  "volume": 15234567,
  "created_at": ISODate("2024-01-15T09:00:00Z")
}
```

**선택 이유**:
- ✅ **대용량 시계열 데이터**: 종목당 일봉 20년 = 5,000건 × 3,000종목 = 1,500만 건
- ✅ **빠른 쓰기 속도**: 실시간 시세 스트림 (초당 수천 건) 처리
- ✅ **Time-Series 최적화**: MongoDB 5.0+ Time-Series Collection
- ✅ **스키마 유연성**: 새로운 필드 추가 시 마이그레이션 불필요

#### 2. **기술 지표 데이터 (indicators 컬렉션)**
```javascript
{
  "_id": ObjectId("..."),
  "symbol": "005930",
  "date": ISODate("2024-01-15"),
  "indicators": {
    "sma_20": 49500,
    "sma_50": 48000,
    "rsi": 45.3,
    "macd": 123.5,
    "bollinger_upper": 52000,
    "bollinger_lower": 47000
  }
}
```

**선택 이유**:
- ✅ **동적 스키마**: 전략마다 필요한 지표가 다름 (SMA, RSI, MACD, Bollinger...)
- ✅ **Nested Document**: 여러 지표를 하나의 Document에 저장
- ✅ **빠른 범위 조회**: 날짜 기반 시계열 검색 최적화
- ✅ **데이터 압축**: MongoDB WiredTiger 압축으로 디스크 절약

#### 3. **AI 예측 결과 (predictions 컬렉션)**
```javascript
{
  "_id": ObjectId("..."),
  "symbol": "005930",
  "prediction_date": ISODate("2024-01-15"),
  "model": "LSTM_v2.3",
  "predictions": {
    "next_day": 51000,
    "next_week": 52000,
    "next_month": 55000
  },
  "confidence": 0.85,
  "features_used": ["price_history", "volume", "sentiment"]
}
```

**선택 이유**:
- ✅ **스키마 진화**: 모델 버전 변경 시 필드 추가 용이
- ✅ **대용량 예측 데이터**: 일별 예측 × 종목 수 = 수백만 건
- ✅ **빠른 조회**: 최신 예측만 조회하는 경우가 많음

---

### 데이터베이스 분리 전략의 성능 이점

#### 1. **쿼리 최적화**
```
PostgreSQL 강점:
SELECT s.name, AVG(b.cagr) as avg_cagr
FROM strategies s
JOIN backtest_results b ON s.id = b.strategy_id
WHERE s.category = 'MOMENTUM'
GROUP BY s.name;
→ JOIN, GROUP BY 등 복잡한 집계 쿼리 최적화

MongoDB 강점:
db.stock_prices.find({
  symbol: "005930",
  date: { $gte: ISODate("2023-01-01"), $lte: ISODate("2023-12-31") }
}).sort({ date: 1 });
→ 시간 범위 검색 및 시계열 조회 최적화
```

#### 2. **확장성**
- **PostgreSQL**: 읽기 replica 추가로 조회 성능 향상 (메타데이터 조회)
- **MongoDB**: Sharding으로 시계열 데이터 분산 저장 (symbol 기준 샤딩)

#### 3. **백업 및 복구**
- **PostgreSQL**: 논리적 백업 (pg_dump) - 작은 크기, 빠른 복구
- **MongoDB**: 스냅샷 백업 - 대용량 시계열 데이터, 증분 백업

---

### 실제 사용 예시

#### 백테스트 실행 플로우
```python
# 1. PostgreSQL에서 전략 메타데이터 조회
strategy = postgres.execute(
    "SELECT conditions, risk_settings FROM strategies WHERE id = 3"
)

# 2. MongoDB에서 주가 데이터 로드 (대용량)
prices = mongodb.stock_prices.find({
    "symbol": {"$in": ["005930", "000660"]},
    "date": {"$gte": start_date, "$lte": end_date}
})

# 3. MongoDB에서 기술 지표 계산 결과 로드
indicators = mongodb.indicators.find({
    "symbol": "005930",
    "date": {"$gte": start_date, "$lte": end_date}
})

# 4. 백테스트 실행 후 결과를 PostgreSQL에 저장 (요약)
postgres.execute(
    "INSERT INTO backtest_results (job_id, strategy_id, cagr, mdd, ...) VALUES (...)"
)

# 5. 상세 거래 내역은 PostgreSQL (구조화된 데이터)
postgres.execute_many(
    "INSERT INTO backtest_trades (backtest_id, date, action, symbol, ...) VALUES (...)"
)
```

---

### 결론

**Kafka + PostgreSQL + MongoDB 조합**은 각 기술의 강점을 최대한 활용합니다:

- **Kafka**: 비동기 처리, 확장성, 신뢰성
- **PostgreSQL**: 메타데이터, 관계형 데이터, ACID 트랜잭션
- **MongoDB**: 대용량 시계열 데이터, 유연한 스키마, 빠른 쓰기

이 아키텍처로 **실시간 트레이딩 시스템의 성능, 확장성, 신뢰성**을 모두 확보할 수 있습니다.

---

## 📨 Kafka Topics 상세 설계

### 1. backtest-requests
**용도**: 백테스트 실행 요청

**메시지 구조**:
```json
{
  "job_id": "uuid-v4",
  "strategy_id": 3,
  "strategy_name": "골든크로스",
  "conditions": {
    "indicators": { ... },
    "buy_conditions": [ ... ],
    "sell_conditions": [ ... ],
    "risk_settings": { ... }
  },
  "parameters": {
    "start_date": "2020-01-01",
    "end_date": "2023-12-31",
    "initial_capital": 10000000,
    "symbols": ["005930", "000660"]
  },
  "requested_by": "admin_user_id",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Partition Key**: `strategy_id % 3` (동일 전략은 같은 파티션)
**Retention**: 7일 (백테스트 재실행 방지)

---

### 2. backtest-results
**용도**: 백테스트 완료 결과

**메시지 구조**:
```json
{
  "job_id": "uuid-v4",
  "strategy_id": 3,
  "status": "COMPLETED",
  "metrics": {
    "cagr": 18.5,
    "mdd": -12.3,
    "sharpe_ratio": 1.24,
    "win_rate": 65.5,
    "total_trades": 150,
    "total_commission": 45000,
    "total_slippage": 30000,
    "profit_factor": 1.85,
    "expectancy": 12500
  },
  "equity_curve": [10000000, 10050000, ...],
  "trades": [
    {
      "date": "2020-01-15",
      "action": "BUY",
      "symbol": "005930",
      "shares": 100,
      "execution_price": 50000,
      "commission": 750,
      "slippage": 500
    },
    ...
  ],
  "completed_at": "2024-01-15T10:35:00Z",
  "execution_time_ms": 5234
}
```

**Partition Key**: `job_id % 3`
**Retention**: 30일 (분석 및 비교용)

---

### 3. trading-signals
**용도**: 실시간 매매 신호

**메시지 구조**:
```json
{
  "signal_id": "uuid-v4",
  "strategy_id": 3,
  "strategy_name": "골든크로스",
  "signal_type": "BUY",  // "BUY", "SELL", "HOLD"
  "symbol": "005930",
  "price": 50000,
  "confidence": 0.85,
  "indicators": {
    "sma_20": 49500,
    "sma_50": 48000,
    "rsi": 45,
    "volume_ratio": 1.2
  },
  "recommended_quantity": 100,
  "risk_score": 0.3,  // 0(안전) ~ 1(위험)
  "timestamp": "2024-01-15T14:30:00Z",
  "valid_until": "2024-01-15T14:35:00Z"  // 5분 유효
}
```

**Partition Key**: `symbol` (종목별 순서 보장)
**Retention**: 365일 (트레이딩 패턴 분석용)

---

### 4. risk-alerts
**용도**: 리스크 경고 알림

**메시지 구조**:
```json
{
  "alert_id": "uuid-v4",
  "alert_type": "STOP_LOSS_TRIGGERED",  // "STOP_LOSS", "MAX_DRAWDOWN", "POSITION_LIMIT", "VIX_SPIKE"
  "severity": "HIGH",  // "LOW", "MEDIUM", "HIGH", "CRITICAL"
  "strategy_id": 3,
  "symbol": "005930",
  "current_value": -5.2,
  "threshold": -5.0,
  "description": "골든크로스 전략 삼성전자 포지션 손절 임계값 도달",
  "recommended_action": "청산 권장",
  "user_ids": ["user_123", "user_456"],  // 알림 대상
  "timestamp": "2024-01-15T14:30:00Z"
}
```

**Partition Key**: `alert_type`
**Retention**: 90일 (리스크 분석용)

---

### 5. market-data
**용도**: 실시간 시장 데이터 스트림

**메시지 구조**:
```json
{
  "symbol": "005930",
  "price": 50000,
  "change": 500,
  "change_pct": 1.01,
  "volume": 15234567,
  "high": 50500,
  "low": 49500,
  "bid": 49950,
  "ask": 50050,
  "bid_size": 1000,
  "ask_size": 800,
  "timestamp": "2024-01-15T14:30:05.123Z"
}
```

**Partition Key**: `symbol`
**Retention**: 1일 (실시간 처리 후 MongoDB 저장)

---

### 6. order-executions
**용도**: 실제 주문 체결 기록 (감사 추적)

**메시지 구조**:
```json
{
  "order_id": "uuid-v4",
  "user_id": "user_123",
  "strategy_id": 3,
  "order_type": "MARKET",  // "MARKET", "LIMIT", "STOP"
  "action": "BUY",
  "symbol": "005930",
  "requested_quantity": 100,
  "executed_quantity": 100,
  "requested_price": 50000,
  "executed_price": 50050,
  "slippage": 50,
  "commission": 750,
  "tax": 0,
  "total_cost": 5005750,
  "order_status": "FILLED",  // "PENDING", "PARTIAL", "FILLED", "CANCELLED"
  "broker": "KIS",
  "broker_order_id": "KIS-202401150001",
  "requested_at": "2024-01-15T14:30:00Z",
  "executed_at": "2024-01-15T14:30:02.456Z"
}
```

**Partition Key**: `user_id`
**Retention**: Permanent (법적 감사 추적 요구사항)
**Compaction**: 불가 (모든 이력 보존 필수)

---

## 🔄 이벤트 플로우 시나리오

### 시나리오 1: 백테스트 실행

```
[Backoffice Admin]
    │
    │ 1. POST /api/admin/backtest/strategies/3/run
    ▼
[Core API - BacktestController]
    │
    │ 2. 전략 정보 조회 (PostgreSQL)
    │ 3. 백테스트 요청 메시지 생성
    ▼
[Kafka Producer - BacktestRequestProducer]
    │
    │ 4. Publish to "backtest-requests"
    ▼
[Kafka Broker]
    │
    │ 5. Store & Route message
    ▼
[Data Engine - BacktestConsumer]
    │
    │ 6. Consume from "backtest-requests"
    │ 7. 데이터 로드 (MongoDB: stock_prices)
    │ 8. 백테스트 실행 (enhanced_backtest_service.py)
    │    - 조건 평가
    │    - 거래 시뮬레이션
    │    - 리스크 관리 적용
    │    - 성과 지표 계산
    ▼
[Kafka Producer - BacktestResultProducer]
    │
    │ 9. Publish to "backtest-results"
    ▼
[Kafka Broker]
    │
    │ 10. Store & Route message
    ▼
[Core API - BacktestResultConsumer]
    │
    │ 11. Consume from "backtest-results"
    │ 12. 결과 저장 (PostgreSQL: backtest_results, backtest_trades)
    │ 13. 전략 메타데이터 업데이트 (strategies.last_cagr, last_mdd, etc.)
    ▼
[Backoffice Admin]
    │
    │ 14. GET /api/admin/backtest/jobs/{jobId} (Polling)
    │ 15. 결과 조회 및 차트 표시
    ▼
```

**타임라인**:
- 1-4단계: ~100ms (API 처리)
- 5단계: ~10ms (Kafka 저장)
- 6-8단계: 10초 ~ 5분 (데이터 크기에 따라)
- 9-10단계: ~10ms
- 11-13단계: ~500ms (DB 저장)
- **총 소요 시간: 10초 ~ 5분**

---

### 시나리오 2: 실시간 트레이딩 신호

```
[KIS API] → 실시간 시세 수신
    │
    ▼
[Data Engine - MarketDataConsumer]
    │
    │ 1. 시세 수신 및 정규화
    ▼
[Kafka Producer]
    │
    │ 2. Publish to "market-data"
    ▼
[Kafka Broker] → Partition by symbol
    │
    ├─▼ [Signal Generator #1] (Partition 0-3)
    │   │ 3. 구독 전략 조회 (strategies where status='ACTIVE')
    │   │ 4. 각 전략 조건 평가
    │   │ 5. 매매 신호 생성
    │   │
    │   ▼
    │   [Kafka Producer]
    │       │ 6. Publish to "trading-signals"
    │       ▼
    │
    ├─▼ [Signal Generator #2] (Partition 4-7)
    │   (동일 프로세스)
    │
    └─▼ [Signal Generator #3] (Partition 8-10)
        (동일 프로세스)

[Kafka Broker - trading-signals]
    │
    ├─▼ [Core API - TradingSignalConsumer]
    │   │ 7. 신호 수신
    │   │ 8. 사용자 구독 확인 (strategy_subscriptions)
    │   │ 9. 리스크 체크 (현재 포지션, 자산 상태)
    │   │
    │   ├─▼ 리스크 OK
    │   │   │ 10. 주문 생성 (KIS API 호출)
    │   │   │ 11. Publish to "order-executions"
    │   │   ▼
    │   │
    │   └─▼ 리스크 NG
    │       │ 10. Publish to "risk-alerts"
    │       │ 11. 사용자 알림 (Slack, Push)
    │       ▼
    │
    └─▼ [Frontend - WebSocket]
        │ 7. 실시간 신호 전달
        │ 8. UI 업데이트
        ▼
```

**타임라인** (실시간):
- 1-2단계: ~50ms
- 3-5단계: ~200ms (전략 평가)
- 6단계: ~10ms
- 7-9단계: ~100ms
- 10-11단계: ~300ms (KIS API 호출)
- **총 Latency: ~660ms (1초 이내)**

---

### 시나리오 3: 리스크 경고 처리

```
[Risk Monitor Service] (scheduled every 1 minute)
    │
    │ 1. 모든 활성 포지션 조회
    │ 2. 각 포지션 리스크 평가
    │    - 손익률 체크
    │    - 포트폴리오 MDD 체크
    │    - 섹터 집중도 체크
    │    - VIX 지수 체크
    ▼
[리스크 임계값 초과 감지]
    │
    │ 3. 경고 메시지 생성
    ▼
[Kafka Producer]
    │
    │ 4. Publish to "risk-alerts"
    ▼
[Kafka Broker]
    │
    ├─▼ [Core API - RiskAlertConsumer]
    │   │ 5. 경고 수신
    │   │ 6. 심각도 판단
    │   │
    │   ├─▼ CRITICAL
    │   │   │ 7. 자동 청산 (긴급)
    │   │   │ 8. 관리자 즉시 알림 (SMS, Call)
    │   │   ▼
    │   │
    │   ├─▼ HIGH
    │   │   │ 7. 포지션 축소 권장
    │   │   │ 8. Slack 알림
    │   │   ▼
    │   │
    │   └─▼ MEDIUM/LOW
    │       │ 7. 로깅만
    │       ▼
    │
    └─▼ [Backoffice Dashboard]
        │ 5. 실시간 경고 표시
        │ 6. 포지션 현황 업데이트
        ▼
```

---

## ⚙️ Kafka 설정 및 운영

### Producer 설정 (Core API)

```kotlin
// KafkaProducerConfig.kt
@Configuration
class KafkaProducerConfig {
    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        val configProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "kafka:29092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,

            // 성능 최적화
            ProducerConfig.ACKS_CONFIG to "1",  // 리더만 확인 (빠름)
            ProducerConfig.COMPRESSION_TYPE_CONFIG to "snappy",  // 압축
            ProducerConfig.BATCH_SIZE_CONFIG to 16384,  // 16KB 배치
            ProducerConfig.LINGER_MS_CONFIG to 10,  // 10ms 대기 (배치 효율)

            // 신뢰성
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 5,

            // 멱등성 (중복 방지)
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
        )
        return DefaultKafkaProducerFactory(configProps)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory())
    }
}

// BacktestRequestProducer.kt
@Component
class BacktestRequestProducer(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val topic = "backtest-requests"

    fun sendBacktestRequest(message: BacktestJobMessage): CompletableFuture<SendResult<String, String>> {
        val key = message.strategyId.toString()  // Partition key
        val value = objectMapper.writeValueAsString(message)

        return kafkaTemplate.send(topic, key, value).also { future ->
            future.whenComplete { result, ex ->
                if (ex != null) {
                    logger.error("Failed to send backtest request: ${message.jobId}", ex)
                } else {
                    logger.info("Backtest request sent: ${message.jobId} to partition ${result.recordMetadata.partition()}")
                }
            }
        }
    }
}
```

### Consumer 설정 (Core API)

```kotlin
// KafkaConsumerConfig.kt
@Configuration
@EnableKafka
class KafkaConsumerConfig {
    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
        val configProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "kafka:29092",
            ConsumerConfig.GROUP_ID_CONFIG to "core-api-consumers",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,

            // 오프셋 관리
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",  // 처음부터 읽기
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,  // 수동 커밋 (신뢰성)

            // 성능
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 100,  // 한 번에 최대 100개
            ConsumerConfig.FETCH_MIN_BYTES_CONFIG to 1024,  // 최소 1KB
            ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG to 500  // 최대 500ms 대기
        )
        return DefaultKafkaConsumerFactory(configProps)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory()
        factory.setConcurrency(3)  // 3개 스레드 병렬 처리
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        return factory
    }
}

// BacktestResultConsumer.kt
@Component
class BacktestResultConsumer(
    private val backtestService: BacktestService,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["backtest-results"],
        groupId = "core-api-consumers",
        concurrency = "3"
    )
    fun consumeBacktestResult(
        message: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment
    ) {
        try {
            val result = objectMapper.readValue(message.value(), BacktestResultMessage::class.java)

            logger.info("Received backtest result: ${result.jobId}")

            // 결과 처리
            backtestService.handleBacktestResult(result)

            // 수동 커밋 (처리 완료 후)
            acknowledgment.acknowledge()

            logger.info("Backtest result processed: ${result.jobId}")

        } catch (e: Exception) {
            logger.error("Failed to process backtest result", e)
            // DLQ(Dead Letter Queue)로 이동 또는 재시도
            throw e
        }
    }
}
```

### Consumer 설정 (Data Engine - Python)

```python
# kafka_consumer.py
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
import json
import asyncio

class BacktestRequestConsumer:
    def __init__(self):
        self.consumer = AIOKafkaConsumer(
            'backtest-requests',
            bootstrap_servers='kafka:29092',
            group_id='data-engine-backtest',
            key_deserializer=lambda k: k.decode('utf-8') if k else None,
            value_deserializer=lambda v: json.loads(v.decode('utf-8')),

            # 성능 설정
            max_poll_records=10,
            fetch_min_bytes=1024,
            fetch_max_wait_ms=500,

            # 오프셋 관리
            enable_auto_commit=False,  # 수동 커밋
            auto_offset_reset='earliest'
        )

        self.producer = AIOKafkaProducer(
            bootstrap_servers='kafka:29092',
            key_serializer=lambda k: k.encode('utf-8') if k else None,
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),

            # 신뢰성
            acks='1',
            retries=3,

            # 성능
            compression_type='snappy',
            batch_size=16384,
            linger_ms=10
        )

    async def start(self):
        await self.consumer.start()
        await self.producer.start()

        try:
            async for message in self.consumer:
                await self.process_message(message)
        finally:
            await self.consumer.stop()
            await self.producer.stop()

    async def process_message(self, message):
        try:
            request = message.value
            job_id = request['job_id']

            logger.info(f"Processing backtest request: {job_id}")

            # 백테스트 실행
            result = await self.backtest_service.run_backtest(request)

            # 결과 발행
            await self.producer.send(
                'backtest-results',
                key=str(request['strategy_id']),
                value=result
            )

            # 커밋
            await self.consumer.commit()

            logger.info(f"Backtest completed: {job_id}")

        except Exception as e:
            logger.error(f"Failed to process message: {e}")
            # 재시도 로직 또는 DLQ 전송
```

---

## 🔍 모니터링 및 운영

### Kafka UI (localhost:8089)
```yaml
메트릭 모니터링:
- Topic별 메시지 처리량 (msg/sec)
- Consumer Lag (지연 메시지 수)
- Partition 분산 상태
- Disk 사용량
```

### 주요 메트릭 알람 설정

```yaml
consumer_lag_threshold: 1000  # 1000개 이상 밀리면 알람
partition_disk_usage: 80%     # 80% 이상 사용 시 알람
producer_error_rate: 1%       # 에러율 1% 이상 시 알람
```

---

## 📚 관련 문서

### 기술 문서
- [전략 관리 구현 계획](./strategy-management-implementation-plan.md) - 전략 시스템 구현
- [트레이딩 시스템 보완](./trading-system-enhancements.md) - 실전 트레이딩

### Jira 계획
- [Jira 티켓 계획](../jira-ticket-plan.md) - 전체 티켓 계획
- [Epic 1: 백테스트](../jira-epics/epic-1-backtest.md) - 백테스트 관련
- [Epic 5: 대시보드](../jira-epics/epic-5-dashboard.md) - 알림 시스템

### API 명세
- [Scheduler API](../api/scheduler.md) - 스케줄러 API
- [Trading API](../api/trading.md) - 트레이딩 API

### 프로젝트 문서
- [ERD](../erd.md) - 데이터베이스 스키마
- [PRD v2.0](../prd-v2.md) - 제품 요구사항

### Backend 문서
- [이벤트 기반 아키텍처](../../quant-jump-stock-backend/docs/architecture/이벤트_기반_아키텍처.md)
- [스케줄러 아키텍처](../../quant-jump-stock-backend/docs/architecture/스케줄러_아키텍처.md)
- [시스템 아키텍처](../../quant-jump-stock-backend/docs/architecture/시스템_아키텍처.md)

---

## 🎯 장점 요약

### 비동기 처리
- 백테스트 요청 즉시 응답 (UI 블로킹 없음)
- 대량 요청 처리 가능 (큐잉)

### 확장성
- Consumer 추가로 처리 능력 증가
- Partition 추가로 처리량 증가

### 신뢰성
- 메시지 영속성 (디스크 저장)
- 재시도 메커니즘
- 순서 보장 (Partition 내)

### 관찰 가능성
- 모든 이벤트 추적 가능
- 디버깅 용이
- 감사 추적 (order-executions)
