# 🎯 MongoDB → PostgreSQL 마이그레이션 빠른 참조

**상태:** ✅ 계획 완료 | **소요시간:** 4-5일 | **난이도:** 중상 | **위험도:** 낮음 (롤백 가능)

---

## 📊 한눈에 보기

```
┌─────────────────────────────────────────────────────────────┐
│                  마이그레이션 아키텍처                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  MongoDB (기존)                  PostgreSQL (신규)           │
│  ├─ users ─────────────────────→ users                     │
│  ├─ trading_configs ────────────→ trading_configs          │
│  ├─ account_balances ───────────→ account_balances         │
│  ├─ stock_holdings ─────────────→ stock_holdings           │
│  ├─ trades ─────────────────────→ trades                   │
│  ├─ trade_signals_executed ────→ trade_signals_executed    │
│  │                                                          │
│  └─ (유지) stock_recommendations                           │
│  └─ (유지) portfolio_snapshots                             │
│  └─ (유지) market_data_archive                             │
│                                                              │
│  JDBC 연결   ◄───── Spring Boot ────► PostgreSQL           │
│             ─────────────────────  Driver                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔄 마이그레이션 플로우

### Phase 1: 계획 & 준비 (완료 ✅)
```
문서 작성 ──→ 스키마 설계 ──→ Entity 설계 ──→ 위험도 분석
  ✅              ✅             ✅              ✅
```

**산출물:**
- ✅ DATABASE_STRATEGY.md (전략)
- ✅ RDB_MIGRATION_PLAN.md (기술 계획)
- ✅ EXECUTION_GUIDE.md (실행 가이드)
- ✅ QUICK_REFERENCE.md (본 문서)

---

### Phase 2: 준비 & 설정 (Day 1 - 2-3시간)
```
docker-compose.yml 업데이트
    ↓
build.gradle.kts 의존성 추가
    ↓
.env 환경 설정
    ↓
application.yml Spring 설정
    ↓
✅ PostgreSQL 로컬 테스트
```

**체크리스트:**
- [ ] DB_PASSWORD 변경 (보안)
- [ ] PostgreSQL 컨테이너 시작 테스트
- [ ] 의존성 다운로드 (`./gradlew clean build`)

---

### Phase 3: 스키마 & 마이그레이션 (Day 2 - 4-5시간)
```
Flyway 마이그레이션 스크립트 작성
├─ V1__Initial_Schema.sql (6 테이블)
└─ V2__Create_Indexes.sql (모든 인덱스)
    ↓
Entity 클래스 작성 (6개)
    ↓
Repository 인터페이스 (6개)
    ↓
RdbMigrationService 구현
    ↓
RdbConfig 설정
    ↓
✅ 코드 컴파일 확인 (`./gradlew build`)
```

**생성할 파일:**
```
quantiq-core/
├── src/main/resources/db/migration/
│   ├── V1__Initial_Schema.sql
│   └── V2__Create_Indexes.sql
└── src/main/kotlin/com/quantiq/core/
    ├── domain/rdb/
    │   ├── User.kt
    │   ├── TradingConfig.kt
    │   ├── AccountBalance.kt
    │   ├── StockHolding.kt
    │   ├── Trade.kt
    │   └── TradeSignalExecuted.kt
    ├── repository/rdb/
    │   └── [6개 Repository]
    ├── service/
    │   └── RdbMigrationService.kt
    └── config/
        └── RdbConfig.kt
```

---

### Phase 4: 마이그레이션 & 검증 (Day 3 - 3-4시간)
```
PostgreSQL 시작
    ↓
Flyway 마이그레이션 (V1, V2)
    ↓
데이터 마이그레이션 (MongoDB → PostgreSQL)
    ↓
데이터 검증
├─ 테이블별 데이터 수
├─ 사용자 정보 일치
├─ 설정값 일치
└─ 무결성 확인
    ↓
성능 테스트
├─ 조인 쿼리 < 20ms
├─ 기본 CRUD < 10ms
└─ 인덱스 효율성 > 90%
    ↓
