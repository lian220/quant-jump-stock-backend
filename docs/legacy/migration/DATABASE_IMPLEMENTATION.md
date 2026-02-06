# Quantiq 데이터베이스 구현 상세 가이드

## 1. PostgreSQL 설정

### 1.1 Docker Compose 업데이트

```yaml
# docker-compose.yml
version: '3.8'

services:
  # 기존 서비스들...

  postgresql:
    image: postgres:15-alpine
    container_name: quantiq-postgres
    environment:
      POSTGRES_DB: quantiq
      POSTGRES_USER: quantiq_user
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-quantiq_password}
      PGDATA: /var/lib/postgresql/data/pgdata
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./docs/sql/01-schema.sql:/docker-entrypoint-initdb.d/01-schema.sql
      - ./docs/sql/02-indexes.sql:/docker-entrypoint-initdb.d/02-indexes.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U quantiq_user"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - quantiq-network

  # MongoDB (기존)
  mongodb:
    image: mongo:latest
    container_name: quantiq-mongodb
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db
    networks:
      - quantiq-network

  # 다른 서비스들...

volumes:
  postgres_data:
  mongodb_data:

networks:
  quantiq-network:
    driver: bridge
```

### 1.2 SQL 스키마 생성

```sql
-- docs/sql/01-schema.sql

-- ============================================
-- 1. Users Table
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100),
    email VARCHAR(100) UNIQUE,
    password_hash VARCHAR(255),
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT valid_email CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}$')
);

COMMENT ON TABLE users IS '사용자 기본 정보';
COMMENT ON COLUMN users.user_id IS '사용자 고유 식별자 (비즈니스 키)';
COMMENT ON COLUMN users.status IS '사용자 상태: ACTIVE, INACTIVE, SUSPENDED';

-- ============================================
-- 2. Trading Configs Table
-- ============================================
CREATE TABLE IF NOT EXISTS trading_configs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    enabled BOOLEAN DEFAULT FALSE,
    auto_trading_enabled BOOLEAN DEFAULT FALSE,
    min_composite_score DECIMAL(5, 2) DEFAULT 2.0,
    max_stocks_to_buy INTEGER DEFAULT 5,
    max_amount_per_stock DECIMAL(12, 2) DEFAULT 10000.0,
    stop_loss_percent DECIMAL(5, 2) DEFAULT -7.0,
    take_profit_percent DECIMAL(5, 2) DEFAULT 5.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT valid_scores CHECK (
        min_composite_score >= 0 AND min_composite_score <= 10
    ),
    CONSTRAINT valid_stocks CHECK (max_stocks_to_buy > 0),
    CONSTRAINT valid_amount CHECK (max_amount_per_stock > 0)
);

COMMENT ON TABLE trading_configs IS '사용자별 거래 설정';
COMMENT ON COLUMN trading_configs.min_composite_score IS '신호 신뢰도 최소값 (0-10)';

-- ============================================
-- 3. Account Balances Table
-- ============================================
CREATE TABLE IF NOT EXISTS account_balances (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    cash DECIMAL(15, 2) NOT NULL DEFAULT 0,
    total_value DECIMAL(15, 2) NOT NULL DEFAULT 0,
    locked_cash DECIMAL(15, 2) DEFAULT 0,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT valid_cash CHECK (cash >= 0),
    CONSTRAINT valid_total CHECK (total_value >= 0),
    CONSTRAINT valid_locked CHECK (locked_cash >= 0)
);

COMMENT ON TABLE account_balances IS '사용자 계좌 잔액 (매우 자주 업데이트됨)';
COMMENT ON COLUMN account_balances.version IS 'Optimistic locking 버전번호';
COMMENT ON COLUMN account_balances.locked_cash IS '미체결 주문으로 인해 잠긴 현금';

-- ============================================
-- 4. Stock Holdings Table
-- ============================================
CREATE TABLE IF NOT EXISTS stock_holdings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    quantity INTEGER NOT NULL,
    average_price DECIMAL(10, 2) NOT NULL,
    total_cost DECIMAL(15, 2) NOT NULL,
    current_value DECIMAL(15, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (user_id, ticker),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT valid_quantity CHECK (quantity > 0),
    CONSTRAINT valid_price CHECK (average_price > 0)
);

COMMENT ON TABLE stock_holdings IS '사용자의 주식 보유 내역';

-- ============================================
-- 5. Trades Table
-- ============================================
CREATE TABLE IF NOT EXISTS trades (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    side VARCHAR(10) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity INTEGER NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL,
    commission DECIMAL(10, 2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (
        status IN ('PENDING', 'EXECUTED', 'FAILED', 'CANCELLED')
    ),
    kis_order_id VARCHAR(100),
    executed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT valid_quantity CHECK (quantity > 0),
    CONSTRAINT valid_price CHECK (price > 0),
    CONSTRAINT valid_amount CHECK (total_amount > 0)
);

COMMENT ON TABLE trades IS '거래 기록 (감사, 정산용)';
COMMENT ON COLUMN trades.kis_order_id IS '외부 거래소 주문 ID (KIS)';

-- ============================================
-- 6. Trade Signals Executed Table
-- ============================================
CREATE TABLE IF NOT EXISTS trade_signals_executed (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    recommendation_id VARCHAR(100) NOT NULL,  -- MongoDB ObjectId
    ticker VARCHAR(10) NOT NULL,
    signal VARCHAR(20) NOT NULL CHECK (signal IN ('BUY', 'SELL', 'HOLD')),
    confidence DECIMAL(3, 2) NOT NULL,
    execution_decision VARCHAR(20) NOT NULL CHECK (
        execution_decision IN ('EXECUTED', 'SKIPPED', 'FAILED')
    ),
    skip_reason VARCHAR(255),
    executed_trade_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (executed_trade_id) REFERENCES trades(id) ON DELETE SET NULL,
    CONSTRAINT valid_confidence CHECK (confidence >= 0 AND confidence <= 1)
);

COMMENT ON TABLE trade_signals_executed IS '거래 신호 실행 로그 (감사, 재현용)';
COMMENT ON COLUMN trade_signals_executed.recommendation_id IS 'MongoDB stock_recommendations._id';
COMMENT ON COLUMN trade_signals_executed.execution_decision IS '실행 여부 및 결과';
```

