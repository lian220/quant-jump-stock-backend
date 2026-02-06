# Quantiq 데이터베이스 전략 분석

## 1. 현재 상태 분석

### 현재 MongoDB 스키마

```
collections:
├─ users
│  ├─ _id: ObjectId
│  ├─ user_id: String (유니크)
│  ├─ name: String
│  ├─ trading_config: Object
│  │  ├─ enabled: Boolean
│  │  ├─ auto_trading_enabled: Boolean
│  │  ├─ min_composite_score: Double
│  │  ├─ max_stocks_to_buy: Integer
│  │  ├─ max_amount_per_stock: Double
│  │  ├─ stop_loss_percent: Double
│  │  └─ take_profit_percent: Double
│  └─ updated_at: DateTime
│
└─ stock_recommendations
   ├─ _id: ObjectId
   ├─ ticker: String
   ├─ date: String (YYYY-MM-DD)
   ├─ stock_name: String
   ├─ technical_indicators: Object
   │  ├─ sma20: Double
   │  ├─ sma50: Double
   │  ├─ rsi: Double
   │  ├─ macd: Double
   │  └─ signal: Double
   ├─ is_recommended: Boolean
   └─ updated_at: DateTime
```

### 현재 문제점

1. **구조화된 데이터가 NoSQL에 저장됨**
   - User는 정규화된 구조가 필요함
   - TradingConfig는 사용자와 1:1 관계

2. **트랜잭션 부재**
   - 여러 컬렉션 간 데이터 일관성 보장 불가
   - 거래 실행 중 장애 발생 시 복구 어려움

3. **확장성 한계**
   - findAll() 후 필터링: O(n) 성능
   - 거래 기록 추적 불가

4. **쿼리 성능**
   - 조인 기능 없음 (lookup 사용 → 성능 저하)
   - 복잡한 쿼리 어려움

---

## 2. RDB vs MongoDB 분류 전략

### 데이터 특성별 분류

```
┌─────────────────────────────────────────────────────────────┐
│             데이터 특성과 저장소 선택                          │
├──────────────────┬─────────────────────┬──────────────────┤
│   데이터 특성    │     관계형 DB       │    MongoDB       │
├──────────────────┼─────────────────────┼──────────────────┤
│ 구조화도         │ 높음 (필수)         │ 낮음 (유연)      │
│ 트랜잭션         │ ACID 지원          │ 제한적 지원      │
│ 조인 복잡도      │ 자주 필요          │ 거의 불필요     │
│ 데이터 크기      │ 중소규모           │ 대규모/시계열   │
│ 쿼리 복잡도      │ 복잡한 쿼리        │ 단순한 쿼리      │
│ 확장성           │ 수직 확장           │ 수평 확장        │
└──────────────────┴─────────────────────┴──────────────────┘
```

---

## 3. 제안: 하이브리드 데이터베이스 아키텍처

### 3.1 RDB에 저장할 데이터 (PostgreSQL/MySQL)

#### Core Business Data (트랜잭션 필요)

```sql
-- 1. 사용자 정보 (OLTP)
TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100),
    email VARCHAR(100) UNIQUE,
    password_hash VARCHAR(255),
    status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED'),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    INDEX idx_user_id (user_id)
)

-- 2. 거래 설정 (사용자와 1:1)
TABLE trading_configs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    enabled BOOLEAN DEFAULT FALSE,
    auto_trading_enabled BOOLEAN DEFAULT FALSE,
    min_composite_score DECIMAL(5,2) DEFAULT 2.0,
    max_stocks_to_buy INT DEFAULT 5,
    max_amount_per_stock DECIMAL(12,2) DEFAULT 10000.0,
    stop_loss_percent DECIMAL(5,2) DEFAULT -7.0,
    take_profit_percent DECIMAL(5,2) DEFAULT 5.0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_enabled (user_id, enabled)
)

-- 3. 계좌 잔액 (중요: 트랜잭션 필요)
TABLE account_balances (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    cash DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_value DECIMAL(15,2) NOT NULL DEFAULT 0,
    locked_cash DECIMAL(15,2) DEFAULT 0,  -- 주문 대기 중
    version BIGINT DEFAULT 0,  -- Optimistic locking
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
)

-- 4. 주식 보유 내역 (포트폴리오)
TABLE stock_holdings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    quantity INT NOT NULL,
    average_price DECIMAL(10,2) NOT NULL,
    total_cost DECIMAL(15,2) NOT NULL,
    current_value DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE KEY unique_user_ticker (user_id, ticker),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_holdings (user_id)
)

-- 5. 거래 기록 (감사, 정산)
TABLE trades (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    side ENUM('BUY', 'SELL') NOT NULL,
    quantity INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    total_amount DECIMAL(15,2) NOT NULL,
    commission DECIMAL(10,2) DEFAULT 0,
    status ENUM('PENDING', 'EXECUTED', 'FAILED', 'CANCELLED'),
    kis_order_id VARCHAR(100),  -- 외부 거래소 ID
    executed_at TIMESTAMP NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_ticker_date (user_id, ticker, executed_at),
    INDEX idx_status (status),
    INDEX idx_kis_order_id (kis_order_id)
)

-- 6. 거래 신호 로그 (감사, 재현 가능)
TABLE trade_signals_executed (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    recommendation_id VARCHAR(100) NOT NULL,  -- MongoDB _id 참조
    ticker VARCHAR(10) NOT NULL,
    signal VARCHAR(20) NOT NULL,  -- BUY, SELL, HOLD
    confidence DECIMAL(3,2) NOT NULL,
    execution_decision ENUM('EXECUTED', 'SKIPPED', 'FAILED') NOT NULL,
    skip_reason VARCHAR(255),  -- 충분한 자금 없음 등
    executed_trade_id BIGINT,  -- trades 테이블 참조
    created_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (executed_trade_id) REFERENCES trades(id) ON DELETE SET NULL,
    INDEX idx_user_timestamp (user_id, created_at),
    INDEX idx_recommendation_id (recommendation_id)
)
```

