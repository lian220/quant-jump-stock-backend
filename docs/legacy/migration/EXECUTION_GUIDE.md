# 🚀 MongoDB → PostgreSQL 마이그레이션 실행 가이드

**상태:** 계획 수립 완료 ✅ | **예상 소요시간:** 4-5일 | **난이도:** 중상

---

## 📋 문서 구조

이 가이드는 다음 문서들과 함께 사용됩니다:

| 문서 | 내용 | 대상 |
|------|------|------|
| **DATABASE_STRATEGY.md** | 데이터 특성 분석, 스키마 설계 | 아키텍처/설계 |
| **RDB_MIGRATION_PLAN.md** | 단계별 기술 구현 계획 | 개발자 |
| **EXECUTION_GUIDE.md** (본 문서) | 실행 체크리스트, 시간표 | PM/리더 |

---

## 🎯 마이그레이션 목표

```
현재 상태 (MongoDB Only)
    ↓↓↓
목표 상태 (MongoDB + PostgreSQL Hybrid)

MongoDB: 분석 데이터, 시계열 데이터 (read-heavy)
PostgreSQL: 사용자, 거래, 계좌, 포트폴리오 (ACID 필요)
```

**기대 효과:**
- ✅ ACID 트랜잭션 보장 (거래 안전성)
- ✅ 쿼리 성능 5-10배 향상
- ✅ 복잡한 조인 쿼리 가능
- ✅ 규정 준수 (감사/정산 기록 필수)

---

## 📊 마이그레이션 타임라인 (4일)

### Day 1: 환경 준비 (2-3시간)
```
목표: PostgreSQL + Flyway 환경 구성
✓ Docker Compose 업데이트
✓ build.gradle.kts 의존성 추가
✓ application.yml 설정
✓ .env 파일 준비
```

**작업량:** 낮음 | **복잡도:** 낮음 | **위험도:** 낮음

### Day 2: 스키마 생성 & 마이그레이션 스크립트 (4-5시간)
```
목표: PostgreSQL 테이블 및 Flyway 마이그레이션 완성
✓ Flyway 초기 스키마 (V1__Initial_Schema.sql)
✓ 인덱스 생성 (V2__Create_Indexes.sql)
✓ Entity 클래스 작성
✓ 마이그레이션 서비스 구현
```

**작업량:** 중상 | **복잡도:** 중상 | **위험도:** 낮음

### Day 3: 데이터 마이그레이션 & 검증 (3-4시간)
```
목표: MongoDB 데이터를 PostgreSQL로 이관
✓ 마이그레이션 실행
✓ 데이터 검증
✓ 성능 테스트
✓ 문제 해결
```

**작업량:** 중 | **복잡도:** 중 | **위험도:** 중 (롤백 계획 필수)

### Day 4: 이중 쓰기 모드 & 전환 (2-3시간)
```
목표: 읽기/쓰기 점진적 전환
✓ 이중 쓰기 모드 활성화
✓ 읽기 소스 전환
✓ MongoDB 제거 준비
✓ 최종 검증
```

**작업량:** 중 | **복잡도:** 중 | **위험도:** 중 (모니터링 필수)

---

## ✅ 사전 체크리스트

### 기술 요구사항
- [ ] Docker & Docker Compose 설치 (최신)
- [ ] PostgreSQL 15+ 지식
- [ ] Spring Boot Data JPA 경험
- [ ] MongoDB 데이터 구조 이해

### 팀 준비
- [ ] 마이그레이션 담당자 할당
- [ ] 검증 담당자 할당
- [ ] 백업 담당자 할당
- [ ] 커뮤니케이션 채널 준비

### 데이터 준비
- [ ] MongoDB 전체 백업 생성
- [ ] 데이터 검증 (users, trading_configs, stocks 등)
- [ ] 이상 데이터 정리 (NULL, 중복 등)