```sql
-- docs/sql/02-indexes.sql

-- ============================================
-- Performance Indexes
-- ============================================

-- Users 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_users_user_id ON users(user_id);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- Trading Configs 인덱스
CREATE INDEX IF NOT EXISTS idx_trading_configs_enabled ON trading_configs(user_id, enabled);

-- Account Balances 인덱스 (매우 자주 쿼리)
CREATE INDEX IF NOT EXISTS idx_account_balances_user_id ON account_balances(user_id);

-- Stock Holdings 인덱스
CREATE INDEX IF NOT EXISTS idx_stock_holdings_user_id ON stock_holdings(user_id);
CREATE INDEX IF NOT EXISTS idx_stock_holdings_ticker ON stock_holdings(ticker);

-- Trades 인덱스 (복합 인덱스)
CREATE INDEX IF NOT EXISTS idx_trades_user_ticker_date ON trades(user_id, ticker, executed_at DESC);
CREATE INDEX IF NOT EXISTS idx_trades_status ON trades(status);
CREATE INDEX IF NOT EXISTS idx_trades_kis_order_id ON trades(kis_order_id) WHERE kis_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trades_executed_at ON trades(executed_at DESC) WHERE status = 'EXECUTED';

-- Trade Signals Executed 인덱스
CREATE INDEX IF NOT EXISTS idx_trade_signals_user_timestamp ON trade_signals_executed(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_trade_signals_recommendation_id ON trade_signals_executed(recommendation_id);

-- ============================================
-- Partitioning for High-Volume Tables (Optional)
-- ============================================
-- 거래 데이터가 매우 많아지면 월별 파티셔닝
-- CREATE TABLE trades_2024_01 PARTITION OF trades
--     FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
```

---

## 2. Spring Boot JPA 설정

### 2.1 RDB 설정 클래스

