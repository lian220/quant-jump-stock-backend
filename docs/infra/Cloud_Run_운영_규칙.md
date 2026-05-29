# Cloud Run 운영 규칙 (Global Standards)

> **최종 업데이트**: 2026-05-22 (qjs-core MongoDB localhost 사고 후 작성)
> **적용 범위**: qjs-core, qjs-data-engine, qjs-frontend, qjs-backoffice, news-collector
> **계기 사고**: 2026-05-22 15:20 KST `volumeMounts ↔ volumes` 불일치로 secret 마운트 실패 → Spring `localhost:27017` fallback → 1-2시간 prod degradation

본 문서는 **글로벌 표준** (12-Factor / OWASP / GCP / K8s / Spring Boot / SRE) 을 우리 시스템에 매핑한 **강제 규칙**. 새 service / 새 PR 모두 본 체크리스트 통과 필수.

---

## 1. 글로벌 표준 → 우리 시스템 매핑

| 표준 | 원칙 | 우리 적용 |
|------|------|----------|
| **12-Factor III** Config | "Config in environment, no default in code" | application-prod.yml 에서 모든 secret default 폐기 (fail-fast) |
| **OWASP A05:2021** | "No default credentials in production" | JWT_SECRET / encryption key 평문 default 제거 |
| **GCP Cloud Run** | "Secret Manager 환경변수 직접 주입 권장 (file mount 는 advanced)" | 현재 file mount + entrypoint.sh parsing 사용 — 향후 env-var 전환 검토 |
| **K8s Probes** | "Liveness ≠ Readiness ≠ Startup" | liveness = shallow JVM, **readiness/startup = deep (DB/Mongo)** |
| **Spring Boot** | "Readiness group 에 외부 의존성 포함" | `management.endpoint.health.group.readiness.include=db,mongo,ping` |
| **SRE** | "Fail fast and loudly. No silent degradation" | entrypoint.sh 의 prod secret 가드 + readiness probe 검증 |

---

## 2. 강제 규칙 (모든 service / 모든 PR)

### 🔴 R1. Prod 환경 secret default 절대 금지

```yaml
# ❌ 금지 — application.yml 에서 prod 영향 받는 위치
mongodb:
  uri: ${MONGODB_URI:mongodb://...localhost:27017/...}   # default 가 fallback 됨

# ✅ application-prod.yml 에서 override 폐기
mongodb:
  uri: ${MONGODB_URI}   # 누락 시 부팅 실패
```

**예외**: `application.yml` 의 dev/local 편의 default 는 그대로 유지 가능. **prod profile 에서만 override 폐기**.

검증:
```bash
grep -nE "\\\${.*:.*}" application.yml | grep -iE "secret|password|uri|key" | head
# → secret 류는 모두 application-prod.yml 의 fail-fast override 있는지 확인
```

### 🔴 R2. Entrypoint 의 prod 가드

shell entrypoint (예: `entrypoint.sh`) 가 secret 파일 마운트 의존하면, prod profile 시 명시 검증 필수:

```sh
if [ "${SPRING_PROFILES_ACTIVE}" = "prod" ]; then
    for required in /secrets/common/env /secrets/db-prod/env /secrets/prod/env; do
        [ -f "$required" ] || { echo "FATAL: missing $required" >&2; exit 1; }
    done
fi
```

→ Cloud Run startup probe 가 실패하면 새 revision 트래픽 못 받음.

### 🔴 R3. Liveness ≠ Readiness 분리

| Probe | 검증 내용 | 권장 endpoint |
|-------|----------|---------------|
| **Liveness** | JVM/프로세스 가동 (restart 가 도움될 때) | `/actuator/health/liveness` (shallow) |
| **Readiness** | 의존성 포함 (DB/Mongo/외부 API 등) | `/actuator/health/readiness` (group=db,mongo,ping) |
| **Startup** | 부팅 초기화 완료 여부 (느린 앱) | readiness 와 동일 |

