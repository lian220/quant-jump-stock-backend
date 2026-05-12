# Composite Score는 세 축이 모두 fresh할 때만 산출한다

Composite Score(`0.3 × AI + 0.4 × Tech + 0.3 × Sentiment`)는 세 축(AI 예측 / 기술적 분석 / 감정 분석)의 데이터가 모두 fresh(7일 이내)할 때만 산출된다. 한 축이라도 stale 또는 부재면 그 종목은 추천 후보에서 제외된다.

기존 `data-engine/src/services/buy_criteria.py:115-137`의 동적 가중치 재분배(`_dynamic_weights`)는 빠진 축의 가중치를 나머지에 재배분하여 *부분 점수를 종합 점수로 둔갑*시켰다. 예: AI/감정 부재 시 `(w_ai, w_tech, w_sent) = (0, 1, 0)`이 발동되어 `composite = tech_score`(max 3.5)가 되고, 슬랙에 "종합점수 3.50, 신뢰도 100%, 강력 추천"으로 표시되었다. 2026-02-27 이후 75일간 사용자에게 거짓 추천을 노출시킨 직접 원인.

## Considered Options

- **유지(현행)**: 동적 가중치 재분배로 부분 점수도 채널 활동성을 유지 — 거짓 라벨링 문제 지속, 사용자 손실 위험.
- **제거(채택)**: 세 축 다 fresh일 때만 종합 점수, 통과 종목 0건이면 "추천 없음" 명시 메시지 — 채널 활동성은 떨어지지만 정직성 회복.

## Consequences

- Vertex AI 적재 실패 시 슬랙 채널이 침묵하지 않고 "추천 없음" 명시 → 운영 측에 자연스러운 재가동 압력.
- `_dynamic_weights` 함수 및 관련 부분 점수 코드 경로 제거 또는 dead code화.
- Core(Kotlin) `MAX_SCORE = 7.4` 와 data-engine의 max가 동일 의미로 일관 — 점수 스케일 이중 진실원 문제 부수 해소.
- 통과 종목 수가 sentiment_analysis 적재 수(매일 ~24개)에 의해 자연 상한. 35개 분석 종목 중 일부만 후보 통과 — 의도된 product 사실.
