# ADR 0006 — 추천 점수 모델 재설계 (0~100 정규화 가중합)

> 작성: 2026-06-07 · 상태: **Accepted** (TDD 구현 200 통과 + 11거래일 운영 분포 재보정 완료, §5 Acceptance 충족)
> 사후 검토 반영: 2026-06-10 — veto 임계 구현 정합·라벨/필터 재보정·드리프트 가드 (§6, spec 2.1.0)
> Supersedes: [ADR 0002](./0002-negative-ai-veto.md)의 negative veto 정책
> 검토: 전문가 패널 4인 (strategy / db-performance / pm-planner / backend-architect) + 구현 후 5인 재검증 (backend/db/frontend/security/pm)
>
> **이 문서 하나로 점수 모델 재설계의 배경·결정·이유·구현계획을 모두 담는다.** (작업 단위가 하나라 문서를 쪼개지 않음)

---

## 1. 문제 — 사용자 신고

**"추천 점수가 다 낮다(전 종목 B·C, 최고 종목도 52/100). 모바일·PC 점수 기준이 다르다."**

코드 + 운영 데이터(alphafoundry.app, 2026-05-20~28, 189 종목-일) 실측으로 6개 결함 확정.

| # | 결함 | 근거 |
|---|------|------|
| 1 | 만점이 도달 불가능 — composite_max=7.4지만 실효 상한 ~5.45(sentiment 실범위 ±0.35 천장 3.5 기준 `0.3·10+0.4·3.5+0.3·3.5`; ai 실효 cap 적용 시 더 낮음), 최고 종목도 52점 | 축별 max ai=10/tech=3.5/sent=10 제각각 |
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
- **TO-BE**: 결측 축 제외 + 남은 weight 재정규화 (예: sentiment 결측 → tech/ai 0.5/0.3 → 0.625/0.375). 단, **coverage guard**를 같이 적용한다.
- **왜**: "데이터 없음"을 "최악(0점)"으로 처리하면 뉴스 커버리지 낮은 소형주가 구조적으로 저평가되는 selection bias 발생. 결측은 벌점이 아니라 "모름"이므로 빼고 나머지로만 평가해야 공정.

**coverage guard**
- `available_axes`, `missing_axes`, `score_coverage`를 Score/API 응답에 보존한다. 화면은 점수와 함께 "데이터 커버리지"를 표시할 수 있어야 한다.
- 기술(tech) 축은 추천의 최소 근거다. `has_tech=false`이면 점수 산출 대상에서 제외(`continue`)한다.
- tech만 있는 단일축 점수는 추천 불가(`is_recommended=false`)로 둔다. 단일축을 100점처럼 보이게 하는 과거 동적 재분배 인플레를 재발시키지 않기 위함.
- 추천 가능 최소 조건은 `available_axes >= 2` 또는 `score_coverage >= 0.8` 중 하나를 만족해야 한다. 미충족 시 composite는 계산하되 `recommendation_label=NONE`으로 둔다.
- 즉, 결측 재정규화는 "없는 데이터를 0점 벌점으로 치지 않는다"는 공정성 장치이지, 적은 근거를 강한 추천으로 포장하는 장치가 아니다.

### 2.5 veto 완화 (ADR 0002 supersede)
- **AS-IS**: AI 하락 예측(rise_pct<0)이면 composite 강제 0/grade D/추천 제외 (전량 차단).
- **TO-BE**: 기본 1차 구현은 **veto 비활성**(`negative_policy: zero` — 코드 enum은 `{zero, veto}` 2값, `zero`=veto 비활성). AI 하락은 ai score 저점수로 흡수한다. 백테스트에서 veto hit rate가 기준 이상이면 **강한 하락(rise_pct < -10%)** 에만 veto를 켠다(`veto`).
- **왜**: 새 구조에서 ai축 선형 매핑이 이미 하락에 자연 페널티를 준다. veto까지 겹치면 이중 처벌. "tech 만점+뉴스 강긍정인데 AI -3%"인 종목을 전멸시키는 건 3축 가중합 철학에 반함. 정확도 미검증 모델 하나에 전체 차단권을 주는 건 부당(pm 전문가). 단 강한 하락 확신은 차단 유지해 배신 방지.

