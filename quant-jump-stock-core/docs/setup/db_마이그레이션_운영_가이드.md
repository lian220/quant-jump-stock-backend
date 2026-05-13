# DB 마이그레이션 운영 가이드

prod PostgreSQL 에 Flyway 마이그레이션(V_*.sql)을 적용하는 표준 절차.

> **운영 정책**: prod DB schema 변경은 **운영자 수동 적용**이 원칙. 자동 마이그레이션은 사용 안 함 (자금 도메인 보수 운영). 본 문서는 그 절차를 표준화하고 사람 실수를 줄이기 위한 체크리스트.
>
> **사고 회고 (2026-05-13)**: V60 컬럼 미적용 상태에서 새 코드 배포 → `AppSecretReencryptionRunner` 가 부팅 시 컬럼 query → 무한 재시작. Cloud Run progressive rollout 가 막아 사용자 영향 0. 본 가이드는 사고 재발 방지가 목적.

---

## 1. 언제 본 가이드가 적용되나

다음 중 하나라도 해당하면 적용:

- PR 에 `quant-jump-stock-core/src/main/resources/db/migration/V*.sql` 신규 파일이 추가/변경됨
- GitHub Actions 의 **`DB Migration Gate`** workflow 가 PR 에 `🔴 schema-change` 라벨을 부착함
- PR comment 에 `🔴 Schema 변경 감지 — 운영자 수동 적용 필요` 가 게시됨

→ **머지 전 prod DB 에 마이그레이션 적용 + `schema-applied-to-prod` 라벨 부착 필요**.

---

## 2. 사전 검토

PR 의 SQL diff 를 보고 다음 점검:

| 항목 | 확인 |
|---|---|
| **Backward compatible?** | 새 컬럼 nullable + default 또는 NOT NULL + 기본값 — 구 코드도 동작? |
| **Lock 영향** | `ALTER TABLE ADD COLUMN` 은 PostgreSQL 11+ 빠른 메타데이터 변경. `CREATE INDEX CONCURRENTLY` 권장. `DROP COLUMN` 은 짧은 잠금 |
| **데이터 변환** | `UPDATE`, `INSERT` 가 포함되면 row 수 추정 + 런타임 추정 |
| **Rollback plan** | DROP/UPDATE 면 롤백 SQL 미리 준비 |
| **Expand-Contract** | DROP/RENAME 같은 destructive 변경은 두 PR 로 분리 (먼저 코드에서 unused → 다음 PR 에서 DROP) |

destructive (DROP/UPDATE 대량) 면 별도 maintenance window 잡고 사용자 공지 권장.

---

## 3. 적용 절차 (PostgreSQL psql 직접)

### 3.1 prod DB credential 로드 (값 transcript 노출 X)

```bash
DB_ENV=$(gcloud secrets versions access latest \
  --secret=qjs-env-db-prod --project=focal-limiter-486614-u8)
DB_HOST=$(echo "$DB_ENV" | grep "^DB_HOST=" | cut -d= -f2-)
DB_PORT=$(echo "$DB_ENV" | grep "^DB_PORT=" | cut -d= -f2-)
DB_NAME=$(echo "$DB_ENV" | grep "^DB_NAME=" | cut -d= -f2-)
DB_USER=$(echo "$DB_ENV" | grep "^DB_USER=" | cut -d= -f2-)
DB_PASSWORD=$(echo "$DB_ENV" | grep "^DB_PASSWORD=" | cut -d= -f2-)
unset DB_ENV
```

### 3.2 백업 확인

Supabase 자동 백업이 활성화되어 있는지 콘솔에서 확인. 또는 수동 dump:

```bash
docker run --rm -e PGPASSWORD="$DB_PASSWORD" postgres:15-alpine \
  pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  --schema-only > /tmp/backup-schema-$(date +%Y%m%d-%H%M).sql
```

### 3.3 마이그레이션 SQL 적용

PR 의 `V_*.sql` 본문을 그대로 적용. 트랜잭션 안에서:

```bash
# 예: V60 적용
docker run --rm -e PGPASSWORD="$DB_PASSWORD" postgres:15-alpine psql \
  -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -v ON_ERROR_STOP=1 -c "
BEGIN;
SET LOCAL lock_timeout = '3s';

-- (PR 의 SQL 본문 여기 붙여넣기)
ALTER TABLE user_kis_accounts
    ADD COLUMN IF NOT EXISTS app_secret_encrypted_v2 VARCHAR(1024);

COMMIT;
"
```

`IF NOT EXISTS` / `CREATE TABLE IF NOT EXISTS` 가 가능한 SQL 은 idempotent 하게 작성. 두 번 실행해도 안전.

### 3.4 Flyway history 기록 (필수)

⚠️ **이 단계 빠뜨리면 다음 부팅 시 Flyway validate 가 fail**.

