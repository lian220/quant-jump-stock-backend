# AI 예측 모델 통합 개선 계획

> 대상 파일: `quant-jump-stock-data-engine/src/ml/predict_optimized.py` (2,321 lines)
> 작성일: 2026-02-22
> 근거: `AI예측모델_개선_계획.md` + `AI예측모델_리뷰_vs_개선계획_비교분석.md` + 실제 코드/데이터 검증
> 상태: **실행 준비 완료**

---

## 0. 현재 상태 요약

### 0.1 코드 검증 결과 (2026-02-22 기준, `fix/predict-model-critical-bugs` 브랜치)

| 항목 | 상태 | 비고 |
|------|------|------|
| 1.1 NaN→0 제거 | ✅ 수정됨 | `ffill()` + `dropna()` + 경제지표 `median()` |
| 1.2 출력층 activation | ✅ 수정됨 | `softplus` 적용 (line 691) |
| 1.3 data leakage | ✅ 수정됨 | `train_data`에만 `fit()` 후 전체 `transform()` (line 929) |
| 1.7 bfill 제거 | ✅ 수정됨 | `ffill()` 만 사용 |
| 2.1 주말/공휴일 필터 | ✅ 수정됨 | `get_all_data()`에서 `stocks_added > 0` 필터 |
| 2.2 yfinance 키 이름 | ✅ 수정됨 | PostgreSQL 매핑(name→ticker)으로 3종 포맷 통일 |
| 2.3 FRED 키 이름 | ✅ 수정됨 | PostgreSQL 매핑(name→code)으로 한국어/코드 통일 |
| 1.4 off-by-one | ✅ 수정됨 | `shift(-(forecast_horizon - 1))` |
| 1.5 MAPE 0나누기 | ✅ 수정됨 | `actual != 0` 필터 |
| 1.6 target_columns 하드코딩 | ✅ 수정됨 | `get_target_columns_from_db()` |
| 3.1 Positional Encoding | ✅ 수정됨 | `SinusoidalPositionalEncoding` 레이어 추가 |
| 3.2 Add→Concatenate | ✅ 수정됨 | `Concatenate(axis=-1)` 정보 보존 |
| 3.3 가격→수익률 | ✅ 수정됨 | log return 예측 방식 전환 |
| 3.4~3.6 나머지 아키텍처 | ❌ 미착수 | 클러스터 모델, 확률적 예측 등 |

### 0.2 MongoDB 데이터 현황 (실측)

| 항목 | 수치 | 비고 |
|------|------|------|
| 총 문서 수 | **7,353** | 2006-01-01 ~ 2026-02-20 |
| 빈 stocks 문서 | 21 | 주말/공휴일 (이미 필터링됨) |
| 종목 수 (현재) | 35 | 구 데이터 24종목, 신규 35종목 |
| Full OHLCV 시작일 | **2025-02-07** | 그 이전은 `close_price`만 존재 |
| Old format (close_price only) | 7,008건 (96.4%) | 2006~2025-02-06 |
| FRED 데이터 보유 | 270건 (3.7%) | 96.3% 비어있음 |
| FRED 키 전환일 | 2026-02-17 | 한국어명 → FRED 코드 |
| yfinance 키 전환일 | 2026-02-12 | 한국어명 → 티커 |
| info(펀더멘탈) 보유 | **4건** | 사실상 사용 불가 |

### 0.3 데이터 포맷 3종 공존 (신규 발견)

| 기간 | stocks 필드 | yfinance_indicators | fred_indicators |
|------|------------|--------------------|-----------------|
| 2006-01-01 ~ 2025-02-06 | `{close_price: 68.34}` | `{"S&P 500 ETF": 127.5}` (스칼라) | 없음 (96.3%) |
| 2025-02-07 ~ 2026-02-11 | `{open, high, low, close, volume, close_price}` | `{"프랑스 CAC 40": {close: 8200}}` (객체+한국어) | `{"기준금리": {value: 4.33}}` (한국어) |
| 2026-02-12 ~ 현재 | OHLCV + `close_price` + `info` (4건만) | `{"^FCHI": {name: "프랑스 CAC 40", close: 8340}}` (티커) | `{"DGS10": {value: 4.5}}` (코드) |