### 환경 준비
- [ ] 개발 환경에서 전체 테스트 (필수!)
- [ ] 스테이징 환경 준비
- [ ] 프로덕션 환경 백업 계획

---

## 🔄 Day 1: 환경 준비

### 1.1 기존 상태 확인

```bash
# 프로젝트 루트에서 실행
pwd
# /Users/imdoyeong/Desktop/workSpace/quantiq

# 현재 파일 확인
ls -la
```

**확인 항목:**
- [ ] docker-compose.yml 존재
- [ ] quantiq-core/ 디렉토리 존재
- [ ] quantiq-data-engine/ 디렉토리 존재
- [ ] docs/migration/ 문서 완성

### 1.2 Docker Compose 업데이트

**파일:** `docker-compose.yml`

참고: `docs/migration/RDB_MIGRATION_PLAN.md` → "Day 1: PostgreSQL 설정" 섹션의 `1.4 docker-compose.yml 업데이트` 참조

**체크항목:**
```yaml
✓ postgresql 서비스 추가됨
✓ 환경변수 설정 (DB_PASSWORD 등)
✓ 볼륨 설정 (postgres_data)
✓ 헬스 체크 설정
✓ 네트워크 설정 (quantiq-network)
```

### 1.3 의존성 추가

**파일:** `quantiq-core/build.gradle.kts`

추가할 의존성:
```kotlin
// PostgreSQL
implementation("org.postgresql:postgresql:42.7.1")

// Flyway (마이그레이션 관리)
implementation("org.flywaydb:flyway-core:9.22.3")

// Spring Data JPA
implementation("org.springframework.boot:spring-boot-starter-data-jpa")

// Connection Pool
implementation("com.zaxxer:HikariCP:5.1.0")
```

**확인:**
```bash
./gradlew dependencies | grep postgresql
./gradlew dependencies | grep flyway
```

### 1.4 환경 설정

**파일:** `.env`

```env
# PostgreSQL 설정
DB_HOST=postgresql
DB_PORT=5432
DB_NAME=quantiq
DB_USER=quantiq_user
DB_PASSWORD=your_secure_password_here  # ⚠️ 변경 필수!

# 기존 설정 유지
MONGODB_URI=mongodb://mongodb:27017
MONGODB_DB_NAME=stock_trading
SPRING_DATA_MONGODB_URI=mongodb://mongodb:27017/stock_trading
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092

# 마이그레이션 제어
RUN_MIGRATION=false  # Day 3에서 true로 변경
DB_DUAL_WRITE=true   # Day 2에서 활성화
DB_READ_SOURCE=rdb   # Day 3에서 변경
```

**체크:**
```bash
cat .env | grep -E "DB_|POSTGRES"
```

### 1.5 설정 파일 작성

**파일:** `quantiq-core/src/main/resources/application.yml`

참고: `docs/migration/RDB_MIGRATION_PLAN.md` → "Day 1: PostgreSQL 설정" → "1.2 application.yml 설정" 섹션 복사

**주요 설정:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: validate  # ← 중요: 자동 생성 금지

  flyway:
    enabled: true
    locations: classpath:db/migration
```

**결과 확인:**
- [ ] application.yml 작성 완료
- [ ] PostgreSQL 연결 문자열 정확
- [ ] ddl-auto: validate 설정됨

### ✅ Day 1 완료 체크리스트

```
환경 준비 (Day 1)
├─ [ ] docker-compose.yml 업데이트 (postgresql 추가)
├─ [ ] build.gradle.kts 의존성 추가 (PostgreSQL, Flyway, JPA)
├─ [ ] .env 파일 작성 (DB 설정)
├─ [ ] application.yml 작성 (Spring 설정)
├─ [ ] PostgreSQL 로컬 테스트
│   └─ [ ] docker-compose up -d postgresql
│   └─ [ ] psql 연결 테스트
└─ [ ] 파일 구조 확인 완료

