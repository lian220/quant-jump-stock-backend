# RDB 마이그레이션 실행 계획

## 1. Spring Entity 자동 테이블 생성 방법 비교

### 옵션 1: Hibernate 자동 생성 (간단, 개발용)
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: create  # create-drop, update, validate, none
    database-platform: org.hibernate.dialect.PostgreSQL15Dialect
```

**장점:**
- ✅ 구현 간단
- ✅ Entity 클래스만으로 테이블 생성
- ✅ 빠른 개발

**단점:**
- ❌ 프로덕션에 부적합
- ❌ 버전 관리 안 됨
- ❌ 롤백 불가능
- ❌ 복잡한 스키마 처리 어려움

---

### 옵션 2: Flyway (권장, 프로덕션)
마이그레이션 버전 관리 도구

**구성:**
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    sql-migration-prefix: V
    sql-migration-separator: __
    sql-migration-suffixes: .sql
  jpa:
    hibernate:
      ddl-auto: validate  # 자동 생성 불가, 검증만 수행
```

**파일 구조:**
```
src/main/resources/db/migration/
├── V1__Initial_Schema.sql          # 초기 스키마
├── V2__Add_Indexes.sql             # 인덱스 추가
├── V3__Add_Trading_Signals.sql     # 새 테이블 추가
└── V4__Fix_Constraints.sql         # 제약조건 수정
```

**장점:**
- ✅ 버전 관리 가능
- ✅ 롤백 가능 (new migration 추가로)
- ✅ 프로덕션 안전
- ✅ 팀 협업 용이
- ✅ 변경 이력 추적

**단점:**
- ⚠️ SQL 스크립트 직접 작성 필요
- ⚠️ 버전 관리 복잡

---

### 옵션 3: Liquibase (복잡, 엔터프라이즈)
더 강력한 마이그레이션 도구 (XML/YAML)

**특징:**
- 여러 DB 지원
- 롤백 자동 생성
- 복잡한 마이그레이션 지원

**단점:**
- 학습곡선 가파름
- 중소 프로젝트에는 과도함

---

## 2. 추천 방식: Flyway + Entity 검증

### 최적 조합

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # ← 중요: 테이블 자동 생성 안 함
    generate-ddl: false
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
```

**이유:**
- 📌 **스키마는 Flyway로 관리** (버전 추적, 안전)
- 📌 **Entity는 검증만** (Flyway와 동기화 확인)
- 📌 **프로덕션 안전성** (예측 가능한 마이그레이션)

---

## 3. 구현 단계 (4일)

### Day 1: PostgreSQL 설정

#### 1.1 build.gradle.kts 업데이트

```kotlin
dependencies {
    // 기존 의존성...

    // PostgreSQL
    implementation("org.postgresql:postgresql:42.7.1")

    // Flyway
    implementation("org.flywaydb:flyway-core:9.22.3")

    // JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Connection Pool
    implementation("com.zaxxer:HikariCP:5.1.0")
}
```

#### 1.2 application.yml 설정

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:quantiq}
    username: ${DB_USER:quantiq_user}
    password: ${DB_PASSWORD:quantiq_password}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000

  jpa:
    hibernate:
      ddl-auto: validate  # 자동 생성 안 함
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQL15Dialect
        jdbc.batch_size: 20
        format_sql: false

  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    sql-migration-prefix: V
    sql-migration-separator: __

db:
  rdb:
    enabled: true
  dual-write: true      # 마이그레이션 기간 MongoDB도 업데이트
  read-source: rdb      # 읽기는 RDB에서
```

#### 1.3 .env 업데이트

```env
# PostgreSQL
DB_HOST=postgresql
DB_PORT=5432
DB_NAME=quantiq
DB_USER=quantiq_user
DB_PASSWORD=your_secure_password

# MongoDB (기존)
SPRING_DATA_MONGODB_URI=mongodb://mongodb:27017/stock_trading

# Kafka (기존)
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092
```

#### 1.4 docker-compose.yml 업데이트