Deploy verify 단계는 **readiness** 폴링 — liveness 만 검증하면 의존성 깨져도 deploy "success".

### 🔴 R4. Volume/Mount 정합성 검증 (deploy 직후)

`gcloud run deploy --set-secrets` 가 매번 새 random suffix volume 생성 → 누적되며 `volumeMounts ↔ volumes` 불일치 발생 가능.

deploy workflow 에 검증 step 추가:

```bash
MISMATCH=$(gcloud run services describe <svc> --region=... --format=json | jq -r '
  {mounts: [.spec.template.spec.containers[0].volumeMounts[]?.name],
   volumes: [.spec.template.spec.volumes[]?.name]}
  | (.mounts - .volumes) | join(",")
')
if [ -n "$MISMATCH" ]; then
  echo "❌ volume/mount mismatch: $MISMATCH"
  exit 1
fi
```

**검증 위치**: deploy 직후, traffic 전환 직전.

### 🟡 R5. Deploy 후 실 API smoke test

readiness probe 통과만으로는 부족 — 실제 데이터 의존 endpoint 1-2개 호출:

```yaml
- name: Smoke test
  run: |
    curl -fsS -m 15 "$SERVICE_URL/api/v1/stocks?limit=1" || exit 1
    curl -fsS -m 15 "$SERVICE_URL/api/v1/news/by-tickers?tickers=AAPL" || exit 1
```

Mongo round-trip 정상성 확인. readiness 가 connection pool init 만 검증할 수 있어 보완.

### 🟡 R6. 모니터링/알림 통합

Cloud Logging metric:
- `DataAccessResourceFailureException` count > 0 (5분) → Slack `#qjs-prod-alert`
- `Connection refused localhost:27017` 패턴 → 즉시 PagerDuty
- `/actuator/health/readiness` 5xx 1분 지속 → Slack

GCP Cloud Monitoring uptime check 추가 권장.

### 🟢 R7. (Recommended) `--set-secrets` env-var 모드 전환

file mount 대신 env 직접 주입:

```bash
--set-secrets "MONGODB_URI=qjs-env-db-prod-mongodb:latest,JWT_SECRET=qjs-env-common-jwt:latest,..."
```

- volume/mount 정합성 사고 자체가 사라짐
- entrypoint.sh shell parsing 사라짐 (보안 표면 ↓)
- 단점: Secret Manager 의 secret 을 key 별로 분리해야 (현재는 dotenv 형태로 통합)

별도 마이그레이션 작업 (~몇 일) — 현재는 R1+R2 fail-fast 로 위험 회피.

### 🔴 R8. CI/CD 일관성 — 모든 prod 컴포넌트 main push 자동 반영

2026-05-20 ~ 5/28 Vertex AI v24 stale package 사고 후속 (`docs/incidents/2026-05-20-vertex-ai-stale-package.md`):

main 머지 시점에 **Cloud Run + Vertex AI ML package + GCS asset 등 prod 영향 주는 모든 컴포넌트가 동일 commit 으로 갱신**되어야 함. 부분 자동화는 silent drift 위험 (사고 시 8일 미반영).

검증:
```bash
# 각 컴포넌트의 deployed sha / mtime 확인 — 최신 main commit 과 일치하는지
gcloud run services describe <svc> --format='value(metadata.labels.commit-sha)'
gsutil ls -l gs://.../<latest>.tar.gz
```

