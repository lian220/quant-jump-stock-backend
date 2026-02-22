# AI 예측 모델 개선 계획

> 대상 파일: `quant-jump-stock-data-engine/src/ml/predict_optimized.py`
> 작성일: 2026-02-22
> 최종 수정: 2026-02-22 (퀀트/금융 전문가 + ML/수학·통계 전문가 리뷰 반영)
> 상태: 검토 완료

---

## Phase 1: 치명적 버그 수정 (현재 구조 유지)

현재 코드의 골격은 유지하면서 결과를 완전히 망가뜨리는 버그만 수정합니다.

### 1.1 NaN → 0 채우기 제거
- [x] **위치**: line 893-894
- **현재**: `data[target_columns].fillna(0)` / `data[economic_features].fillna(0)`
- **문제**: 주가 $0이 대량 주입 → MinMaxScaler 범위 [0, $230]에서 33%가 $0
- **수정**: `fillna(0)` 제거, `ffill().bfill()` 후에도 NaN인 행은 `dropna()` 처리
- **영향 범위**: 스케일링 정확도, 학습 데이터 품질

### 1.2 출력층 activation 추가 (음수 가격 방지)
- [x] **위치**: line 691
- **현재**: `outputs = Dense(target_size)(merged)` — 제약 없이 (-∞, +∞) 출력
- **문제**: inverse_transform 후 음수 주가 발생 (Apple: -$51, AMD: -$0.22)
- ~~**수정 (기존안)**: `activation='sigmoid'` — [0,1] 범위~~
  - ⚠️ **수학적 오류**: sigmoid가 [0,1]을 출력해도 `MinMaxScaler.inverse_transform()` 수식 `X*(max-min)+min`을 통과하면 음수 방지 효과가 없음. min이 0이 아닌 이상 scaler 범위 밖의 값이 여전히 발생 가능.
- **올바른 수정**:
  - **근본 해결**: 3.3 가격→수익률 변환 적용 시 sigmoid 자체가 불필요해짐 (권장)
  - **임시 수정**: `softplus` activation 또는 출력에 `tf.math.abs()` 적용
    ```python
    outputs = Dense(target_size, activation='softplus')(merged)  # (0, +∞) 보장
    ```
- **영향 범위**: 예측값 범위

### 1.3 학습 시퀀스 data leakage 제거
- [x] **위치**: line 908-915
- **현재**: `stock_scaler.fit_transform(data[target_columns])` — 전체 데이터에 fit
- **문제**: 테스트 데이터의 min/max가 학습에 유입 (미래 정보 누출)
- **수정**: `stock_scaler.fit(train_data)` → `transform(전체)` 방식으로 변경
- **영향 범위**: 학습/평가 신뢰도

### 1.4 평가 시 off-by-one 오류 수정
- [ ] **위치**: line 1992
- **현재**: `actual = data[actual_col].shift(-forecast_horizon)` — 14일 shift
- **문제**: 학습 타깃은 `i + forecast_horizon - 1` (13일 후)인데, 평가는 14일 shift
- **수정**: `shift(-forecast_horizon)` → `shift(-(forecast_horizon - 1))`
- **영향 범위**: MAPE, MAE 등 모든 평가 지표 정확도

### 1.5 MAPE 0 나누기 방지
- [ ] **위치**: line 2007
- **현재**: `mape = (abs((actual - predicted) / actual).mean()) * 100`
- **문제**: actual이 0이면 무한대 발생
- **수정**: `actual.replace(0, np.nan)` 후 계산하거나 `np.where(actual != 0, ...)` 사용
- **영향 범위**: 평가 지표 안정성

### 1.6 2단계 target_columns 하드코딩 제거
- [ ] **위치**: line 2220-2228
- **현재**: 35개 종목명 하드코딩 (1단계는 DB에서 동적 조회)
- **문제**: DB에서 종목 추가/삭제 시 2단계와 불일치
- **수정**: 1단계처럼 DB에서 가져오거나, 1단계 결과에서 종목명 추출
- **영향 범위**: 2단계 분석 정확성

