# Stock 데이터 마이그레이션: MongoDB → PostgreSQL

**작성일**: 2026-02-01
**상태**: ✅ 완료

## 📋 목차

1. [배경 및 목적](#배경-및-목적)
2. [마이그레이션 개요](#마이그레이션-개요)
3. [수행 내역](#수행-내역)
4. [생성된 파일](#생성된-파일)
5. [데이터 검증](#데이터-검증)
6. [다음 단계](#다음-단계)
7. [문제 해결](#문제-해결)

---

## 배경 및 목적

### 문제점
- `stocks` 컬렉션이 MongoDB에 저장되어 있었음
- 주식 메타데이터는 **정적 참조 데이터**로, 거의 변경되지 않음
- MongoDB의 유연한 스키마 장점을 활용할 필요가 없음

### 해결 방안
- `stocks`를 PostgreSQL로 이동하여 **RDB의 장점** 활용
  - 인덱스 기반 빠른 조회
  - FK 제약조건으로 참조 무결성 보장
  - 복잡한 JOIN 쿼리 지원

### 데이터 특성 분석

| 컬렉션/테이블 | 데이터 특성 | DB | 이유 |
|--------------|-----------|-----|------|
| `stocks` | 메타데이터 (정적) | **PostgreSQL** | 거의 변경 없음, FK 관계, 구조화된 쿼리 |
| `daily_stock_data` | 시계열 (비정형) | **MongoDB** | 높은 쓰기 처리량, 유연한 스키마 |
| `stock_recommendations` | 분석 결과 | **MongoDB** | 중첩 구조, 빈번한 변경 |
| `stock_analysis_results` | 분석 결과 | **MongoDB** | 중첩 구조, ML 결과 |

---

## 마이그레이션 개요

### 마이그레이션 흐름
```
MongoDB (stocks collection)
    ↓
[migrate_stocks.py]
    ↓
PostgreSQL (stocks table)
```

### 수행 단계
1. ✅ PostgreSQL 테이블 생성 (Flyway 마이그레이션)
2. ✅ JPA Entity 및 Repository 생성
3. ✅ 데이터 마이그레이션 (MongoDB → PostgreSQL)
4. ⏸️ 비즈니스 로직 적용 (향후 진행)

---

## 수행 내역

### 1. PostgreSQL 테이블 생성

**파일**: `V6__Create_Stocks_Table.sql`

```sql
CREATE TABLE stocks (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL,
    stock_name VARCHAR(200) NOT NULL,
    stock_name_en VARCHAR(200),
    is_etf BOOLEAN NOT NULL DEFAULT FALSE,
    leverage_ticker VARCHAR(20),
    exchange VARCHAR(50),
    sector VARCHAR(100),
    industry VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_stocks_ticker UNIQUE(ticker)
);
```

**인덱스**:
- `idx_stocks_ticker` (ticker)
- `idx_stocks_is_active` (is_active) - WHERE is_active = TRUE
- `idx_stocks_sector` (sector) - WHERE sector IS NOT NULL
- `idx_stocks_industry` (industry) - WHERE industry IS NOT NULL
- `idx_stocks_is_etf` (is_etf) - WHERE is_etf = TRUE

**실행**:
```bash
./gradlew bootRun  # Flyway 자동 실행
```

### 2. JPA Entity 생성

**파일**: `StockEntity.kt`

```kotlin
@Entity
@Table(name = "stocks")
data class StockEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(unique = true, nullable = false, length = 20)
    val ticker: String,

    @Column(name = "stock_name", nullable = false, length = 200)
    val stockName: String,

    // ... 기타 필드

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
```

### 3. JPA Repository 생성

**파일**: `StockJpaRepository.kt`

```kotlin
@Repository
interface StockJpaRepository : JpaRepository<StockEntity, Long> {
    fun findByTicker(ticker: String): StockEntity?
    fun findByIsActive(isActive: Boolean): List<StockEntity>
    fun findByIsEtf(isEtf: Boolean): List<StockEntity>
    fun findBySector(sector: String): List<StockEntity>
    fun findByIndustry(industry: String): List<StockEntity>

    @Query("SELECT s FROM StockEntity s WHERE s.isActive = true ORDER BY s.ticker")
    fun findAllActiveStocks(): List<StockEntity>
}
```

### 4. 데이터 마이그레이션

**파일**: `migrate_stocks.py`

**실행 방법**:
```bash
# MongoDB URI 설정 필요
MONGODB_URI="mongodb://quantiq_user:quantiq_password@localhost:27017/stock_trading?authSource=admin" \
python3 migrate_stocks.py
```

**마이그레이션 결과**:
```
✅ MongoDB에서 35개 stocks 조회 완료
✅ PostgreSQL에 35개 삽입 성공
❌ 실패: 0개
```

---

## 생성된 파일

### 1. Database Migration
- `src/main/resources/db/migration/V6__Create_Stocks_Table.sql`

### 2. JPA Layer
- `src/main/kotlin/com/quantiq/core/adapter/output/persistence/jpa/StockEntity.kt`
- `src/main/kotlin/com/quantiq/core/adapter/output/persistence/jpa/StockJpaRepository.kt`

### 3. 마이그레이션 스크립트
- `migrate_stocks.py` ✅ **사용 권장**
- `migrate_stocks.sh` (mongosh 필요)

### 4. Spring Boot 통합 (사용하지 않음)
- `src/main/kotlin/com/quantiq/core/infrastructure/migration/StockDataMigration.kt`
  - GCP 의존성 문제로 standalone 스크립트 사용

---

## 데이터 검증

### PostgreSQL 데이터 확인

```sql
SELECT COUNT(*) FROM stocks;
-- 결과: 35

SELECT ticker, stock_name, is_etf, is_active
FROM stocks
ORDER BY ticker
LIMIT 10;
```

**샘플 데이터**:
```
Ticker | Stock Name            | ETF   | Active
--------------------------------------------------
AAPL   | 애플                  | False | True
AMAT   | 어플라이드 머티리얼즈  | False | True
AMD    | AMD                   | False | True
AMZN   | 아마존                | False | True
APP    | 앱플로빈              | False | True
AVGO   | 브로드컴              | False | True
BE     | 블룸에너지            | False | True
CLS    | 셀레스티카            | False | True
CRDO   | 크리도 테크놀로지 그룹 | False | True
CRM    | 세일즈포스            | False | True
```

### MongoDB 원본 데이터
- 컬렉션: `stocks`
- 문서 수: 35개
- 상태: 유지 (dual-write 지원 예정)

---

## 다음 단계

### 1. 비즈니스 로직 적용 (TODO)

현재 `StockJpaRepository`를 사용하는 서비스가 없음. 향후 구현 필요:

#### Option A: Adapter 패턴 (권장)
```kotlin
@Component
class StockPersistenceAdapter(
    private val stockJpaRepository: StockJpaRepository,
    private val stockMongoRepository: StockRepository,
    @Value("\${db.read-source:rdb}") private val readSource: String,
    @Value("\${db.dual-write:true}") private val dualWrite: Boolean
) {
    fun findByTicker(ticker: String): Stock? {
        return when (readSource) {
            "rdb" -> stockJpaRepository.findByTicker(ticker)?.toDomain()
            "mongo" -> stockMongoRepository.findByTicker(ticker)
            else -> stockJpaRepository.findByTicker(ticker)?.toDomain()
        }
    }

    @Transactional
    fun save(stock: Stock): Stock {
        // 1. RDB에 저장 (Primary)
        val savedEntity = stockJpaRepository.save(stock.toEntity())
        val savedStock = savedEntity.toDomain()

        // 2. MongoDB에 dual-write (Secondary)
        if (dualWrite) {
            try {
                stockMongoRepository.save(savedStock)
            } catch (e: Exception) {
                logger.error("MongoDB dual-write 실패", e)
            }
        }

        return savedStock
    }
}
```

#### Option B: Service Layer 직접 사용
```kotlin
@Service
class StockService(
    private val stockJpaRepository: StockJpaRepository
) {
    fun findByTicker(ticker: String): Stock? {
        return stockJpaRepository.findByTicker(ticker)?.toDomain()
    }

    fun findActiveStocks(): List<Stock> {
        return stockJpaRepository.findAllActiveStocks().map { it.toDomain() }
    }
}
```

### 2. MongoDB 단계적 제거 계획

1. **Phase 1**: Dual-write 모드 (현재)
   - RDB Primary, MongoDB Secondary
   - `db.dual-write: true`

2. **Phase 2**: RDB Only 모드
   - MongoDB 쓰기 중단
   - `db.dual-write: false`

3. **Phase 3**: MongoDB 데이터 삭제
   - 충분한 검증 후 MongoDB stocks 컬렉션 삭제

### 3. 참조 관계 추가 (선택)

향후 필요 시 FK 제약조건 추가:

```sql
-- 예: trades 테이블에서 stocks 참조
ALTER TABLE trades
ADD CONSTRAINT fk_trades_stock
    FOREIGN KEY (ticker)
    REFERENCES stocks(ticker)
    ON DELETE RESTRICT;
```

---

## 문제 해결

### 1. GCP 의존성 오류

**문제**:
```
Error creating bean 'vertexAIService': Unsatisfied dependency
expressed through constructor parameter 0
```

**원인**: GcpConfig가 조건부 로딩되지 않아 JobServiceClient Bean을 찾을 수 없음

**해결**: Standalone Python 스크립트 사용
- `migrate_stocks.py`로 Spring Boot 없이 직접 마이그레이션

**향후 개선**:
```kotlin
// VertexAIService.kt, VertexAIController.kt에 추가
@ConditionalOnProperty(
    prefix = "gcp",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
```

### 2. MongoDB 인증 오류

**문제**:
```
Authentication failed.
```

**원인**: `authSource=admin` 누락

**해결**:
```bash
# 올바른 URI
MONGODB_URI="mongodb://quantiq_user:quantiq_password@localhost:27017/stock_trading?authSource=admin"
```

### 3. mongosh 미설치

**문제**: `migrate_stocks.sh` 실행 시 `mongosh: command not found`

**해결**: Python 스크립트 사용
```bash
python3 migrate_stocks.py
```

---

## 부록

### application.yml 설정

```yaml
db:
  rdb:
    enabled: true
  dual-write: ${DB_DUAL_WRITE:true}       # MongoDB도 업데이트
  read-source: ${DB_READ_SOURCE:rdb}      # rdb, mongo, both
  migration:
    enabled: ${RUN_MIGRATION:false}
```

### 환경 변수

```bash
# PostgreSQL
DB_HOST=localhost
DB_PORT=5433
DB_NAME=quantiq
DB_USER=quantiq_user
DB_PASSWORD=quantiq_password

# MongoDB
MONGODB_URI=mongodb://quantiq_user:quantiq_password@localhost:27017/stock_trading?authSource=admin

# Dual-write 설정
DB_DUAL_WRITE=true
DB_READ_SOURCE=rdb
```

---

## 참고 자료

- Flyway Documentation: https://flywaydb.org/
- Spring Data JPA: https://spring.io/projects/spring-data-jpa
- PostgreSQL Indexes: https://www.postgresql.org/docs/current/indexes.html