예상 소요시간: 2-3시간
```

---

## 🔧 Day 2: 스키마 생성 & 마이그레이션 스크립트

### 2.1 Flyway 마이그레이션 스크립트 작성

**디렉토리 생성:**
```bash
mkdir -p quantiq-core/src/main/resources/db/migration
```

**파일 1:** `V1__Initial_Schema.sql`

참고: `docs/migration/RDB_MIGRATION_PLAN.md` → "Day 2: Flyway 마이그레이션 스크립트 작성" → "2.1 초기 스키마 생성" 섹션 전체 복사

**생성할 테이블 6개:**
```sql
✓ users
✓ trading_configs
✓ account_balances
✓ stock_holdings
✓ trades
✓ trade_signals_executed
```

**파일 2:** `V2__Create_Indexes.sql`

참고: `docs/migration/RDB_MIGRATION_PLAN.md` → "Day 2: Flyway 마이그레이션 스크립트 작성" → "2.2 인덱스 생성" 섹션 전체 복사

**생성할 인덱스:**
```sql
✓ idx_users_user_id
✓ idx_trading_configs_enabled
✓ idx_stock_holdings_*
✓ idx_trades_* (3개)
✓ idx_trade_signals_*
```

### 2.2 Entity 클래스 작성

**디렉토리 구조:**
```
quantiq-core/src/main/kotlin/com/quantiq/core/domain/rdb/
├── User.kt
├── TradingConfig.kt
├── AccountBalance.kt
├── StockHolding.kt
├── Trade.kt
└── TradeSignalExecuted.kt
```

**예시 Entity:** `User.kt`

```kotlin
@Entity
@Table(name = "users", indexes = [
    Index(name = "idx_users_user_id", columnList = "user_id"),
    Index(name = "idx_users_status", columnList = "status")
])
@Data
@NoArgsConstructor
@AllArgsConstructor
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 50, unique = true)
    val userId: String = "",

    @Column(length = 100)
    val name: String? = null,

    @Column(length = 100, unique = true)
    val email: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: UserStatus = UserStatus.ACTIVE,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class UserStatus {
    ACTIVE, INACTIVE, SUSPENDED
}
```

**파일 참고:** `docs/migration/RDB_MIGRATION_PLAN.md` → "Day 3: Entity 클래스 작성"

### 2.3 Repository 인터페이스

**디렉토리:**
```
quantiq-core/src/main/kotlin/com/quantiq/core/repository/rdb/
├── UserRepository.kt
├── TradingConfigRepository.kt
├── AccountBalanceRepository.kt
├── StockHoldingRepository.kt
├── TradeRepository.kt
└── TradeSignalRepository.kt
```

**예시:**
```kotlin
@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUserId(userId: String): User?
    fun findByEmail(email: String): User?
}

@Repository
interface TradingConfigRepository : JpaRepository<TradingConfig, Long> {
    fun findByUserId(userId: Long): TradingConfig?
}
```

### 2.4 마이그레이션 서비스

**파일:** `RdbMigrationService.kt`

참고: `docs/migration/RDB_MIGRATION_PLAN.md` → "Day 4: 데이터 마이그레이션" → "4.1 MongoDB → RDB 마이그레이션 스크립트"

**주요 기능:**
```kotlin
@Service
class RdbMigrationService {

    fun migrateAllUsers()  // MongoDB users → RDB users

    fun migrateTradingConfigs()  // MongoDB → RDB

    fun migrateAccountBalances()  // 초기화