### 1.7 bfill 제거 (look-ahead bias 방지)
- [x] **위치**: line 889-890
- **현재**: `data[target_columns].ffill().bfill()`
- **문제**: `bfill`은 미래 데이터로 과거를 채움 (시계열 위반)
- **수정**: `ffill()`만 유지, `bfill()` 제거. 앞쪽 NaN은 `dropna()` 처리
- **영향 범위**: 시계열 무결성

---

## Phase 2: 데이터 파이프라인 개선

MongoDB 데이터 자체의 품질 문제를 해결합니다.

### 2.1 주말/공휴일 빈 문서 필터링
- [x] **위치**: `get_all_data()` 함수 (line ~500-650)
- **현재**: 주말/공휴일에도 빈 문서가 저장됨 (stocks: {}, fred: {})
- **문제**: 빈 행이 DataFrame에 포함 → 모든 컬럼 NaN → fillna(0)으로 $0 주입
- **수정**: stocks 필드가 비어있는 문서는 스킵하는 필터 추가
- **검증 방법**: 필터 후 `data.shape` 로그 출력

### 2.2 yfinance_indicators 키 이름 불일치 해결
- [ ] **위치**: `get_all_data()` 내 yfinance 처리부 (line 576-585)
- **현재**:
  - 2026-02-11 이전: 한국어명 (`"S&P 500 ETF"`, `"나스닥 종합지수"`)
  - 2026-02-12 이후: 티커 (`"SPY"`, `"^GSPC"`)
- **문제**: 같은 지표가 두 개 컬럼으로 분리 → 각각 대부분 NaN
- **수정 방안**:
  - A) DB 수집 코드에서 키 이름 통일 (근본적 해결)
  - B) `get_all_data()`에서 티커→한국어명 매핑 적용 (임시)
- **우선순위**: 높음 (최근 데이터가 아예 다른 컬럼으로 들어감)

### 2.3 FRED 경제지표 희소성 대응
- [ ] **현재 상황**:
  - 7353개 문서 중 273개만 FRED 데이터 보유 (3.7%)
  - 2025-01-01 이전 데이터엔 FRED 0건
  - 월간/분기 발표 지표 (기준금리, GDP 등)는 연간 3~12건만 존재
- **문제 1**: 20년치 데이터 중 96.3%가 FRED 지표 NaN → fillna(0) → 왜곡
- **문제 2 (추가)**: ⚠️ **FRED Revision Bias** — GDP 등 일부 지표는 발표 후 수정(revision)이 이루어짐. 수집 시점에 따라 실시간 버전(재발표 이전)과 수정 버전이 혼용될 경우 또 다른 look-ahead bias 발생 가능. B안 채택 시 반드시 확인 필요.
- **수정 방안**:
  - A) 학습 기간을 2025-02-01 이후로 제한 (FRED 데이터 존재 구간)
  - B) FRED 지표를 `ffill()`로 채우되 첫 출현 이전은 제외 + revision 버전 단일화 확인
  - C) FRED를 아예 피처에서 제외하고 yfinance_indicators만 사용
- **권장**: B안 (월간/분기 데이터는 발표 시점 이후 ffill이 자연스러움) — revision 이슈 확인 후 진행

### 2.4 stock 가격 데이터 null 대량 발생 원인 조사
- [ ] **현재 상황**: 모든 종목에서 127~132건 null (2025년 이후 기준 약 33%)
- **추정 원인**:
  - 데이터 수집 스크립트가 특정 날짜에 실패
  - 주말/공휴일 문서에 stocks 필드가 빈 객체로 저장
  - yfinance API 에러 시 해당 종목 누락
- **조사 필요**:
  - null이 발생하는 날짜 패턴 분석 (연속? 산발?)
  - 데이터 수집 코드 로그 확인
  - 주말/공휴일 필터링 후 null 비율 재확인