> **핵심**: 코드의 `get_all_data()`는 세 포맷을 모두 처리하지만, 결과적으로 구 데이터(2006~2025)는 `close_price` 하나만 유효. open/high/low/volume은 96%가 NaN이 되어 모델 입력으로 부적절.

---

## Phase 1: 치명적 버그 수정 (현 구조 유지)

### ~~1.3 학습 시퀀스 data leakage 제거~~ — **P0, ✅ 수정됨**
- **위치**: line 919-932
- **수정 내용**: `train_data = data.iloc[:train_size]` → `stock_scaler.fit(train_data[...])` → `transform(전체)`
- 학습 데이터만으로 fit 후 전체에 transform하는 올바른 방식으로 수정 완료

### ~~1.4 평가 시 off-by-one 오류 수정~~ — **P1, ✅ 수정됨**
- **위치**: line 2009
- **수정 내용**: `shift(-forecast_horizon)` → `shift(-(forecast_horizon - 1))` — 학습 타깃과 일치

### ~~1.5 MAPE 0 나누기 방지~~ — **P1, ✅ 수정됨**
- **위치**: line 2024-2028
- **수정 내용**: `actual != 0`인 데이터만으로 MAPE 계산, 전부 0이면 `np.nan` 반환

### ~~1.6 2단계 target_columns 하드코딩 제거~~ — **P1, ✅ 수정됨**
- **위치**: line 2240
- **수정 내용**: 하드코딩 리스트 → `get_target_columns_from_db()` 호출 (1단계와 동일)

### ~~1.6 2단계 target_columns 하드코딩 제거~~ — **P1, ✅ 수정됨**
- **위치**: line 2240
- **수정 내용**: 하드코딩 리스트 → `get_target_columns_from_db()` 호출

### ~~1.2 출력층 activation~~ — **P1, ✅ 수정됨**
- **위치**: line 691
- **수정 내용**: `activation='softplus'` 적용 완료 — (0, +∞) 보장
- Phase 3.3 (가격→수익률) 적용 시 재검토 필요

---

## Phase 2: 데이터 파이프라인 개선

### ~~2.2 yfinance_indicators 키 이름 통일~~ — **P1, ✅ 수정됨**
- **실측 결과**: 3종 포맷 공존 (스칼라 한국어, 스칼라 한국어, 객체 티커)
  - 2006~2025-02-04: `{"S&P 500 ETF": 482.88}` (한국어명 스칼라, 3개 지표)
  - 2025-02-05~2026-02-11: `{"프랑스 CAC 40": 7737.2}` (한국어명 스칼라, 21개)
  - 2026-02-12~: `{"^FCHI": {name: "프랑스 CAC 40", close: 8429}}` (티커 객체, 20개)
- **수정**: `get_indicator_key_mappings()`로 PostgreSQL `yfinance_indicators(name→ticker)` 매핑 로드
  - `get_all_data()`에서 `normalized_key = yfinance_name_to_ticker.get(key, key)` 적용
  - 모든 포맷이 ticker 기준으로 통일됨 → `get_economic_features_from_postgres()`와 일치

### ~~2.3 FRED 경제지표 키 통일~~ — **P1, ✅ 수정됨**
- **실측**: 7,353건 중 270건만 FRED 보유 (3.7%), 5개 지표만 활성
- **FRED 키 전환**: 한국어(~2026-02-16) → FRED 코드(2026-02-17~)
  - 한국어 키: `10년 만기 미국 국채 수익률`, `나스닥 종합지수` 등
  - 코드 키: `DGS10`, `NASDAQCOM` 등