```kotlin
// src/main/kotlin/com/quantiq/core/config/RdbConfig.kt
package com.quantiq.core.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.persistence.EntityManagerFactory

@Configuration
@EnableJpaRepositories(
    basePackages = ["com.quantiq.core.repository.rdb"],
    entityManagerFactoryRef = "rdbEntityManagerFactory",
    transactionManagerRef = "rdbTransactionManager"
)
@EnableTransactionManagement
@ConditionalOnProperty(name = "db.rdb.enabled", havingValue = "true", matchIfMissing = true)
class RdbConfig(
    @Value("\${spring.datasource.url}")
    private val dbUrl: String,

    @Value("\${spring.datasource.username}")
    private val dbUsername: String,

    @Value("\${spring.datasource.password}")
    private val dbPassword: String,

    @Value("\${spring.datasource.hikari.maximum-pool-size:20}")
    private val maxPoolSize: Int,

    @Value("\${spring.datasource.hikari.minimum-idle:5}")
    private val minIdle: Int
) {

    @Bean
    @Primary
    fun rdbDataSource(): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = dbUrl
            username = dbUsername
            password = dbPassword
            maximumPoolSize = maxPoolSize
            minimumIdle = minIdle
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            autoCommit = true

            // PostgreSQL 특화 설정
            addDataSourceProperty("cachePreparedStatements", "true")
            addDataSourceProperty("preparedStatementCacheSize", "250")
            addDataSourceProperty("preparedStatementCacheSqlLimit", "2048")
            addDataSourceProperty("stringtype", "unspecified")
        }
        return HikariDataSource(config)
    }

    @Bean
    fun rdbEntityManagerFactory(dataSource: DataSource): LocalContainerEntityManagerFactoryBean {
        val adapter = HibernateJpaVendorAdapter().apply {
            setGenerateDdl(false)
            setShowSql(false)
        }

        return LocalContainerEntityManagerFactoryBean().apply {
            this.dataSource = dataSource
            setPackagesToScan("com.quantiq.core.domain.rdb")
            jpaVendorAdapter = adapter
            setJpaPropertyMap(jpaProperties())
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
            "hibernate.format_sql" to "false",
            "hibernate.use_sql_comments" to "true",
            "hibernate.jdbc.batch_size" to "20",
            "hibernate.order_inserts" to "true",
            "hibernate.order_updates" to "true",
            "hibernate.jdbc.use_get_generated_keys" to "true",

            // 성능 최적화
            "hibernate.jdbc.fetch_size" to "100",
            "hibernate.jdbc.batch_versioned_data" to "true",

            // 자동 스키마 생성 (운영 환경에서는 false)
            "hibernate.hbm2ddl.auto" to "validate",

            // 플러시 옵션
            "hibernate.flush_mode" to "COMMIT"
        )
    }
}
```

### 2.2 애플리케이션 설정

```yaml
# application.yml
spring:
  jpa:
    open-in-view: false  # N+1 문제 방지
    properties:
      hibernate:
        jdbc:
          batch_size: 20
          fetch_size: 100
          use_get_generated_keys: true

  datasource:
    url: jdbc:postgresql://postgresql:5432/quantiq
    username: quantiq_user
    password: ${POSTGRES_PASSWORD:quantiq_password}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  mongodb:
    uri: mongodb://mongodb:27017/stock_trading
    auto-index-creation: true

db:
  rdb:
    enabled: true
  dual-write: true  # 이중 쓰기 모드
  read-source: rdb  # rdb, mongo, both
```

---

## 3. 엔티티 정의

### 3.1 User 엔티티 (RDB)

```kotlin
// src/main/kotlin/com/quantiq/core/domain/rdb/User.kt
package com.quantiq.core.domain.rdb

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(
    name = "users",
    indexes = [
        Index(name = "idx_user_id", columnList = "user_id", unique = true),
        Index(name = "idx_status", columnList = "status")
    ]
)
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 50, unique = true)
    val userId: String,

    @Column(length = 100)
    val name: String? = null,

    @Column(length = 100, unique = true)
    val email: String? = null,

    @Column(length = 255)
    val passwordHash: String? = null,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val status: UserStatus = UserStatus.ACTIVE,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val tradingConfig: TradingConfig? = null,

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val balance: AccountBalance? = null,

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val holdings: List<StockHolding> = emptyList(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val trades: List<Trade> = emptyList()
) {
    fun isActive(): Boolean = status == UserStatus.ACTIVE
}

enum class UserStatus {
    ACTIVE, INACTIVE, SUSPENDED
}
```

### 3.2 AccountBalance 엔티티 (RDB)