    fun validateMigration()  // 데이터 검증
}
```

### 2.5 Configuration 클래스

**파일:** `RdbConfig.kt`

```kotlin
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
            jdbcUrl = "jdbc:postgresql://${System.getenv("DB_HOST")}:${System.getenv("DB_PORT")}/${System.getenv("DB_NAME")}"
            username = System.getenv("DB_USER")
            password = System.getenv("DB_PASSWORD")
            maximumPoolSize = 20
            minimumIdle = 5
        }
    }

    // EntityManagerFactory, TransactionManager 설정
    // 참고: RDB_MIGRATION_PLAN.md → "Spring Boot JPA Configuration"
}
```

### ✅ Day 2 완료 체크리스트

```
스키마 & 마이그레이션 (Day 2)
├─ [ ] db/migration/ 디렉토리 생성
├─ [ ] V1__Initial_Schema.sql 작성 (6개 테이블)
├─ [ ] V2__Create_Indexes.sql 작성 (모든 인덱스)
├─ [ ] Entity 클래스 작성 (6개)
├─ [ ] Repository 인터페이스 작성 (6개)
├─ [ ] RdbMigrationService 구현
├─ [ ] RdbConfig 클래스 작성
├─ [ ] application.yml 최종 검토
├─ [ ] 코드 컴파일 확인
│   └─ [ ] ./gradlew clean build
└─ [ ] IDE에서 에러 확인 완료

예상 소요시간: 4-5시간
```

---

## 🔄 Day 3: 데이터 마이그레이션 & 검증

### 3.1 PostgreSQL 시작 및 스키마 생성

```bash
# 1. PostgreSQL 컨테이너 시작
docker-compose up -d postgresql

# 2. 대기 (헬스 체크 통과 대기)
sleep 10

# 3. 연결 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq -c "SELECT version();"

# 예상 출력:
# PostgreSQL 15.x on ...
```

**문제 해결:**
```bash
# 이미 5432 포트가 사용 중인 경우
lsof -i :5432
kill -9 <PID>

# 또는 docker-compose.yml에서 포트 변경
# "5432:5432" → "5433:5432"
```

### 3.2 Flyway 마이그레이션 실행

```bash
# 1. Spring Boot 애플리케이션 시작
cd quantiq-core
docker build -t quantiq-core:migration .

# 2. 컨테이너 시작 (마이그레이션 비활성화)
export RUN_MIGRATION=false
docker-compose up -d quantiq-core

# 3. 로그 확인 (Flyway 실행)
docker-compose logs -f quantiq-core | grep -i flyway

# 예상 출력:
# ... Flyway validation: using schema "public" ...
# ... Successfully validated 2 migrations ...
# ... Successfully applied 2 migrations ...
```

**문제 확인:**
```bash
# PostgreSQL에 접속해서 테이블 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq -c "\dt"

# 예상 출력:
#              List of relations
# Schema |      Name      | Type  |  Owner
#--------+----------------+-------+----------
# public | users          | table | quantiq_user
# public | trading_configs | table | quantiq_user
# ... (모두 6개)
```

### 3.3 데이터 마이그레이션 실행

```bash
# 1. 마이그레이션 활성화
export RUN_MIGRATION=true

# 2. 애플리케이션 재시작
docker-compose restart quantiq-core

# 3. 마이그레이션 로그 확인
docker-compose logs -f quantiq-core | grep -i -E "migration|migrate"

# 예상 출력:
# ... Starting RDB migration...
# ... Migrated user: user1 ✅
# ... Migrated user: user2 ✅
# ... Migration completed: 42/42 users ✅
```

### 3.4 데이터 검증

**쿼리 1: 테이블별 데이터 수**
```bash
docker-compose exec postgresql psql -U quantiq_user -d quantiq << EOF
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
EOF
```

**쿼리 2: MongoDB vs RDB 비교**
```bash
# MongoDB 데이터 수
docker-compose exec mongodb mongosh << EOF
use stock_trading
db.users.countDocuments()
db.stock_recommendations.countDocuments()
EOF

# RDB 데이터 수와 비교
# users: MongoDB users ≈ PostgreSQL users
# trades: 새로 생성됨 (initial = 0)
```

**쿼리 3: 특정 사용자 상세 검증**
```bash
docker-compose exec postgresql psql -U quantiq_user -d quantiq << EOF
SELECT u.id, u.user_id, u.name,
       tc.enabled, tc.auto_trading_enabled,
       ab.cash, ab.total_value