- **수정**: `get_indicator_key_mappings()`로 PostgreSQL `fred_indicators(name→code)` 매핑 로드
  - `get_all_data()`에서 `normalized_key = fred_name_to_code.get(key, key)` 적용
  - 한국어/코드 키가 FRED code로 통일됨
- **잔여 과제**: 96.3% 데이터가 비어있어 ffill 효과 제한적 → 희소성 자체는 데이터 수집 개선 필요

### 2.5 (신규) 데이터 포맷 불일치 정규화 — **P0**
- **문제**: 구 데이터(2006~2025)에 `close_price`만 존재 → 모델이 사용하는 피처 중 open/high/low/volume이 96% NaN
- **실측**: AAPL 기준 7,268건 중 close 필드 보유 260건 (3.6%), close_price만 7,008건 (96.4%)
- **영향**: 현재 코드에서 `target_columns`이 종목명(=close_price 기반)이면 문제 없지만, OHLCV 기반 피처를 추가하려면 2025-02-07 이후 데이터만 사용 가능
- **수정**:
  - A) 모델은 `close_price`만 사용하도록 제한 (20년 데이터 활용 가능) — **현재 코드가 이 방식**
  - B) OHLCV 피처 필요 시 학습 기간을 2025-02-07 이후로 제한 (~260일)
- **권장**: A안 유지. 추후 Phase 3.5(Pre-train → Fine-tune)에서 OHLCV 활용

### 2.6 (신규) FRED 키 이름 전환 갭 처리
- **문제**: 한국어 키(~2026-01-01) → FRED 코드(2026-02-17~) 사이 47일 갭에 FRED 데이터 없음
- **수정**: 매핑 테이블로 키 통합 + 갭 기간은 ffill 처리

### ~~2.7 종목 수 변화 대응~~ — **해당 없음**
- 종목은 지속적으로 추가 예정이며, DB에서 동적으로 가져오는 구조 → 별도 대응 불필요
- 1.6(하드코딩 제거)만 완료하면 자동 대응됨

---

## Phase 3: 모델 아키텍처 근본 개선

> Phase 1-2 완료 후 진행. 아래 항목은 두 문서 + 리뷰 합의 기반.

### ~~3.3 예측 대상 변경: 가격 → 수익률~~ — **P0, ✅ 수정됨**
- **수정**: log return 예측 방식으로 전환
  ```python
  returns = np.log(prices / prices.shift(1))
  predicted_price = current_price * np.exp(predicted_return)
  ```
- **효과**: 스케일링 문제 근본 해결, 음수 가격 불가능, 1.2 sigmoid 이슈 자동 해소

### ~~3.1 Positional Encoding 추가~~ — **P1, ✅ 수정됨**
- **위치**: `build_transformer_with_two_inputs()` — 양 스트림 입력 직후
- **수정**: `SinusoidalPositionalEncoding` Keras 레이어 추가 (Vaswani et al., 2017)
  - `build()` 시점에 input_shape로부터 seq_len, d_model 자동 계산
  - stock/econ 양 스트림 모두 적용 → 90일 시퀀스 내 시간 순서 구분 가능

### ~~3.2 Add() → Concatenate()~~ — **P2, ✅ 수정됨**
- **위치**: `build_transformer_with_two_inputs()` 머지 레이어
- **수정**: `Add()` → `Concatenate(axis=-1)` — 두 스트림의 정보가 상쇄되지 않고 보존됨
- 후속 `Dense(128)`이 64+64=128차원 입력을 처리

### 3.4 35개 종목 단일 모델 → 개별/클러스터 모델 — **P3**
- **권장**: C안 (공유 인코더 + 종목별 헤드, Multi-Task Learning)

### 3.5 학습 데이터량 부족 해결 — **P3**
- **실측**: ~260일(2025-02-07 이후) × 35종목, lookback 90일 차감 → ~170 샘플
- **단, close_price만 사용 시**: 20년 데이터 활용 가능 (7,353건)
- **권장**: C안 — Pre-train (20년, close_price) → Fine-tune (최근 1년, OHLCV + 경제지표)