```kotlin
// src/main/kotlin/com/quantiq/core/domain/rdb/AccountBalance.kt
package com.quantiq.core.domain.rdb

import java.math.BigDecimal
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "account_balances")
data class AccountBalance(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    val user: User,

    @Column(nullable = false, precision = 15, scale = 2)
    var cash: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 15, scale = 2)
    var totalValue: BigDecimal = BigDecimal.ZERO,

    @Column(precision = 15, scale = 2)
    var lockedCash: BigDecimal = BigDecimal.ZERO,

    @Version
    @Column(nullable = false)
    var version: Long = 0,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun getAvailableCash(): BigDecimal = cash - lockedCash

    fun getTotalHoldingsValue(holdings: List<StockHolding>): BigDecimal {
        return holdings.fold(BigDecimal.ZERO) { acc, holding ->
            acc + holding.currentValue
        }
    }
}
```

### 3.3 Trade 엔티티 (RDB)

```kotlin
// src/main/kotlin/com/quantiq/core/domain/rdb/Trade.kt
package com.quantiq.core.domain.rdb

import java.math.BigDecimal
import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(
    name = "trades",
    indexes = [
        Index(name = "idx_trades_user_ticker_date", columnList = "user_id, ticker, executed_at"),
        Index(name = "idx_trades_status", columnList = "status"),
        Index(name = "idx_trades_kis_order_id", columnList = "kis_order_id")
    ]
)
data class Trade(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(nullable = false, length = 10)
    val ticker: String,

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    val side: TradeSide,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false, precision = 10, scale = 2)
    val price: BigDecimal,

    @Column(nullable = false, precision = 15, scale = 2)
    val totalAmount: BigDecimal,

    @Column(precision = 10, scale = 2)
    val commission: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: TradeStatus = TradeStatus.PENDING,

    @Column(length = 100)
    val kisOrderId: String? = null,

    @Column(name = "executed_at")
    var executedAt: LocalDateTime? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun isExecuted(): Boolean = status == TradeStatus.EXECUTED
    fun isFailed(): Boolean = status == TradeStatus.FAILED
}

enum class TradeSide {
    BUY, SELL
}

enum class TradeStatus {
    PENDING, EXECUTED, FAILED, CANCELLED
}
```

---

## 4. Repository 정의

### 4.1 RDB Repository

```kotlin
// src/main/kotlin/com/quantiq/core/repository/rdb/UserRepository.kt
package com.quantiq.core.repository.rdb

import com.quantiq.core.domain.rdb.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUserId(userId: String): User?

    @Query(
        "SELECT u FROM User u " +
        "JOIN FETCH u.tradingConfig tc " +
        "WHERE u.userId = :userId AND tc.enabled = true"
    )
    fun findActiveUserWithConfig(userId: String): User?

    @Query(
        "SELECT u FROM User u " +
        "JOIN u.tradingConfig tc " +
        "WHERE tc.autoTradingEnabled = true AND u.status = 'ACTIVE'"
    )
    fun findAllAutoTradingUsers(): List<User>
}

// src/main/kotlin/com/quantiq/core/repository/rdb/AccountBalanceRepository.kt
package com.quantiq.core.repository.rdb

import com.quantiq.core.domain.rdb.AccountBalance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import javax.persistence.LockModeType

@Repository
interface AccountBalanceRepository : JpaRepository<AccountBalance, Long> {
    fun findByUserId(userId: Long): AccountBalance?

    // 비관적 잠금 (Pessimistic Lock)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.user.id = :userId")
    fun findByUserIdForUpdate(userId: Long): AccountBalance?
}

// src/main/kotlin/com/quantiq/core/repository/rdb/TradeRepository.kt
package com.quantiq.core.repository.rdb

import com.quantiq.core.domain.rdb.Trade
import com.quantiq.core.domain.rdb.TradeStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface TradeRepository : JpaRepository<Trade, Long> {
    fun findByUserIdAndStatusOrderByCreatedAtDesc(
        userId: Long,
        status: TradeStatus,
        pageable: Pageable
    ): Page<Trade>

    @Query(
        "SELECT t FROM Trade t " +
        "WHERE t.user.id = :userId " +
        "AND t.executedAt >= :startDate " +
        "AND t.executedAt <= :endDate " +
        "ORDER BY t.executedAt DESC"
    )
    fun findUserTradesByDateRange(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        pageable: Pageable
    ): Page<Trade>

    fun findByKisOrderId(kisOrderId: String): Trade?
}
```

---

## 5. Service 구현

### 5.1 Balance Service (RDB)