---

## Phase 3: 모델 아키텍처 근본 개선

현재 모델의 설계적 한계를 해결합니다. Phase 1-2 완료 후 진행합니다.

### 3.1 Positional Encoding 추가
- [ ] **위치**: `transformer_encoder()` 함수 (line 659-671)
- **현재**: 트랜스포머에 위치 인코딩 없음
- **문제**: 90일 시퀀스에서 "어제"와 "3달 전"을 구분 못함 → 시계열 의미 상실
- **수정**: Sinusoidal 또는 Learnable Positional Encoding 추가
- **구현 예시**:
  ```python
  class PositionalEncoding(tf.keras.layers.Layer):
      def __init__(self, max_len, d_model):
          super().__init__()
          positions = np.arange(max_len)[:, np.newaxis]
          dims = np.arange(d_model)[np.newaxis, :]
          angles = positions / np.power(10000, 2 * (dims // 2) / d_model)
          angles[:, 0::2] = np.sin(angles[:, 0::2])
          angles[:, 1::2] = np.cos(angles[:, 1::2])
          self.pos_encoding = tf.constant(angles[np.newaxis, :, :], dtype=tf.float32)

      def call(self, x):
          return x + self.pos_encoding[:, :tf.shape(x)[1], :]
  ```

### 3.2 Add() → Concatenate() 결합 방식 변경
- [ ] **위치**: line 687
- **현재**: `merged = Add()([stock_encoded, econ_encoded])`
- **문제**: 원소별 합산 → 정보 상쇄 (주식 [+0.5] + 경제 [-0.5] = [0])
- **수정**: `Concatenate(axis=-1)`로 변경하고 후속 Dense 레이어 크기 조정
- **구현**:
  ```python
  merged = Concatenate(axis=-1)([stock_encoded, econ_encoded])
  merged = Dense(128, activation="relu")(merged)
  ```

### 3.3 예측 대상 변경: 가격 → 수익률
- [ ] **현재**: 원시 주가를 직접 예측 (MinMaxScaler → Dense → inverse_transform)
- **문제**:
  - 주가는 non-stationary → MinMaxScaler 범위가 학습/추론 시 다름
  - $100→$200 상승과 $200→$300 상승이 동일하게 취급됨
- **수정**: log return 또는 % 변화율을 예측 대상으로 변경
  ```python
  # 가격 → 수익률 변환
  returns = np.log(prices / prices.shift(1))
  # 예측 후 역변환
  predicted_price = current_price * np.exp(predicted_return)
  ```
- **영향**: 스케일링 문제 근본적 해결, 음수 가격 불가능

### 3.4 35개 종목 단일 모델 → 개별/클러스터 모델
- [ ] **현재**: 35개 종목을 하나의 모델로 동시 예측
- **문제**:
  - 반도체(NVDA)와 헬스케어(LLY)는 가격 패턴이 완전히 다름
  - 단일 모델이 모든 패턴을 학습하기 어려움
- **수정 방안**:
  - A) 섹터별 클러스터 모델 (반도체, 빅테크, 헬스케어, ETF 등)
  - B) 종목별 개별 모델 (가장 정확하지만 리소스 많음)
  - C) 공유 인코더 + 종목별 헤드 (Multi-Task Learning)
- **권장**: C안 (효율성과 정확도 균형)

### 3.5 학습 데이터량 부족 해결
- [ ] **현재**: ~260일 (2025-02-05 이후) × 35종목
  - 90일 lookback 차감 → 실제 학습 샘플 ~170개
  - 4-layer Transformer × 2 스트림은 이 데이터량에 과도
- **수정 방안**:
  - A) 모델 축소: Transformer 2레이어, hidden dim 줄이기
  - B) 데이터 증강: 2006년 이후 전체 데이터 활용 (FRED 없이)
  - C) 학습 전략: Pre-train (20년 데이터, 주가만) → Fine-tune (최근 1년, 경제지표 포함)
