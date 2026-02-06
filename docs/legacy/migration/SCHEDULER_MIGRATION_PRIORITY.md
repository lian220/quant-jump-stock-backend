# 🚀 스케줄러 마이그레이션 (최우선)

**목표:** AutoTradingService를 MongoDB 기반에서 PostgreSQL 기반으로 전환

**소요시간:** 3-4일 | **난이도:** 중상 | **위험도:** 중 (자동 매매 관련)

---

## 📊 현재 스케줄러 구조

```
┌─────────────────────────────────────────────────┐
│  quantiq-data-engine (Python)                  │
│  분석 완료 → Kafka: quantiq.analysis.completed  │
└────────────────┬────────────────────────────────┘
                 │
                 ↓
┌─────────────────────────────────────────────────┐
│  quantiq-core (Spring Boot)                    │
│  KafkaMessageListener                          │
│  ├─ quantiq.analysis.completed 토픽 구독       │
│  └─ AutoTradingService 호출                    │
└────────────────┬────────────────────────────────┘
                 │
                 ↓
    ┌───────────────────────────────┐
    │  AutoTradingService           │
    ├───────────────────────────────┤
    │ 1. Recommendations 조회       │
    │    ├─ MongoDB: stock_         │
    │    │  recommendations         │
    │    │  (date, is_recommended)  │
    │    │                          │
    │ 2. 활성 사용자 조회           │
    │    ├─ MongoDB: users          │
    │    │  (tradingConfig.enabled) │
    │    │                          │
    │ 3. 계좌 잔액 확인             │
    │    ├─ BalanceService          │
    │    │  (MockBalanceService)    │
    │    │                          │
    │ 4. 자동 매매 실행             │
    │    ├─ 추천 종목 상위 N개 선택 │
    │    └─ 매수 주문 생성 (미구현) │
    └───────────────────────────────┘
```

---

## ⚠️ 현재 문제점

### 1️⃣ 비효율적인 사용자 조회
```kotlin
// 현재 코드 (MongoDB)
val users = userRepository.findAll().filter {
    it.tradingConfig?.enabled == true &&
    it.tradingConfig.autoTradingEnabled
}
// 문제: findAll() 후 메모리에서 필터링 (O(n))
// 개선: DB에서 직접 쿼리 (O(log n))
```

### 2️⃣ MockBalanceService 사용
```kotlin
// 현재: 실제 계좌 잔액이 아님
class MockBalanceService {
    fun getAvailableCash() {
        return 1_000_000  // 하드코딩된 값
    }
}
// 개선: PostgreSQL account_balances에서 조회
```

### 3️⃣ 주문 실행 미구현
```kotlin
// 현재: 로그만 출력
logger.info("Placing BUY order for ${stock.ticker}")
// 개선: KIS API 호출 또는 DB 주문 생성
```

### 4️⃣ MongoDB → PostgreSQL 트랜잭션 보장 없음
```
문제: 거래 중 장애 발생 시 데이터 일관성 보장 안 됨
개선: PostgreSQL ACID 트랜잭션 보장
```

---

## 🔄 마이그레이션 전략

### Phase 1: RDB 스키마 생성 (Day 1-2)
```
목표: PostgreSQL에 필요한 테이블 생성

필요 테이블:
✓ users                    (사용자 정보)
✓ trading_configs          (거래 설정)
✓ account_balances         (계좌 잔액)
✓ stock_holdings           (보유 종목)
✓ trades                   (거래 기록)
✓ trade_signals_executed   (신호 실행 로그)
```

**참고:** `docs/migration/RDB_MIGRATION_PLAN.md` → Day 1-2 절차

### Phase 2: 데이터 마이그레이션 (Day 3)
```
순서:
1. users (MongoDB → PostgreSQL)
2. trading_configs (1:1 관계)
3. account_balances (초기화)
4. stock_holdings (조회용, 선택사항)
```

### Phase 3: AutoTradingService 변경 (Day 3-4)
```
변경 사항:
1. UserRepository 쿼리 최적화
   ├─ findAll() 제거
   ├─ optimized query 추가
   └─ SQL: SELECT u.* FROM users u
           JOIN trading_configs tc ON u.id = tc.user_id
           WHERE tc.enabled = true AND tc.auto_trading_enabled = true

2. BalanceService 개선
   ├─ MockBalanceService 제거
   ├─ RealBalanceService 구현
   └─ SQL: SELECT cash, total_value
           FROM account_balances
           WHERE user_id = ?

3. 거래 저장 로직 추가
   ├─ trades 테이블에 INSERT
   ├─ trade_signals_executed 기록
   └─ ACID 트랜잭션 보장
```