```kotlin
// src/main/kotlin/com/quantiq/core/service/RdbBalanceService.kt
package com.quantiq.core.service

import com.quantiq.core.domain.rdb.AccountBalance
import com.quantiq.core.domain.rdb.Trade
import com.quantiq.core.domain.rdb.TradeStatus
import com.quantiq.core.repository.rdb.AccountBalanceRepository
import com.quantiq.core.repository.rdb.TradeRepository
import java.math.BigDecimal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RdbBalanceService(
    private val balanceRepository: AccountBalanceRepository,
    private val tradeRepository: TradeRepository
) {

    @Transactional(transactionManager = "rdbTransactionManager")
    fun executeTradeWithLocking(trade: Trade, userId: Long): Boolean {
        // 1. 비관적 잠금으로 잔액 조회
        val balance = balanceRepository.findByUserIdForUpdate(userId)
            ?: throw IllegalStateException("Balance not found for user: $userId")

        // 2. 가용 현금 확인
        val availableCash = balance.getAvailableCash()
        if (availableCash < trade.totalAmount) {
            throw InsufficientBalanceException(
                "Available: ${availableCash}, Required: ${trade.totalAmount}"
            )
        }

        // 3. 잔액 업데이트 (잠금 상태에서)
        balance.cash -= trade.totalAmount
        balance.lockedCash -= trade.totalAmount
        balance.updatedAt = java.time.LocalDateTime.now()

        balanceRepository.save(balance)

        // 4. 거래 저장
        tradeRepository.save(trade)

        return true
    }

    @Transactional(transactionManager = "rdbTransactionManager")
    fun updateBalanceAfterExecution(
        userId: Long,
        trade: Trade,
        executedPrice: BigDecimal,
        executedQuantity: Int
    ) {
        val balance = balanceRepository.findByUserId(userId)
            ?: throw IllegalStateException("Balance not found")

        val actualAmount = executedPrice * executedQuantity.toBigDecimal()

        // 실제 체결 금액과의 차액 처리
        val difference = trade.totalAmount - actualAmount

        balance.cash += difference  // 차액 반환
        balance.lockedCash -= trade.totalAmount
        balance.totalValue += (executedQuantity * executedPrice.toDouble()).toBigDecimal()
        balance.updatedAt = java.time.LocalDateTime.now()

        balanceRepository.save(balance)
    }

    fun getBalance(userId: Long): AccountBalance {
        return balanceRepository.findByUserId(userId)
            ?: throw IllegalStateException("Balance not found for user: $userId")
    }
}

class InsufficientBalanceException(message: String) : RuntimeException(message)
```

### 5.2 Auto Trading Service (RDB + MongoDB)