### 3.6 확률적 예측 도입 — **P2**
- MC Dropout 기반 (기존 코드 변경 최소)
- 신뢰도 공식: `confidence = 1 - (std / (std + |mean| + 1e-8))`

---

## Phase 4: 평가 및 추천 시스템 개선

### 4.1 Walk-Forward 백테스트 — **P2**
- 롤링 윈도우 교차검증
- 레짐 변화 구간(2008 금융위기, 2020 코로나, 2022 금리급등) 별도 검증

### 4.2 추천 로직 정교화 — **P2**
- Phase 3.6 확률적 예측 결과 활용
- 임계값: Precision-Recall 커브 기반 데이터 기반 설정

### 4.3 벤치마크 대비 성능 비교 — **P3**
- Buy & Hold, 이동평균 교차 등 단순 전략과 비교
- 방향성 정확도(Directional Accuracy) 추가

### 4.4 생존 편향 제거 — **P2**
- 현재 35종목은 "살아남은" 주식만 포함 → 낙관 편향

### 4.5 거래비용 및 슬리피지 반영 — **P2**
- 수수료 0.1% + 슬리피지 0.05% 반영

---

## Phase 5: 인프라 및 운영 개선

> 개선 계획 원본 + 전문가 리뷰 보완 항목 통합

### 5.1 PyTorch → TensorFlow 컨테이너 — **P4**
- `pytorch-gpu.1-13` → `tf-gpu.2-14` 또는 커스텀 컨테이너

### 5.2 모델 버전 관리 + 실험 추적 — **P4**
- 하이퍼파라미터, 데이터 기간, 평가 지표 함께 저장
- **(리뷰 추가)**: TensorBoard 또는 W&B 학습 곡선/하이퍼파라미터 로깅

### 5.3 모니터링 + 리포팅 강화 — **P4**
- MAPE 임계값 초과/음수 예측 시 알림
- **(리뷰 추가)**: `print` → `logging` 모듈 전환 (레벨+파일)
- **(리뷰 추가)**: 예측 플롯 `savefig()`로 파일 저장 + 대시보드 연동

### 5.4 시장 레짐 감지기 — **P3**
- VIX, 이동평균 기울기 기반 레짐 분류
- 레짐별 서로 다른 가중치 앙상블

### 5.5 설명 가능성 (SHAP) — **P3**
- SHAP DeepExplainer로 상위 기여 피처 시각화
- 월별 Top-10 기여 피처 로그 → 피처 드리프트 조기 감지

### 5.6 예측 분포 드리프트 감지 — **P4**
- PSI 또는 KL-Divergence 기반 모니터링

---

## Phase 6: 전문가 리뷰 전용 보완 항목 (신규)

> `AI예측모델_리뷰_vs_개선계획_비교분석.md` §4에서 리뷰에만 있던 항목 중 실행 가능한 것을 채택

### ~~6.1 거시 지표 시차(래그) 적용~~ — **P2, ✅ 수정됨**
- 경제지표를 1일 `shift(1)` 래그 적용 — 당일이 아닌 전일 값만 모델에 노출
- NaN 처리 완료 후, 스케일링 전에 적용
- look-ahead bias 방지 완료

### ~~6.2 이상치 탐지·윈저화~~ — **P2, ✅ 수정됨**
- 주식+경제 전 컬럼 대상 3σ 윈저화 (mean ± 3*std 클리핑)
- NaN 처리 완료 후, 래그 적용 후, 스케일링 전에 적용
- 극단 이상치가 MinMaxScaler 범위를 왜곡하는 것을 방지

### 6.3 하이퍼파라미터 탐색 — **P3**
- lookback(30/60/90), num_heads(4/8), ff_dim(128/256), forecast_horizon(7/14/30) 그리드 탐색
- Bayesian Optimization(Optuna) 도입 권장

