# Database Relationships & Data Flow

## 연관 관계 요약

### PostgreSQL 내부 연관 관계

#### User 중심 관계
```
UserEntity (1:1) ─────────────────────────────────────── TradingConfigEntity
    │                                                         거래 설정
    │
    ├─ (1:1) ──────────────────────────────────────────── AccountBalanceEntity
    │                                                         계좌 잔액
    │
    ├─ (1:1) ──────────────────────────────────────────── UserKisAccountEntity
    │                                                         KIS 계정 정보
    │
    ├─ (1:1) ──────────────────────────────────────────── UserTierEntity
    │                                                         사용자 티어 (Rate Limiting)
    │
    ├─ (1:N) ──────────────────────────────────────────── TradeEntity
    │                                                         거래 내역
    │
    ├─ (1:N) ──────────────────────────────────────────── TradeSignalExecutedEntity
    │                                                         거래 신호 실행 기록
    │
    └─ (1:N) ──────────────────────────────────────────── KisTokenEntity
                                                              KIS Access Token
```

#### Trade 관련 관계
```
TradeEntity (1:1) ←───────── TradeSignalExecutedEntity
    │                            ↑
    │                            │
    │                            └── recommendation_id (MongoDB 참조)
    │
    └─── ticker ──→ StockEntity (PostgreSQL) ✨
                     └── 2026-02-01 마이그레이션 완료
```

---

## PostgreSQL ↔ MongoDB 연동

### 종목 데이터 흐름 (Stock 마이그레이션 완료)

```
StockEntity (PostgreSQL) ✨ 2026-02-01 마이그레이션
  │
  │ ticker로 참조 (Soft Reference)
  │
  ├──→ StockAnalysis (MongoDB - 기술적 분석)
  │        │
  │        └─── metrics: mae, rmse, accuracy
  │
  ├──→ SentimentAnalysis (MongoDB - 감정 분석)
  │        │
  │        └─── sentiment_score, article_count
  │
  ├──→ StockRecommendation (MongoDB - 종합 추천)
  │        │
  │        ├─── composite_score (종합 점수)
  │        ├─── technical_indicators (기술적 지표)
  │        └─── sentiment_score (감정 점수)
  │
  └──→ PredictionResult (MongoDB - Vertex AI 예측)
           │
           ├─── predicted_price
           ├─── confidence
           ├─── signal (BUY/SELL/HOLD)
           └─── vertex_ai_job_id
```

### 마이그레이션 배경
- **이전**: MongoDB `stocks` 컬렉션
- **현재**: PostgreSQL `stocks` 테이블
- **이유**: 주식 메타데이터는 정적 참조 데이터로 RDB가 더 적합
- **참조 방식**: ticker (String)로 Soft Reference 유지

---

## MongoDB 간 참조 관계

---

## 데이터 흐름 (Data Flow)

### 1. 경제 데이터 수집 → 분석 → 예측

```
EconomicData (경제 지표)
    ↓
    │ 매일 수집 (Scheduler)
    │
    ├──→ StockAnalysis 생성
    │       │
    │       └─── 기술적 분석 수행
    │
    └──→ SentimentAnalysis 생성
            │
            └─── 뉴스 감정 분석 수행

StockAnalysis + SentimentAnalysis
    ↓
    │ 종합 분석
    │
    └──→ StockRecommendation 생성
            │
            ├─── composite_score 계산
            └─── is_recommended 판단

StockRecommendation
    ↓
    │ Vertex AI CustomJob 실행
    │
    └──→ PredictionResult 생성
            │
            ├─── predicted_price
            ├─── confidence
            └─── signal (BUY/SELL/HOLD)
```

---

### 2. 자동 매매 프로세스