```kotlin
// src/main/kotlin/com/quantiq/core/service/HybridAutoTradingService.kt
package com.quantiq.core.service

import com.quantiq.core.domain.mongo.StockRecommendation
import com.quantiq.core.domain.rdb.*
import com.quantiq.core.repository.mongo.StockRecommendationRepository as MongoRecommendationRepository
import com.quantiq.core.repository.rdb.*
import java.math.BigDecimal
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class HybridAutoTradingService(
    private val mongoRecommendationRepository: MongoRecommendationRepository,
    private val userRepository: UserRepository,
    private val balanceService: RdbBalanceService,
    private val tradeRepository: TradeRepository,
    private val tradeSignalRepository: RdbTradeSignalRepository,
    private val kissApiClient: KisApiClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(transactionManager = "rdbTransactionManager")
    fun executeAutoTrading() {
        logger.info("Starting Auto Trading...")

        // 1. MongoDB에서 오늘의 추천 신호 조회
        val today = LocalDate.now().toString()
        val recommendations = mongoRecommendationRepository.findByDateAndIsRecommendedTrue(today)
        logger.info("Found ${recommendations.size} recommendations for $today")

        if (recommendations.isEmpty()) return

        // 2. RDB에서 활성 사용자 조회
        val activeUsers = userRepository.findAllAutoTradingUsers()
        logger.info("Found ${activeUsers.size} active users")

        activeUsers.forEach { user ->
            try {
                processUserRecommendations(user, recommendations)
            } catch (e: Exception) {
                logger.error("Error processing user ${user.userId}", e)
            }
        }

        logger.info("Auto Trading Completed")
    }

    @Transactional(transactionManager = "rdbTransactionManager")
    private fun processUserRecommendations(
        user: User,
        recommendations: List<StockRecommendation>
    ) {
        val config = user.tradingConfig ?: return
        val maxStocks = config.maxStocksToBuy

        // 신뢰도 필터링
        val filteredRecommendations = recommendations
            .filter { it.signal_generation.confidence_score >= config.minCompositeScore }
            .take(maxStocks)

        filteredRecommendations.forEach { rec ->
            try {
                executeTrade(user, rec)
            } catch (e: Exception) {
                logger.error("Failed to execute trade for ${rec.ticker}", e)

                // RDB에 실패 기록
                recordSignalExecution(
                    user.id!!,
                    rec._id!!,
                    rec.ticker,
                    rec.signal_generation.primary_signal,
                    rec.signal_generation.confidence_score,
                    "FAILED",
                    e.message
                )
            }
        }
    }

    @Transactional(transactionManager = "rdbTransactionManager")
    private fun executeTrade(user: User, recommendation: StockRecommendation) {
        val config = user.tradingConfig!!

        // 1. Trade 객체 생성 (PENDING 상태)
        val trade = Trade(
            user = user,
            ticker = recommendation.ticker,
            side = TradeSide.BUY,
            quantity = calculateQuantity(user, config, recommendation),
            price = recommendation.price_info.current,
            totalAmount = calculateTotalAmount(user, config, recommendation)
        )

        // 2. 잔액 확인 및 차감 (비관적 잠금)
        balanceService.executeTradeWithLocking(trade, user.id!!)

        // 3. KIS API 호출 (비동기로 처리하는 것이 좋음)
        try {
            val kisResult = kissApiClient.placeTrade(
                ticker = recommendation.ticker,
                quantity = trade.quantity,
                price = recommendation.price_info.current
            )

            // 4. 거래 상태 업데이트
            trade.status = TradeStatus.EXECUTED
            trade.executedAt = LocalDateTime.now()
            trade.kisOrderId = kisResult.orderId
            tradeRepository.save(trade)

            // 5. 잔액 최종 업데이트
            balanceService.updateBalanceAfterExecution(
                user.id!!,
                trade,
                kisResult.executedPrice,
                kisResult.executedQuantity
            )

            // 6. 신호 실행 기록
            recordSignalExecution(
                user.id!!,
                recommendation._id!!,
                recommendation.ticker,
                recommendation.signal_generation.primary_signal,
                recommendation.signal_generation.confidence_score,
                "EXECUTED",
                null,
                trade.id
            )

            logger.info("Trade executed: ${recommendation.ticker} for user ${user.userId}")

        } catch (e: Exception) {
            // 7. 실패 시 거래 상태 업데이트
            trade.status = TradeStatus.FAILED
            tradeRepository.save(trade)

            // 8. 잔액 롤백 (트랜잭션 롤백)
            throw TradeExecutionException("Failed to execute trade on KIS", e)
        }
    }

    @Transactional(transactionManager = "rdbTransactionManager")
    private fun recordSignalExecution(
        userId: Long,
        recommendationId: String,
        ticker: String,
        signal: String,
        confidence: Double,
        decision: String,
        skipReason: String? = null,
        executedTradeId: Long? = null
    ) {
        val signalRecord = TradeSignalExecuted(
            user = userRepository.getReferenceById(userId),
            recommendationId = recommendationId,
            ticker = ticker,
            signal = signal,
            confidence = confidence.toBigDecimal(),
            executionDecision = decision,
            skipReason = skipReason,
            executedTradeId = executedTradeId
        )
        tradeSignalRepository.save(signalRecord)
    }

    private fun calculateQuantity(
        user: User,
        config: TradingConfig,
        recommendation: StockRecommendation
    ): Int {
        val maxPerStock = config.maxAmountPerStock
        val quantity = (maxPerStock / recommendation.price_info.current).toInt()
        return maxOf(quantity, 1)  // 최소 1주
    }

    private fun calculateTotalAmount(
        user: User,
        config: TradingConfig,
        recommendation: StockRecommendation
    ): BigDecimal {
        val quantity = calculateQuantity(user, config, recommendation)
        return recommendation.price_info.current * quantity.toBigDecimal()
    }
}

class TradeExecutionException(message: String, cause: Throwable) : RuntimeException(message, cause)
```

