# ADR 0006 — 추천 점수 모델 재설계 (0~100 정규화 가중합)

> 작성: 2026-06-07
> **Proposed** (TDD 구현 + 운영 분포 재검증 후 Accepted 전환)
> Supersedes: 일부 [ADR 0002](./0002-negative-ai-veto.md) (negative veto 정책)
> 관련: `CONTEXT.md` 용어집, `scoring_spec.yaml`, 전문가 패널 검토(strategy/db-performance/pm-planner/backend-architect)

## 배경

사용자 신고: "추천 점수가 다 낮다(전 종목 B·C, 최고 종목도 52/100), 모바일·PC 점수 기준이 다르다."

코드 + 운영 데이터(alphafoundry.app, 2026-05-20~28, 189 종목-일) 분석으로 다음 결함을 확정:

### 발견된 결함

1. **만점이 도달 불가능한 이론값**: composite = `0.3·ai + 0.4·tech + 0.3·sentiment`, 축별 max가 ai=10/tech=3.5/sent=10으로 제각각 → composite_max=7.4. 실효 상한은 ~5.7. 최고 종목도 display 52점.
2. **weight가 실제 기여와 모순**: tech가 weight 0.4(최대)인데 max 3.5라 실제 composite 기여는 1.4(최소). "기술이 가장 중요"라 선언해놓고 실제론 가장 적게 반영.
3. **이중 스케일 버그**: 화면 숫자=`compositeScoreDisplay`(0~100), 색/등급=`getScoreGrade(원점수 0~7.4)`. → "52점인데 녹색(우수)", "71점인데 노랑(중간)" 모순. 모바일/PC 레이아웃 차이로 사용자가 "기준이 다르다"고 인지.
4. **0.5 컷오프로 절반이 0점**: ai `normalized<0.5`(하락예측)·sentiment `raw≤0`(중립~부정)이 전부 0점. 운영 ai 21/42·sentiment 18/42가 0점.
5. **sentiment 입력-분모 스케일 불일치**: Alpha Vantage `ticker_sentiment_score` 실범위 -0.35~+0.35인데 분모 10 → 최선의 긍정 뉴스도 3.5점이 천장.
6. **SSoT 드리프트**: `scoring_spec.yaml`(Python)과 `RecommendationCriteria.kt`(Kotlin)에 max/weight/임계 중복 정의. 일부 값 분기(`PredictionService.kt:96` `*7.5` vs yaml 7.4, `MIN_RECOMMEND_SCORE=5.18` vs yaml 2.0).

## 결정

### 1. 구조: 축별 0~1 정규화 → weight 가중합 → 0~100

```
composite_100 = ( w_tech·(tech/3.5) + w_ai·(ai/10) + w_sent·(sent/10) ) × 100
```

- 각 축을 먼저 0~1로 정규화 후 weight 곱. weight 합 = 1.0 → composite는 자연히 0~1 → ×100 = 0~100.
- composite_max=7.4, 별도 display 정규화, getScoreGrade 이중 스케일 **모두 폐기**. display score = composite score(단일 척도).

### 2. weight: tech 0.5 / ai 0.3 / sentiment 0.2

신뢰성 순서(tech > ai > sentiment) 반영. 근거:
- **tech**: 가격 기반, 결측 거의 없음, 결정론적 → 가장 안정적 신호. 최고 weight.
- **ai**: 예측 정확도 사후검증 루프 부재(예측가 vs 실제가 비교 안 함) → 검증 전까지 0.3으로 제한. 검증 후 IC 양수면 유지, 음/0이면 추가 강등.
- **sentiment**: 커버리지 42% 결측 + 입력 실범위 좁음 → 0.2로 최소.

### 3. 축별 선형 매핑 (0.5/0 컷오프 제거)

