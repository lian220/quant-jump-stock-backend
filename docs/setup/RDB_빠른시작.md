# RDB 마이그레이션 빠른 시작 (5분)

## 📋 준비 확인

다음이 완료되었는지 확인하세요:

- ✅ `.env` 파일에 RDB 정보 입력
- ✅ `build.gradle.kts` 업데이트 (PostgreSQL, Flyway 의존성)
- ✅ `docker-compose.yml` 업데이트 (PostgreSQL 서비스 추가)
- ✅ `application.yml` 업데이트 (RDB, Flyway 설정)
- ✅ Flyway 마이그레이션 SQL 파일 생성

모두 완료되었으니 아래 단계를 따르세요!

---

## 🚀 실행 단계

### Step 1: PostgreSQL 시작 (2분)

```bash
# 1. 최신 docker-compose.yml로 PostgreSQL만 시작
docker-compose up -d postgresql

# 2. PostgreSQL 헬스체크 (Ready가 될 때까지 대기)
docker-compose ps
# quantiq-postgres        healthy 확인

# 3. 연결 테스트
docker-compose exec postgresql psql -U quantiq_user -d quantiq -c "\dt"
# Flyway 마이그레이션 실행 후 테이블 나타남

# 4. 마이그레이션 이력 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq \
  -c "SELECT version, description, success FROM flyway_schema_history;"
```

**예상 결과:**
```
 version |    description     | success
---------+--------------------+---------
       1 | Initial Schema     | t
       2 | Create Indexes     | t
```

---

### Step 2: Spring Boot 빌드 (2분)

```bash
cd quantiq-core

# 1. 의존성 다운로드 및 빌드
./gradlew clean build -x test

# 2. 확인
ls -la build/libs/
# quantiq-core-0.0.1-SNAPSHOT.jar 생성됨
```

---

### Step 3: 애플리케이션 시작 (1분)

```bash
cd /Users/imdoyeong/Desktop/workSpace/quantiq

# 1. 환경 변수 설정 (.env 파일 사용)
source .env  # 또는 export 명령으로 각각 설정

# 2. 애플리케이션 시작
docker-compose up -d quantiq-core

# 3. 로그 확인 (마이그레이션 검증 메시지 대기)
docker-compose logs -f quantiq-core | tail -20
```

**확인할 로그:**
```
20XX-XX-XX XX:XX:XX.XXX  INFO ... Flyway: Successfully validated 2 migrations
20XX-XX-XX XX:XX:XX.XXX  INFO ... Creating new Flyway schema history table [public.flyway_schema_history]
20XX-XX-XX XX:XX:XX.XXX  INFO ... Flyway: Successfully migrated from version 0 to 1
20XX-XX-XX XX:XX:XX.XXX  INFO ... Flyway: Successfully migrated from version 1 to 2
20XX-XX-XX XX:XX:XX.XXX  INFO ... Started QuantiqCoreApplication
```

---

## ✅ 검증

### 1. PostgreSQL 테이블 확인

```bash
# 모든 테이블 조회
docker-compose exec postgresql psql -U quantiq_user -d quantiq -c "\dt"

# 예상 결과:
#            List of relations
#  Schema |         Name         | Type  | Owner
# --------+----------------------+-------+-----
#  public | account_balances     | table | quantiq_user
#  public | flyway_schema_history| table | quantiq_user
#  public | stock_holdings       | table | quantiq_user
#  public | trades               | table | quantiq_user
#  public | trading_configs      | table | quantiq_user
#  public | trade_signals_executed| table | quantiq_user
#  public | users                | table | quantiq_user
```

### 2. 데이터베이스 상태 확인

```bash
# 각 테이블의 데이터 수 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq << EOF
SELECT
  (SELECT COUNT(*) FROM users) as users,
  (SELECT COUNT(*) FROM trading_configs) as trading_configs,
  (SELECT COUNT(*) FROM account_balances) as account_balances,
  (SELECT COUNT(*) FROM stock_holdings) as stock_holdings,
  (SELECT COUNT(*) FROM trades) as trades,
  (SELECT COUNT(*) FROM trade_signals_executed) as trade_signals;
EOF

# 예상 결과:
# users | trading_configs | account_balances | stock_holdings | trades | trade_signals
#-------+-----------------+------------------+----------------+--------+---------------
#     0 |               0 |                0 |              0 |      0 |             0
```

### 3. API 응답 테스트

```bash
# Spring Boot 헬스 체크
curl http://localhost:10010/api/health

# 예상 결과:
# {"status":"UP",...}
```

---

## 📊 마이그레이션 상태 모니터링

