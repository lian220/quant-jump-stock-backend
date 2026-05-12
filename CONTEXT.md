# Quant Jump Stock — Backend Context

이 파일은 종목 추천/예측 도메인의 핵심 개념과 결정을 기록합니다. 코드와 도메인 언어가 갈리는 경계에서 진실원으로 동작합니다. 용어 충돌이 발견되면 즉시 이 파일에 박고, 코드 측 명명도 점진적으로 정렬합니다.

## Glossary

### Data Freshness / Staleness

종목 추천에 사용되는 AI 예측 데이터의 신선도. AI 분석 결과(MongoDB `stock_predictions`, `stock_analysis_results`)의 적재일을 분석 요청일과 비교하여 측정.

- **stale**: AI 데이터 적재일이 분석 요청일로부터 **7일을 초과**한 경우.
  - 7일 임계는 AI 모델의 prediction horizon(14일)의 절반을 보수적으로 잡은 값.
  - 코드의 `_MAX_PREDICTION_LOOKBACK_DAYS = 7` (data-engine `comprehensive_report.py:18`)와 일관.
- **stale 종목**: 해당 종목의 AI 데이터가 stale 상태인 종목. 페이지 추천 응답(`/api/predictions/buy-signals`) `data` 배열에서 **제외**됨.
- **freshness 메타데이터**: API 응답 스키마에 포함되는 필드. 클라이언트가 데이터 신선도를 즉시 인지하도록.
  - 예: `ai_data_age_days`, `freshness="fresh"|"stale"`

**도메인 의미**: 2026-02-27 ~ 2026-05-12 동안 75일째 AI 적재가 정지되었음에도 누구도 알아채지 못한 사고에서 보듯, freshness는 단순 indicator가 아니라 추천 응답의 *fitness criterion*. AI 적재 자체가 실패해도 시스템은 옛 데이터를 새 라벨로 재방송할 수 있다 (`sync_service.py` fallback 동작).

### Composite Score (종합 점수)

추천 종목의 정량 평가 지표. **AI 예측 + 기술적 분석 + 감정 분석 세 축이 모두 fresh할 때만 산출**된다.

- 산식: `composite = 0.3 × ai + 0.4 × tech + 0.3 × sentiment`
- 점수 범위: 0 ~ 7.4 (`0.3×10 + 0.4×3.5 + 0.3×10`)
- 등급: S ≥ 6.0 / A ≥ 4.5 / B ≥ 3.0 / C ≥ 1.5 / D < 1.5 (`RecommendationCriteria.kt`)

**진정성 규칙**: 종합 점수는 *세 축이 다 있을 때만* 종합 점수다. 한 축이라도 stale/부재면 그 종목은 종합 점수 추천 후보에서 제외된다.

**과거 결함 (2026-02-27 이후)**: `data-engine/src/services/buy_criteria.py`의 동적 가중치 재분배(`_dynamic_weights`)가 빠진 축의 가중치를 나머지에 재배분하여 *부분 점수를 종합 점수로 둔갑*시켰다. 예: AI/감정 부재 시 `(w_ai, w_tech, w_sent) = (0, 1, 0)`이 발동되어 `composite = tech_score` (max 3.5)가 되고, 슬랙 메시지에 "종합점수 3.50, 신뢰도 100%, 강력 추천"으로 표시됨. 이 로직은 도메인 규칙 위반이며, 진정성 규칙 도입 후 제거 또는 사용 중단된다.

### Partial Score (부분 점수)

세 축 중 일부만 가용한 상태에서 산출되는 점수. **추천 후보로 사용되지 않으며, 슬랙 메시지에 노출되지 않는다.** 데이터 freshness 모니터링 또는 디버깅 용도로만 보존.

### Empty Recommendation Message (추천 없음 알림)

세 축이 모두 fresh한 종목이 0건일 때 슬랙 채널(`SLACK_CHANNEL_ANALYSIS`, 사용자/고객도 받는 채널)에 발송되는 명시적 알림. 침묵은 허용되지 않는다 — *결과를 모르는 것*과 *결과가 없는 것*은 다르며, 둘 다 사용자에게 정직하게 전달되어야 한다.

- **임계**: 통과 종목 0건일 때만 발동. 1건이라도 진짜 종합 점수가 산출되면 그대로 발송 (몇 건이든).
- **톤**: 사용자 친화적. 예: "오늘은 종합 점수 기준 추천 종목이 없습니다. 분석 데이터를 보강 중입니다."
- **운영 채널 별도 알림**: 같은 사건에 대해 운영자 채널(`SLACK_CHANNEL_ERROR` 또는 `SLACK_CHANNEL_SCHEDULER`)에 별도로 직설적 health check 메시지 발송 가능 (예: `AI 데이터 stale 75일, runbook URL`). 운영자 채널과 사용자 채널은 같은 사건을 두 톤으로 전달.

**의도된 부수 효과**: AI 적재 실패 시 사용자 채널에 즉시 "추천 없음" 노출 → 사용자가 알아채면 운영자에게 자연스러운 재가동 압력 작용. 운영자 채널엔 더 직설적인 진단 정보 동시 발송 → 운영 대응 가속.