```yaml
version: '3.8'

services:
  postgresql:
    image: postgres:15-alpine
    container_name: quantiq-postgres
    environment:
      POSTGRES_DB: quantiq
      POSTGRES_USER: quantiq_user
      POSTGRES_PASSWORD: ${DB_PASSWORD:-quantiq_password}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U quantiq_user"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - quantiq-network

  # 기존 서비스들...
  mongodb:
    image: mongo:latest
    container_name: quantiq-mongodb
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db
    networks:
      - quantiq-network

  quantiq-core:
    build: ./quantiq-core
    container_name: quantiq-core
    ports:
      - "10010:8080"
    depends_on:
      postgresql:
        condition: service_healthy
      mongodb:
        condition: service_started
      kafka:
        condition: service_started
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgresql:5432/quantiq
      SPRING_DATASOURCE_USERNAME: quantiq_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-quantiq_password}
      SPRING_DATA_MONGODB_URI: mongodb://mongodb:27017/stock_trading
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    networks:
      - quantiq-network

volumes:
  postgres_data:
  mongodb_data:

networks:
  quantiq-network:
    driver: bridge
```

---

### Day 2: Flyway 마이그레이션 스크립트 작성

#### 2.1 초기 스키마 생성

```sql
-- src/main/resources/db/migration/V1__Initial_Schema.sql

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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, ticker),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

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
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'EXECUTED', 'FAILED', 'CANCELLED')),
    kis_order_id VARCHAR(100),
    executed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ============================================
-- 6. Trade Signals Executed Table
-- ============================================
CREATE TABLE IF NOT EXISTS trade_signals_executed (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    recommendation_id VARCHAR(100) NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    signal VARCHAR(20) NOT NULL CHECK (signal IN ('BUY', 'SELL', 'HOLD')),
    confidence DECIMAL(3, 2) NOT NULL,
    execution_decision VARCHAR(20) NOT NULL CHECK (execution_decision IN ('EXECUTED', 'SKIPPED', 'FAILED')),
    skip_reason VARCHAR(255),
    executed_trade_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (executed_trade_id) REFERENCES trades(id) ON DELETE SET NULL
);
```

#### 2.2 인덱스 생성

```sql
-- src/main/resources/db/migration/V2__Create_Indexes.sql

-- Users 인덱스
CREATE INDEX IF NOT EXISTS idx_users_user_id ON users(user_id);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- Trading Configs 인덱스
CREATE INDEX IF NOT EXISTS idx_trading_configs_enabled ON trading_configs(user_id, enabled);

-- Stock Holdings 인덱스
CREATE INDEX IF NOT EXISTS idx_stock_holdings_user_id ON stock_holdings(user_id);
CREATE INDEX IF NOT EXISTS idx_stock_holdings_ticker ON stock_holdings(ticker);

-- Trades 인덱스
CREATE INDEX IF NOT EXISTS idx_trades_user_ticker_date ON trades(user_id, ticker, executed_at DESC);
CREATE INDEX IF NOT EXISTS idx_trades_status ON trades(status);
CREATE INDEX IF NOT EXISTS idx_trades_kis_order_id ON trades(kis_order_id) WHERE kis_order_id IS NOT NULL;

-- Trade Signals Executed 인덱스
CREATE INDEX IF NOT EXISTS idx_trade_signals_user_timestamp ON trade_signals_executed(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_trade_signals_recommendation_id ON trade_signals_executed(recommendation_id);
```

---

### Day 3: Entity 클래스 작성

Entity는 스키마와 동기화만 되도록 (자동 생성 불가)

```kotlin
// src/main/kotlin/com/quantiq/core/domain/rdb/User.kt
@Entity
@Table(name = "users")
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: UserStatus = UserStatus.ACTIVE,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
```

---

### Day 4: 데이터 마이그레이션

#### 4.1 MongoDB → RDB 마이그레이션 스크립트

