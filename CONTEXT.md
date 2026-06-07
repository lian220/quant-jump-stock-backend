# CONTEXT — Backend 용어집 (Glossary)

> 이 문서는 **용어집**입니다. 구현 디테일·스펙·스크래치패드를 두지 않습니다.
> 점수 모델의 결정(되돌리기 어려운 트레이드오프)은 `docs/architecture/decisions/`(ADR)에 둡니다.

## 추천 점수 모델 (Scoring)

- **composite score (종합 점수)**: 한 종목의 추천 강도. **각 축을 0~1로 정규화한 뒤 weight 가중합 → 0~100** (재설계 방향, [[scoring-redesign]]). 기존 구조(축별 max가 3.5/10/10으로 제각각이라 만점이 7.4가 되고 weight가 실제 기여와 어긋나던 결함)를 대체. SSoT = `scoring_spec.yaml`.
  - 기존(폐기 예정): `0.3·ai + 0.4·tech + 0.3·sentiment`, 축별 max 제각각 → composite_max 7.4. tech가 weight 0.4(최대)인데 max 3.5라 실제 기여는 최소인 모순 존재.
- **axis (축)**: 점수를 구성하는 3개 입력 — `ai`(AI 가격예측), `tech`(기술적 지표), `sentiment`(뉴스 감성). 각 축은 먼저 0~1로 정규화된 뒤 weight로 가중합된다. **weight: tech 0.5 / ai 0.3 / sentiment 0.2** (신뢰성 순서 tech>ai>sentiment 반영, [[scoring-redesign]]). 펀더멘탈은 현재 축이 아니라 [[fundamental-badge]]로 분리(검증 후 4번째 축 승격 예정).
- **raw sentiment**: Alpha Vantage `ticker_sentiment_score`의 종목별 일간 평균(`average_sentiment_score`). 입력 범위는 라벨 정의상 **-0.35 ~ +0.35**(≤-0.35 Bearish, ≥+0.35 Bullish, 그 사이 neutral 대역). 산출: [[sentiment-linear-mapping]].
- **sentiment score**: raw sentiment를 0~max(현 10)로 매핑한 축 점수. 매핑 방식은 [[sentiment-linear-mapping]] 참조.
- **ai score**: AI 14일 예측 상승률(`rise_pct`)을 0~max(현 10)로 매핑한 축 점수. 선형 전체 매핑(`clip(0.5+rise_pct/40,0,1)×max`) — 하락 예측도 연속 점수를 갖는다. 단 **추천 여부**는 [[veto]]가 별도 결정(축 점수와 추천 가부는 분리된 관심사). 매핑 방식 [[ai-linear-mapping]].
- **결측(missing) vs 중립(neutral)**: "뉴스 없음(데이터 결측)"과 "뉴스가 중립적(raw≈0)"은 **다른 개념**. 결측은 해당 축 제외 대상, 중립은 중간 점수(선형 매핑 시 5점) 대상.
- **display score**: 사용자 화면에 보이는 0~100 점수. 재설계 후 composite 자체가 0~100이므로 **display score = composite score**(별도 스케일 없음). 기존의 Kotlin `PredictionService.normalize()` 이중 정규화는 폐기 대상([[scoring-redesign]]).
- **grade (등급)**: composite(0~100) 구간 라벨 S/A/B/C/D. 임계는 운영 분포 percentile 기반(잠정 S≥74/A≥68/B≥60/C≥54), `scoring_spec.yaml grades`. [[grade-thresholds]].
- **veto**: AI가 **강한 하락(rise_pct < -10%)** 예측 시 composite를 강제 0/grade D/추천제외하는 차단. -10%~0% 약한 하락은 veto 아니라 ai score 저점수로 흡수. ADR 0002(rise_pct<0 전량 차단)를 [[ai-veto-relaxation]]이 supersede. 단 AI 예측 정확도 사후검증(veto hit rate) 확보 전까진 veto 비활성 가능. 축 점수와 별개 — 축은 연속값, veto는 추천 가부.
- **fundamental badge (저평가 뱃지)**: yfinance 펀더멘탈(PER/PBR/ROE 등)로 산출하는 보조 표식. 점수 축이 아님(시간축 불일치·섹터 정규화 복잡성 때문). 섹터내 percentile 검증 후 4번째 점수 축으로 승격 예정. [[fundamental-badge]].
