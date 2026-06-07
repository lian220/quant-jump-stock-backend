# ADR 0006 — 추천 점수 모델 재설계 (0~100 정규화 가중합)

> 작성: 2026-06-07 · 상태: **Proposed** (TDD 구현 + 운영 분포 재검증 후 Accepted)
> Supersedes: [ADR 0002](./0002-negative-ai-veto.md)의 negative veto 정책
> 검토: 전문가 패널 4인 (strategy / db-performance / pm-planner / backend-architect)
>
> **이 문서 하나로 점수 모델 재설계의 배경·결정·이유·구현계획을 모두 담는다.** (작업 단위가 하나라 문서를 쪼개지 않음)

---

## 1. 문제 — 사용자 신고

**"추천 점수가 다 낮다(전 종목 B·C, 최고 종목도 52/100). 모바일·PC 점수 기준이 다르다."**

코드 + 운영 데이터(alphafoundry.app, 2026-05-20~28, 189 종목-일) 실측으로 6개 결함 확정.

| # | 결함 | 근거 |
|---|------|------|
| 1 | 만점이 도달 불가능 — composite_max=7.4지만 실효 상한 ~5.7, 최고 종목도 52점 | 축별 max ai=10/tech=3.5/sent=10 제각각 |
| 2 | weight가 실제 기여와 모순 — tech가 weight 0.4(최대)인데 max 3.5라 실제 기여 1.4(최소) | "기술이 가장 중요"라며 가장 적게 반영 |
| 3 | 이중 스케일 버그 — 숫자=display(0~100), 색/등급=원점수(0~7.4) → "52점인데 녹색(우수)" | `getScoreGrade(compositeScore)` vs `compositeScoreDisplay` |
| 4 | 0.5 컷오프로 절반이 0점 — ai 21/42, sentiment 18/42 | ai `normalized<0.5`·sentiment `raw≤0` 전부 0 |
| 5 | sentiment 입력-분모 불일치 — 입력 실범위 ±0.35인데 분모 10 → 최선의 긍정도 3.5점 천장 | Alpha Vantage `ticker_sentiment_score` |
| 6 | SSoT 드리프트 — yaml(Python) ↔ Kotlin 중복정의, 값 분기 | `PredictionService.kt:96` `*7.5` vs yaml 7.4, `MIN_RECOMMEND_SCORE=5.18` vs yaml 2.0 |