### ~~6.4 Early Stopping + 학습 설정~~ — **P2, ✅ 수정됨**
- `EarlyStopping(monitor='val_loss', patience=10, restore_best_weights=True)` 적용
- epoch 50 → 200, `validation_split=0.15` 추가

### 6.5 시작 시 필수 환경변수 검증 — **P3**
- `MONGODB_URI`, `DATABASE_URL` 등 누락 시 명시적 실패
- 현재 `db is None` 체크는 있으나 시작 시점 통합 검증 없음

### 6.6 MongoDB bulk_write 재시도 — **P3**
- 지수 백오프(exponential backoff) 재시도 정책
- `save_predictions_to_db()`에 적용

### 6.7 모듈 분리 (리팩토링) — **P3**
- 현재: 2,321줄 단일 파일
- 목표 구조:
  ```text
  ml/
  ├── predict_optimized.py  → 오케스트레이션(entry point)
  ├── data_ingest.py        → get_all_data(), get_stock_data_from_db()
  ├── preprocess.py         → NaN 처리, 스케일링, 시퀀스 생성
  ├── model.py              → transformer_encoder(), build_model()
  ├── evaluate.py           → evaluate_predictions(), MAPE/MAE
  ├── recommend.py          → generate_recommendation(), analyze_rise
  └── db_io.py              → save_predictions_to_db(), get_predictions_from_db()
  ```

### ~~6.8 공식 평가 지표 저장~~ — **P2, ✅ 이미 구현됨**
- `save_predictions_to_db()`에서 MAE, MSE, RMSE, MAPE, Accuracy를 종목별로 MongoDB에 저장 중 (line 1921-1927)
- **잔여**: Directional Accuracy(방향 정확도)는 미구현 → 4.3(벤치마크 비교) 시 함께 추가 예정

---

## 우선순위 요약 (통합)

> ✅ = 수정됨, ⚠️ = 부분 수정, ❌ = 미수정
> 정렬: 우선순위(P0→P4) → 영향도(높→낮) → 난이도(낮→높)

### 미수정 항목