- **sentiment**: `clip(raw, -0.35, 0.35)` → `(clip+0.35)/0.7` (0~1). 중립(raw 0)=0.5. 결측은 별도 처리(아래 4).
- **ai**: `clip(0.5 + rise_pct/40, 0, 1)` (0~1). 하락 예측도 연속 저점수.

### 4. 결측 처리: 0점 부여 금지, 축 제외 후 weight 재정규화

뉴스/AI가 결측인 종목은 해당 축을 0점으로 깔지 않고 **축을 빼고 남은 weight를 재정규화**. (예: sentiment 결측 → tech/ai weight를 0.5/0.3 → 0.625/0.375). selection bias(소형주가 뉴스 없어서 저평가되는 문제) 제거.
- "결측"과 "중립(raw≈0)"은 구분: 결측=축 제외, 중립=0.5점.

### 5. veto 완화 (ADR 0002 supersede)

- **강한 하락(rise_pct < -10%)** 에만 veto(composite=0/grade D/추천제외).
- -10%~0% 약한 하락은 ai score 저점수로 자연 흡수(veto 아님).
- 단 AI 예측 정확도 백테스트(veto hit rate ≥ 60%) 검증 전까지는 veto 비활성(전량 페널티) 옵션 유지.
- 근거: 새 구조에서 ai축 선형 매핑이 ADR 0002의 coherence 게이트 역할을 대체. "tech 만점+뉴스 강긍정인데 AI -3%"인 종목을 전멸시키는 건 가중합 철학에 반함.

### 6. 등급 임계: 0~100 percentile 기반

운영 분포 percentile로 동적 산출(코드 하드코딩 금지). 잠정값(W1 분포 기준): **S≥74 / A≥68 / B≥60 / C≥54 / D<54**. weight·매핑 확정 후 실제 `rise_pct` 연속값 분포로 재보정.

### 7. 펀더멘탈: 이번엔 "저평가 뱃지", 검증 후 4축 승격

yfinance 펀더멘탈(PER/PBR/ROE)은 이번 PR에서 점수 축이 아니라 보조 뱃지로 분리. 시간축 불일치(일별 신호 vs 분기 가치)·섹터 정규화(percentile 2-pass)·결측 처리 복잡성 때문. 섹터내 percentile 데이터 품질 검증 후 다음 PR에서 4번째 축 승격.

### 8. SSoT 단일화

`scoring_spec.yaml`을 단일 SSoT로. Kotlin이 런타임 로드(또는 0~100 단일 스케일 전환으로 Kotlin display 정규화 자체를 제거). `PredictionService.kt:96` `*7.5` 매직넘버, `MIN_RECOMMEND_SCORE=5.18` 드리프트 정합.

## 대안 (기각)

| 대안 | 기각 이유 |
|------|----------|
| 등급 임계만 percentile 재조정 (구조 유지) | display 이중 스케일·weight 모순·0점 쏠림 미해결 |
| 분모만 교정 (sentiment max 10→5) | weight 모순·이중 스케일 미해결, 부분 처방 |
| 펀더멘탈 즉시 4축 추가 | 시간축 불일치·섹터 2-pass·결측 처리 리스크 → 2단계 진화로 |
| veto 완전 제거 | "AI 하락인데 추천" 리스크 → 강한 하락은 차단 유지 |

## 결과 (Consequences)

- **시계열 단절**: 과거 점수와 값이 완전히 달라짐(어제 52 → 오늘 70). BETA 뱃지로 커뮤니케이션, `score_version` 구분 필요.
- **재계산 범위**: Python `scoring_policy.py`(산식) + `scoring_spec.yaml`(SSoT) + Kotlin display 로직 + FE `predictions.ts`(getScoreGrade/TIER) 동시 변경. golden/property/regression 테스트 재작성.
- **신규 KPI**: veto hit rate(veto된 종목 중 실제 하락 비율) 추적 신설 → veto 임계·재활성 데이터 기반 결정.
- **검증 의존**: 등급 임계는 운영 분포로 재보정 전제. 코드 상수 하드코딩 금지.