- **권장**: C안 (풍부한 과거 주가 + 최근 경제지표 활용)

### 3.6 확률적 예측 도입
- [ ] **현재**: 단일 포인트 예측 → "변화율"을 "확률"이라 부름
- **문제**: 예측 불확실성 정보 없음
- **수정 방안**:
  - A) Monte Carlo Dropout: 추론 시 Dropout 활성화 → 여러 번 예측 → 분포 추정
  - B) Quantile Regression: 10%, 50%, 90% 분위수 예측
  - C) Bayesian Neural Network
- **권장**: A안 (기존 코드 변경 최소)
  ```python
  # 추론 시 (training=True로 Dropout 활성화)
  predictions = [model(inputs, training=True) for _ in range(100)]
  mean_pred = np.mean(predictions, axis=0)
  std_pred = np.std(predictions, axis=0)
  ```
- ⚠️ **신뢰도 공식 수학적 오류 수정**:
  ```python
  # 기존 (잘못됨): mean_pred가 0 또는 음수이면 발산하거나 confidence > 1
  # confidence = 1 - (std_pred / mean_pred)

  # 수정: 정규화된 불확실성으로 항상 [0, 1] 범위 보장
  confidence = 1 - (std_pred / (std_pred + np.abs(mean_pred) + 1e-8))
  ```

---

## Phase 4: 평가 및 추천 시스템 개선

### 4.1 Walk-Forward 백테스트 도입
- [ ] **현재**: 고정 train/test 분할 (80/20)
- **문제**: 시계열에서 단일 분할은 특정 기간 편향
- **수정**: 롤링 윈도우 교차검증
  ```
  [Train: 2006~2024] → [Test: 2025-01~03]
  [Train: 2006~2025-03] → [Test: 2025-04~06]
  ...
  ```
- ⚠️ **레짐 변화 구간 별도 검증 (추가)**: 롤링 윈도우 외에 아래 특수 구간을 테스트셋으로 별도 지정하여 모델의 레짐 변화 대응력을 검증해야 함:
  | 구간 | 이벤트 |
  |---|---|
  | 2008-09 ~ 2009-03 | 금융위기 |
  | 2020-02 ~ 2020-04 | 코로나 쇼크 |
  | 2022-01 ~ 2022-12 | 금리 급등기 |
  - 일반 롤링 백테스트 MAPE가 낮더라도 위 구간 성능이 크게 떨어지면 실전 위험 큼

### 4.2 추천 로직 정교화
- [ ] **현재**: MAPE > 30 → HOLD, 아니면 변화율로 BUY/SELL
- **문제**: 모든 종목의 MAPE가 80~100% → 전부 "HOLD (Low Confidence)"
- **수정**: Phase 3.6의 확률적 예측 결과 활용
  - 상승 확률 > 70% & 신뢰도 > 60% → BUY
  - 상승 확률 > 85% & 신뢰도 > 75% → STRONG BUY
  - 하락 확률 > 70% → SELL
  - 기타 → HOLD
- ⚠️ **임계값 근거 필요 (추가)**: 위 70%/85%/60%/75% 수치는 임의값으로 과적합 위험 있음.
  - **권장**: Precision-Recall 커브를 그려 F1-score 또는 백테스트 수익률을 기준으로 임계값을 데이터 기반으로 설정
  - 연속 BUY/SELL 신호 시 포지션 관리 로직(최대 보유 기간, 중복 진입 방지)도 함께 정의 필요

### 4.3 벤치마크 대비 성능 비교
- [ ] **현재**: 절대적 MAPE만 계산
- **수정**: 단순 전략과 비교 (Buy & Hold, 이동평균 교차 등)
  - 모델 예측이 단순 전략보다 나은지 검증
  - 방향성 정확도 (Directional Accuracy) 추가

---