#### 데이터 관계도 (RDB)

```
users (1) ──┬──→ (1) trading_configs
            │
            ├──→ (1) account_balances
            │
            ├──→ (N) stock_holdings
            │
            ├──→ (N) trades
            │
            └──→ (N) trade_signals_executed
                        │
                        └──→ trades (선택적)
```

---

### 3.2 MongoDB에 저장할 데이터 (시계열/분석)

#### Time Series & Analysis Data

```javascript
// 1. 주식 추천 신호 (시계열)
db.stock_recommendations.insertOne({
    _id: ObjectId(),
    ticker: "AAPL",
    date: "2024-01-15",  // YYYY-MM-DD
    stock_name: "Apple Inc.",

    // 기술적 지표 (스냅샷)
    technical_indicators: {
        sma20: 175.30,
        sma50: 174.50,
        rsi: 28,
        macd: 0.05,
        signal: -0.02,
        bbands_upper: 178.00,
        bbands_lower: 172.50,
        roc: 2.5
    },

    // 신호 생성 로직
    signal_generation: {
        primary_signal: "BUY",
        confidence_score: 0.85,
        reasons: [
            "SMA20 < EMA12 상향 교점",
            "RSI 과매도 (28)",
            "MACD 양수 전환"
        ],
        strength: "STRONG"  // WEAK, MEDIUM, STRONG
    },

    // 가격 정보
    price_info: {
        current: 150.25,
        day_high: 151.50,
        day_low: 149.80,
        prev_close: 149.75
    },

    is_recommended: true,

    // 메타데이터
    source: "quantiq-data-engine",
    analysis_time: ISODate("2024-01-15T16:00:00Z"),
    updated_at: ISODate("2024-01-15T16:30:00Z")
})

// 2. 기술적 분석 결과 (일일 스냅샷)
db.daily_analysis_results.insertOne({
    _id: ObjectId(),
    ticker: "AAPL",
    date: "2024-01-15",

    // 주간 데이터
    weekly_data: {
        high: 155.00,
        low: 148.00,
        close: 150.25,
        volume: 50000000
    },

    // 여러 시간대 분석
    timeframes: {
        "1h": {
            sma20: 150.50,
            rsi: 35,
            trend: "UP"
        },
        "4h": {
            sma20: 150.00,
            rsi: 32,
            trend: "UP"
        },
        "1d": {
            sma20: 175.30,
            rsi: 28,
            trend: "UP"
        }
    },

    // 지표 상태
    indicators_health: {
        moving_average_status: "BULLISH",
        momentum_status: "STRONG",
        volatility_level: "NORMAL"
    },

    created_at: ISODate("2024-01-15T16:00:00Z")
})

// 3. 포트폴리오 성과 (일일 스냅샷)
db.portfolio_snapshots.insertOne({
    _id: ObjectId(),
    user_id: "user123",  // RDB 사용자 ID 참조
    date: "2024-01-15",

    portfolio: {
        total_value: 1100000,
        cash: 900000,
        holdings_value: 200000,
        invested_amount: 150000
    },

    performance: {
        daily_return: 1500,  // 원화
        daily_return_percent: 0.15,
        mtd_return: 25000,
        ytd_return: 50000,
        overall_return: 100000
    },

    holdings_snapshot: {
        "AAPL": { quantity: 100, value: 150250, cost: 145000 },
        "MSFT": { quantity: 50, value: 49750, cost: 50000 }
    },

    created_at: ISODate("2024-01-15T16:00:00Z")
})

// 4. 시장 데이터 아카이브 (충분한 크기의 데이터)
db.market_data_archive.insertOne({
    _id: ObjectId(),
    ticker: "AAPL",
    date: "2024-01-15",

    // 분 단위 데이터
    intraday: [
        { time: "09:30", open: 149.80, high: 149.90, low: 149.50, close: 149.75 },
        { time: "09:31", open: 149.75, high: 150.00, low: 149.60, close: 149.95 },
        // ... 400+ 분 단위 데이터
    ],

    // 거래량 분석
    volume_profile: {
        total_volume: 50000000,
        peak_volume_time: "10:30",
        volume_by_price: {}  // 가격대별 거래량
    },

    ttl: 7776000  // 90일 후 자동 삭제 (TTL 인덱스)
})
```