**veto 확정 조건** (2026-06-10 사후 검토로 구현 정합 — §6)
- `rise_pct < -10%`는 즉시 Accepted된 임계가 아니라 1차 후보값이다. spec `axes.ai.veto_threshold_pct: -10.0`으로 보존되며, 판정 로직(`should_veto_rise_pct`/`should_veto_normalized`)이 이 값을 사용한다.
- 최근 N영업일 운영 데이터로 `veto hit rate = veto된 종목 중 실제 하락한 비율`을 산출한다.
- `veto hit rate >= 60%`이고 zero-rec day가 ADR 0002 가드 안에 있으면 `negative_policy: veto`로 전환한다(코드 enum은 `{zero, veto}` 2값 — 전환해도 `veto_threshold_pct` 때문에 강한 하락만 차단된다).
- 기준 미달이면 veto는 계속 비활성이고, 하락 예측은 ai 축 점수만 낮추는 방식으로 처리한다.
- 활성화는 별도 PR로 게이트하고, 켜기 전 shadow mode(판정 로깅만, 차단 없음)로 관찰 후 전환한다(패널 권고).

### 2.6 등급 임계: 0~100 percentile 기반
- **AS-IS**: 원점수 기준 S6.0/A4.5/B3.0/C1.5 (도달 불가, S·A 전무).
- **TO-BE**: 0~100 분포 percentile로 산출. **재보정 완료 S≥82/A≥77/B≥68/C≥58/D<58** (2026-05-12~06-04 11거래일×42=462표본 실측, p90/p75/p50/p25 → 부록 B). 코드 하드코딩 금지, `scoring_spec.yaml.grades` SSoT. **잠정 성격**(저변동 단일 국면 표본) — 표본 누적 시 재산출.
- **왜**: 만점이 도달 불가하니 S·A가 영영 안 나옴. percentile 기반이면 "상위 10%=S"가 항상 의미를 갖는다. 462표본 적용 결과 분포 S11%/A13%/B25%/C24%/D27% — "상위 ~10%=S" 충족. **반올림 규칙**: S는 p90 내림(top10% 보장), A/B/C는 정수 올림.

**threshold 운영 방식**
- 1차 잠정값도 Kotlin/Frontend 코드 상수가 아니라 `scoring_spec.yaml.grades`에 둔다.
- 동적 percentile은 후속 PR에서 산출하되, 매 요청/매일 실시간 재계산하지 않는다. 최근 N영업일 분포로 산출하고 `formula_version`별로 freeze한다.
- 같은 composite 점수가 날짜마다 다른 등급으로 흔들리지 않도록 threshold 갱신은 명시적 운영 작업으로 수행한다.
- API는 `compositeScore`와 `compositeGrade`를 함께 내려주고, Frontend는 등급 임계값을 재계산하지 않는다.

### 2.7 펀더멘탈: 이번엔 "저평가 뱃지", 검증 후 4축 승격 (2단계)
- **AS-IS**: yfinance 펀더멘탈(PER/PBR/ROE 182필드)을 수집만 하고 점수·AI입력에 전혀 안 씀.
- **TO-BE**: 이번 PR은 점수 축 아님 — 보조 "저평가 뱃지". 섹터 percentile 검증 후 다음 PR에서 4번째 축 승격.
- **왜**: 시간축 불일치(일별 신호 vs 분기 가치) + 섹터 정규화 복잡(percentile 2-pass) + 결측 처리 리스크. 검증 없이 4축에 욱여넣으면 노이즈 유입(backend-architect). 단계적 진화가 안전.

### 2.8 SSoT 단일화
- **AS-IS**: `scoring_spec.yaml`(Python) ↔ `RecommendationCriteria.kt`(Kotlin) 중복 정의, 값 분기(7.5 vs 7.4, 5.18 vs 2.0).
- **TO-BE**: `scoring_spec.yaml` 단일 SSoT. 0~100 단일 스케일 전환으로 Kotlin display 정규화 자체 제거(가능 시). 드리프트 정합.
- **왜**: 같은 값을 두 언어가 각자 들고 있으면 반드시 어긋난다(이미 어긋남). 0~100로 가면 Kotlin은 표시만 하면 되므로 정규화 로직이 불필요해짐.