## Phase 4.5: 백테스트 신뢰도 보완 (퀀트 전문가 추가)

### 4.4 생존 편향(Survivorship Bias) 제거
- [ ] **현재**: 35개 종목은 현재 시장에서 살아남은 주식만 포함
- **문제**: 과거에 상장폐지되거나 크게 하락한 종목이 학습 데이터에서 제외 → 백테스트 성과 낙관 편향
- **수정 방안**:
  - 각 학습 기간 기준 시점의 S&P 500 구성 종목을 사용 (Point-in-Time 종목 선택)
  - 최소한 현재 종목 선정 기준과 생존 편향 가능성을 문서화하여 성과 해석 시 참고

### 4.5 거래비용 및 슬리피지 반영
- [ ] **현재**: 백테스트 및 추천 로직에 거래비용 없음
- **문제**: BUY/SELL 신호가 빈번하면 수수료+슬리피지로 실제 수익률이 현저히 낮아짐 (과최적화)
- **수정**:
  ```python
  # 백테스트 수익률 계산 시
  COMMISSION_RATE = 0.001   # 0.1% (매매 편도)
  SLIPPAGE = 0.0005         # 0.05% 슬리피지 가정
  net_return = gross_return - (COMMISSION_RATE + SLIPPAGE) * 2  # 매수+매도
  ```
- 14일 예측 기반 신호이므로 빈도는 낮지만, 연속 신호 발생 시 턴오버 비용 추적 필요

---

## Phase 5: 인프라 및 운영 개선

### 5.1 PyTorch 컨테이너 → TensorFlow 컨테이너
- [ ] **위치**: `src/config/vertex_ai_job_env.py` line 34
- **현재**: `pytorch-gpu.1-13:latest` 컨테이너에서 TensorFlow 코드 실행
- **수정**: `tf-gpu.2-14:latest` 또는 커스텀 컨테이너 사용

### 5.2 모델 버전 관리
- [ ] **현재**: GCS에 저장하지만 버전/메타데이터 관리 없음
- **수정**: 학습 하이퍼파라미터, 데이터 기간, 평가 지표를 함께 저장

### 5.3 모니터링 및 알림
- [ ] **현재**: Slack으로 성공/실패만 알림
- **수정**: MAPE 임계값 초과 시 경고, 음수 예측 발생 시 즉시 알림

### 5.4 시장 레짐(Market Regime) 감지기
- [ ] **현재**: 단일 모델이 불장/약세장/횡보장을 구분 없이 예측
- **문제**: 레짐에 따라 주가 패턴이 근본적으로 다름 → 단일 모델로 모든 레짐 커버 어려움
- **수정 방안**:
  - 별도 레짐 분류기(VIX 수준, 이동평균 기울기 등 기반) 학습
  - 앙상블: 레짐 분류 결과에 따라 서로 다른 예측 모델 가중치 적용
  - 최소한 추론 시 현재 레짐 레이블을 로그에 기록하여 성능 분석에 활용

### 5.5 설명 가능성(Explainability) 도입
- [ ] **현재**: 블랙박스 예측 — 어떤 피처가 예측에 기여했는지 알 수 없음
- **문제**: 모델 신뢰도 저하, 디버깅 어려움, 잘못된 피처 의존 감지 불가
- **수정**:
  ```python
  import shap
  explainer = shap.DeepExplainer(model, background_data)
  shap_values = explainer.shap_values(X_test)
  # 상위 기여 피처 시각화
  shap.summary_plot(shap_values, X_test, feature_names=feature_names)
  ```
  - 최소한 월별로 Top-10 기여 피처를 로그로 남겨 피처 드리프트 조기 감지

