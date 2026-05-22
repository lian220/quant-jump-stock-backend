# Runbook: 점수 모델 prod regression 실행

> 최초 작성: 2026-05-21 (PR 1: 점수 모델 SSoT 중앙화)
> 대상 스크립트: `quant-jump-stock-data-engine/scripts/scoring_regression_prod.py`

## 목적

`scoring_spec.yaml` 또는 `ScoringPolicy` 산식이 바뀌는 PR을 머지하기 전, 운영 PostgreSQL `prediction_results` 의 최근 N일치 추천 데이터에 대해 **신 산식**으로 composite_score를 재계산하여 **저장된 구 값과의 drift**를 정량화한다. drift JSON을 PR description에 첨부하여 리뷰어가 변경 영향을 판단할 수 있게 한다.

## 언제 실행

| 트리거 | 시점 |
|--------|------|
| 신규 PR이 `scoring_spec.yaml` 수정 | 머지 직전 1회 |
| 신규 PR이 `ScoringPolicy` 또는 4개 호출처(`sync_service`, `buy_criteria`, `slack_notifier`, `comprehensive_report`) 수정 | 머지 직전 1회 |
| Phase 1A 보안 사전 작업 종료 후 처음 spec 변경 | 머지 직전 1회 |
| 정기 점검 | 분기 1회 권장 (drift가 누적되지 않았는지) |

> ⚠ Agent 자동 실행 금지. 운영자(사람)가 PR 검토 직전 1회 실행. read-only이지만 prod 접근의 책임 분리 원칙.

## 사전 준비

### 1. read-only PostgreSQL 자격증명 확보

**⚠ 2026-05-21 변경**: composite_score 는 PostgreSQL `prediction_results` 테이블 (sync_service 가 write) 에 저장됨. **MongoDB 가 아님**. 스크립트는 PG SELECT 만 수행.

| 출처 | 위치 |
|------|------|
| **Supabase Dashboard** | Project → Database → Roles → `readonly` (또는 운영팀이 별도 생성한 RO role) |
| **GCP Secret Manager** | `qjs-pg-readonly-credentials` (운영팀 키 이름 — 콘솔 확인) |
| **본인 노트북** | `~/.config/qjs/pg-readonly` (개인 보관, 절대 repo 커밋 금지) |

**🚨 절대 사용 금지**: `.env.db.prod` 의 `DB_USER`/`DB_PASSWORD` (대개 write 권한 포함). 본 스크립트는 read-only를 강제하지 않으므로 계정 자체로 권한을 분리한다.

### 2. spec 파일 경로 확인

PR 브랜치의 `scoring_spec.yaml`을 검증할 것이므로:

```bash
git checkout refactor/scoring-policy-ssot-pr1  # 또는 검증할 PR 브랜치
realpath quant-jump-stock-backend/scoring_spec.yaml
# 예: /Users/sfn1/Desktop/workSpace.nosync/quant-jump-stock/quant-jump-stock-backend/scoring_spec.yaml
```

### 3. Poetry 의존성

```bash
cd quant-jump-stock-backend/quant-jump-stock-data-engine
poetry install   # pymongo + ScoringPolicy 의존성 보장
```

## 실행 명령

```bash
cd quant-jump-stock-backend/quant-jump-stock-data-engine

PG_HOST_RO=<supabase-host> \
PG_PORT_RO=5432 \
PG_USER_RO=<readonly-user> \
PG_PASSWORD_RO=<password> \
PG_NAME_RO=<dbname> \
SCORING_SPEC_PATH=/Users/<you>/.../quant-jump-stock-backend/scoring_spec.yaml \
  poetry run python scripts/scoring_regression_prod.py --days 7 --threshold 0.01
```

### CLI 옵션

| 옵션 | 기본값 | 설명 |
|------|--------|------|
| `--days N` | 7 | 스캔할 최근 일수. 정기 점검 시 30+ 권장 |
| `--threshold T` | 0.01 | composite 절대값 차이 허용치. drift 판정 임계 |
| `--output PATH` | `regression_unexpected.json` | drift 기록 JSON 출력 경로 |
| `--limit N` | (전체) | 디버깅용 처리 건수 제한 |

### Exit 코드

| 코드 | 의미 | 조치 |
|------|------|------|
| **0** | drift 없음 또는 대상 0건 | PR 머지 진행 가능 |
| **1** | drift 있음 — JSON 첨부됨 | 아래 "결과 해석" 참조 |
| **2** | 환경변수 오류 | `PG_*_RO` 설정 확인 |

## 결과 해석

### Exit 1 → drift JSON 검토

`regression_unexpected.json` 구조:

```json
{
  "spec_version": "1.0.0",
  "composite_max": 7.4,
  "cutoff_date": "2026-05-14",
  "threshold": 0.01,
  "total_processed": 132,
  "drift_count": 8,
  "grade_changes": 2,
  "drifts": [
    {
      "ticker": "AAPL",
      "stock_name": "Apple Inc.",
      "date": "2026-05-15",
      "old_composite": 4.32,
      "new_composite": 4.85,
      "delta": 0.53,
      "old_grade": "B",
      "new_grade": "A",
      "new_label": "RECOMMEND",
      "grade_changed": true,
      "missing_axes": []
    }
  ]
}
```

`drifts` 는 절대 delta 내림차순. 상위 종목부터 검토.

### 머지 게이트 기준

| drift 양상 | 조치 |
|-----------|------|
| `drift_count == 0` | ✅ 즉시 머지 |
| 모든 drift `< 0.1` & grade/label 보존 | ✅ JSON을 PR description 첨부 + 머지 |
| 일부 drift `≥ 0.1` 또는 grade/label 변경 있음 | ⚠ **머지 금지**. plan에 명시된 의도된 변화인지 검증. 아래 "PR별 알려진 drift" 참조 |
| 모든 drift `≥ 0.5` & 다수 종목 grade 점프 | 🚨 산식 버그 가능성. **slack `#data-engine` 채널 합의 필수** |

### PR별 알려진 drift (intentional)

| PR | 변경 | 예상 drift |
|----|------|-----------|
| **PR 1** | `comprehensive_report` ad-hoc rescale 제거 + `ROUND_HALF_UP` 통일 | 일부 종목 confidence ±0.32 가능 (per-axis 비균일 rescale 제거). grade/label 변경 가능 |
| PR 2 | PostgreSQL `prediction_results` 컬럼 추가 | drift 0 (저장 계약만 변경) |
| PR 3 | AI source disagreement + negative AI veto | 음수 rise_probability 종목들의 grade 강제 D로 변경 |
| PR 4 | sentiment N/A 재분배 | sentiment 누락 종목들의 composite 상승 |
| PR 5 | VIX 거시 gate | VIX 임계 초과 시기의 추천 강제 NONE |

PR 1 실행 시 drift가 plan과 일치하면 머지 진행. 그 외 패턴은 의심하고 합의.

## 트러블슈팅

| 증상 | 원인 / 해결 |
|------|------------|
| `필수 환경변수 누락` | `PG_HOST_RO`/`PG_USER_RO`/`PG_PASSWORD_RO`/`PG_NAME_RO` 중 누락. shell 에서 `env \| grep PG_` 확인 |
| `SpecNotFoundError` | `SCORING_SPEC_PATH` 절대경로 확인. backend repo 루트의 `scoring_spec.yaml` |
| `psycopg2` 미설치 | `poetry install` 재실행. Python 3.11 환경인지 확인 |
| Supabase 연결 실패 (`psycopg2.OperationalError`) | (a) VPN/회사망 IP가 Supabase allow-list 에 있는지 (b) `PG_PORT_RO=5432` 명시 (c) SSL 옵션 필요 시 URI 방식 (`postgresql://...?sslmode=require`) |
| 첫 실행에서 `regression 대상 0건` | (a) `--days` 늘려보기 (default 7 → 30) (b) `prediction_results.is_recommended=TRUE` 필터에 걸리는 데이터가 N일치 안에 없음. sync_service 실행 시점 확인 |

## 보안 점검

- ✅ 스크립트는 `SELECT` 만 사용 (`INSERT`/`UPDATE`/`DELETE` 없음 — `grep` 으로 확인)
- ✅ JSON 출력에 URI/credential 포함 안 함 (스크립트 검토로 확인)
- ✅ `regression_unexpected.json`은 ticker/date/composite/grade 만 — 비공개 가격/매매 정보 없음
- ⚠ 그래도 JSON에 운영 종목 리스트가 노출되므로 **PR description 첨부 후 PR 검토 종료 시 GitHub 첨부 파일 삭제 권장** (또는 GitHub PR description 외 비공개 채널에서 공유)

## 변경 이력

| 날짜 | 작성자 | 변경 |
|------|--------|------|
| 2026-05-21 | Claude (Opus 4.7) | PR 1 머지 직전 작성. 운영자가 직접 실행하는 책임 분리 명시 |
| 2026-05-21 | Claude (Opus 4.7) | **MongoDB → PostgreSQL prediction_results 로 데이터 소스 변경**. plan 작성 시점에 composite 저장처를 mongo 로 잘못 가정. PR 1 머지 직전 실제 prod E2E 검증 중 발견 후 정정. 환경변수 `MONGODB_URI_RO` → `PG_*_RO` (5개). |