**SSoT 범위**
- `scoring_spec.yaml`: weight, 축별 정규화 파라미터, grade threshold, recommendation gate, formula/spec version의 단일 원천.
- Python Data Engine: spec을 로드해 점수를 산출하고 `prediction_results`에 0~100 composite와 grade를 저장한다.
- Kotlin Core: 점수 재계산/정규화 금지. 저장된 0~100 composite와 grade를 전달하고, 필터링 threshold가 필요하면 spec에서 온 값 또는 저장된 grade를 사용한다.
- Frontend/Backoffice: 점수 기준 재정의 금지. API가 준 `compositeScore`, `compositeGrade`, `scoreCoverage`를 표시만 한다. `getScoreGrade`, `TIER_THRESHOLDS` 같은 로컬 기준은 제거하거나 API grade 매핑만 담당한다.
- 예외: 화면 색상 클래스/라벨 문구 같은 순수 presentation mapping은 FE에 둘 수 있지만, 점수 임계값은 FE에 두지 않는다.

### 2.9 설명가능성(XAI): 축별 기여도 노출 (P0)
- **AS-IS**: composite만 보여주고 "왜 이 점수인지" 근거가 없다. 사용자 원불만("점수가 낮다")의 본질도 결국 근거 부재.
- **TO-BE**: 산식에서 이미 계산되는 **축별 기여값**(`w_tech·(tech/3.5)×100`, `w_ai·(ai/10)×100`, `w_sent·(sent/10)×100`)을 버리지 않고 `axis_contributions`로 Score/API 응답에 보존한다. 화면은 "3축 막대(tech/ai/sent 기여 점수) + 한 줄 사유" 수준으로 표시한다.
- **왜**: 2026 AI 투자 플랫폼의 최대 차별점이자 신뢰 요건은 설명가능성이다(Danelfin·CFA·EU AI Act human oversight). 기여값은 가중합 과정에서 **이미 계산되므로 추가 산식 비용이 거의 0**인데, 노출하지 않아 무료 차별점을 버리고 있었다. 단 cognitive overload를 피해 3축 막대 + 한 줄로 제한한다(과도한 설명 금지).
- **범위**: 1차는 축별 기여 점수 보존·표시까지. 피처 단위(어떤 지표가 tech를 올렸는지) 세부 설명은 후속.

### 기각한 대안
| 대안 | 기각 이유 |
|------|----------|
| 등급 임계만 percentile 재조정(구조 유지) | display 이중스케일·weight 모순·0점 쏠림 미해결 |
| 분모만 교정(sentiment max 10→5) | weight 모순·이중스케일 미해결, 부분 처방 |
| 펀더멘탈 즉시 4축 추가 | 시간축 불일치·섹터 2-pass·결측 리스크 → 2단계로 |
| veto 영구 제거 | "AI 강한 하락인데 추천" 리스크 → 백테스트로 정확도가 확인되면 강한 하락 차단은 유지 |

---

## 3. 구현 계획 (옵션 A — 레이어 순차)

데이터 흐름이 "산출→저장→표시" 단방향이라 레이어 순차가 자연스럽고, 가장 위험한 산식을 먼저 TDD로 못 박는다. **push는 전체 검증 완료까지 보류**(사용자 지시).