> **"모바일/PC 기준이 다르다"의 실체**: 둘 다 같은 이중스케일 버그(#3)지만 레이아웃이 달라 모순이 눈에 띄는 정도가 화면마다 다를 뿐. 별도 버그 아님.

**한 줄 원인**: 점수가 낮은 건 종목이 나빠서가 아니라 (a) 만점이 도달 불가능하게 설정 + (b) 중립/약세 입력을 0점으로 깎는 컷오프 때문. 분모·컷오프·weight·표시 스케일이 모두 어긋나 있음.

---

## 2. 결정 — 각 항목 "왜 바꾸는가"

각 결정은 **AS-IS → TO-BE → 왜** 형식.

### 2.1 구조: 축별 0~1 정규화 → weight 가중합 → 0~100
- **AS-IS**: `0.3·ai + 0.4·tech + 0.3·sent`, 축별 max 제각각(10/3.5/10) → composite_max=7.4. 화면은 별도로 0~100 display를 또 계산.
- **TO-BE**: `composite_100 = (w_tech·(tech/3.5) + w_ai·(ai/10) + w_sent·(sent/10)) × 100`. 각 축 먼저 0~1 정규화 후 weight 곱(합=1.0) → 0~1 → ×100. **display = composite** (단일 척도).
- **왜**: 7.4라는 어정쩡한 만점은 "축별 max가 다른데 그냥 가중합"해서 나온 값. 축을 먼저 0~1로 맞추면 만점이 깔끔히 100이 되고, weight가 진짜 의도대로 작동하며(결함 2), 별도 display 정규화·등급 이중스케일(결함 1·3)이 통째로 사라진다. **결함 1·2·3을 한 번에 해소.**

### 2.2 weight: tech 0.5 / ai 0.3 / sentiment 0.2
- **AS-IS**: ai 0.3 / tech 0.4 / sentiment 0.3 (ai·sentiment 동급).
- **TO-BE**: tech 0.5 / ai 0.3 / sentiment 0.2.
- **왜**: 신뢰성 순서가 tech > ai > sentiment인데 weight가 이를 안 따랐다. tech는 가격 기반·결측 없음·결정론적이라 가장 안정 → 최고. ai는 **예측 정확도 사후검증 루프가 없어**(예측가 vs 실제가 비교 안 함) 검증 전까지 0.3 제한. sentiment는 커버리지 42% 결측 + 입력 실범위 좁아 가장 약함 → 0.2. (strategy 전문가: 검증 없는 AI에 높은 weight는 부당.)

### 2.3 축별 선형 매핑 (0.5/0 컷오프 제거)
- **AS-IS**: ai `normalized<0.5`(하락예측) → 0, sentiment `raw≤0`(중립~부정) → 0. 입력 절반이 0점.
- **TO-BE**: sentiment `(clip(raw,-0.35,0.35)+0.35)/0.7` (중립 raw 0 → 0.5), ai `clip(0.5+rise_pct/40,0,1)` (하락도 연속 저점수).
- **왜**: "중립 뉴스"와 "데이터 없음"이 똑같이 0점이 되어 정보가 죽었다(결함 4·5). 입력 실범위를 0~1에 정직하게 펼치면 중립=중간(0.5), 강세=만점으로 직관적. 운영 ai 21·sentiment 18 종목의 0점 쏠림 해소.

### 2.4 결측 처리: 0점 금지 → 축 제외 후 weight 재정규화
- **AS-IS**: 뉴스/AI 결측 시 해당 축 0점 합산(`missing_policy: zero`). composite 상한이 깎임.
- **TO-BE**: 결측 축 제외 + 남은 weight 재정규화 (예: sentiment 결측 → tech/ai 0.5/0.3 → 0.625/0.375).
- **왜**: "데이터 없음"을 "최악(0점)"으로 처리하면 뉴스 커버리지 낮은 소형주가 구조적으로 저평가되는 selection bias 발생. 결측은 벌점이 아니라 "모름"이므로 빼고 나머지로만 평가해야 공정.

### 2.5 veto 완화 (ADR 0002 supersede)
- **AS-IS**: AI 하락 예측(rise_pct<0)이면 composite 강제 0/grade D/추천 제외 (전량 차단).
- **TO-BE**: **강한 하락(rise_pct < -10%)** 에만 veto. -10%~0% 약한 하락은 ai score 저점수로 흡수. AI 정확도 백테스트(veto hit rate ≥ 60%) 검증 전까진 veto 비활성 옵션.
- **왜**: 새 구조에서 ai축 선형 매핑이 이미 하락에 자연 페널티를 준다. veto까지 겹치면 이중 처벌. "tech 만점+뉴스 강긍정인데 AI -3%"인 종목을 전멸시키는 건 3축 가중합 철학에 반함. 정확도 미검증 모델 하나에 전체 차단권을 주는 건 부당(pm 전문가). 단 강한 하락 확신은 차단 유지해 배신 방지.

### 2.6 등급 임계: 0~100 percentile 기반
- **AS-IS**: 원점수 기준 S6.0/A4.5/B3.0/C1.5 (도달 불가, S·A 전무).
- **TO-BE**: 0~100 분포 percentile로 산출(코드 하드코딩 금지). 잠정 S≥74/A≥68/B≥60/C≥54/D<54. 실제 rise_pct 분포로 재보정.
- **왜**: 만점이 도달 불가하니 S·A가 영영 안 나옴. percentile 기반이면 "상위 10%=S"가 항상 의미를 갖는다. **1차 구현은 잠정 고정값 → 동적 산출은 후속** (§4 열린질문).

### 2.7 펀더멘탈: 이번엔 "저평가 뱃지", 검증 후 4축 승격 (2단계)
- **AS-IS**: yfinance 펀더멘탈(PER/PBR/ROE 182필드)을 수집만 하고 점수·AI입력에 전혀 안 씀.
- **TO-BE**: 이번 PR은 점수 축 아님 — 보조 "저평가 뱃지". 섹터 percentile 검증 후 다음 PR에서 4번째 축 승격.
- **왜**: 시간축 불일치(일별 신호 vs 분기 가치) + 섹터 정규화 복잡(percentile 2-pass) + 결측 처리 리스크. 검증 없이 4축에 욱여넣으면 노이즈 유입(backend-architect). 단계적 진화가 안전.

### 2.8 SSoT 단일화
- **AS-IS**: `scoring_spec.yaml`(Python) ↔ `RecommendationCriteria.kt`(Kotlin) 중복 정의, 값 분기(7.5 vs 7.4, 5.18 vs 2.0).
- **TO-BE**: yaml 단일 SSoT. 0~100 단일 스케일 전환으로 Kotlin display 정규화 자체 제거(가능 시). 드리프트 정합.
- **왜**: 같은 값을 두 언어가 각자 들고 있으면 반드시 어긋난다(이미 어긋남). 0~100로 가면 Kotlin은 표시만 하면 되므로 정규화 로직이 불필요해짐.

### 기각한 대안
| 대안 | 기각 이유 |
|------|----------|
| 등급 임계만 percentile 재조정(구조 유지) | display 이중스케일·weight 모순·0점 쏠림 미해결 |
| 분모만 교정(sentiment max 10→5) | weight 모순·이중스케일 미해결, 부분 처방 |
| 펀더멘탈 즉시 4축 추가 | 시간축 불일치·섹터 2-pass·결측 리스크 → 2단계로 |
| veto 완전 제거 | "AI 하락인데 추천" 리스크 → 강한 하락은 차단 유지 |

---

## 3. 구현 계획 (옵션 A — 레이어 순차)

데이터 흐름이 "산출→저장→표시" 단방향이라 레이어 순차가 자연스럽고, 가장 위험한 산식을 먼저 TDD로 못 박는다. **push는 전체 검증 완료까지 보류**(사용자 지시).

| Phase | 범위 | 검증 |
|-------|------|------|
| 1. Python 산식 | `scoring_policy.py` + `scoring_spec.yaml` — 0~100 구조, weight 0.5/0.3/0.2, 선형매핑, 결측 재정규화, veto -10% | golden/property/regression 테스트 재작성 (TDD), `pytest` |
| 2. Kotlin display | `PredictionService.kt`, `RecommendationCriteria.kt` — 0~100 단일 스케일, 이중 정규화 제거, 드리프트 정합 | `./gradlew test` |
| 3. Frontend | `lib/api/predictions.ts`(getScoreGrade/TIER) + 점수 표시 컴포넌트 — 0~100 기준, 이중스케일 버그·매수기준 단일화 | vitest(신규) + lint |
| 4. 시각 검증 | prod 데이터로 모바일/PC 점수-색 일치 | Playwright (1280px/390px) |

**기각**: 옵션 B(수직 슬라이스 — 산식 안정 전 표시 만지면 디버깅 혼란), 옵션 C(백엔드만 PR — 0~100 바뀌면 기존 프론트 깨져 화면 망가짐).

---

## 4. 열린 질문 (구현 전 확정)

1. 등급 임계 1차는 잠정 고정값(S74/A68/B60/C54), 동적 percentile은 후속? → 제안: 예
2. veto 1차: -10% 즉시 적용 vs 정확도 검증 전까지 전면 비활성? → pm 권고: 검증 전 비활성 (제품 판단 필요)
3. Kotlin SSoT: 런타임 yaml 로드 vs 0~100 전환으로 정규화 제거? → 아키텍트 권고: 후자
4. 3축 모두 결측 시 처리? → 현재 `continue`(제외) 유지?

---

## 5. 결과 (Consequences)

- **시계열 단절**: 과거 점수와 값 완전히 달라짐(어제 52 → 오늘 70). BETA 뱃지로 커뮤니케이션 + `score_version` 구분 필요.
- **재계산 범위**: Python 산식 + yaml + Kotlin display + FE 동시 변경, 테스트 전면 재작성.
- **신규 KPI**: veto hit rate(veto된 종목 중 실제 하락 비율) 추적 → veto 임계 데이터 기반 결정.
- **검증 의존**: 등급 임계는 운영 분포로 재보정 전제, 코드 상수 하드코딩 금지.

---

## 부록 A. 용어 (Glossary)

- **composite score**: 종목 추천 강도. 각 축 0~1 정규화 후 weight 가중합 → 0~100. SSoT=`scoring_spec.yaml`.
- **axis (축)**: ai(AI 가격예측) / tech(기술지표) / sentiment(뉴스 감성). weight tech 0.5 / ai 0.3 / sentiment 0.2.
- **raw sentiment**: Alpha Vantage `ticker_sentiment_score` 일간 평균. 실범위 ±0.35.
- **결측 vs 중립**: 결측="데이터 없음"(축 제외), 중립="raw≈0"(0.5점). 다른 개념.
- **display score**: 화면 표시 점수. 재설계 후 = composite score(별도 스케일 없음).
- **grade**: composite(0~100) 구간 라벨 S/A/B/C/D. percentile 기반.
- **veto**: AI 강한 하락(rise_pct<-10%) 시 composite 강제 0/추천제외. 축 점수와 별개.
- **fundamental badge**: 펀더멘탈(PER/ROE) 기반 보조 표식. 점수 축 아님(검증 후 4축 승격 예정).

## 부록 B. 전문가 패널 결론 요약

- **strategy**: weight 0.5/0.3/0.2. 검증 없는 AI에 높은 weight 부당. sentiment 42% 결측 → 준보조.
- **db-performance**: 새 구조로 B·C 쏠림 해소(mean 60·std 12). 단 AI 51% clip 때문에 임계는 실제 rise_pct 분포로 재보정 필수.
- **pm-planner**: veto 완화 + ADR supersede. veto hit rate KPI 신설. AI 검증 전 veto 비활성 권고.
- **backend-architect**: 펀더멘탈은 보조뱃지(시간축 불일치). SSoT 단일화·드리프트 정합. 0~100 전환 시 Kotlin 정규화 제거 가능.