### Phase 4: 이중 쓰기 & 전환 (Day 4+)
```
1. 이중 쓰기 모드
   ├─ 읽기: PostgreSQL
   ├─ 쓰기: PostgreSQL + MongoDB (선택사항)

2. 모니터링 (1주)
   ├─ 거래 정상 실행 확인
   ├─ 성능 저하 없음 확인
   ├─ 데이터 일관성 확인

3. MongoDB 제거 (확정 후)
   ├─ users 컬렉션 삭제
   ├─ trading_configs 삭제
   └─ 아카이빙 (선택사항)
```

---

## 🎯 스케줄러 마이그레이션 로드맵

### Day 1: 준비 (2-3시간)

**1.1 PostgreSQL 준비**
```bash
# docker-compose.yml에 postgresql 추가
# build.gradle.kts에 의존성 추가 (PostgreSQL, Flyway, JPA)
# application.yml 설정

# 참고: EXECUTION_GUIDE.md → Day 1
```

**체크항목:**
- [ ] PostgreSQL 서비스 시작
- [ ] psql 연결 테스트
- [ ] 빌드 성공

### Day 2: 스키마 생성 (4-5시간)

**2.1 Flyway 마이그레이션 스크립트**
```sql
-- V1__Initial_Schema.sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE trading_configs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    enabled BOOLEAN DEFAULT FALSE,
    auto_trading_enabled BOOLEAN DEFAULT FALSE,
    min_composite_score DECIMAL(5,2),
    max_stocks_to_buy INT,
    max_amount_per_stock DECIMAL(12,2),
    stop_loss_percent DECIMAL(5,2),
    take_profit_percent DECIMAL(5,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE account_balances (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    cash DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_value DECIMAL(15,2) NOT NULL DEFAULT 0,
    locked_cash DECIMAL(15,2) DEFAULT 0,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE trades (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    side VARCHAR(10) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    kis_order_id VARCHAR(100),
    executed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- V2__Create_Indexes.sql
CREATE INDEX idx_users_user_id ON users(user_id);
CREATE INDEX idx_trading_configs_enabled ON trading_configs(user_id, enabled);
CREATE INDEX idx_account_balances_user ON account_balances(user_id);
CREATE INDEX idx_trades_user_date ON trades(user_id, created_at DESC);
```

**2.2 Entity 클래스 작성**
```kotlin
@Entity
@Table(name = "users")
data class User(
    @Id @GeneratedValue val id: Long? = null,
    @Column(unique = true) val userId: String,
    val name: String? = null,
    @Enumerated(EnumType.STRING) val status: UserStatus = UserStatus.ACTIVE,
    @CreationTimestamp val createdAt: LocalDateTime = LocalDateTime.now(),
    @UpdateTimestamp val updatedAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(name = "trading_configs")
data class TradingConfig(
    @Id @GeneratedValue val id: Long? = null,
    @ManyToOne val user: User,
    val enabled: Boolean = false,
    val autoTradingEnabled: Boolean = false,
    val minCompositeScore: BigDecimal? = null,
    val maxStocksToBuy: Int? = null,
    val maxAmountPerStock: BigDecimal? = null,
    val stopLossPercent: BigDecimal? = null,
    val takeProfitPercent: BigDecimal? = null
)

@Entity
@Table(name = "account_balances")
data class AccountBalance(
    @Id @GeneratedValue val id: Long? = null,
    @ManyToOne val user: User,
    val cash: BigDecimal = BigDecimal.ZERO,
    val totalValue: BigDecimal = BigDecimal.ZERO,
    val lockedCash: BigDecimal = BigDecimal.ZERO,
    @Version val version: Long = 0
)
```

**2.3 Repository 인터페이스**
```kotlin
@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUserId(userId: String): User?
}

@Repository
interface TradingConfigRepository : JpaRepository<TradingConfig, Long> {
    fun findByUserIdAndEnabledTrueAndAutoTradingEnabledTrue(): List<TradingConfig>
}

@Repository
interface AccountBalanceRepository : JpaRepository<AccountBalance, Long> {
    fun findByUserId(userId: Long): AccountBalance?
}
```

**체크항목:**
- [ ] Flyway 마이그레이션 스크립트 완성
- [ ] Entity 클래스 작성 완료
- [ ] Repository 인터페이스 작성 완료
- [ ] 코드 컴파일 성공