| # | 우선순위 | Phase | 항목 | 난이도 | 영향도 | 비고 |
|---|---------|-------|------|-------|-------|------|
| ~~1~~ | ~~**P0**~~ | ~~3.3~~ | ~~가격→수익률 예측~~ | ~~높음~~ | ~~**치명적**~~ | ✅ log return 전환 완료 |
| ~~2~~ | ~~**P1**~~ | ~~2.2~~ | ~~yfinance 키 통일~~ | ~~중간~~ | ~~높음~~ | ✅ PostgreSQL 매핑으로 통일 |
| ~~3~~ | ~~**P1**~~ | ~~2.3~~ | ~~FRED 키 통일~~ | ~~중간~~ | ~~높음~~ | ✅ PostgreSQL 매핑으로 통일 |
| ~~4~~ | ~~**P1**~~ | ~~3.1~~ | ~~Positional Encoding~~ | ~~중간~~ | ~~높음~~ | ✅ Sinusoidal PE 추가 |
| 9 | **P2** | 4.4 | 생존 편향 제거 | 중간 | 높음 | |
| ~~10~~ | ~~**P2**~~ | ~~3.2~~ | ~~Add→Concatenate~~ | ~~낮음~~ | ~~중간~~ | ✅ Concatenate(axis=-1) 적용 |
| 11 | **P2** | 3.6 | 확률적 예측 | 중간 | 중간 | MC Dropout |
| 12 | **P2** | 4.1 | Walk-Forward 백테스트 | 높음 | 중간 | 레짐 구간 검증 포함 |
| 13 | **P2** | 4.2 | 추천 로직 정교화 | 중간 | 중간 | P-R 기반 임계값 |
| ~~14~~ | ~~**P2**~~ | ~~6.1~~ | ~~거시지표 래그~~ | ~~낮음~~ | ~~중간~~ | ✅ shift(1) 적용 |
| ~~15~~ | ~~**P2**~~ | ~~6.2~~ | ~~이상치 윈저화~~ | ~~낮음~~ | ~~중간~~ | ✅ 3σ 클리핑 |
| ~~16~~ | ~~**P2**~~ | ~~6.8~~ | ~~평가 지표 공식 저장~~ | ~~낮음~~ | ~~중간~~ | ✅ 이미 구현됨 (방향 정확도만 잔여) |
| 17 | **P2** | 2.6 | FRED 키 전환 갭 처리 | 낮음 | 중간 | 매핑 테이블 |
| 18 | **P2** | 4.5 | 거래비용 반영 | 낮음 | 중간 | |
| 19 | **P3** | 3.4 | 클러스터/개별 모델 | 높음 | 높음 | C안 Multi-Task |
| 20 | **P3** | 3.5 | 데이터량 부족 해결 | 높음 | 높음 | Pre-train → Fine-tune |
| 21 | **P3** | 5.4 | 시장 레짐 감지기 | 높음 | 높음 | |
| 22 | **P3** | 6.3 | 하이퍼파라미터 탐색 | 중간 | 높음 | Optuna |
| 23 | **P3** | 4.3 | 벤치마크 비교 | 중간 | 중간 | |
| 24 | **P3** | 5.5 | 설명 가능성 (SHAP) | 중간 | 중간 | |
| 25 | **P3** | 6.7 | 모듈 분리 | 높음 | 중간 | 2,321줄 → 7파일 |
| 26 | **P3** | 6.5 | 환경변수 검증 | 낮음 | 낮음 | |
| 27 | **P3** | 6.6 | bulk_write 재시도 | 낮음 | 낮음 | |
| 28 | **P4** | 5.6 | 드리프트 감지 | 중간 | 중간 | |
| 29 | **P4** | 5.2 | 모델 버전 관리 | 중간 | 낮음 | |
| 30 | **P4** | 5.3 | 모니터링+로깅 | 중간 | 낮음 | print→logging 포함 |
| 31 | **P4** | 5.1 | 컨테이너 수정 | 낮음 | 낮음 | |

### 수정 완료 항목

| Phase | 항목 | 완료 시점 | 비고 |
|-------|------|----------|------|
| 1.1 | NaN→0 제거 | Phase 1 P0 | `ffill()` + `dropna()` + `median()` |
| 1.2 | 출력층 softplus | Phase 1 P0 | `activation='softplus'` (line 691) |
| 1.3 | data leakage 제거 | Phase 1 P0 | `train_data`에만 `fit()` (line 929) |
| 1.7 | bfill 제거 | Phase 1 P0 | `ffill()` only |
| 2.1 | 주말/공휴일 필터 | Phase 1 P0 | `stocks_added > 0` 필터 |
| 1.4 | off-by-one 수정 | Phase 1 P1 | `shift(-(forecast_horizon - 1))` |
| 1.5 | MAPE 0나누기 방지 | Phase 1 P1 | `actual != 0` 필터 |
| 1.6 | 하드코딩 제거 | Phase 1 P1 | `get_target_columns_from_db()` 호출 |
| 6.4 | Early Stopping | Phase 6 P2 | `patience=10, val_split=0.15, epochs=200` |
| 2.2 | yfinance 키 통일 | Phase 2 P1 | `get_indicator_key_mappings()` name→ticker 매핑 |
| 2.3 | FRED 키 통일 | Phase 2 P1 | `get_indicator_key_mappings()` name→code 매핑 |
| 3.1 | Positional Encoding | Phase 3 P1 | `SinusoidalPositionalEncoding` 레이어 |
| 3.2 | Add→Concatenate | Phase 3 P2 | `Concatenate(axis=-1)` 정보 보존 |
| 6.1 | 거시지표 래그 | Phase 6 P2 | `shift(1)` look-ahead bias 방지 |
| 6.2 | 이상치 윈저화 | Phase 6 P2 | 3σ 기준 클리핑 |
| 3.3 | 가격→수익률 전환 | Phase 3 P0 | log return 예측, `np.exp()` 역변환 |
| 2.5 | 데이터 포맷 정규화 | 확인 완료 | 현재 A안(close_price only)으로 동작 중 |

