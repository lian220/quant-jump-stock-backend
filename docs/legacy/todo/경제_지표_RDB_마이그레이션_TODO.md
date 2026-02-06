# 경제 지표 메타데이터 RDB 마이그레이션 TODO

**작성일**: 2026-02-01
**우선순위**: Medium
**예상 작업 시간**: 2-3시간
**상태**: 준비 완료 (MongoDB 사용 중)

---

## 📋 목적

현재 `fred_indicators`와 `yfinance_indicators` 컬렉션이 MongoDB에 저장되어 있으나, 이들은 정적 메타데이터/설정 데이터로 **PostgreSQL(RDB)이 더 적합**함.

## 🎯 배경 및 문제점

### 현재 상태
- **MongoDB**: `fred_indicators`, `yfinance_indicators` 컬렉션에 경제 지표 메타데이터 저장
- **Data Engine**: MongoDB에서 지표 목록을 읽어 FRED/Yahoo Finance API 호출
- **문제점**:
  - 정적 설정 데이터를 문서 DB에 저장
  - 스키마 검증 부족
  - 마이그레이션 관리 어려움
  - 참조 무결성 보장 불가

### RDB가 더 나은 이유

| 항목 | PostgreSQL | MongoDB |
|------|-----------|---------|
| **데이터 특성** | ✅ 정적 참조 데이터에 최적 | ❌ 동적 시계열 데이터에 적합 |
| **스키마 관리** | ✅ 엄격한 스키마 + Flyway 마이그레이션 | ❌ 스키마리스 (검증 부족) |
| **데이터 무결성** | ✅ NOT NULL, UNIQUE, FK 제약조건 | ❌ 애플리케이션 레벨 검증 필요 |
| **조인 성능** | ✅ 효율적 (설정 테이블과 조인) | ❌ lookup 또는 애플리케이션 조인 |
| **변경 이력** | ✅ Flyway로 추적 | ❌ 별도 추적 필요 |

---

## ✅ 완료된 작업

### Phase 1: 준비 (2026-02-01 완료)

- [x] **PostgreSQL 테이블 생성**
  - 파일: `V6__Create_Economic_Indicators_Tables.sql`
  - 테이블: `fred_indicators`, `yfinance_indicators`
  - 제약조건: UNIQUE, NOT NULL, CHECK
  - 인덱스: `is_active`, `category`, `indicator_type`

- [x] **MongoDB 마이그레이션 스크립트 작성**
  - 파일: `scripts/migrations/migrate_economic_indicators_mongodb.js`
  - 기능: upsert로 중복 방지, 인덱스 생성

- [x] **프로덕션 데이터 추출 스크립트**
  - 파일: `scripts/migrations/export_prod_indicators.sh`
  - 기능: 프로덕션 MongoDB → JSON 파일 추출

- [x] **초기 데이터 삽입**
  - PostgreSQL: V6 migration에 포함
  - MongoDB: 직접 삽입 완료
  - FRED: 8개 지표 (GDP, UNRATE, CPI, FEDFUNDS, DGS10, DGS2, T10Y2Y, DEXUSEU)
  - Yahoo Finance: 8개 지표 (SPY, QQQ, SOXX, DIA, IWM, VIX, GLD, USO)

---

## 🔄 다음 단계 (TODO)

### Phase 2: Data Engine 수정

#### 1. Repository 레이어 수정
**파일**: `quantiq-data-engine/src/features/economic_data/repository.py`

**현재 코드**:
```python
def find_active_indicators(self, collection_name: str) -> List[Dict]:
    """MongoDB에서 활성화된 지표 조회"""
    collection = self.db[collection_name]
    return list(collection.find({"is_active": True}))
```

**수정 후**:
```python
import psycopg2
from psycopg2.extras import RealDictCursor

def find_active_fred_indicators(self) -> List[Dict]:
    """PostgreSQL에서 활성화된 FRED 지표 조회"""
    conn = self._get_postgres_connection()
    cursor = conn.cursor(cursor_factory=RealDictCursor)

    cursor.execute("""
        SELECT code, name, description, category, unit, frequency
        FROM fred_indicators
        WHERE is_active = true
        ORDER BY code
    """)

    results = cursor.fetchall()
    cursor.close()
    conn.close()

    return [dict(row) for row in results]

def find_active_yfinance_indicators(self) -> List[Dict]:
    """PostgreSQL에서 활성화된 Yahoo Finance 지표 조회"""
    conn = self._get_postgres_connection()
    cursor = conn.cursor(cursor_factory=RealDictCursor)

    cursor.execute("""
        SELECT ticker, name, description, indicator_type
        FROM yfinance_indicators
        WHERE is_active = true
        ORDER BY ticker
    """)

    results = cursor.fetchall()
    cursor.close()
    conn.close()

    return [dict(row) for row in results]

def _get_postgres_connection(self):
    """PostgreSQL 연결 생성"""
    return psycopg2.connect(
        host=settings.POSTGRES_HOST,
        port=settings.POSTGRES_PORT,
        database=settings.POSTGRES_DB,
        user=settings.POSTGRES_USER,
        password=settings.POSTGRES_PASSWORD
    )
```

