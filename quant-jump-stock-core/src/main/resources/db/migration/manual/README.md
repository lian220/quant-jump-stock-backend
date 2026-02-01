# 수동 실행 마이그레이션

이 폴더의 SQL 파일들은 **자동으로 실행되지 않습니다**.
필요할 때만 수동으로 실행하세요.

## 📁 파일 목록

### V7__Insert_Initial_Stocks_Data.sql
**목적**: Stock 초기 데이터 35개 종목 삽입

**실행 시기**:
- 새로운 환경에서 초기 데이터가 필요할 때
- 테스트 환경 구축 시
- Stock 데이터가 완전히 비어있을 때

**⚠️ 주의사항**:
- 이미 데이터가 있으면 중복 삽입되지 않음 (`ON CONFLICT DO NOTHING`)
- 프로덕션에서는 신중하게 실행

---

## 🚀 수동 실행 방법

### 방법 1: Docker 환경에서 실행

```bash
# PostgreSQL 컨테이너 접속
docker-compose exec postgresql psql -U quantiq_user -d quantiq

# 파일 실행
\i /path/to/V7__Insert_Initial_Stocks_Data.sql

# 또는 직접 복사해서 실행
```

### 방법 2: 로컬에서 psql 사용

```bash
# 파일 경로
cd quantiq-core/src/main/resources/db/migration/manual/

# 실행
psql -h localhost -U quantiq_user -d quantiq -f V7__Insert_Initial_Stocks_Data.sql
```

### 방법 3: Flyway locations 설정으로 포함

**application.yml 임시 수정**:
```yaml
spring:
  flyway:
    enabled: true
    locations:
      - classpath:db/migration
      - classpath:db/migration/manual  # 임시 추가
```

**실행 후 다시 제거**:
```yaml
spring:
  flyway:
    enabled: true
    locations:
      - classpath:db/migration  # manual 제거
```

---

## ✅ 실행 전 체크리스트

- [ ] 현재 Stock 데이터 확인 (`SELECT COUNT(*) FROM stocks;`)
- [ ] 백업 완료 여부 확인
- [ ] 실행 환경 확인 (dev/staging/production)
- [ ] 중복 데이터 없는지 확인 (`SELECT ticker, COUNT(*) FROM stocks GROUP BY ticker HAVING COUNT(*) > 1;`)

---

## 📊 실행 후 검증

```sql
-- 1. 삽입된 데이터 수 확인
SELECT COUNT(*) as total_stocks FROM stocks;
-- 예상: 35개

-- 2. 중복 확인
SELECT ticker, COUNT(*) as count
FROM stocks
GROUP BY ticker
HAVING COUNT(*) > 1;
-- 예상: 0개 (중복 없음)

-- 3. 활성 종목 확인
SELECT COUNT(*) as active_stocks
FROM stocks
WHERE is_active = TRUE;
-- 예상: 35개

-- 4. ETF 확인
SELECT ticker, stock_name
FROM stocks
WHERE is_etf = TRUE;
-- 예상: QQQ, SOXX, SPY
```

---

## 🔄 롤백 방법

```sql
-- 모든 Stock 데이터 삭제
DELETE FROM stocks;

-- 또는 특정 ticker만 삭제
DELETE FROM stocks WHERE ticker IN ('AAPL', 'TSLA', ...);
```

---

**마지막 업데이트**: 2026-02-01
**작성자**: Claude Sonnet 4.5