```
PredictionResult (Vertex AI 예측)
    ↓
    │ 신호 감지 (BUY/SELL)
    │
    └──→ TradingConfigEntity 확인
            │
            ├─── auto_trading_enabled = true?
            ├─── min_composite_score 충족?
            └─── max_stocks_to_buy 이내?

            ↓ YES
            │
            └──→ AccountBalanceEntity 확인
                    │
                    ├─── 사용 가능 현금 충분?
                    │    (getAvailableCash())
                    │
                    └─── locked_cash 업데이트

                    ↓
                    │
                    └──→ TradeEntity 생성
                            │
                            ├─── status: PENDING
                            ├─── ticker, quantity, price
                            │
                            └──→ KIS API 주문 실행
                                    │
                                    ├─── KisTokenEntity에서 토큰 조회
                                    ├─── UserKisAccountEntity에서 계좌 정보 조회
                                    │
                                    └──→ 주문 체결
                                            │
                                            ├─── TradeEntity 업데이트
                                            │    (status: EXECUTED, kis_order_id)
                                            │
                                            ├─── TradeSignalExecutedEntity 생성
                                            │    (execution_decision: EXECUTED)
                                            │
                                            └─── AccountBalanceEntity 업데이트
                                                 (cash, locked_cash 조정)
```

---

### 3. 백테스트 Rate Limiting

```text
백테스트 실행 요청 (POST /api/v1/backtest/run)
    ↓
    │ Authorization 헤더에서 userId 추출
    │
    └──→ UserEntity 조회 (userId: String → PK: Long)
            │
            └──→ UserTierEntity 조회 (findByUserId)
                    │
                    ├─── canPerformBacktest() 확인
                    │    ├─── FREE: backtestCountToday < 3
                    │    └─── PREMIUM: 항상 true
                    │
                    ├─── ❌ 한도 초과 → 429 응답
                    │    └─── BacktestRateLimitResponse
                    │         (Retry-After: 86400)
                    │
                    └─── ✅ 허용 → 백테스트 실행
                            │
                            └──→ 성공 시 incrementBacktestCount()
                                 (backtestCountToday + 1)
```

---

### 4. 손익 관리 (Stop Loss / Take Profit)

```
TradeEntity (EXECUTED 상태)
    ↓
    │ 매 1분마다 체크 (Scheduler)
    │
    └──→ 현재 시세 조회
            │
            ├─── 손익률 계산
            │    (현재가 - 매수가) / 매수가 * 100
            │
            ├─── TradingConfigEntity 비교
            │    │
            │    ├─── stop_loss_percent: -7.0%
            │    └─── take_profit_percent: 5.0%
            │
            └──→ 조건 충족 시 매도
                    │
                    ├─── TradeEntity 생성 (side: SELL)
                    ├─── KIS API 매도 주문
                    │
                    └─── 체결 완료
                            │
                            ├─── TradeEntity 업데이트
                            └─── AccountBalanceEntity 업데이트
                                 (cash 증가)
```

---

## 동시성 제어 시나리오

### AccountBalanceEntity 낙관적 락

```
사용자 A: 매수 요청 (AAPL)
사용자 A: 매수 요청 (TSLA)

Thread 1: AccountBalanceEntity 조회 (version: 1)
Thread 2: AccountBalanceEntity 조회 (version: 1)

Thread 1: cash 차감 → 저장 시도 (version: 2)  ✅ 성공
Thread 2: cash 차감 → 저장 시도 (version: 2)  ❌ 실패 (OptimisticLockException)

↓
Thread 2: 재시도
  ├─── AccountBalanceEntity 재조회 (version: 2)
  ├─── cash 잔액 재확인
  ├─── 충분한 경우 차감 → 저장 (version: 3)  ✅ 성공
  └─── 부족한 경우 주문 취소
```

---

## 인덱스 활용 쿼리 패턴

### PostgreSQL

#### 1. 사용자별 거래 내역 조회
```sql
SELECT * FROM trades
WHERE user_id = ?
  AND created_at >= ?
ORDER BY created_at DESC
LIMIT 100;

-- INDEX: (user_id, created_at DESC)
```

