# 🔄 MongoDB → PostgreSQL 마이그레이션

Quantiq의 하이브리드 데이터베이스 마이그레이션 전략 및 실행 가이드입니다.

**상태:** ✅ 계획 완료 | **소요시간:** 4-5일 | **난이도:** 중상 | **위험도:** 낮음 (롤백 가능)

---

## 📚 문서 구조 (읽는 순서)

### 1. 빠른 참조 (5분) 📌
**파일:** `QUICK_REFERENCE.md`
- 한눈에 보는 마이그레이션 플로우
- 날짜별 체크리스트
- 성능 비교표
- 긴급 대응 가이드

**대상:** PM, 리더, 모든 팀원

---

### 2. 실행 가이드 (자세함) 🚀
**파일:** `EXECUTION_GUIDE.md`
- Day 1-4 상세 절차
- 명령어 예시
- 검증 방법
- 트러블슈팅

**대상:** 개발자, 운영자

---

### 3. 마이그레이션 계획 (기술) 🔧
**파일:** `RDB_MIGRATION_PLAN.md`
- Spring Entity 설정 방법
- Flyway 마이그레이션 스크립트
- PostgreSQL 설정
- 마이그레이션 서비스 구현

**대상:** 개발자 (백엔드)

---

### 4. 데이터베이스 전략 (설계) 🏗️
**파일:** `DATABASE_STRATEGY.md`
- 현재 MongoDB 스키마 분석
- RDB vs MongoDB 데이터 분류
- PostgreSQL 스키마 설계 (6개 테이블)
- MongoDB 유지 데이터 (시계열/분석)
- 성능 비교 (Before/After)

**대상:** 아키텍트, 설계자

---

### 5. 구현 상세 (코드) 📝
**파일:** `DATABASE_IMPLEMENTATION.md`
- Docker Compose 설정
- build.gradle.kts 의존성
- application.yml Spring 설정
- Entity 클래스 작성
- Repository 구현

**대상:** 개발자 (구현)

---

## 🎯 마이그레이션 개요

### 데이터 흐름

```
MongoDB (기존)              PostgreSQL (신규)
├─ users ───────────────→ users
├─ trading_configs ─────→ trading_configs
├─ account_balances ────→ account_balances
├─ stock_holdings ──────→ stock_holdings
├─ trades ──────────────→ trades
├─ trade_signals_executed → trade_signals_executed
│
└─ (유지)
   ├─ stock_recommendations
   ├─ daily_analysis_results
   ├─ portfolio_snapshots
   └─ market_data_archive
```

### 마이그레이션 타임라인

| 날짜 | 단계 | 시간 | 주요 작업 |
|------|------|------|---------|
| Day 1 | 준비 | 2-3h | 환경 설정, 의존성 추가 |
| Day 2 | 개발 | 4-5h | Flyway, Entity, Repository |
| Day 3 | 마이그레이션 | 3-4h | 데이터 이관, 검증 |
| Day 4 | 전환 | 2-3h | 이중 쓰기, 동기화 |

### 기대 효과

- ✅ **성능:** 쿼리 5-10배 향상 (100-500ms → 5-50ms)
- ✅ **안정성:** ACID 트랜잭션 보장
- ✅ **확장성:** 복잡한 조인 쿼리 가능
- ✅ **규정:** 완벽한 감사 기록

---

## 🚀 빠른 시작

### 1단계: 계획 이해
```bash
# 문서 읽기 순서
1. QUICK_REFERENCE.md (5분)
2. EXECUTION_GUIDE.md (30분)
3. 기술 문서들 (필요시)
```

### 2단계: 준비
```bash
# 필수 사전 준비
- [ ] MongoDB 완전 백업
- [ ] 팀 공지
- [ ] Day 1 일정 예약
```

### 3단계: 실행
```bash
# EXECUTION_GUIDE.md의 Day 1-4 따라하기
Day 1 → Day 2 → Day 3 → Day 4 → ✅ 완료
```

---

## 📋 단계별 체크리스트

### Day 1: 환경 준비 (2-3시간)
```
☐ docker-compose.yml 업데이트
☐ build.gradle.kts 의존성 추가
☐ .env 파일 작성
☐ application.yml 설정
☐ PostgreSQL 시작 테스트
```

### Day 2: 스키마 & 마이그레이션 (4-5시간)
```
☐ Flyway 스크립트 작성 (V1, V2)
☐ 6개 Entity 클래스 작성
☐ 6개 Repository 작성
☐ RdbMigrationService 구현
☐ 코드 컴파일 확인
```

### Day 3: 데이터 마이그레이션 (3-4시간)
```
☐ PostgreSQL 스키마 생성
☐ Flyway 마이그레이션 실행
☐ 데이터 이관 완료
☐ 데이터 검증 통과
☐ 성능 테스트 완료
```

### Day 4: 점진적 전환 (2-3시간)
```
☐ 이중 쓰기 모드 활성화
☐ 데이터 동기화 검증
☐ 24시간 안정성 확인
☐ 최종 검증 통과
☐ 마이그레이션 완료
```

---

## 🔄 문서 선택 가이드

**"지금 뭘 봐야 하나?"** 상황별 문서:

| 상황 | 문서 | 시간 |
|------|------|------|
| 전체 개요 알고 싶음 | QUICK_REFERENCE.md | 5m |
| 실행 절차 알고 싶음 | EXECUTION_GUIDE.md | 30m |
| 왜 PostgreSQL인가? | DATABASE_STRATEGY.md | 20m |
| SQL/Entity 코드 필요 | RDB_MIGRATION_PLAN.md | 1h |
| Docker/Spring 설정 | DATABASE_IMPLEMENTATION.md | 30m |
| 긴급 문제 발생 | QUICK_REFERENCE.md → 긴급 대응 | 2m |

---

## 🎯 성공 기준

마이그레이션이 성공한 것으로 판단하는 기준:

```
✅ 기술
├─ PostgreSQL 6개 테이블 생성됨
├─ 모든 인덱스 활성화됨
├─ Flyway 마이그레이션 이력 기록됨
└─ 컴파일 에러 0개

✅ 데이터
├─ MongoDB 데이터 100% 이관됨
├─ 데이터 일치율 100%
└─ 중복/손상 데이터 0개

✅ 성능
├─ 쿼리 응답 < 50ms
├─ 조인 쿼리 < 20ms
└─ 인덱스 효율 > 90%

✅ 운영
├─ 이중 쓰기 안정 (24시간)
├─ 에러 로그 < 1개/시간
└─ 성공률 99.9%
```

---

## 🚨 긴급 대응

**문제 발생 시 즉시 조치 (1분):**

```bash
# 읽기 소스를 MongoDB로 복구
export DB_READ_SOURCE=mongo
docker-compose restart quantiq-core

# 또는 이중 쓰기 비활성화
export DB_DUAL_WRITE=false
docker-compose restart quantiq-core
```

---

## 📞 문의

| 역할 | 담당자 | 연락처 |
|------|-------|--------|
| 마이그레이션 리더 | ________ | ________ |
| 개발자 | ________ | ________ |
| DBA | ________ | ________ |

---

## 📅 마이그레이션 일정

**시작 날짜:** ________________

**예상 완료:** ________________

**실제 완료:** ________________

---

**마지막 업데이트:** 2025-01-29 ✅
**버전:** 2.0 (최종 실행 가이드 완성)