| Phase | 범위 | 검증 |
|-------|------|------|
| 1. Python 산식 | `scoring_policy.py` + `scoring_spec.yaml` — 0~100 구조, weight 0.5/0.3/0.2, 선형매핑, 결측 재정규화 + coverage guard, veto 기본 비활성, **축별 기여도(`axis_contributions`) 산출·보존** | golden/property/regression 테스트 재작성 (TDD), `pytest` |
| 2. Kotlin display | `PredictionService.kt`, `RecommendationCriteria.kt` — 점수 재계산/이중 정규화 제거, 저장된 0~100 단일 스케일 전달, spec/grade 기반 필터 정합 | `./gradlew test` |
| 3. Frontend | `lib/api/predictions.ts`(getScoreGrade/TIER 제거 또는 API grade 매핑화) + 점수 표시 컴포넌트 — 0~100 기준, 이중스케일 버그·매수기준 단일화, **축별 기여도 막대 + 한 줄 사유 표시(XAI)** | vitest(신규) + lint |
| 4. 시각 검증 | prod 데이터로 모바일/PC 점수-색 일치 | Playwright (1280px/390px) |

**기각**: 옵션 B(수직 슬라이스 — 산식 안정 전 표시 만지면 디버깅 혼란), 옵션 C(백엔드만 PR — 0~100 바뀌면 기존 프론트 깨져 화면 망가짐).

---

## 4. 열린 질문 → 결론 (전부 해소)

1. 등급 임계: 잠정 고정값 → **11거래일 462표본 percentile로 재보정 완료** S82/A77/B68/C58 (`scoring_spec.yaml`). 동적 산출은 후속. ✅
2. veto 1차: → **검증 전 비활성** 결정 (`negative_policy: zero`). `-10%`는 후보 임계로 보존, hit rate 검증 후 활성. ✅
3. Kotlin SSoT: → **0~100 전환으로 정규화 제거** 채택. Kotlin은 저장값 pass-through만(재계산 금지). ✅
4. 3축 모두 결측 시: → `continue`(제외) 유지. ✅
5. tech 없는 종목: → 추천 제외(`is_recommended=false`), 점수 산출에서도 제외. ✅
6. tech만 있는 단일축: → composite 계산은 하되 추천 불가(coverage guard, `is_recommended=false`). ✅

---

## 5. 결과 (Consequences)

- **시계열 단절**: 과거 점수와 값 완전히 달라짐(어제 52 → 오늘 70). BETA 뱃지로 커뮤니케이션 + `score_version` 구분 필요.
- **재계산 범위**: Python 산식 + yaml + Kotlin display + FE 동시 변경, 테스트 전면 재작성.
- **신규 KPI**: veto hit rate(veto된 종목 중 실제 하락 비율) 추적 → veto 임계 데이터 기반 결정.
- **검증 의존**: 등급 임계는 운영 분포로 재보정 전제, 코드 상수 하드코딩 금지.
- **커버리지 노출 필요**: 결측 재정규화로 점수 자체는 공정해지지만, 근거 축 수가 적은 추천은 사용자 신뢰 리스크가 있다. `score_coverage`/`missing_axes`를 API와 화면에 보존한다.
- **SSoT 계약 변경**: FE/Kotlin의 로컬 threshold 상수는 제거 대상이다. 점수 기준 변경은 `scoring_spec.yaml` 변경 + formula/spec version 갱신으로만 수행한다.

### Acceptance Criteria (충족 — 2026-06-07, 11거래일 462표본 실측, §부록 B)

- [x] **0점 쏠림 해소**: present 축 0점 비율 sentiment 0.4%·ai 2% (<10%). (컷오프 제거로 중립→0.5. AS-IS ai 50%·sent 43%였음. 결측은 0점 벌점이 아니라 재정규화 제외.)
- [x] **등급 분포**: 462표본 S 11%/A 13%/B 25%/C 24%/D 27% — "상위 ~10%=S" 충족, S·A 영구 전무 상태 해소.
- [x] **이중스케일 버그 0건**: 모바일(390px)/PC(1280px) 전 화면 숫자-색-등급 동일 스케일(Playwright 검증, `compositeScore==compositeScoreDisplay`).
- [x] **SSoT 일치**: yaml ↔ Kotlin(grade fallback 82/77/68/58) ↔ FE(임계 제거, compositeGrade pass-through) 분기 0건.
- [x] **회귀**: Python pytest 200, Kotlin gradle test, FE vitest 19 전부 green.
- **롤백 전략**: `formula_version`(V73 컬럼) 으로 신/구 점수 구분 저장. 분포가 기준을 벗어나면 spec의 weight/normalization/grade 블록을 직전 `formula_version`으로 되돌린다(yaml 단일 변경). veto는 `negative_policy` 토글로 즉시 비활성 복귀.