#### 2. 활성 토큰 조회
```sql
SELECT * FROM kis_tokens
WHERE user_id = ?
  AND account_type = ?
  AND is_active = true
  AND expiration_time > NOW();

-- INDEX: (user_id, account_type) UNIQUE
-- INDEX: expiration_time
```

#### 3. 실행된 거래 신호 조회
```sql
SELECT * FROM trade_signals_executed
WHERE user_id = ?
  AND execution_decision = 'EXECUTED'
  AND created_at >= ?;

-- INDEX: (user_id, execution_decision, created_at)
```

#### 4. 활성 종목 조회 ✨
```sql
SELECT * FROM stocks
WHERE is_active = true
ORDER BY ticker;

-- INDEX: idx_stocks_is_active (WHERE is_active = TRUE)
```

#### 5. 섹터별 종목 조회 ✨
```sql
SELECT * FROM stocks
WHERE sector = ?
  AND is_active = true
ORDER BY stock_name;

-- INDEX: idx_stocks_sector (WHERE sector IS NOT NULL)
```

#### 6. ETF 목록 조회 ✨
```sql
SELECT * FROM stocks
WHERE is_etf = true
  AND is_active = true;

-- INDEX: idx_stocks_is_etf (WHERE is_etf = TRUE)
```

### MongoDB

#### 1. 추천 종목 조회 (점수 높은 순)
```javascript
db.stock_recommendations.find({
  date: "2024-01-01",
  is_recommended: true
}).sort({ composite_score: -1 }).limit(10);

// INDEX: (date, is_recommended), composite_score DESC
```

#### 2. 예측 결과 조회 (신뢰도 높은 순)
```javascript
db.prediction_results.find({
  date: { $gte: ISODate("2024-01-01") },
  signal: "BUY"
}).sort({ confidence: -1 });

// INDEX: (date, signal), confidence DESC
```

#### 3. 감정 분석 조회
```javascript
db.sentiment_analysis.find({
  ticker: "AAPL",
  date: { $gte: "2024-01-01" }
}).sort({ date: -1 });

// INDEX: (ticker, date)
```

---

## 트랜잭션 경계

### PostgreSQL 트랜잭션

#### 1. 자동 매수 트랜잭션
```kotlin
@Transactional
fun executeBuyOrder(userId: Long, ticker: String, quantity: Int, price: BigDecimal) {
    // 1. 계좌 잔액 조회 및 락
    val accountBalance = accountBalanceRepository.findByUserId(userId)

    // 2. 사용 가능 현금 확인
    require(accountBalance.getAvailableCash() >= totalAmount)

    // 3. locked_cash 증가
    accountBalance.lockedCash += totalAmount
    accountBalanceRepository.save(accountBalance)  // @Version 검증

    // 4. 거래 생성
    val trade = TradeEntity(user, ticker, BUY, quantity, price, ...)
    tradeRepository.save(trade)

    // 5. KIS API 주문 (외부 API 호출)
    val kisOrderId = kisApiClient.createOrder(...)

    // 6. 거래 업데이트
    trade.kisOrderId = kisOrderId
    trade.status = EXECUTED
    tradeRepository.save(trade)

    // 7. 계좌 잔액 최종 업데이트
    accountBalance.cash -= totalAmount
    accountBalance.lockedCash -= totalAmount
    accountBalanceRepository.save(accountBalance)

    // 8. 실행 기록 생성
    val executed = TradeSignalExecutedEntity(user, recommendationId, ticker, ...)
    tradeSignalExecutedRepository.save(executed)
}
```

#### 2. 롤백 시나리오
```
트랜잭션 시작
  ├─ AccountBalance 조회 및 locked_cash 증가  ✅
  ├─ Trade 생성                              ✅
  ├─ KIS API 호출                            ❌ 실패 (네트워크 오류)
  │
  └─ 롤백
      ├─ Trade 삭제
      ├─ AccountBalance 복원 (locked_cash 감소)
      └─ TradeSignalExecutedEntity 생성 (execution_decision: FAILED)
```

---

## MongoDB 참조 무결성