**체크리스트**:
- [ ] `psycopg2` 의존성 추가 (`requirements.txt`)
- [ ] PostgreSQL 연결 헬퍼 메서드 추가
- [ ] `find_active_fred_indicators()` 구현
- [ ] `find_active_yfinance_indicators()` 구현
- [ ] 기존 `find_active_indicators()` 제거

#### 2. Service 레이어 수정
**파일**: `quantiq-data-engine/src/features/economic_data/service.py`

**현재 코드**:
```python
def _load_fred_indicators(self) -> Dict[str, str]:
    indicators = {}
    docs = self.repository.find_active_indicators("fred_indicators")
    for doc in docs:
        if "code" in doc and "name" in doc:
            indicators[doc["code"]] = doc["name"]
    return indicators
```

**수정 후**:
```python
def _load_fred_indicators(self) -> Dict[str, str]:
    """PostgreSQL에서 FRED 지표 로드"""
    indicators = {}
    docs = self.repository.find_active_fred_indicators()
    for doc in docs:
        indicators[doc["code"]] = doc["name"]
    return indicators

def _load_yfinance_indicators(self) -> Dict[str, str]:
    """PostgreSQL에서 Yahoo Finance 지표 로드"""
    indicators = {}
    docs = self.repository.find_active_yfinance_indicators()
    for doc in docs:
        indicators[doc["name"]] = doc["ticker"]
    return indicators
```

**체크리스트**:
- [ ] `_load_fred_indicators()` 수정
- [ ] `_load_yfinance_indicators()` 수정

#### 3. 환경변수 추가

**파일**: `quantiq-data-engine/src/core/config.py`

```python
class Settings(BaseSettings):
    # 기존 MongoDB 설정
    MONGO_URL: str
    MONGO_USER: str
    MONGO_PASSWORD: str
    MONGODB_DATABASE: str = "stock_trading"

    # PostgreSQL 설정 추가
    POSTGRES_HOST: str = "quantiq-postgres"  # Docker 컨테이너 이름
    POSTGRES_PORT: int = 5432
    POSTGRES_DB: str = "quantiq_db"
    POSTGRES_USER: str
    POSTGRES_PASSWORD: str
```

**Docker Compose 수정** (필요 시):
```yaml
quantiq-data-engine:
  environment:
    - POSTGRES_HOST=quantiq-postgres
    - POSTGRES_PORT=5432
    - POSTGRES_DB=quantiq_db
    - POSTGRES_USER=${POSTGRES_USER}
    - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
  depends_on:
    - quantiq-postgres  # 의존성 추가
```

**체크리스트**:
- [ ] `Settings` 클래스에 PostgreSQL 설정 추가
- [ ] `.env` 파일에 PostgreSQL 환경변수 추가
- [ ] Docker Compose 네트워크 설정 확인
- [ ] `depends_on` 설정으로 PostgreSQL 시작 순서 보장

---

### Phase 3: 테스트 및 검증

#### 1. 로컬 테스트
**체크리스트**:
- [ ] PostgreSQL 연결 테스트
  ```python
  # 테스트 스크립트 작성
  from src.features.economic_data.repository import EconomicDataRepository

  repo = EconomicDataRepository()
  fred = repo.find_active_fred_indicators()
  yahoo = repo.find_active_yfinance_indicators()

  print(f"FRED: {len(fred)}개")
  print(f"Yahoo: {len(yahoo)}개")
  ```

- [ ] 경제 데이터 수집 E2E 테스트
  ```bash
  # API 호출
  curl -X POST "http://localhost:10010/api/v1/economic-data/collections?startDate=2026-02-01"

  # 로그 확인
  docker logs quantiq-data-engine --tail 50 | grep "FRED\|Yahoo"

  # MongoDB 데이터 확인
  mongosh --eval "db.daily_stock_data.findOne({date: '2026-02-01'})"
  ```

- [ ] 성능 비교
  - MongoDB 쿼리 시간 측정
  - PostgreSQL 쿼리 시간 측정
  - 차이 로깅

#### 2. 롤백 계획
**문제 발생 시 즉시 롤백**:
```python
# repository.py에 fallback 로직 추가
def find_active_fred_indicators(self) -> List[Dict]:
    try:
        # PostgreSQL 시도
        return self._find_from_postgres("fred_indicators")
    except Exception as e:
        logger.error(f"PostgreSQL 조회 실패, MongoDB로 fallback: {e}")
        # MongoDB fallback
        return list(self.db.fred_indicators.find({"is_active": True}))
```

**체크리스트**:
- [ ] Fallback 로직 구현
- [ ] 롤백 테스트
- [ ] 모니터링 알람 설정

---

### Phase 4: 배포