#### MongoDB 컬렉션 설계

| 컬렉션 | 목적 | 크기 | 갱신빈도 | TTL |
|--------|------|------|---------|-----|
| stock_recommendations | 일일 추천 신호 | 중소 | 1회/일 | 1년 |
| daily_analysis_results | 기술적 분석 | 중소 | 1회/일 | 1년 |
| portfolio_snapshots | 포트폴리오 추적 | 중소 | 1회/일 | 2년 |
| market_data_archive | 고주파 시장 데이터 | 대 | 지속 | 90일 |
| analysis_audit_logs | 분석 프로세스 로그 | 대 | 지속 | 30일 |

---

## 4. 마이그레이션 전략

### 단계별 마이그레이션 계획

#### Phase 1: RDB 생성 및 기초 데이터 이관 (1-2주)

```sql
-- PostgreSQL 생성
CREATE DATABASE quantiq;

-- 테이블 생성
CREATE TABLE users (...);
CREATE TABLE trading_configs (...);
CREATE TABLE account_balances (...);
CREATE TABLE stock_holdings (...);
CREATE TABLE trades (...);
CREATE TABLE trade_signals_executed (...);

-- 인덱스 생성
CREATE INDEX idx_user_id ON users(user_id);
CREATE INDEX idx_enabled ON trading_configs(user_id, enabled);
```

```kotlin
// 마이그레이션 스크립트
@Service
class DataMigrationService(
    private val mongoUserRepository: MongoUserRepository,
    private val rdbUserRepository: RdbUserRepository,
    private val mongoRecommendationRepository: MongoRecommendationRepository,
    private val rdbTradeSignalRepository: RdbTradeSignalRepository
) {
    @Transactional
    fun migrateUsersToRdb() {
        val mongoUsers = mongoUserRepository.findAll()

        mongoUsers.forEach { mongoUser ->
            // RDB User 생성
            val rdbUser = User(
                userId = mongoUser.userId,
                name = mongoUser.name,
                email = mongoUser.email ?: "unknown@example.com",
                status = UserStatus.ACTIVE
            )
            val savedUser = rdbUserRepository.save(rdbUser)

            // RDB TradingConfig 생성
            mongoUser.tradingConfig?.let { config ->
                val tradingConfig = TradingConfig(
                    userId = savedUser.id,
                    enabled = config.enabled,
                    autoTradingEnabled = config.autoTradingEnabled,
                    // ... 다른 필드들
                )
                tradingConfigRepository.save(tradingConfig)
            }

            logger.info("Migrated user: ${mongoUser.userId}")
        }
    }
}
```

#### Phase 2: 이중 쓰기 모드 (병렬 실행, 1주)

```kotlin
@Service
class HybridUserService(
    private val rdbUserRepository: RdbUserRepository,
    private val mongoUserRepository: MongoUserRepository,
    @Value("\${db.dual-write:true}")
    private val dualWrite: Boolean
) {
    @Transactional(transactionManager = "rdbTransactionManager")
    fun createUser(request: CreateUserRequest): User {
        // 1. RDB에 저장 (Primary)
        val rdbUser = rdbUserRepository.save(request.toRdbEntity())

        // 2. MongoDB에도 저장 (Backup)
        if (dualWrite) {
            try {
                val mongoUser = request.toMongoEntity()
                mongoUserRepository.save(mongoUser)
            } catch (e: Exception) {
                logger.warn("MongoDB write failed, but RDB write succeeded", e)
                // MongoDB 실패는 무시 (RDB가 primary)
            }
        }

        return rdbUser.toDto()
    }
}
```