본 시스템 적용:
- ✅ `deploy-core.yml` (qjs-core)
- ✅ `deploy-data-engine.yml` (qjs-data-engine)
- ✅ `deploy-ml-package.yml` (Vertex AI ML package — PR #126 추가, 2026-05-29)

새 prod 컴포넌트 추가 시 deploy workflow 도 함께 추가 필수.

### 🔴 R9. PostgreSQL pool pre-ping 강제

2026-05-27 사고 (`docs/incidents/2026-05-20-vertex-ai-stale-package.md` §4.2):

`psycopg2.pool.ThreadedConnectionPool` 또는 동등 pool 사용 시 **getconn() 후 SELECT 1 ping 필수**. Supabase 측 idle timeout 으로 끊긴 connection 을 클라이언트가 인지 못해 첫 query 에서 `server closed the connection unexpectedly` 발생.

공통 헬퍼: `adapter/output/postgresql/_pool_util.py` `get_validated_conn(pool)` 사용.

추가로 **silent swallow except 금지**:
```python
# ❌ 금지
except Exception:
    return []

# ✅ 권장
except SpecificError:
    logger.error(...)
    raise  # 또는 명시적 sentinel
```

본 시스템 적용 (PR #125, 2026-05-29):
- ✅ `adapter/output/postgresql/stock_repository.py`
- ✅ `adapter/output/postgresql/collector_state_repository.py`
- ✅ `adapter/output/postgresql/backtest_repository.py`
- ✅ `adapter/output/postgresql/strategy_repository.py`
- ✅ `core/database.py` (PostgreSQL.get_connection)

---

## 3. 사고 시 체크리스트

prod 에러 alert 받으면 30초 내 확인:

1. **Cloud Run revision spec drift**:
   ```bash
   gcloud run services describe <svc> --region=... --format=json | jq '
     {mounts: [.spec.template.spec.containers[0].volumeMounts[].name],
      volumes: [.spec.template.spec.volumes[].name],
      mismatch: ([.spec.template.spec.containers[0].volumeMounts[].name] - [.spec.template.spec.volumes[].name])}'
   ```
   mismatch non-empty → 즉시 복구 (4번 단계)

2. **readiness 상태**:
   ```bash
   curl -fsS <SERVICE_URL>/actuator/health/readiness | jq
   ```
   DB/Mongo DOWN 이면 secret 마운트 또는 네트워크 문제

3. **Cloud Run 환경 변수 확인**:
   ```bash
   gcloud run services describe <svc> --format=yaml | grep -A 2 -iE "mongo|MONGO|secret"
   ```

4. **즉시 복구** (mismatch 시):
   ```bash
   gcloud run services update <svc> --region=asia-northeast3 \
     --update-secrets "/secrets/common/env=qjs-env-common:latest,/secrets/db-prod/env=qjs-env-db-prod:latest,/secrets/prod/env=qjs-env-prod:latest"
   ```
   → 새 revision 자동 생성 + traffic 100% 전환

---

## 4. 새 service 체크리스트 (R1-R7 적용)

| # | 항목 | 확인 |
|---|------|------|
| R1 | application-prod.yml 의 secret default 폐기 | ⬜ |
| R2 | entrypoint 의 prod 필수 secret 가드 | ⬜ |
| R3 | liveness ≠ readiness 분리 | ⬜ |
| R4 | deploy workflow 의 volume/mount drift 검증 | ⬜ |
| R5 | Deploy 후 실 API smoke test | ⬜ |
| R6 | Cloud Logging metric + Slack alert | ✅ (2026-05-29 gcloud CLI: metric 2 + alert 4 + uptime 5, Terraform 비관리. 루트 repo docs/infra/모니터링_CLI관리.md) |
| R7 | (선택) `--set-secrets` env-var 모드 | ⬜ |

---

## 5. 변경 이력

| 날짜 | 작성자 | 변경 |
|------|--------|------|
| 2026-05-22 | qjs-core MongoDB localhost 사고 (15:20 KST) 후속 | 초안 작성 + R1~R4 적용 PR (`fix/cloud-run-secret-failfast`) |

## 6. 참고

- [12-Factor App](https://12factor.net/config)
- [OWASP A05:2021 — Security Misconfiguration](https://owasp.org/Top10/A05_2021-Security_Misconfiguration/)
- [GCP Cloud Run — Use Secret Manager](https://cloud.google.com/run/docs/configuring/secrets)
- [Spring Boot — Kubernetes Probes](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.endpoints.kubernetes-probes)
- [SRE Book — Embracing Risk](https://sre.google/sre-book/embracing-risk/)