```bash
docker run --rm -e PGPASSWORD="$DB_PASSWORD" postgres:15-alpine psql \
  -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
  -v ON_ERROR_STOP=1 -c "
INSERT INTO flyway_schema_history
    (installed_rank, version, description, type, script,
     checksum, installed_by, installed_on, execution_time, success)
VALUES (
    (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history),
    '60',                                              -- 적용한 버전
    'Add App Secret Encrypted V2 Column',              -- 파일명의 description
    'SQL',
    'V60__Add_App_Secret_Encrypted_V2_Column.sql',     -- 정확한 파일명
    NULL,                                              -- checksum: 향후 자동 보정
    'manual_$(date +%Y-%m-%d)',                        -- 누가 적용했는지 추적용
    NOW(),
    0,
    true
);
"
```

**checksum NULL 의 의미**: `application.yml` 의 `validate-on-migrate=false` + `ignore-missing-migrations=true` 가 통과시킴. 향후 `validate-on-migrate=true` 로 전환 시 정확한 checksum 보정 필요 (별 phase).

### 3.5 검증

```sql
-- 컬럼/테이블 추가 확인
SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_name = 'user_kis_accounts' AND column_name = 'app_secret_encrypted_v2';

-- Flyway history success=true 확인
SELECT version, description, success, installed_on, installed_by
FROM flyway_schema_history
ORDER BY installed_rank DESC LIMIT 5;
```

`unset DB_PASSWORD` 로 환경변수 정리.

---

## 4. 머지 + 배포

1. PR 에 **`schema-applied-to-prod`** 라벨 부착 (직접 GitHub UI 에서)
2. CI 의 `Schema migration applied check` status 가 ✅ 로 전환
3. PR 머지
4. Deploy Core API workflow 자동 트리거
5. Cloud Run 새 revision 부팅 시 새 코드가 새 schema 정상 인식
6. health check (`/actuator/health` 200, login 200) 검증

---

## 5. 사고 시 대응

### 5.1 부팅 실패 (revision 무한 재시작)

**원인 가능성** (우선순위):
1. Schema 미적용 → §3 로 적용 후 §6 으로 강제 재시작
2. `APP_ENCRYPTION_KEY_V2` 같은 secret 누락 → Secret Manager 에 추가 후 §6
3. 코드 자체 결함 → revert PR + redeploy

**진단 명령**:
```bash
# 최신 revision 의 startup error
gcloud logging read \
  "resource.type=cloud_run_revision AND resource.labels.service_name=qjs-core AND severity>=ERROR" \
  --limit=20 --format="value(timestamp,severity,textPayload)" --freshness=15m
```

### 5.2 운영 영향 확인

```bash
# 트래픽 분배 (latest revision 이 100% 인지)
gcloud run services describe qjs-core --region=asia-northeast3 --format="value(status.traffic)"

# prod URL health
curl -s https://api.alphafoundry.app/actuator/health
```

Cloud Run progressive rollout 가 죽은 revision 에 트래픽 라우팅 하지 않음 → 보통 사용자 영향 0. 단 모든 revision 이 fail 하면 영향 발생.

---

## 6. Cloud Run 강제 재시작 (DB 변경 반영)

DB 적용 후 기존 revision 은 캐시된 metadata 로 동작 가능. 새 revision 으로 강제 재시작:

```bash
# 가장 단순 — label 변경으로 새 revision 생성
gcloud run services update qjs-core --region=asia-northeast3 \
  --update-labels=manual-restart=$(date +%s)
```

또는 GitHub Actions Deploy Core API workflow rerun.

---

## 7. 절대 하지 말 것

| 행위 | 이유 |
|---|---|
| `flyway_schema_history` row 삭제/수정 | Flyway 의 무결성 검증 메커니즘 깨짐. 향후 validate fail |
| `application.yml` 의 `FLYWAY_ENABLED=true` 운영 임시 켜기 | 모든 미적용 마이그레이션이 한 번에 시도됨 — 의도하지 않은 변경 위험 |
| `db/migration/manual/README.md` 의 "임시 enable" 패턴 | 위와 동일. 본 가이드의 §3 (psql 직접) 가 안전 |
| 적용 안 하고 라벨만 부착 | CI gate 우회 — 다음 deploy 에서 사고 직접 야기 |
| destructive (DROP) 를 사용자 공지 없이 적용 | 데이터 손실. 별 maintenance window |

---

## 8. 향후 개선 (백로그)

- **Spring Boot 부팅 시 Flyway validate-only Bean** — 부팅 단계 schema-code 일치 자동 검증 (CI gate 우회 시 마지막 안전망)
- **Hibernate `ddl-auto=validate`** — entity-table mapping mismatch 검증 (Flyway 와 다른 layer)
- **`flyway_schema_history` checksum 정리** — 본 가이드의 NULL checksum 들을 정확한 값으로 보정. 그 후 `validate-on-migrate=true` 활성
- **Python data-engine 부팅 검증** — `information_schema` 의존 컬럼 존재 확인 (cascading 차단)
- **Slack alert layer** — log only → critical alert (현재는 log aggregation 알림 룰 별 layer)

---

**마지막 업데이트**: 2026-05-13 (V60 사고 회고 후 작성)