### 5.6 예측 분포 드리프트(Prediction Drift) 감지
- [ ] **현재**: MAPE 임계값 초과만 감지
- **문제**: MAPE가 낮아도 예측 분포 자체가 실제 분포에서 멀어지면 신호 품질 저하
- **수정**: PSI(Population Stability Index) 또는 KL-Divergence 기반 드리프트 모니터링
  ```python
  from scipy.stats import entropy
  # 매주 예측 분포 vs 실제 분포 KL Divergence 계산
  kl_div = entropy(actual_distribution, predicted_distribution)
  if kl_div > DRIFT_THRESHOLD:
      send_slack_alert(f"Prediction drift detected: KL={kl_div:.3f}")
  ```

---

## 우선순위 요약

> 전문가 리뷰(퀀트/금융 + ML/수학·통계) 반영 후 재조정

| 우선순위 | Phase | 항목 | 난이도 | 영향도 | 비고 |
|---------|-------|------|-------|-------|-----|
| **P0** | 1.3 | data leakage 제거 | 중간 | **치명적** | 이게 없으면 모든 평가 지표가 허구 |
| **P0** | 2.1 | 주말/공휴일 빈 문서 필터링 | 낮음 | 매우 높음 | 33% NaN의 근본 원인 |
| **P0** | 1.1 | NaN → 0 채우기 제거 | 낮음 | 매우 높음 | |
| **P0** | 1.7 | bfill 제거 | 낮음 | 높음 | |
| **P0** | 3.3 | 가격 → 수익률 예측 | 높음 | 매우 높음 | 1.2 sigmoid 오류도 함께 해결됨 |
| **P1** | 1.2 | 출력층 activation 수정 | 낮음 | 높음 | sigmoid→softplus로 변경, 3.3 완료 시 불필요 |
| **P1** | 3.1 | Positional Encoding | 중간 | 높음 | 트랜스포머 구조 의미 복원 |
| **P1** | 2.2 | yfinance 키 이름 통일 | 중간 | 높음 | 최근 데이터 활용 필수 |
| **P1** | 2.3 | FRED 희소성 대응 | 중간 | 높음 | revision bias 함께 확인 |
| **P1** | 1.4 | off-by-one 수정 | 낮음 | 중간 | |
| **P1** | 1.5 | MAPE 0 나누기 방지 | 낮음 | 낮음 | |
| **P1** | 1.6 | 하드코딩 제거 | 낮음 | 중간 | |
| **P2** | 2.4 | stock null 원인 조사 | 조사 | 높음 | |
| **P2** | 3.2 | Add → Concatenate | 낮음 | 중간 | |
| **P2** | 3.6 | 확률적 예측 도입 | 중간 | 중간 | 신뢰도 공식 수정 포함 |
| **P2** | 4.1 | Walk-Forward 백테스트 | 높음 | 중간 | 레짐 변화 구간 별도 검증 포함 |
| **P2** | 4.2 | 추천 로직 정교화 | 중간 | 중간 | 임계값 Precision-Recall 기반 설정 |
| **P2** | 4.4 | 생존 편향 제거 | 중간 | 높음 | 신규 추가 |
| **P2** | 4.5 | 거래비용 반영 | 낮음 | 중간 | 신규 추가 |
| **P3** | 3.4 | 클러스터/개별 모델 | 높음 | 높음 | |
| **P3** | 3.5 | 데이터량 부족 해결 | 높음 | 높음 | Pre-train 시 레이어 frozen 전략 필요 |
| **P3** | 4.3 | 벤치마크 비교 | 중간 | 중간 | |
| **P3** | 5.4 | 시장 레짐 감지기 | 높음 | 높음 | 신규 추가 |
| **P3** | 5.5 | 설명 가능성 (SHAP) | 중간 | 중간 | 신규 추가 |
| **P4** | 5.1 | 컨테이너 수정 | 낮음 | 낮음 | |
| **P4** | 5.2 | 모델 버전 관리 | 중간 | 낮음 | |
| **P4** | 5.3 | 모니터링 강화 | 중간 | 낮음 | |
| **P4** | 5.6 | 예측 드리프트 감지 | 중간 | 중간 | 신규 추가 |