✅ 모든 검증 통과
```

**검증 쿼리:**
```bash
# 1. 테이블 존재 확인
SELECT * FROM information_schema.tables
WHERE table_schema = 'public';

# 2. 데이터 수 확인
SELECT COUNT(*) FROM users;  -- MongoDB 수와 비교

# 3. 인덱스 확인
SELECT * FROM pg_indexes
WHERE schemaname = 'public';

# 4. 쿼리 성능 확인
EXPLAIN ANALYZE SELECT * FROM users WHERE user_id = '...';
```

---

### Phase 5: 점진적 전환 (Day 4 - 2-3시간)
```
이중 쓰기 모드 활성화
├─ RDB: Primary (쓰기/읽기)
├─ MongoDB: Backup (쓰기만, 실패 무시)
└─ (기존 트래픽 계속 정상 처리)
    ↓
데이터 일관성 검증 (24시간)
├─ 신규 데이터 일치 확인
├─ 거래 기록 정상
└─ 성능 저하 없음
    ↓
이중 쓰기 모드 비활성화 (준비 완료)
├─ RDB만 쓰기
├─ MongoDB는 필요시 수동 업데이트
└─ 점진적 제거 준비
    ↓
✅ 마이그레이션 완료
```

**설정 변수:**
```yaml
DB_DUAL_WRITE: true/false      # 이중 쓰기
DB_READ_SOURCE: rdb/mongo      # 읽기 소스
DB_RDB_ENABLED: true/false     # RDB 활성화
RUN_MIGRATION: true/false      # 마이그레이션 실행
```

---

## 📅 타임라인 (4-5일)

| 날짜 | Phase | 시간 | 주요 작업 |
|------|-------|------|---------|
| Day 1 | 준비 | 2-3h | Docker, 의존성, 환경 설정 |
| Day 2 | 개발 | 4-5h | Flyway, Entity, Repository |
| Day 3 | 마이그레이션 | 3-4h | 데이터 이관, 검증, 성능 테스트 |
| Day 4 | 전환 | 2-3h | 이중 쓰기, 데이터 동기화 검증 |
| *Day 5+ | 모니터링 | 지속 | 에러 추적, 성능 모니터링 |

---

## 💾 데이터 매핑

### RDB로 이동 (ACID 트랜잭션 필요)

| MongoDB | PostgreSQL | 이유 |
|---------|-----------|------|
| users | users | 사용자 정보 (1:1) |
| trading_configs | trading_configs | 거래 설정 (1:1) |
| account_balances | account_balances | 계좌 잔액 (트랜잭션) |
| stock_holdings | stock_holdings | 포트폴리오 (정확한 추적) |
| trades | trades | 거래 기록 (감사) |
| (신규) | trade_signals_executed | 신호 실행 기록 |

### MongoDB에 유지 (시계열/분석)

| 컬렉션 | 목적 | TTL |
|-------|------|-----|
| stock_recommendations | 일일 추천 신호 | 1년 |
| daily_analysis_results | 기술적 분석 | 1년 |
| portfolio_snapshots | 포트폴리오 스냅샷 | 2년 |
| market_data_archive | 시장 데이터 | 90일 |

---

## ✅ 필수 체크리스트

### 시작 전 (Day 0)
```
준비물:
  ☐ Docker & Docker Compose 최신 버전
  ☐ PostgreSQL 15+ 지식
  ☐ Spring Boot Data JPA 경험
  ☐ MongoDB 데이터 백업 (필수!)
  ☐ 팀 공지 (마이그레이션 일정)
```

### Day 1 완료 기준
```
  ☐ docker-compose.yml 업데이트
  ☐ build.gradle.kts 의존성 추가
  ☐ .env 파일 작성
  ☐ application.yml 설정
  ☐ PostgreSQL 컨테이너 시작 테스트 성공
  ☐ ./gradlew clean build 성공