### Day 3: 데이터 마이그레이션 (3-4시간)

**3.1 마이그레이션 서비스 구현**
```kotlin
@Service
class RdbMigrationService(
    private val mongoUserRepository: com.quantiq.core.repository.mongo.UserRepository,
    private val rdbUserRepository: UserRepository,
    private val tradingConfigRepository: TradingConfigRepository,
    private val accountBalanceRepository: AccountBalanceRepository
) {
    @Transactional
    fun migrateUsers() {
        val mongoUsers = mongoUserRepository.findAll()

        mongoUsers.forEach { mongoUser ->
            // 1. User 저장
            val rdbUser = User(
                userId = mongoUser.userId,
                name = mongoUser.name,
                status = UserStatus.ACTIVE
            )
            val savedUser = rdbUserRepository.save(rdbUser)

            // 2. TradingConfig 저장
            mongoUser.tradingConfig?.let { config ->
                val tradingConfig = TradingConfig(
                    user = savedUser,
                    enabled = config.enabled,
                    autoTradingEnabled = config.autoTradingEnabled,
                    minCompositeScore = config.minCompositeScore?.toBigDecimal(),
                    maxStocksToBuy = config.maxStocksToBuy,
                    maxAmountPerStock = config.maxAmountPerStock?.toBigDecimal(),
                    stopLossPercent = config.stopLossPercent?.toBigDecimal(),
                    takeProfitPercent = config.takeProfitPercent?.toBigDecimal()
                )
                tradingConfigRepository.save(tradingConfig)
            }

            // 3. AccountBalance 초기화
            val balance = AccountBalance(
                user = savedUser,
                cash = BigDecimal("1000000"),
                totalValue = BigDecimal("1000000")
            )
            accountBalanceRepository.save(balance)
        }
    }
}
```

**3.2 데이터 마이그레이션 실행**
```bash
# 1. PostgreSQL 스키마 생성 (Flyway 자동 실행)
docker-compose up quantiq-core

# 2. 마이그레이션 실행
export RUN_MIGRATION=true
docker-compose restart quantiq-core

# 3. 데이터 검증
docker-compose exec postgresql psql -U quantiq_user -d quantiq << EOF
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM trading_configs;
SELECT COUNT(*) FROM account_balances;
EOF
```

**체크항목:**
- [ ] PostgreSQL 테이블 생성됨
- [ ] Flyway 마이그레이션 성공
- [ ] 데이터 이관 완료
- [ ] 데이터 일치 확인

### Day 4: AutoTradingService 변경 (4-5시간)

**4.1 AutoTradingService 개선**
```kotlin
@Service
class AutoTradingService(
    private val tradingConfigRepository: TradingConfigRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val stockRecommendationRepository: StockRecommendationRepository,
    private val tradeRepository: TradeRepository,
    private val balanceService: BalanceService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun executeAutoTrading() {
        logger.info("🚀 Starting Auto Trading Execution...")
        val today = LocalDate.now().toString()

        // 1️⃣ 추천 종목 조회 (MongoDB)
        val recommendations = stockRecommendationRepository
            .findByDateAndIsRecommendedTrue(today)
        logger.info("✅ Found ${recommendations.size} recommendations for today")

        if (recommendations.isEmpty()) {
            logger.info("❌ No recommendations found. Skipping trading.")
            return
        }

        // 2️⃣ 활성 사용자 조회 (최적화된 쿼리)
        // 변경 전: userRepository.findAll().filter { ... }
        // 변경 후:
        val activeUsers = tradingConfigRepository
            .findByUserIdAndEnabledTrueAndAutoTradingEnabledTrue()
        logger.info("✅ Found ${activeUsers.size} active users for auto trading")

        activeUsers.forEach { tradingConfig ->
            try {
                val user = tradingConfig.user
                logger.info("👤 Processing user: ${user.userId}")

                // 3️⃣ 계좌 잔액 조회 (개선)
                // 변경 전: balanceService.getAvailableCash() // 100만원 하드코딩
                // 변경 후:
                val balance = accountBalanceRepository.findByUserId(user.id!!)
                    ?: throw Exception("No balance found for user ${user.userId}")

                val availableCash = balance.cash - balance.lockedCash
                logger.info("💰 Available cash: $availableCash")

                if (availableCash <= BigDecimal.ZERO) {
                    logger.info("⚠️ No available cash. Skipping.")
                    return@forEach
                }

                // 4️⃣ 거래 실행
                val maxStocks = tradingConfig.maxStocksToBuy ?: 5
                val targetStocks = recommendations.take(maxStocks)

                targetStocks.forEach { recommendation ->
                    try {
                        val price = recommendation.currentPrice ?: return@forEach
                        val quantity = (availableCash.divide(
                            price.toBigDecimal(),
                            0,
                            RoundingMode.DOWN
                        )).toInt().coerceAtMost(10) // 최대 10주

                        if (quantity <= 0) {
                            logger.warn("Insufficient funds for ${recommendation.ticker}")
                            return@forEach
                        }

                        // 5️⃣ 거래 기록 생성 (DB 저장)
                        val trade = Trade(
                            user = user,
                            ticker = recommendation.ticker,
                            side = "BUY",
                            quantity = quantity,
                            price = price.toBigDecimal(),
                            totalAmount = price.toBigDecimal() * quantity.toBigDecimal(),
                            status = "PENDING"
                        )
                        tradeRepository.save(trade)

                        logger.info("📊 Created BUY order: ${recommendation.ticker} x$quantity @ $price")

                        // 6️⃣ 실제 주문 실행 (KIS API 또는 브로커)
                        // kis.placeOrder(trade)  // TODO: 구현

                    } catch (e: Exception) {
                        logger.error("❌ Error placing order for ${recommendation.ticker}", e)
                    }
                }

            } catch (e: Exception) {
                logger.error("❌ Error processing user", e)
            }
        }

        logger.info("✅ Auto Trading Execution Completed.")
    }
}
```