---

## 6. 마이그레이션 스크립트

### 6.1 기존 MongoDB → RDB 마이그레이션

```kotlin
// src/main/kotlin/com/quantiq/core/migration/MongoToRdbMigration.kt
package com.quantiq.core.migration

import com.quantiq.core.domain.mongo.User as MongoUser
import com.quantiq.core.domain.rdb.*
import com.quantiq.core.repository.mongo.UserRepository as MongoUserRepository
import com.quantiq.core.repository.rdb.*
import java.time.LocalDateTime
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MongoToRdbMigration(
    private val mongoUserRepository: MongoUserRepository,
    private val rdbUserRepository: UserRepository,
    private val rdbConfigRepository: TradingConfigRepository,
    private val rdbBalanceRepository: AccountBalanceRepository
) {

    @EventListener(ApplicationReadyEvent::class)
    @Transactional(transactionManager = "rdbTransactionManager")
    fun migrateData() {
        if (System.getenv("SKIP_MIGRATION") == "true") {
            return
        }

        logger.info("Starting data migration from MongoDB to RDB...")

        val mongoUsers = mongoUserRepository.findAll()
        var migratedCount = 0

        mongoUsers.forEach { mongoUser ->
            try {
                migrateUser(mongoUser)
                migratedCount++
            } catch (e: Exception) {
                logger.error("Failed to migrate user: ${mongoUser.userId}", e)
            }
        }

        logger.info("Migration completed. Migrated $migratedCount users")
    }

    private fun migrateUser(mongoUser: MongoUser) {
        // 1. User 생성
        val rdbUser = User(
            userId = mongoUser.userId,
            name = mongoUser.name,
            email = mongoUser.email ?: "unknown@example.com",
            status = UserStatus.ACTIVE
        )
        val savedUser = rdbUserRepository.save(rdbUser)

        // 2. TradingConfig 생성
        mongoUser.tradingConfig?.let { mongoConfig ->
            val rdbConfig = TradingConfig(
                user = savedUser,
                enabled = mongoConfig.enabled,
                autoTradingEnabled = mongoConfig.autoTradingEnabled,
                minCompositeScore = mongoConfig.minCompositeScore,
                maxStocksToBuy = mongoConfig.maxStocksToBuy,
                maxAmountPerStock = mongoConfig.maxAmountPerStock.toBigDecimal(),
                stopLossPercent = mongoConfig.stopLossPercent,
                takeProfitPercent = mongoConfig.takeProfitPercent
            )
            rdbConfigRepository.save(rdbConfig)
        }

        // 3. AccountBalance 생성
        val balance = AccountBalance(
            user = savedUser,
            cash = BigDecimal("1000000"),  // 초기 자본
            totalValue = BigDecimal("1000000")
        )
        rdbBalanceRepository.save(balance)

        logger.info("Migrated user: ${mongoUser.userId}")
    }
}
```

---

## 7. 이중 쓰기 모드 설정

```yaml
# application.yml
db:
  rdb:
    enabled: true
  dual-write: true  # 마이그레이션 기간 활성화
  read-source: rdb  # rdb, mongo, both (비교 검증)
  migration:
    enabled: false
    batch-size: 100
```

---

## 8. 성능 테스트

### 벤치마크 스크립트

```kotlin
@SpringBootTest
class DatabasePerformanceTest {

    @Test
    fun testRdbQueryPerformance() {
        // RDB 쿼리 성능: < 10ms
        val start = System.currentTimeMillis()
        repeat(1000) {
            userRepository.findByUserId("user123")
        }
        val duration = System.currentTimeMillis() - start
        println("RDB Average: ${duration / 1000}ms")
    }

    @Test
    fun testBalanceUpdatePerformance() {
        // 잔액 업데이트 성능: < 50ms
        val start = System.currentTimeMillis()
        repeat(100) {
            balanceService.executeTradeWithLocking(mockTrade, userId)
        }
        val duration = System.currentTimeMillis() - start
        println("Balance Update Average: ${duration / 100}ms")
    }
}
```

---

## 다음 단계

- [DATABASE_MIGRATION.md](DATABASE_MIGRATION.md) - 상세 마이그레이션 가이드
- [PERFORMANCE_TUNING.md](PERFORMANCE_TUNING.md) - 성능 최적화 가이드