---

## 실행 순서 권장

### Sprint 1~3: ✅ 완료 (2026-02-22)

모든 P0/P1 버그 수정, 데이터 파이프라인 개선, 핵심 아키텍처 개선 완료:
- Phase 1 (P0/P1): data leakage, softplus, off-by-one, MAPE, 하드코딩, Early Stopping
- Phase 2 (P1): yfinance/FRED 키 매핑 통일
- Phase 3 (P0~P2): log return 전환, Positional Encoding, Concatenate
- Phase 6 (P2): 거시지표 래그, 이상치 윈저화

### Sprint 4 (다음): 평가 고도화
1. **3.6** MC Dropout 확률적 예측
2. **4.1** Walk-Forward 백테스트 + 레짐 구간 검증
3. **4.2** 추천 로직 정교화
4. **4.4 + 4.5** 생존 편향 + 거래비용 반영
5. **2.6** FRED 키 전환 갭 처리

### Sprint 5+: 장기 과제
- 3.4 (클러스터 모델), 3.5 (Pre-train), 5.x (인프라), 6.7 (모듈 분리)

---

## 부록: 폐기/통합된 항목

| 원본 항목 | 처리 | 사유 |
|-----------|------|------|
| 개선계획 1.1, 1.2, 1.3, 1.7, 2.1 | ✅ 완료 | 코드에 반영 확인됨 (`fix/predict-model-critical-bugs` 브랜치) |
| 개선계획 2.5 데이터 포맷 | ✅ 확인 | 현재 A안(close_price only)으로 정상 동작 |
| 비교분석 전체 | 통합 완료 | §4 리뷰 전용 항목 → Phase 6으로 흡수 |
| 비교분석 §2 도메인별 매핑 | 참조 완료 | 개별 항목으로 Phase에 배치 완료 |

---

## 부록 B: Vertex AI 학습 결과 (2026-02-22)

### Full Training (v8 패키지, Job `7298941828835835904`)
- **모델 파라미터**: 539,859개, SinusoidalPositionalEncoding + Concatenate
- **학습**: 18 epochs (EarlyStopping patience=10, best epoch 8)
- **성능**: val_loss=0.0096, val_mae=0.0707
- **결과**: 35종목 분석, 251,615 stock_predictions, 7,189 daily predictions 저장

### Fine-tuning (Job `450514907977220096`)
- **학습**: 22 epochs (best epoch 12), lr=5e-05
- **성능**: val_loss=0.0075 (↓22%), val_mae=0.0614 (↓13%)
- **상위 정확도**: 로빈후드 95.9%, 블룸에너지 94.2%, 비스트라 에너지 94.2%, 애플 93.8%
- **STRONG BUY**: 네비우스 그룹 +9.38%, SOXX ETF +8.86%, 월마트 +6.95%

### 코드리뷰 수정 (PR #56)
| 항목 | 심각도 | 수정 내용 |
|------|--------|-----------|
| S1 하드코딩 DB 비밀번호 | 높음 | `os.getenv()` fallback 제거 → `EnvironmentError` raise |
| S3 substring 컬럼 매칭 | 높음 | `if col in col_str` → `f"{col}_Predicted"` 정확 매칭 (2곳) |
| Q2 forecast_horizon 중복 | 낮음 | 2단계 중복 정의 제거 |
| CI JobResult import 누락 | 높음 | lazy import 시 모듈 스코프 사용 타입은 반드시 모듈 레벨 import |

> 이 문서는 `AI예측모델_개선_계획.md`와 `AI예측모델_리뷰_vs_개선계획_비교분석.md`를 대체합니다.
> 실제 코드(predict_optimized.py)와 MongoDB 데이터 실측에 기반하여 검증된 내용만 포함합니다.