**4.2 BalanceService 개선**
```kotlin
@Service
class BalanceService(
    private val accountBalanceRepository: AccountBalanceRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun getAvailableCash(userId: Long): BigDecimal {
        val balance = accountBalanceRepository.findByUserId(userId)
            ?: return BigDecimal.ZERO

        return balance.cash - balance.lockedCash
    }

    @Transactional
    fun updateBalance(userId: Long, cashDelta: BigDecimal) {
        val balance = accountBalanceRepository.findByUserId(userId)
            ?: throw Exception("No balance found for user $userId")

        // Optimistic locking 활용
        balance.cash = balance.cash.plus(cashDelta)
        balance.updatedAt = LocalDateTime.now()
        accountBalanceRepository.save(balance)

        logger.info("Updated balance for user $userId: +$cashDelta")
    }
}
```

**체크항목:**
- [ ] AutoTradingService 쿼리 최적화 완료
- [ ] BalanceService 개선 완료
- [ ] Trade 저장 로직 구현 완료
- [ ] 코드 컴파일 및 테스트 성공

### Day 4 (오후): 통합 테스트 (2-3시간)

**4.3 E2E 테스트**
```bash
# 1. PostgreSQL 데이터 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq << EOF
SELECT u.user_id, tc.enabled, ab.cash
FROM users u
LEFT JOIN trading_configs tc ON u.id = tc.user_id
LEFT JOIN account_balances ab ON u.id = ab.user_id
LIMIT 10;
EOF

# 2. 스케줄러 실행 테스트
# Kafka 분석 완료 이벤트 발행
echo '{"type":"TECHNICAL","status":"success"}' | \
docker-compose exec -T kafka kafka-console-producer.sh \
  --broker-list kafka:29092 \
  --topic quantiq.analysis.completed

# 3. 로그 확인
docker-compose logs -f quantiq-core | grep -E "(Auto Trading|Processing user|BUY order)"

# 4. 생성된 거래 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq << EOF
SELECT user_id, ticker, side, quantity, price, status
FROM trades
ORDER BY created_at DESC
LIMIT 10;
EOF
```

**체크항목:**
- [ ] 스케줄러 시작 로그 보임
- [ ] 활성 사용자 조회 성공
- [ ] 거래 기록 생성됨
- [ ] 계좌 잔액 정상 조회됨
- [ ] 에러 로그 없음

---

## 📋 마이그레이션 체크리스트

### Day 1 완료 기준
```
☐ PostgreSQL 시작 및 헬스 체크
☐ build.gradle.kts 의존성 추가
☐ application.yml 설정
☐ .env 파일 작성
☐ ./gradlew clean build 성공
```

### Day 2 완료 기준
```
☐ V1__Initial_Schema.sql 작성 (4개 테이블)
☐ V2__Create_Indexes.sql 작성
☐ User, TradingConfig, AccountBalance Entity 작성
☐ 3개 Repository 작성
☐ 코드 컴파일 성공
```