#### Phase 3: 읽기 전환 (1주)

```kotlin
@Service
class UserService(
    private val rdbUserRepository: RdbUserRepository,
    private val mongoUserRepository: MongoUserRepository,
    @Value("\${db.read-source:rdb}")  // rdb, mongo, both
    private val readSource: String
) {
    fun getUserById(userId: String): User? {
        return when (readSource) {
            "rdb" -> {
                val rdbUser = rdbUserRepository.findByUserId(userId)
                rdbUser?.toDto()
            }
            "mongo" -> {
                val mongoUser = mongoUserRepository.findByUserId(userId)
                mongoUser?.toDto()
            }
            "both" -> {
                // 비교 검증
                val rdbUser = rdbUserRepository.findByUserId(userId)
                val mongoUser = mongoUserRepository.findByUserId(userId)

                if (rdbUser?.toDto() != mongoUser?.toDto()) {
                    logger.warn("Data mismatch for user: $userId")
                }
                rdbUser?.toDto()
            }
            else -> null
        }
    }
}
```

#### Phase 4: MongoDB 제거 (1주)

```kotlin
@Configuration
@ConditionalOnProperty(name = "db.remove-mongo", havingValue = "true")
class MongoDisableConfig {
    // MongoDB 빈 제거
}
```

---

## 5. 구현 로드맵

### 기술 선택

#### RDB: PostgreSQL

**이유:**
- ACID 트랜잭션 완벽 지원
- 복잡한 쿼리 최적화
- 자동 증분, 외래 키 제약 조건
- 오픈 소스, 프로덕션 검증됨

**버전:** PostgreSQL 15+

```yaml
# docker-compose.yml
postgresql:
  image: postgres:15-alpine
  environment:
    POSTGRES_DB: quantiq
    POSTGRES_USER: quantiq
    POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
  ports:
    - "5432:5432"
  volumes:
    - postgres_data:/var/lib/postgresql/data
    - ./docs/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql
```

#### Spring Boot JPA Configuration

```kotlin
// RDB 설정
@Configuration
@EnableJpaRepositories(
    basePackages = ["com.quantiq.core.repository.rdb"],
    entityManagerFactoryRef = "rdbEntityManagerFactory",
    transactionManagerRef = "rdbTransactionManager"
)
@EnableTransactionManagement
class RdbConfig {

    @Bean
    fun dataSource(): DataSource {
        return HikariDataSource().apply {
            jdbcUrl = "jdbc:postgresql://postgres:5432/quantiq"
            username = "quantiq"
            password = System.getenv("POSTGRES_PASSWORD")
            maximumPoolSize = 20
            minimumIdle = 5
        }
    }

    @Bean
    fun rdbEntityManagerFactory(): LocalContainerEntityManagerFactoryBean {
        return LocalContainerEntityManagerFactoryBean().apply {
            dataSource = dataSource()
            setPackagesToScan("com.quantiq.core.domain.rdb")
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaProperties(jpaProperties())
        }
    }

    @Bean
    fun rdbTransactionManager(
        entityManagerFactory: EntityManagerFactory
    ): PlatformTransactionManager {
        return JpaTransactionManager(entityManagerFactory)
    }

    private fun jpaProperties(): Map<String, String> {
        return mapOf(
            "hibernate.dialect" to "org.hibernate.dialect.PostgreSQL15Dialect",
            "hibernate.format_sql" to "true",
            "hibernate.jdbc.batch_size" to "20",
            "hibernate.order_inserts" to "true",
            "hibernate.order_updates" to "true",
            "hibernate.jdbc.use_get_generated_keys" to "true"
        )
    }
}

// MongoDB 설정 유지
@Configuration
@EnableMongoRepositories(
    basePackages = ["com.quantiq.core.repository.mongo"]
)
class MongoConfig {
    // 기존 설정
}
```

---

## 6. 데이터 흐름 (변경 후)

### 거래 실행 시나리오

