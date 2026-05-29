# Incident — Vertex AI v24 stale package + PG pool stale (2026-05-20 ~ 2026-05-28)

> **유형**: prod 추천 마비 (silent degradation)
> **MTTD**: 8일 (사용자 직접 신고로 발견)
> **MTTR**: 2시간 (진단 + 수동 복구 + fix PR 작성)
> **재발 방지**: PR #125 (pool pre-ping) + PR #126 (ML package CI/CD)

---

## 1. 요약

| 항목 | 내용 |
|------|------|
| **발생 시점** | 2026-05-20 04:11 UTC (Vertex AI 첫 fail) |
| **발견 시점** | 2026-05-28 (사용자가 "월요일 이후 추천 안 도네" 신고) |
| **영향 기간** | 8일 (5/20 ~ 5/28) |
| **영향 범위** | prod 종목 추천 미산출. Slack 분석 채널 발송 차단 (pre-flight gate 정상 동작) |
| **데이터 손실** | 없음 (PG `prediction_results` row 미생성, 잘못된 데이터 저장 안 됨) |
| **복구** | 2026-05-28 ML package v25 수동 upload → Vertex AI 정상 → 추천 5개 발송 |

## 2. 사용자에게 보인 증상

```
잡스 [오전 8:31]
⚠ 추천 산출 보류 — 입력 데이터 결손
분석일 2026-05-27 추천 송출 보류
AI 예측 또는 분석 입력이 결손되어 사용자 채널 송출을 차단했습니다.
데이터 파이프라인 복구 후 재처리 필요.
- 분석일: 2026-05-27
- 총 분석: 0개
- AI 예측: 0개
- 감정 분석: 0개
- 기술 분석: 0개
```

→ Pre-flight gate (PR 115 의 보호) 정상 동작. 사용자 채널 송출 차단 + 운영자 채널 알림.

## 3. 타임라인

| 시점 (KST) | 사건 | 시스템 반응 |
|-----------|------|------------|
| 5/20 11:23 | `quantiq-ml-package-v24.tar.gz` GCS 업로드 (buggy 코드) | Vertex AI 이후 Job v24 사용 |
| 5/20 13:11 | evening-pipeline 발화 | Vertex AI Job **첫 FAIL** |
| 5/20 14:19 | commit `4ad49e8` (stock_predictions reader string query fix) main 머지 | **그러나 ML package 자동 빌드 메커니즘 없음** → v25 미업로드 |
| 5/21~5/27 | 매일 evening-pipeline 발화 | **7일 연속 Vertex AI FAILED** |
| 5/27 13:40 | evening-pipeline 발화 | PG connection pool stale → `server closed the connection` → `No active stocks found` → 전체 파이프라인 마비 |
| 5/27 14:20 | stock-recommendation cron | PG 0 row 저장 |
| 5/28 00:21 | 사용자 신고 | 진단 시작 |
| 5/28 09:21 | `POST /api/v1/ml/upload` 수동 호출 | v25 GCS 업로드 |
| 5/28 09:23 | evening-pipeline 수동 trigger | v25 적용 Vertex AI Job 시작 |
| 5/28 09:44 | Vertex AI Job **SUCCEEDED** | `stock_analysis_results` 5/27 채움 |
| 5/28 15:34 | stock-recommendation 수동 trigger | PG 37 row + Slack 5 추천 전송 ✅ |

## 4. 근본 원인 (2개 — AND 조건)

### 4.1 **Vertex AI ML package CI/CD 자동화 부재** (시발 원인)

| 컴포넌트 | main 머지 시 자동 반영? |
|----------|------------------------|
| Cloud Run Data Engine | ✅ `deploy-data-engine.yml` 자동 |
| Cloud Run Core | ✅ `deploy-core.yml` 자동 |
| **Vertex AI ML package (GCS tar.gz)** | ❌ **사람이 `POST /api/v1/ml/upload` 수동 호출 필요** |

5/20 의 fix commit (`4ad49e8`) 가 Data Engine 에는 즉시 반영됐지만, Vertex AI 가 사용하는 GCS package 는 5/28 까지 v24 (buggy) 그대로 사용.

→ **글로벌 표준 위반**: CI/CD 패턴은 "main 머지 = prod 전 컴포넌트 자동 반영" 일관 적용해야.