### Day 3 완료 기준
```
☐ PostgreSQL 테이블 생성됨
☐ Flyway 마이그레이션 성공
☐ RdbMigrationService 구현
☐ 데이터 마이그레이션 완료
☐ users: MongoDB 수와 일치
☐ trading_configs: MongoDB 수와 일치
☐ account_balances: 생성 확인
```

### Day 4 완료 기준
```
☐ AutoTradingService 쿼리 최적화 완료
☐ BalanceService 개선 완료
☐ Trade 저장 로직 구현
☐ 스케줄러 테스트 성공
☐ 거래 기록 생성 확인
☐ 에러 로그 0개
```

---

## 🔄 단계별 명령어 치트시트

### PostgreSQL 준비
```bash
cd /Users/imdoyeong/Desktop/workSpace/quantiq

# PostgreSQL 시작
docker-compose up -d postgresql
sleep 10

# 연결 테스트
docker-compose exec postgresql psql -U quantiq_user -d quantiq -c "SELECT version();"
```

### 데이터 마이그레이션
```bash
# 빌드
cd quantiq-core
./gradlew clean build

# Docker 이미지 빌드
docker build -t quantiq-core:scheduler .

# 마이그레이션 실행
export RUN_MIGRATION=true
docker-compose up quantiq-core

# 로그 확인
docker-compose logs -f quantiq-core | grep -i migration
```

### 스케줄러 테스트
```bash
# Kafka 메시지 발행
echo '{"type":"TECHNICAL"}' | docker-compose exec -T kafka kafka-console-producer.sh \
  --broker-list kafka:29092 \
  --topic quantiq.analysis.completed

# 스케줄러 로그 확인
docker-compose logs -f quantiq-core | grep -E "(Auto Trading|Found.*users|BUY order)"
```

---

## ⚠️ 주의사항

### 1️⃣ 거래 관련
- ✅ 거래 기록은 DB에 저장
- ⚠️ 실제 주문 실행은 미구현 (KIS API 필요)
- ⚠️ 테스트 환경에서만 실행 권장

### 2️⃣ 동시성
- ✅ Optimistic Locking (version 필드)
- ⚠️ 여러 스케줄러 동시 실행 시 충돌 가능
- 개선: Pessimistic Locking 또는 분산 락 추가

### 3️⃣ 성능
- ✅ 쿼리 최적화 (인덱스, JOIN)
- ✅ 트랜잭션 범위 최소화
- ⚠️ 추천 종목 많을 경우 배치 처리 고려

### 4️⃣ 보안
- ⚠️ 사용자별 데이터 격리 확인
- ⚠️ 계좌 잔액 조회 권한 확인
- 개선: 레벨 기반 접근 제어 추가

---

## 🎯 마이그레이션 완료 기준

```
✅ 기술
├─ PostgreSQL 4개 테이블 생성
├─ Flyway 마이그레이션 성공
└─ Entity, Repository 구현 완료

✅ 데이터
├─ MongoDB → PostgreSQL 이관 100%
├─ 데이터 일치율 100%
└─ 거래 기록 생성 정상

✅ 성능
├─ 쿼리 응답 < 100ms
├─ 스케줄러 실행 < 5초
└─ CPU/메모리 정상

✅ 운영
├─ 스케줄러 24시간 안정
├─ 에러 로그 0개
└─ 자동 매매 정상 작동
```

---

## 📞 문제 해결

### "거래 생성 안 됨"
```bash
# 1. 활성 사용자 확인
psql: SELECT * FROM trading_configs WHERE enabled = true;

# 2. 추천 종목 확인
mongosh: db.stock_recommendations.find({is_recommended: true}).count()

# 3. 계좌 잔액 확인
psql: SELECT * FROM account_balances WHERE cash > 0;
```

### "스케줄러 느림"
```bash
# 1. 쿼리 성능 분석
EXPLAIN ANALYZE SELECT * FROM trading_configs WHERE enabled = true;

# 2. 인덱스 확인
SELECT * FROM pg_indexes WHERE tablename = 'trading_configs';

# 3. 테이블 통계 업데이트
ANALYZE trading_configs;
```

---

**다음 액션:**

1. ✅ 현재 분석 기능 검증 (ANALYSIS_VERIFICATION_CHECKLIST.md)
2. ✅ 마이그레이션 계획 검토 (이 문서)
3. 🚀 **내일부터: Day 1 환경 준비 시작**

---

**마지막 업데이트:** 2025-01-29
**버전:** 1.0
**상태:** 준비 완료 ✅