```

### Day 2 완료 기준
```
  ☐ V1__Initial_Schema.sql 작성 (6 테이블)
  ☐ V2__Create_Indexes.sql 작성
  ☐ 6개 Entity 클래스 작성
  ☐ 6개 Repository 작성
  ☐ RdbMigrationService 구현
  ☐ RdbConfig 작성
  ☐ ./gradlew build 성공 (컴파일 에러 0)
```

### Day 3 완료 기준
```
  ☐ PostgreSQL 시작 및 헬스 체크 통과
  ☐ Flyway 마이그레이션 성공 (V1, V2)
  ☐ 6개 테이블 생성됨
  ☐ 모든 인덱스 생성됨
  ☐ 데이터 마이그레이션 완료
  ☐ 테이블별 데이터 수 일치
  ☐ 쿼리 성능 테스트 통과
```

### Day 4 완료 기준
```
  ☐ 이중 쓰기 모드 활성화
  ☐ 신규 데이터 양쪽 저장 확인
  ☐ 읽기 소스 = RDB 설정
  ☐ 성능 저하 없음
  ☐ 에러 로그 0개
  ☐ 24시간 이상 안정적 운영
  ☐ 최종 검증 통과
```

---

## 🚨 긴급 대응

### 문제 발생 시 즉시 조치 (1분)
```bash
# 읽기 소스를 MongoDB로 즉시 복구
export DB_READ_SOURCE=mongo
docker-compose restart quantiq-core

# 또는 이중 쓰기 비활성화
export DB_DUAL_WRITE=false
docker-compose restart quantiq-core
```

### 선택지
| 상황 | 대응 | 시간 | 영향 |
|------|------|------|------|
| 데이터 손상 | 읽기 소스 → MongoDB | 1분 | 없음 |
| 성능 저하 | 이중 쓰기 비활성화 | 1분 | 신규 데이터 RDB만 저장 |
| 연결 오류 | PostgreSQL 재시작 | 5분 | 짧은 다운타임 |
| 심각한 문제 | 완전 롤백 (mongodb 기본) | 10분 | 1시간 데이터 손실 가능 |

---

## 📈 성능 비교

### Before (MongoDB Only)
```
쿼리: 100-500ms (문서 수에 따라)
조인: lookup 사용 (느림)
트랜잭션: 보장 안 됨
확장: 수평 확장만 가능
```

### After (Hybrid)
```
쿼리: 5-50ms ⚡ (90% 단축)
조인: 자연 조인 (빠름)
트랜잭션: ACID 보장 ✅
확장: 수직 확장 가능
```

---

## 🎓 문서 네비게이션

### 빠른 참조 필요
→ **현재 문서 (QUICK_REFERENCE.md)**

### 실행 단계별 가이드 필요
→ **EXECUTION_GUIDE.md**
- Day 1-4 상세 절차
- 체크리스트
- 트러블슈팅

### 기술 구현 상세 필요
→ **RDB_MIGRATION_PLAN.md**
- Flyway 스크립트
- Entity 코드
- Service 구현

### 아키텍처/전략 이해 필요
→ **DATABASE_STRATEGY.md**
- 데이터 분류 전략
- 스키마 설계
- 마이그레이션 전략

---

## 📞 연락처

| 역할 | 담당자 | 연락처 |
|------|-------|--------|
| 마이그레이션 리더 | ________ | ________ |
| 개발자 | ________ | ________ |
| DBA | ________ | ________ |
| PM | ________ | ________ |

---

## 🎯 마이그레이션 상태 추적

```
준비 (Day 0)    ☐
설정 (Day 1)    ☐
개발 (Day 2)    ☐
마이그레이션 (Day 3)  ☐
전환 (Day 4)    ☐
완료 (Day 5+)   ☐
```

**마이그레이션 시작:** ________________

**예상 완료:** ________________

**실제 완료:** ________________

---

**마지막 업데이트:** 2025-01-29
**버전:** 1.0
**상태:** 준비 완료 ✅