---

## 6. 사후 검토 반영 (2026-06-10, spec 2.1.0)

Accepted 이후 코드 대조 검토 + 전문가 패널(strategy/pm-planner/backend-architect) 재검증으로 아래를 정정·보강했다. **산식(formula 2.0.0)은 불변** — 게이트/라벨/임계만 변경.

| # | 발견 | 조치 |
|---|------|------|
| 1 | §2.5가 약속한 "강한 하락만 veto"가 코드에 없었음 — 활성화 시 모든 하락(<0%) 차단(구 PR 3b)이 부활하는 상태. 본문 enum(`strong_negative_veto`)도 코드 enum(`{zero, veto}`)과 모순 | spec `veto_threshold_pct: -10.0` 신설 + `should_veto_rise_pct`/`should_veto_normalized` 판정 통일(raw %·normalized 두 경로 등가성 property test). 본문 enum 표기 정정 |
| 2 | `recommendation_filter.min_composite_score: 60`이 "grade B 경계" 주석과 모순(B=68) — 등급 재보정을 안 따라간 드리프트. 60이면 전 종목 ~65% 통과로 게이트 무력 | **68(B 경계)로 정렬**. Kotlin `MIN_RECOMMEND_SCORE`/`DEFAULT_MIN_CONFIDENCE`(0.68)도 동기화 |
| 3 | `recommendation_labels`(STRONG 0.65/REC 0.45/WATCH 0.30)가 구 스케일(composite/7.4) confidence 기준 잔존 — 신 스케일(mean 0.66)에선 평균 종목이 STRONG 후보(라벨 인플레), "C등급인데 강력 추천" 비일관 | **등급 경계 정렬**: STRONG 0.77(=A)/RECOMMEND 0.68(=B)/WATCH 0.58(=C). `min_tech_signals` 게이트 제거 — tech는 composite의 50% weight로 이미 반영(이중 반영 + "B등급인데 라벨 WATCH" 채널 불일치 해소, 패널 Critical). 라벨=등급의 채널별 표현(Slack=라벨, 웹=등급) |
| 4 | 라벨 임계가 `RecommendationGrade.from_scores`(Python)에 하드코딩 중복 — 결함 #6(SSoT 드리프트)과 동형 | 하드코딩 제거, `ScoringPolicy.label_from_confidence_and_signals`(yaml SSoT) 위임 |
| 5 | Kotlin grade fallback(82/77/68/58)·필터 상수가 yaml의 수동 복사본인데 드리프트 가드 부재 | `ScoringSpecParityTest` 신설 — gradle test가 yaml을 파싱해 Kotlin 상수와 대조(경계±0.01 검증). spec 미발견 환경은 skip으로 구분 |

**남은 항목 (비차단, 후속)**
- **zero-rec day 정책**: 필터 68 + 약세장 분포 좌측 이동 시 추천 0건 날 발생 가능. "추천 없음 = 관망 신호" empty-state UX/문구를 제품 차원에서 정의 필요(패널 strategy·pm 공통 권고).
- **라벨 강등 커뮤니케이션**: 임계 상향으로 기존 "강력 추천" 종목이 "추천/관심"으로 보일 수 있음 — 1회성 공지 권장.
- `BuyCriteria.min_composite_score=2.0` 등 read-0 잠정 필드 cleanup(기존 PR 2 예정 항목).
- veto 임계의 상대값(예: AI 예측 분포 p5) 후보 검토 — hit rate 표본 누적 후.

## 부록 A. 용어 (Glossary)