```
┌─────────────────────────────────────────────────────────┐
│ 1. MongoDB에서 추천 신호 수신                             │
│    stock_recommendations (ticker, signal, confidence)   │
└────────────────┬──────────────────────────────────────┘
                 │
┌─────────────────▼──────────────────────────────────────┐
│ 2. RDB에서 사용자 정보 조회 (트랜잭션 시작)             │
│    SELECT * FROM users WHERE user_id = ?               │
│    SELECT * FROM trading_configs WHERE user_id = ?     │
└────────────────┬──────────────────────────────────────┘
                 │
┌─────────────────▼──────────────────────────────────────┐
│ 3. RDB에서 계좌 잔액 확인 (Pessimistic Lock)           │
│    SELECT * FROM account_balances WHERE user_id = ?    │
│    FOR UPDATE SKIP LOCKED;                              │
└────────────────┬──────────────────────────────────────┘
                 │
┌─────────────────▼──────────────────────────────────────┐
│ 4. RDB에서 거래 기록 생성 (PENDING)                     │
│    INSERT INTO trades (status='PENDING')                │
└────────────────┬──────────────────────────────────────┘
                 │
┌─────────────────▼──────────────────────────────────────┐
│ 5. 외부 API 호출 (KIS)                                  │
│    거래 실행 요청                                        │
└────────────────┬──────────────────────────────────────┘
                 │
         ┌───────┴─────────┐
         │                 │
    성공  │                 │ 실패
    ┌────▼──────┐    ┌─────▼──────┐
    │  거래 체결  │    │ 재시도/실패 │
    └────┬──────┘    └─────┬──────┘
         │                 │
┌────────▼──────────────────▼──────────────────┐
│ 6. RDB 업데이트                               │
│    - trades (status='EXECUTED')               │
│    - account_balances (cash 업데이트)         │
│    - stock_holdings (수량 업데이트)           │
│    - trade_signals_executed (기록)            │
│ (트랜잭션 커밋)                                │
└────────┬─────────────────────────────────────┘
         │
┌────────▼──────────────────────────────────────┐
│ 7. MongoDB에 비동기 저장                       │
│    - stock_recommendations (executed=true)    │
│    - portfolio_snapshots (성과 기록)          │
│ (비동기, 실패해도 무시)                       │
└───────────────────────────────────────────────┘
```

---

## 7. 성능 비교

### Before (MongoDB Only)

```
거래 실행 흐름:
1. Users 컬렉션 조회 (findAll 후 필터) - O(n)
2. StockRecommendations 조회 - O(log n)
3. 수동으로 데이터 검증 및 조작
4. MongoDB 여러 문서 업데이트 (Atomic 보장 안 됨)

성능:
- 쿼리: 100-500ms (문서 수에 따라)
- 업데이트: 50-200ms
- 일관성: 보장 안 됨
- 확장성: 문서 수 증가 시 성능 저하
```

### After (Hybrid)

```
거래 실행 흐름:
1. RDB 조인 쿼리 (최적화된 인덱스)
   SELECT u.*, tc.*, ab.*
   FROM users u
   JOIN trading_configs tc ON u.id = tc.user_id
   JOIN account_balances ab ON u.id = ab.user_id
   WHERE u.user_id = ?
   - O(log n)

2. 트랜잭션 내 업데이트 (ACID 보장)
   - 여러 테이블 원자적 업데이트
   - 장애 시 자동 롤백

3. 비동기 분석 데이터 저장 (MongoDB)
   - 메인 거래 흐름 영향 없음

성능:
- 쿼리: 5-50ms (인덱스 최적화)
- 업데이트: 10-50ms (트랜잭션)
- 일관성: 완벽 보장 (ACID)
- 확장성: 수직 확장 가능
```

---

## 8. 마이그레이션 체크리스트

- [ ] PostgreSQL 15 설치 및 docker-compose 추가
- [ ] RDB 스키마 설계 및 생성
- [ ] Spring Data JPA 설정
- [ ] RDB 엔티티 클래스 생성
- [ ] RDB Repository 인터페이스 생성
- [ ] 이중 쓰기 서비스 구현
- [ ] 마이그레이션 스크립트 작성
- [ ] 데이터 마이그레이션 실행
- [ ] 읽기/쓰기 전환 (feature flag)
- [ ] 성능 테스트 (RDB vs MongoDB)
- [ ] MongoDB 프로덕션 제거 계획
- [ ] 백업/복구 전략 수립
- [ ] 모니터링 및 알림 설정

---

## 9. 다음 단계

1. [DATABASE_IMPLEMENTATION.md](DATABASE_IMPLEMENTATION.md) - 구현 상세 가이드
2. [SCHEMA_MIGRATION.md](SCHEMA_MIGRATION.md) - 스키마 마이그레이션 가이드
3. [PERFORMANCE_TUNING.md](PERFORMANCE_TUNING.md) - 성능 최적화 가이드