```kotlin
// src/main/kotlin/com/quantiq/core/service/RdbMigrationService.kt
@Service
class RdbMigrationService(
    private val mongoUserRepository: com.quantiq.core.repository.mongo.UserRepository,
    private val rdbUserRepository: com.quantiq.core.repository.rdb.UserRepository,
    private val mongoTradingConfigRepository: MongoTradingConfigRepository,
    private val rdbTradingConfigRepository: TradingConfigRepository
) {

    fun migrateAllUsers() {
        logger.info("🔄 MongoDB → RDB 사용자 데이터 마이그레이션 시작")

        val mongoUsers = mongoUserRepository.findAll()
        var successCount = 0

        mongoUsers.forEach { mongoUser ->
            try {
                // 1. RDB에 사용자 저장
                val rdbUser = User(
                    userId = mongoUser.userId,
                    name = mongoUser.name,
                    email = mongoUser.email ?: "unknown@${mongoUser.userId}.local",
                    status = UserStatus.ACTIVE
                )
                val savedUser = rdbUserRepository.save(rdbUser)

                // 2. 거래 설정 마이그레이션
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
                    rdbTradingConfigRepository.save(rdbConfig)
                }

                // 3. 계좌 잔액 초기화
                val balance = AccountBalance(
                    user = savedUser,
                    cash = BigDecimal("1000000"),
                    totalValue = BigDecimal("1000000")
                )
                rdbBalanceRepository.save(balance)

                successCount++
                logger.info("✅ 사용자 마이그레이션: ${mongoUser.userId}")

            } catch (e: Exception) {
                logger.error("❌ 사용자 마이그레이션 실패: ${mongoUser.userId}", e)
            }
        }

        logger.info("✅ 마이그레이션 완료: $successCount/${mongoUsers.size}명")
    }
}
```

#### 4.2 마이그레이션 실행 (Spring Boot 시작 시)

```kotlin
@SpringBootApplication
class QuantiqCoreApplication(
    private val migrationService: RdbMigrationService
) {

    @EventListener(ApplicationReadyEvent::class)
    fun runMigration() {
        if (System.getenv("RUN_MIGRATION") == "true") {
            migrationService.migrateAllUsers()
        }
    }
}

fun main(args: Array<String>) {
    runApplication<QuantiqCoreApplication>(*args)
}
```

---

## 4. 실행 절차

### Step 1: 환경 준비 (약 10분)

```bash
# 1. .env 파일 업데이트
cat > .env << EOF
DB_HOST=postgresql
DB_PORT=5432
DB_NAME=quantiq
DB_USER=quantiq_user
DB_PASSWORD=your_secure_password
SPRING_DATA_MONGODB_URI=mongodb://mongodb:27017/stock_trading
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092
RUN_MIGRATION=false
EOF

# 2. build.gradle.kts 의존성 추가
# PostgreSQL, Flyway 추가

# 3. docker-compose.yml 업데이트
# postgresql 서비스 추가
```

### Step 2: PostgreSQL 시작 (약 5분)

```bash
# 1. PostgreSQL 컨테이너 시작
docker-compose up -d postgresql

# 2. 연결 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq -c "\dt"

# 3. 대기: 테이블 생성 완료 확인
# (Flyway가 V1__Initial_Schema.sql 실행)
```

### Step 3: 애플리케이션 빌드

```bash
cd quantiq-core

# 1. 의존성 다운로드
./gradlew clean build

# 2. Docker 이미지 빌드
docker build -t quantiq-core:latest .
```

### Step 4: 데이터 마이그레이션 (약 5분)

```bash
# 1. 마이그레이션 전 MongoDB 데이터 확인
docker-compose exec mongodb mongosh
> use stock_trading
> db.users.countDocuments()

# 2. 애플리케이션 시작 (마이그레이션 비활성화)
docker-compose up -d quantiq-core

# 3. 로그 확인
docker-compose logs -f quantiq-core

# 4. RDB에 데이터 도착 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq -c "SELECT COUNT(*) FROM users;"

# 5. 마이그레이션 활성화 후 재시작
export RUN_MIGRATION=true
docker-compose restart quantiq-core

# 6. 마이그레이션 로그 확인
docker-compose logs -f quantiq-core | grep -i migration
```