### Soft Reference 패턴

```kotlin
// PostgreSQL → MongoDB 참조
data class TradeSignalExecutedEntity(
    val recommendationId: String,  // MongoDB StockRecommendation._id
    val ticker: String,            // PostgreSQL StockEntity.ticker ✨
    // ...
)

// 조회 시 JOIN 없이 별도 쿼리
val signal = tradeSignalExecutedRepository.findById(id)
val recommendation = stockRecommendationRepository.findById(signal.recommendationId)  // MongoDB
val stock = stockJpaRepository.findByTicker(signal.ticker)  // PostgreSQL ✨
```

### 참조 데이터 중복 저장 (Denormalization)

```kotlin
// 성능을 위한 비정규화
data class TradeSignalExecutedEntity(
    val recommendationId: String,
    val ticker: String,            // ✅ 중복 저장 (조회 성능 향상)
    val signal: TradeSignal,       // ✅ 중복 저장 (JOIN 없이 조회)
    val confidence: BigDecimal,    // ✅ 중복 저장
    // ...
)
```

---

## 데이터 정합성 유지

### 1. 주문 체결 확인 Job
```
매 5분마다 실행
  ├─ TradeEntity.status = PENDING인 주문 조회
  ├─ KIS API로 주문 상태 확인
  │
  ├─ 체결 완료 → status = EXECUTED 업데이트
  ├─ 주문 실패 → status = FAILED 업데이트
  └─ AccountBalance 동기화
```

### 2. 토큰 만료 정리 Job
```
매 1시간마다 실행
  ├─ KisTokenEntity.expiration_time < NOW() 조회
  ├─ is_active = false 업데이트
  └─ 7일 이상 만료된 토큰 삭제
```

### 3. MongoDB 데이터 정리 Job
```
매일 자정 실행
  ├─ stock_recommendations: 30일 이전 데이터 삭제
  ├─ prediction_results: 90일 이전 데이터 삭제
  └─ sentiment_analysis: 60일 이전 데이터 삭제
```

---

## 확장 고려사항

### 1. PostgreSQL 파티셔닝
```sql
-- trades 테이블 월별 파티셔닝
CREATE TABLE trades (
    ...
) PARTITION BY RANGE (created_at);

CREATE TABLE trades_2024_01 PARTITION OF trades
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
```

### 2. MongoDB 샤딩
```javascript
// prediction_results 컬렉션 샤딩 (date 기준)
sh.enableSharding("quantiq")
sh.shardCollection(
  "quantiq.prediction_results",
  { date: 1 }
)
```

### 3. 읽기 전용 레플리카
```
PostgreSQL
  ├─ Primary (읽기/쓰기)
  └─ Replica (읽기 전용)
      └─ 분석 쿼리, 리포트 생성

MongoDB
  ├─ Primary (읽기/쓰기)
  ├─ Secondary 1 (읽기 전용)
  └─ Secondary 2 (읽기 전용)
```

---

---

## 📚 관련 문서

### 데이터베이스
- [데이터베이스 스키마](./SCHEMA.md) - 전체 스키마 정의

### 아키텍처
- [하이브리드 데이터베이스 전략](../architecture/하이브리드_데이터베이스_전략.md) - PostgreSQL + MongoDB 전략
- [데이터베이스 마이그레이션 현황](../architecture/데이터베이스_마이그레이션_현황.md) - 마이그레이션 상태
- [시스템 아키텍처](../architecture/시스템_아키텍처.md) - 전체 시스템 구조
- [KIS 토큰 관리](../architecture/KIS_토큰_관리.md) - 토큰 관리 시스템

### 설정 가이드
- [RDB 빠른시작](../setup/RDB_빠른시작.md) - PostgreSQL 빠른 시작
- [초기 데이터 설정](../setup/초기_데이터_설정.md) - 초기 데이터 구성

### 레거시 (참고용)
- [마이그레이션 가이드](../legacy/migration/README.md) - MongoDB→PostgreSQL 완료