### 4.2 **PG connection pool stale 처리 부재** (장기 잠재 위험)

`psycopg2.pool.ThreadedConnectionPool` 은 Supabase 측 idle timeout 으로 끊긴 connection 을 인지 못함. Cloud Run min-instances=1 + 비활성 시간이 길어 connection idle → 끊김 → 다음 query 첫 사용 시:
```
psycopg2.DatabaseError: server closed the connection unexpectedly
```

5 PG repository 모두 동일 패턴. 5/27 사고 시 `stock_repository.get_active_stocks()` 가 이 에러 → `except Exception: return []` (silent swallow) → "No active stocks found" → 전체 파이프라인 마비.

→ **글로벌 표준 위반**: SRE "fail loudly". Pool 사용 시 pre-ping 또는 retry 패턴 표준.

## 5. 진단 과정 (어떻게 찾았나)

> 사용자가 "월요일 이후 추천 안 도네" 신고 → 4단 진단

### Step 1: 스케줄러 상태
- `gcloud scheduler jobs list` — 모든 cron ENABLED + last attempt 정상
- → 스케줄러 정상, Pub/Sub 메시지 발행 정상

### Step 2: Data Engine 처리 결과
- `gcloud logging read ... textPayload:"동기화 완료"` — 5/22 35종목 → 5/26 6종목 → 5/27 0종목
- 5/27 의 `server closed the connection unexpectedly` 118건 발견

### Step 3: Mongo 컬렉션 인벤토리
- `stock_recommendations` 5/22 정체 (실제로는 last write 5/27 정상)
- **`stock_analysis_results` 5/18 정체 (10일!)** — Vertex AI 미수집 의심

### Step 4: Vertex AI Job history
- `gcloud ai custom-jobs list --filter="createTime>2026-05-18"`
- **5/20 04:11 UTC 부터 7일 연속 FAILED** 확인
- Job log: "총 268,176 문서 저장 완료" (1단계 OK) → "0개 문서 조회" (2단계 fail) → exit 1
- commit history `git log` 로 5/19~5/20 변경점 발견 → `f075653` (string 통일) → `4ad49e8` (reader fix) 확인
- GCS `quantiq-ml-package-v24.tar.gz` mtime 2026-05-20T02:23 — **v25 없음** → 자동 빌드 안 됨

→ **사용자가 모른 채 7일** 동안 사고 진행. 운영자 알림 없었음 (pre-flight gate 가 추천 송출만 차단, alert 발송 안 함).

## 6. 영구 fix

### PR #125 — PG pool pre-ping (`fix #1`)

신규 헬퍼 `adapter/output/postgresql/_pool_util.py`:
```python
def get_validated_conn(pool, max_retries=2):
    for _ in range(max_retries):
        conn = pool.getconn()
        try:
            with conn.cursor() as cur:
                cur.execute("SELECT 1")
                cur.fetchone()
            return conn  # ✅ alive
        except Exception:
            pool.putconn(conn, close=True)  # dead 영구 폐기
    raise
```

5 repository + `core/database.py` 의 PostgreSQL 모두 적용. 정상 conn 비용 < 1ms.

### PR #126 — ML package CI/CD 자동화 (`fix #2`)

신규 workflow `.github/workflows/deploy-ml-package.yml`:
- trigger: main push 에 `predict_optimized.py` 또는 `storage_service.py` 변경
- step:
  1. Cloud Run 의 commit-sha 가 현재 push 와 일치할 때까지 polling (deploy-data-engine.yml race 방지)
  2. `POST /api/v1/ml/upload` (Cloud Run identity token)
  3. response `success: true` + version 검증
  4. GCS 의 `predict_optimized-latest.tar.gz` 존재 확인

→ main 머지 = 자동 v+1 빌드/GCS upload. 8일 지연 영구 방지.

## 7. 글로벌 표준 매핑