FROM users u
LEFT JOIN trading_configs tc ON u.id = tc.user_id
LEFT JOIN account_balances ab ON u.id = ab.user_id
WHERE u.user_id = 'user1';
EOF
```

### 3.5 성능 테스트

```bash
# 1. 간단한 쿼리 성능 (이전/이후 비교)

# RDB 성능 테스트
time docker-compose exec postgresql psql -U quantiq_user -d quantiq << EOF
SELECT u.*, tc.*, ab.* FROM users u
LEFT JOIN trading_configs tc ON u.id = tc.user_id
LEFT JOIN account_balances ab ON u.id = ab.user_id
LIMIT 100;
EOF

# 예상: 5-20ms

# 2. 인덱스 효율성 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq << EOF
EXPLAIN ANALYZE
SELECT * FROM trades
WHERE user_id = 1 AND executed_at > NOW() - INTERVAL '7 days'
ORDER BY executed_at DESC;
EOF
```

### ✅ Day 3 완료 체크리스트

```
데이터 마이그레이션 (Day 3)
├─ [ ] PostgreSQL 시작 & 헬스 체크 통과
├─ [ ] Flyway 마이그레이션 성공 (V1, V2)
├─ [ ] 6개 테이블 생성 확인
├─ [ ] 모든 인덱스 생성 확인
├─ [ ] RDB 마이그레이션 서비스 실행
├─ [ ] 데이터 이관 성공 (n개 사용자)
├─ [ ] MongoDB vs RDB 데이터 검증
│   ├─ [ ] users 일치
│   ├─ [ ] trading_configs 일치
│   └─ [ ] 기타 설정 일치
├─ [ ] 성능 테스트 완료
│   ├─ [ ] 조인 쿼리 < 20ms
│   ├─ [ ] 인덱스 활용 확인 (EXPLAIN ANALYZE)
│   └─ [ ] 기본 CRUD < 10ms
└─ [ ] 에러 로그 확인 (없음)

예상 소요시간: 3-4시간
```

---

## 🔀 Day 4: 이중 쓰기 & 점진적 전환

### 4.1 이중 쓰기 모드 활성화

**설정 변경:**
```yaml
# application.yml
db:
  rdb:
    enabled: true
  dual-write: true      # ← 활성화
  read-source: rdb      # 읽기는 RDB에서
```

**동작:**
- 모든 쓰기는 RDB + MongoDB에 동시 실행
- 읽기는 RDB에서만
- MongoDB 실패해도 무시 (RDB가 primary)

```bash
# 1. 설정 변경 후 빌드
cd quantiq-core
./gradlew clean build

# 2. 컨테이너 재시작
docker build -t quantiq-core:dual-write .
docker-compose up -d quantiq-core

# 3. 로그 확인
docker-compose logs -f quantiq-core | grep -i "dual-write"
```

### 4.2 읽기 소스 전환 검증

**테스트:**
```bash
# 1. 새로운 데이터 생성 (API 호출)
curl -X POST http://localhost:10010/api/users \
  -H "Content-Type: application/json" \
  -d '{"userId":"test123", "name":"Test User"}'

# 2. RDB에서 조회
docker-compose exec postgresql psql -U quantiq_user -d quantiq \
  -c "SELECT * FROM users WHERE user_id = 'test123';"

# 3. MongoDB에도 있는지 확인
docker-compose exec mongodb mongosh \
  -c "use stock_trading; db.users.findOne({user_id: 'test123'})"

# 예상: 둘 다 존재
```

### 4.3 데이터 일관성 검증

```bash
# 1. MongoDB에만 있는 데이터 확인
docker-compose exec mongodb mongosh << EOF
use stock_trading
db.users.count() - db.users.find().count()  // 차이 확인
EOF