#### 1. 스테이징 배포
**체크리스트**:
- [ ] 스테이징 환경에 PostgreSQL 환경변수 설정
- [ ] Data Engine 재배포
- [ ] 경제 데이터 수집 테스트 (1주일치)
- [ ] 로그 모니터링 (오류 없는지)
- [ ] MongoDB 데이터와 비교 검증

#### 2. 프로덕션 배포
**체크리스트**:
- [ ] 프로덕션 PostgreSQL 데이터 확인
  ```sql
  SELECT COUNT(*) FROM fred_indicators WHERE is_active = true;
  SELECT COUNT(*) FROM yfinance_indicators WHERE is_active = true;
  ```
- [ ] 배포 시간 결정 (트래픽 낮은 시간)
- [ ] Data Engine 배포
- [ ] 실시간 모니터링
- [ ] 데이터 수집 정상 동작 확인
- [ ] 24시간 안정성 모니터링

#### 3. MongoDB 컬렉션 제거 (최종 단계)
**검증 후 진행**:
```javascript
// 1주일 이상 안정적으로 운영 후
use stock_trading;

// 백업 (선택사항)
db.fred_indicators.find().forEach(printjson) > fred_backup.json
db.yfinance_indicators.find().forEach(printjson) > yfinance_backup.json

// 삭제
db.fred_indicators.drop();
db.yfinance_indicators.drop();

print("✅ MongoDB 경제 지표 컬렉션 삭제 완료");
```

**체크리스트**:
- [ ] 1주일 이상 안정적 운영 확인
- [ ] MongoDB 백업 (선택)
- [ ] `fred_indicators` 컬렉션 삭제
- [ ] `yfinance_indicators` 컬렉션 삭제
- [ ] Fallback 로직 제거 (선택)

---

## 📚 관련 파일

```
quantiq-core/
└── src/main/resources/db/migration/
    └── V6__Create_Economic_Indicators_Tables.sql

quantiq-data-engine/
├── requirements.txt (psycopg2 추가 필요)
└── src/
    ├── core/
    │   └── config.py (PostgreSQL 설정 추가 필요)
    └── features/economic_data/
        ├── repository.py (수정 필요)
        └── service.py (수정 필요)

scripts/migrations/
├── migrate_economic_indicators_mongodb.js
└── export_prod_indicators.sh

docs/todo/
└── 경제_지표_RDB_마이그레이션_TODO.md (이 파일)
```

---

## ⚠️ 주의사항

1. **네트워크 설정**
   - `quantiq-data-engine` 컨테이너가 `quantiq-postgres`에 접근 가능한지 확인
   - Docker Compose 네트워크 설정 필요

2. **Connection Pool**
   - 프로덕션 환경에서는 `psycopg2.pool.SimpleConnectionPool` 사용 권장
   - 현재는 단순 연결로 시작, 추후 최적화

3. **에러 핸들링**
   - PostgreSQL 연결 실패 시 적절한 에러 처리
   - 초기에는 MongoDB fallback 로직 유지

4. **데이터 동기화**
   - 지표 추가/수정 시 양쪽 DB에 수동 동기화 필요 (마이그레이션 전)
   - PostgreSQL만 사용하게 되면 Flyway로 관리

---

## 📊 마이그레이션 후 DB 구조

### PostgreSQL (메타데이터/설정)
- ✅ `fred_indicators` - FRED 경제 지표 정의
- ✅ `yfinance_indicators` - Yahoo Finance 지표 정의
- ✅ `stocks` - 종목 정보 (이미 마이그레이션 완료)
- ✅ `users` - 사용자 정보
- ✅ `user_kis_accounts` - KIS 계좌 정보
- ✅ `kis_tokens` - KIS 토큰

### MongoDB (시계열 데이터)
- ✅ `daily_stock_data` - 일별 주가/경제 데이터 (시계열)
- ✅ `stock_recommendations` - 분석 추천 결과
- ✅ `prediction_results` - 예측 결과
- ❌ ~~`fred_indicators`~~ (PostgreSQL로 이관 예정)
- ❌ ~~`yfinance_indicators`~~ (PostgreSQL로 이관 예정)

---

## 🔗 관련 이슈 및 커밋

- **관련 커밋**: `feat: Slack 알림에 날짜 정보 추가 및 MongoDB URI 버그 수정` (2026-02-01)
- **관련 TODO**: `데이터베이스_마이그레이션_TODO.md`
- **참고 문서**: Stock 마이그레이션 경험 활용

---

## 📝 작업 로그

### 2026-02-01
- ✅ PostgreSQL 테이블 생성 (V6 migration)
- ✅ MongoDB 마이그레이션 스크립트 작성
- ✅ 프로덕션 데이터 추출 스크립트 작성
- ✅ 초기 데이터 양쪽 DB에 삽입 완료
- ✅ TODO 문서 작성
- 📝 현재 MongoDB 사용 중, 추후 PostgreSQL 전환 예정