| 표준 | 원칙 | 본 사고 위반 | fix |
|------|------|-------------|------|
| **SRE** | "Fail loudly, never silently" | `except Exception: return []` 가 진짜 에러 숨김 + 7일 사용자만 인지 | PR #125 pre-ping (`server closed` 시 자동 재시도, raise) |
| **12-Factor X** | "Dev/prod parity" — 모든 환경 동일 반영 | main 머지 후 Vertex AI 만 8일 지연 | PR #126 CI/CD 일관 적용 |
| **CI/CD best practice** | "main = single source of truth, auto-deploy" | ML package 만 수동 trigger | PR #126 자동화 |
| **Cloud Native** | "Pool 사용 시 pre-ping or keepalive" | psycopg2 pool 사용 그대로 (검증 없이) | PR #125 |
| **MTTD < 5분** (사고 자동 감지) | Cloud Monitoring alert 통합 | 사용자 직접 신고 → MTTD 8일 | follow-up: Cloud Monitoring alert webhook 설정 (PR #36 코드 머지됨) |

## 8. Cloud_Run_운영_규칙.md 에 추가할 규칙

### 🔴 R8. CI/CD 일관성 — 모든 prod 컴포넌트 main push 자동 반영

main 머지 시점에 Cloud Run + Vertex AI ML package + GCS asset 등 **prod 에 영향 주는 모든 컴포넌트가 동일 commit 으로 갱신**되어야 함. 부분 자동화는 silent drift 위험.

검증:
```bash
# 각 컴포넌트의 현재 deployed sha 또는 mtime 확인
gcloud run services describe <svc> --format='value(metadata.labels.commit-sha)'
gsutil ls -l gs://.../latest.tar.gz  # mtime vs latest commit
```

### 🔴 R9. PG pool pre-ping 강제

`psycopg2.pool.ThreadedConnectionPool` 또는 동등 pool 사용 시 **getconn() 후 SELECT 1 ping 필수**. `_pool_util.get_validated_conn()` 공통 헬퍼 사용.

빈약한 except 절도 금지:
```python
# ❌ 금지
except Exception:
    return []

# ✅ 권장 (sentinel 만 OK 한 경우 명시)
except specific_error:
    logger.error(...)
    raise  # 또는 명시적 sentinel
```

## 9. 학습 (Lessons Learned)

1. **Silent fallback 은 적입니다** — `return []` 같은 빈약 swallow 가 7일 마비. 가장 빨리 fix 할 부분.
2. **자동화 부분 적용은 부재보다 위험할 수 있음** — Data Engine 만 자동 deploy 하니 사용자는 "main 머지 = 끝" 으로 오해. ML package 도 자동화하면 "main = 진짜 truth" 회복.
3. **Mongo 컬렉션 이름이 misleading** (`stock_recommendations` = 사실 기술 분석 결과) — PR #124 의 dead repo 정리로 일부 해소. 더 명확한 이름 검토 권장.
4. **운영자 자동 alert 부재가 MTTD 8일** — Cloud Monitoring 의 PR #36 (mongo_localhost_fallback / cloud_run_startup_failure / uptime check) 운영자 webhook 설정만 하면 분 단위 MTTD. **운영자 액션 필수**.
5. **사고 자체보다 진단이 더 오래 걸림** — Job stdout 빈 채로, error 메시지 generic ("exit 1"). 구조화 로깅 + 진단 runbook 권장.

## 10. Follow-up (별도 PR / 운영자 액션)

- 🟡 **Cloud Monitoring alert webhook 설정** (운영자 1회) — PR #36 코드 머지됨, `terraform.tfvars` 의 `monitoring_slack_webhook_url` 만 채우면 활성
- 🟢 **ML package 빌드 자동화 후속 검증** (PR #126 의 첫 자연 발화 시)
- 🟢 **Cloud_Run_운영_규칙.md 에 R8, R9 추가** (별도 docs PR)

## 11. 참고

- PR #125: https://github.com/lian220/quant-jump-stock-backend/pull/125
- PR #126: https://github.com/lian220/quant-jump-stock-backend/pull/126
- PR #36 (monorepo, Cloud Monitoring alert): https://github.com/lian220/quant-jump-stock/pull/36
- 직전 사고 회고: `docs/infra/Cloud_Run_운영_규칙.md` (2026-05-22 qjs-core MongoDB localhost fallback)

## 12. 변경 이력

| 날짜 | 작성자 | 변경 |
|------|--------|------|
| 2026-05-29 | 사고 회고 작성 | 초안 |