# 2. RDB와 데이터 비교
docker-compose exec postgresql psql -U quantiq_user -d quantiq << EOF
-- RDB에 있지만 MongoDB에 없는 사용자 찾기
SELECT u.user_id FROM users u
WHERE NOT EXISTS (
  SELECT 1 FROM ... -- MongoDB lookup
);
EOF
```

### 4.4 MongoDB 제거 준비 (향후)

**다음 단계:**
1. 이중 쓰기 모드에서 7일 이상 운영
2. 모든 데이터 일치 확인
3. MongoDB 컬렉션별로 제거
4. 최종 삭제

```yaml
# 제거 예정 MongoDB 컬렉션:
- users (→ PostgreSQL users로 완전 이관)
- trading_configs (→ PostgreSQL trading_configs로 완전 이관)
- account_balances (→ PostgreSQL account_balances로 완전 이관)
- stock_holdings (→ PostgreSQL stock_holdings로 완전 이관)
- trades (→ PostgreSQL trades로 완전 이관)

# 유지 MongoDB 컬렉션:
✓ stock_recommendations (분석 데이터)
✓ daily_analysis_results (기술적 분석)
✓ portfolio_snapshots (포트폴리오 스냅샷)
✓ market_data_archive (시장 데이터)
```

### 4.5 모니터링 설정

```bash
# 1. 로그 모니터링 (실시간)
docker-compose logs -f quantiq-core | grep -i -E "error|exception|warn"

# 2. 데이터 일관성 모니터링
# (매일 실행)
docker-compose exec postgresql psql -U quantiq_user -d quantiq << EOF
SELECT
  (SELECT COUNT(*) FROM users) as rdb_users,
  NOW() as check_time;
EOF

# 3. 성능 메트릭
# API 응답 시간, DB 쿼리 시간 모니터링
```

### ✅ Day 4 완료 체크리스트

```
점진적 전환 (Day 4)
├─ [ ] 이중 쓰기 모드 활성화
├─ [ ] 읽기 소스 = RDB 설정
├─ [ ] 새 데이터 쓰기 테스트
│   ├─ [ ] RDB에 저장됨
│   └─ [ ] MongoDB에도 저장됨
├─ [ ] 읽기 테스트
│   ├─ [ ] RDB에서만 읽기
│   └─ [ ] 결과 정상
├─ [ ] 데이터 일관성 검증
│   ├─ [ ] 사용자 수 일치
│   ├─ [ ] 설정값 일치
│   └─ [ ] 거래 기록 일치
├─ [ ] 예상 쿼리 성능 달성
├─ [ ] 모니터링 설정 완료
├─ [ ] 에러/경고 로그 0개
└─ [ ] 최종 검증 통과

예상 소요시간: 2-3시간
```

---

## 🎯 최종 검증

### 완성 기준

마이그레이션이 성공한 것으로 판단할 기준:

```
✅ 기술 기준
├─ PostgreSQL 모든 테이블 생성됨
├─ 모든 인덱스 활성화됨
├─ Flyway 마이그레이션 히스토리 기록됨
├─ 0개의 스키마 에러
└─ 0개의 마이그레이션 에러

✅ 데이터 기준
├─ MongoDB 데이터 100% 이관됨
├─ RDB 데이터 검증 통과
├─ 데이터 일관성 100%
└─ 중복/손상 데이터 0개

✅ 성능 기준
├─ 쿼리 응답시간 < 50ms
├─ 조인 쿼리 < 20ms
├─ 이중 쓰기 오버헤드 < 100ms
└─ 인덱스 효율성 > 90%

✅ 운영 기준
├─ 이중 쓰기 모드 안정적 (24시간)
├─ 에러/경고 로그 < 1개/시간
├─ 데이터 동기화 성공률 99.9%
└─ 자동 롤백 계획 수립됨
```

### 최종 서명

마이그레이션 완료 후:

```
검증자: ________________  날짜: _________
개발자: ________________  날짜: _________
PM:     ________________  날짜: _________
```

---

## 🚨 긴급 롤백 계획

**상황: 심각한 데이터 손상 또는 성능 저하**

### 1단계: 즉시 조치 (1분 이내)
```bash
# 읽기 소스를 MongoDB로 전환
export DB_READ_SOURCE=mongo
docker-compose restart quantiq-core

