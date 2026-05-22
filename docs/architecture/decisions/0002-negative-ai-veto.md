# ADR 0002 — Negative AI Veto + Source Disagreement Policy

> 작성: 2026-05-22 (PR 3a)
> 상태: **Proposed** (PR 3b 머지 시 Accepted)
> 관련 PR: lian220/quant-jump-stock-backend#3a (schema), #3b (negative veto), #3c (disagreement)
> 관련 plan: `docs/plans/점수모델_중앙화_구현계획.md` §1 PR 분리

## 배경

PR 1 (commit `dbc1abf`) + PR 2 (commit `d5d5e9e`) 가 산식 SSoT 중앙화 + 라벨/저장 가시화 정리. **결과 보존**. 첫 의도된 행동 변경이 PR 3 시리즈.

### 사건 원인

2026-05-20 사용자 의문: composite **2.40/7.4** 인 종목이 "강력 추천" + "달성도 87%" 로 표시 → 산식과 라벨이 불일치하는 모순 출력으로 시스템 신뢰 손상.

PR 1+2 가 라벨/저장 계약 정합성 회복. PR 3 는 **산식이 모순을 만들지 않도록 정직성 게이트** 추가.

### 결정 사안

| 항목 | 옵션 A (강) | 옵션 B (보수) | 선택 |
|------|------|------|------|
| 음수 AI 예측 처리 | veto (composite=0) | 점수 -50% | **A** |
| AI 소스 disagreement 처리 | veto | 신호 약화 (warning) | **B** (당분간) |
| `min_composite_score` | 3.0 상향 | 2.0 유지 | **2.0 유지** (분포 본 후 결정) |
| spec_version | 1.1.0 | 같음 | **1.1.0** (behavior 변경 첫 PR) |

## Negative AI Veto (PR 3b 에서 적용)

### 결정

`scoring_spec.yaml`:
```yaml
axes:
  ai:
    negative_policy: veto   # was: zero
```

rise_pct < 0 일 때:
- `Score.veto_reasons += ("ai_negative",)`
- `composite_score = 0` (강제)
- `grade = "D"`
- `recommendation_label = "NONE"`
- `is_recommended = FALSE`

### 근거 (Taleb 비대칭 위험)

- **False positive 비용 (현재)**: "AI: -9.5%, 시스템: 강력 추천" 같은 모순 → 사용자 신뢰 영구 손상. 회수 불가.
- **False negative 비용 (PR 3b 후)**: AI 가 음수 예측한 종목이 실제론 반등 → 사용자가 매수 기회 놓침. 측정 불가능 (가설적).
- **비대칭**: 가시적 신뢰 손상 vs 비가시적 기회 비용. Taleb 의 *via negativa* — 명백히 나쁜 추천 제거가 영리한 추천 추가보다 우선.

### 예상 영향 (prod 데이터 정량 분석)

2026-05-15 ~ 5/20 데이터에서 `rise_pct < 0` 비율:

| 날짜 | 음수 AI 비율 | 영향 받는 종목 수 |
|------|-----|-----|
| 2026-05-15 | 33% | 12/36 |
| 2026-05-18 | 56% | 20/36 |
| 2026-05-19 | 39% | 14/36 |
| 2026-05-20 | 53% | 19/36 |

**예상**: 매일 5개 추천 → **2-4개 평균, 일부 zero-rec day 발생**.

### Distribution 가드 (수용 한계)

| 지표 | 정상 범위 | 경계 (검토) | rollback trigger |
|-----|-----------|---------|--------|
| 일평균 추천 수 | 2-5개 | 1-2개 | 5일 연속 0개 |
| Zero-rec day 비율 (7일) | < 20% | 20-40% | **> 40%** |
| Veto 부여 비율 | < 60% | 60-80% | > 80% (AI 모델 자체 의심) |

위 trigger 발동 시 `negative_policy: zero` 로 yaml flip + 재배포 (PR 3a 가 schema 보존하므로 코드 rollback 불필요).

## AI Source Disagreement (PR 3c 보류)

### 결정 (전문가 합의)

**현재 두 소스 (`stock_analysis_results`, `stock_predictions`) 는 같은 시계가 아니다**:

| 소스 | 모델 | 시계 |
|------|------|------|
| stock_analysis_results | Vertex AI 분석 파이프라인 | 단기 (수일) |
| stock_predictions | predict_optimized.py 별도 모델 | **14일 forecast_horizon** |

prod 데이터에서 두 소스 **부호 불일치는 44~47% (정상)**. 단순 sign 비교로 veto 적용 시 false positive 폭주.

### PR 3c 사전 조건

데이터 엔진 owner 와 다음 합의 후 PR 3c 진행:
1. 두 소스가 같은 시계로 통일된 prediction 인지 (또는 별도 모델 인지) **schema contract** 명시
2. 같은 시계라면 ± epsilon band 정의 (예: 5% 이내 부호 다름은 noise 로 간주)
3. 다른 시계라면 disagreement detection 폐기 (대신 두 소스 합의 점수 가산점 정도)

### PR 3a 에서 미리 준비

- `Score.warnings` 필드는 이미 존재 (PR 1 도입)
- PG 컬럼 `warnings TEXT[]` 추가 (V70)
- PR 3c 머지 시 spec 변경 + Score 채움 + Slack 표시만 추가

## min_composite_score (별도 PR)

- PR 3b 머지 후 **2 주** prod 분포 관찰
- 평균 추천 수 + zero-rec day 비율이 ADR 가드 안에 들어오면 2.0 → 3.0 별도 PR
- 들어오지 않으면 negative_policy 자체 재검토 (zero 로 rollback 또는 다른 정책)

## 사용자 공지 (PR 3b 머지 직전)

Slack 분석 채널에 **pinned 공지** 1회:

```
📢 추천 알고리즘 개선 안내 (2026-05-XX 적용)

AI 모델이 음수 예측한 종목은 **추천 대상에서 자동 제외**됩니다.

이전: AI 음수 예측이어도 기술 분석/감정 분석 점수가 좋으면 강력 추천 표시
변경: AI 음수 예측 = 추천 차단

이유:
- 추천과 AI 신호의 정합성 향상
- "AI 하락 예측 + 강력 추천" 의 모순 출력 방지

영향:
- 일평균 추천 수: 5개 → 2-4개 평균
- 추천 0개 인 날 발생 가능 (시장 약세 시)
- 추천이 0개 라면: "오늘은 AI 신호 신뢰도 부족 — 추천 없음" 안내 메시지 발송

문의: #data-engine 채널
```

Zero-rec day 메시지 템플릿 (`SlackNotifier.notify_recommendation_data_gap` 확장):
```
⚠ 추천 없음 — 2026-XX-XX
오늘 분석 결과:
  - 총 분석 35개
  - 음수 AI 예측으로 차단 (NN개)
  - 통과 기준 미달 (MM개)
  
시장이 정상이고 신호가 부족할 뿐입니다.
파이프라인 결함이 아닙니다.
```

## Rollback 절차

1. yaml flip: `scoring_spec.yaml` `negative_policy: veto → zero`
2. spec_version 다시 1.0.0 또는 1.1.0-rollback (audit trail 보존)
3. `git push` → CI 자동 재배포 (~10분)
4. PG schema 는 그대로 유지 (PR 3a 의 `veto_reasons` 컬럼은 NULL 로 자연 회복)
5. Slack 공지: "산식 복귀 안내"

## 검증 절차 (PR 3b 머지 전 필수)

1. **Dry-run**: 지난 30일 prod 데이터에 PR 3b 산식 적용 → ADR distribution 가드 안에 들어오는지 확인
2. **Golden CSV 재생성**: spec_version 1.1.0 으로 frozen 50 케이스
3. **prod regression**: PR 3a 적용 후 (veto_reasons 컬럼 비어있을 때) drift 0 확인
4. **운영자 합의 + ADR Accepted 처리**

## 참고

- PR 1 plan: `docs/plans/점수모델_중앙화_구현계획.md`
- 3 전문가 검토 보고: 본 ADR §결정 사안 결정
- Taleb *Antifragile* (2012) — via negativa 원칙
- Christensen *JTBD* — 추천 코히어런스가 사용자 기대 job

## 결정 이력

| 날짜 | 작성자 | 변경 |
|------|--------|------|
| 2026-05-22 | PR 3a | 초안 작성 — Proposed |
| TBD | PR 3b 머지 | Accepted 처리 |