### Step 5: 검증

```bash
# 1. 테이블 구조 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq -c "\d users"

# 2. 데이터 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq << EOF
SELECT COUNT(*) as user_count FROM users;
SELECT COUNT(*) as config_count FROM trading_configs;
SELECT COUNT(*) as balance_count FROM account_balances;
EOF

# 3. Flyway 마이그레이션 이력 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq \
  -c "SELECT * FROM flyway_schema_history;"
```

---

## 5. 검증 체크리스트

- [ ] PostgreSQL 컨테이너 정상 실행
- [ ] Flyway 마이그레이션 성공 (V1, V2 완료)
- [ ] RDB 테이블 생성 확인 (6개 테이블)
- [ ] MongoDB → RDB 데이터 마이그레이션 완료
- [ ] 사용자 데이터 일치 확인 (MongoDB vs RDB)
- [ ] 인덱스 생성 확인
- [ ] Spring Boot 애플리케이션 정상 시작
- [ ] 이중 쓰기 모드 동작 확인

---

## 6. 마이그레이션 후 확인 쿼리

```bash
# PostgreSQL 접속
docker-compose exec postgresql psql -U quantiq_user -d quantiq

# 1. 테이블 목록
\dt

# 2. 각 테이블 데이터 수
SELECT 'users' as table_name, COUNT(*) as count FROM users
UNION ALL
SELECT 'trading_configs', COUNT(*) FROM trading_configs
UNION ALL
SELECT 'account_balances', COUNT(*) FROM account_balances
UNION ALL
SELECT 'stock_holdings', COUNT(*) FROM stock_holdings
UNION ALL
SELECT 'trades', COUNT(*) FROM trades
UNION ALL
SELECT 'trade_signals_executed', COUNT(*) FROM trade_signals_executed;

# 3. Flyway 마이그레이션 이력
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC;

# 4. 특정 사용자 데이터 확인
SELECT u.id, u.user_id, tc.enabled, ab.cash
FROM users u
LEFT JOIN trading_configs tc ON u.id = tc.user_id
LEFT JOIN account_balances ab ON u.id = ab.user_id
WHERE u.user_id = 'user1';
```

---

## 7. 트러블슈팅

### 문제: Flyway 마이그레이션 실패

```bash
# 1. Flyway 테이블 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq \
  -c "SELECT * FROM flyway_schema_history;"

# 2. 마이그레이션 수동 복구 (이전 버전으로 돌리기 필요 시)
DELETE FROM flyway_schema_history WHERE version = 2;

# 3. 애플리케이션 재시작
docker-compose restart quantiq-core
```

### 문제: 데이터 마이그레이션 실패

```bash
# 1. MongoDB 데이터 확인
docker-compose exec mongodb mongosh
> db.users.findOne()

# 2. RDB 연결 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq -c "\d users"

# 3. 로그 확인
docker-compose logs quantiq-core | grep ERROR
```

### 문제: 포트 충돌

```bash
# 5432 포트 확인
lsof -i :5432

# 다른 프로세스 종료
kill -9 <PID>

# 또는 docker-compose.yml에서 포트 변경
# "5432:5432" → "5433:5432"
```

---

## 8. 성능 최적화

### 마이그레이션 후 분석 재구성

```sql
-- 통계 재생성
ANALYZE;

-- 인덱스 검증
REINDEX INDEX CONCURRENTLY idx_trades_user_ticker_date;

-- 테이블 통계
SELECT schemaname, tablename,
       pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

---

## 다음 단계

- [ ] 위 절차 따라 마이그레이션 실행
- [ ] 검증 체크리스트 완료
- [ ] 성능 테스트 (쿼리 응답시간)
- [ ] 이중 쓰기 모드 비활성화
- [ ] MongoDB 거래 데이터 아카이빙