- **composite score**: 종목 추천 강도. 각 축 0~1 정규화 후 weight 가중합 → 0~100. SSoT=`scoring_spec.yaml`.
- **axis (축)**: ai(AI 가격예측) / tech(기술지표) / sentiment(뉴스 감성). weight tech 0.5 / ai 0.3 / sentiment 0.2.
- **raw sentiment**: Alpha Vantage `ticker_sentiment_score` 일간 평균. 실범위 ±0.35.
- **결측 vs 중립**: 결측="데이터 없음"(축 제외), 중립="raw≈0"(0.5점). 다른 개념.
- **display score**: 화면 표시 점수. 재설계 후 = composite score(별도 스케일 없음).
- **grade**: composite(0~100) 구간 라벨 S/A/B/C/D. percentile 기반.
- **score coverage**: 점수 산출에 사용된 축의 가중치 커버리지. 결측 재정규화 후에도 추천 신뢰도를 설명하기 위해 보존.
- **axis_contributions**: composite를 구성한 축별 기여 점수(`weight × normalized × 100`). 가중합 과정에서 이미 산출되며, "왜 이 점수인지" 설명(XAI)을 위해 보존·표시한다(§2.9).
- **veto**: AI 강한 하락(rise_pct<-10%) 시 composite 강제 0/추천제외. 1차 구현은 비활성, veto hit rate 검증 후 활성화. 축 점수와 별개.
- **fundamental badge**: 펀더멘탈(PER/ROE) 기반 보조 표식. 점수 축 아님(검증 후 4축 승격 예정).

## 부록 B. 운영 데이터 근거 (재보정 산출물)

**표본**: 2026-05-12 ~ 06-04 11거래일 × 42종목 = **462 종목-일**. prod MongoDB(읽기) → 신 산식(`scoring_policy.py` formula 2.0.0) → 로컬 PG 산출.
> ⚠️ 저변동 강세 단일 국면 표본(VIX 30일 평균 17.93). S 경계(상위10%)는 표본이 얇아 CI가 넓다. **잠정값** — 다국면/30거래일↑ 누적 후 재산출 권장.

**composite 분포**: n=462, mean **66.0**, std **15.1**.

**percentile → grade 임계** (반올림: S 내림, 나머지 올림):
| 분위 | 값 | grade min | 결과 비율 |
|------|-----|-----------|----------|
| p90 (상위10%) | 82.1 | **S=82** | 11% |
| p75 | 76.6 | **A=77** | 13% |
| p50 | 67.8 | **B=68** | 25% |
| p25 | 57.6 | **C=58** | 24% |
| — | — | **D<58** | 27% |

**컷오프 해소 검증**: present 축 0점 비율 sentiment 0.4%(233중 1) · ai 2%(462중 10, 정당한 강한 하락). AS-IS는 ai/sentiment 절반이 0점이었음.
**커버리지**: sentiment 결측 ~50%(재정규화로 제외, 0점 벌점 아님).

**재현 방법**: prod Mongo(자격=GCP Secret `qjs-env-db-prod`) 읽기 → `RecommendationSyncService.sync_latest_recommendations(date)` 로컬 PG 적재 → `SELECT percentile_cont(ARRAY[0.25,0.5,0.75,0.9]) WITHIN GROUP (ORDER BY composite_score) FROM prediction_results`. (상세: `memory/local-scoring-verification.md`)
**veto hit rate**: 1차 veto 비활성이라 표본 0 — shadow 적재 후 산출 예정(후속 KPI).

현재 문서의 `최고 52/100`, `sentiment 42% 결측`, `mean 60/std 12` 등은 방향성 판단 근거로 사용하되, Accepted 전에는 재현 가능한 산출물로 고정한다.

## 부록 C. 전문가 패널 결론 요약

- **strategy**: weight 0.5/0.3/0.2. 검증 없는 AI에 높은 weight 부당. sentiment 42% 결측 → 준보조.
- **db-performance**: 새 구조로 B·C 쏠림 해소(mean 60·std 12). 단 AI 51% clip 때문에 임계는 실제 rise_pct 분포로 재보정 필수.
- **pm-planner**: veto 완화 + ADR supersede. veto hit rate KPI 신설. AI 검증 전 veto 비활성 권고.
- **backend-architect**: 펀더멘탈은 보조뱃지(시간축 불일치). SSoT 단일화·드리프트 정합. 0~100 전환 시 Kotlin 정규화 제거 가능.