```bash
# 실시간 로그 모니터링
docker-compose logs -f quantiq-core

# 특정 키워드로 필터링
docker-compose logs quantiq-core | grep -i "error\|migration\|exception"

# 컨테이너 상태 확인
docker-compose ps

# 포트 상태 확인
lsof -i :5432    # PostgreSQL
lsof -i :8080    # Spring Boot
lsof -i :27017   # MongoDB
```

---

## 🔄 데이터 마이그레이션 (MongoDB → RDB)

MongoDB의 기존 사용자 데이터를 RDB로 이동하려면:

```bash
# 1. 마이그레이션 활성화
export RUN_MIGRATION=true

# 2. 환경 변수 적용
docker-compose restart quantiq-core

# 3. 마이그레이션 로그 확인
docker-compose logs -f quantiq-core | grep -i "migration\|user"

# 4. 완료 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq \
  -c "SELECT COUNT(*) FROM users;"
```

---

## ⚠️ 문제 해결

### PostgreSQL 연결 실패

```bash
# 1. 컨테이너 상태 확인
docker-compose ps postgresql

# 2. 로그 확인
docker-compose logs postgresql

# 3. 포트 충돌 확인
lsof -i :5432

# 4. 재시작
docker-compose restart postgresql
```

### Flyway 마이그레이션 실패

```bash
# 1. 마이그레이션 이력 확인
docker-compose exec postgresql psql -U quantiq_user -d quantiq \
  -c "SELECT * FROM flyway_schema_history;"

# 2. 마이그레이션 파일 확인
ls -la quantiq-core/src/main/resources/db/migration/

# 3. SQL 문법 검증
docker-compose exec postgresql psql -U quantiq_user -d quantiq \
  -f quantiq-core/src/main/resources/db/migration/V1__Initial_Schema.sql
```

### Spring Boot 시작 실패

```bash
# 1. 전체 로그 확인
docker-compose logs quantiq-core

# 2. 최근 에러 필터링
docker-compose logs quantiq-core | tail -50 | grep -i error

# 3. 데이터베이스 연결 테스트
docker-compose exec quantiq-core curl http://localhost:8080/api/health

# 4. 재빌드
cd quantiq-core
./gradlew clean build -x test
docker build -t quantiq-core:latest .
docker-compose up -d quantiq-core
```

---

## 📈 다음 단계

1. ✅ **이 단계 완료**: PostgreSQL 및 Flyway 설정
2. 📝 **다음**: MongoDB → RDB 데이터 마이그레이션 (docs/RDB_MIGRATION_PLAN.md 참고)
3. 🔄 **그 다음**: 이중 쓰기 모드 검증
4. 🚀 **최종**: 프로덕션 배포

---

## 💡 팁

### PostgreSQL 직접 쿼리 실행

```bash
# PostgreSQL 접속
docker-compose exec postgresql psql -U quantiq_user -d quantiq

# 내부에서:
\dt                     # 테이블 목록
SELECT * FROM users;    # 사용자 조회
\d users               # users 테이블 스키마
\quit                  # 종료
```

### Docker 정리

```bash
# 모든 컨테이너 정지
docker-compose down

# 볼륨 포함 정리 (주의: 데이터 삭제됨)
docker-compose down -v

# 이미지 삭제
docker-compose down --rmi all
```

### 빌드 캐시 초기화

```bash
cd quantiq-core
./gradlew clean --refresh-dependencies
```

---

## 📞 도움말

각 단계에서 문제가 발생하면:

1. 로그 확인: `docker-compose logs <service-name>`
2. 문서 참고: 아래 관련 문서 섹션 확인
3. 상태 확인: `docker-compose ps`, `lsof -i`

모든 것이 정상이면 **이제 RDB 마이그레이션을 시작할 준비가 되었습니다!** 🎉

---

## 📚 관련 문서

### 아키텍처
- [하이브리드 데이터베이스 전략](../architecture/하이브리드_데이터베이스_전략.md) - PostgreSQL + MongoDB 전략
- [데이터베이스 마이그레이션 현황](../architecture/데이터베이스_마이그레이션_현황.md) - 마이그레이션 상태

### 데이터베이스
- [데이터베이스 스키마](../database/SCHEMA.md) - 전체 스키마 정의
- [테이블 관계도](../database/RELATIONSHIPS.md) - ERD 및 관계

### 설정 가이드
- [환경설정 가이드](./환경설정_가이드.md) - 전체 환경 설정
- [초기 데이터 설정](./초기_데이터_설정.md) - 초기 데이터 구성
- [배포 운영 가이드](./배포_운영_가이드.md) - 프로덕션 배포

### 레거시 (참고용)
- [마이그레이션 가이드](../legacy/migration/README.md) - MongoDB→PostgreSQL 완료