# 또는 이중 쓰기 비활성화
export DB_DUAL_WRITE=false
docker-compose restart quantiq-core
```

### 2단계: 데이터 복구 (5분 이내)
```bash
# MongoDB 백업에서 복구
docker-compose exec mongodb mongosh << EOF
use stock_trading
// 백업에서 복구
EOF
```

### 3단계: 상태 복구 (30분 이내)
```bash
# 모든 설정을 기존 상태로 되돌림
export DB_DUAL_WRITE=false
export DB_READ_SOURCE=mongo
export DB_RDB_ENABLED=false
docker-compose restart
```

---

## 📞 문의 & 트러블슈팅

### 자주 하는 질문

**Q1: 마이그레이션 중 서비스 다운타임이 있나요?**
```
A: 아니요. 이중 쓰기 모드로 운영하므로 무중단 마이그레이션입니다.
  - Day 1-2: 개발 환경에서만 테스트
  - Day 3: 스테이징에서 검증
  - Day 4: 프로덕션 배포 (이중 쓰기 모드)
```

**Q2: 롤백이 가능한가요?**
```
A: 네. 설정 변경만으로 즉시 롤백 가능합니다.
  - 읽기 소스를 MongoDB로 변경 (1분)
  - 이중 쓰기 비활성화 (1분)
  - PostgreSQL 데이터는 유지되므로 나중에 다시 마이그레이션 가능
```

**Q3: 데이터 손실 가능성은?**
```
A: 거의 없습니다. (< 0.01%)
  - MongoDB 완전 백업 필수
  - RDB 마이그레이션 전 검증
  - 이중 쓰기 모드에서 일관성 검증
  - PostgreSQL 완전 백업 유지
```

### 트러블슈팅 테이블

| 증상 | 원인 | 해결책 |
|------|------|--------|
| PostgreSQL 연결 실패 | 포트 충돌 | `lsof -i :5432` 후 종료 또는 포트 변경 |
| Flyway 마이그레이션 실패 | SQL 문법 오류 | `docs/sql/` 파일 재검토 |
| 데이터 일치하지 않음 | 마이그레이션 불완전 | RdbMigrationService 재실행 |
| 이중 쓰기 오버헤드 높음 | MongoDB 느림 | 마이그레이션 완료 후 MongoDB 제거 |
| 롤백 필요 | 성능/데이터 문제 | DB_READ_SOURCE=mongo로 전환 |

---

## 📚 추가 참고 자료

- **DATABASE_STRATEGY.md** - 전략 및 스키마 설계
- **RDB_MIGRATION_PLAN.md** - 기술 구현 계획
- **DATABASE_IMPLEMENTATION.md** - 구현 상세 가이드
- **Spring Data JPA 공식 문서**
- **PostgreSQL 15 공식 문서**
- **Flyway 공식 문서**

---

## ✨ 주요 성과

마이그레이션 완료 후 기대되는 개선사항:

| 항목 | 기존 (MongoDB) | 변경 후 (Hybrid) | 개선율 |
|------|---|---|---|
| 쿼리 응답시간 | 100-500ms | 5-50ms | **90% 단축** |
| 조인 복잡도 | 높음 (lookup) | 자연 | **단순화** |
| 트랜잭션 보장 | 부분 | 완벽 (ACID) | **안정성 ↑** |
| 확장성 | 수평 확장 | 수직 확장 | **비용 최적화** |
| 감사/정산 | 어려움 | 완벽한 기록 | **규정 준수** |

---

**마이그레이션 시작 날짜:** ________________

**예상 완료 날짜:** ________________

**담당자:** ________________
