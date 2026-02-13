# 스케줄러 파이프라인 리팩토링 계획

> 작성일: 2026-02-13
> 상태: TODO
> 관련: `QuartzConfig.kt`, `EconomicDataUpdate2JobAdapter.kt`, `ParallelAnalysisJob.kt`

---

## 배경

현재 스케줄러 파이프라인에 **3가지 구조적 문제**가 있어 데이터 정합성과 예측 품질에 영향을 줌.

### 현재 타임라인 (KST)

```
06:05  EconomicDataUpdateJob      → FRED + Yahoo Finance + 종목 35개 수집
22:00  EconomicDataUpdate2Job     → 경제 데이터 재수집 + Vertex AI 예측 (GCP only)
23:05  ParallelAnalysisJob        → 기술적 분석(SMA/RSI/MACD) + 감정 분석
00:30  AutoBuyJob                 → 자동 매수
```

---

## 문제점

### 문제 1: EST(겨울) 시간대에 경제 데이터 발표 전 수집

| 시기 | CPI/NFP 발표 (8:30 AM ET → KST) | 현재 Job 시각 | 결과 |
|------|----------------------------------|---------------|------|
| EDT (3~11월) | 21:30 KST | 22:00 | ✅ 발표 30분 후 |
| **EST (11~3월)** | **22:30 KST** | **22:00** | ❌ **발표 30분 전** |

- `QuartzConfig.kt` 주석에 "CPI/NFP 발표(21:30) 직후"라 적혀있지만 이건 EDT(서머타임) 기준
- EST 기간(현재)에는 22:00 실행 시 CPI/NFP가 아직 미발표 → FRED API에서 이전 값 반환

### 문제 2: Vertex AI 레이스 컨디션

```kotlin
// EconomicDataUpdate2JobAdapter.kt:44-52
economicDataUseCase.triggerEconomicDataUpdate().get()  // ← Kafka 메시지 발행 완료만 대기
vertexAIService.runPrediction()                         // ← 바로 실행!
```

- `.get()`은 **Kafka 메시지 발행**만 기다림
- Data Engine에서 실제 데이터 수집(FRED API 호출, Yahoo Finance 35종목 크롤링)은 비동기로 진행 중
- **Vertex AI가 데이터 수집 완료 전에 실행될 수 있음** → 불완전한 데이터로 예측

### 문제 3: Vertex AI가 기술적 분석 없이 실행

```
22:00  Vertex AI 예측 실행  ← 기술적 분석 데이터 없음
23:05  기술적 분석 실행      ← 65분 뒤에야 SMA/RSI/MACD 계산
```

- Vertex AI 모델이 기술적 분석 피처(SMA, RSI, MACD)를 입력으로 사용한다면, 현재 순서에서는 해당 데이터가 없는 상태로 예측
- 모든 분석 데이터가 확보된 후 Vertex AI를 실행해야 예측 품질 보장

---

## 개선안: 목표 타임라인

```
06:05  EconomicDataUpdateJob        → 미국장 마감 후 데이터 수집 (유지)

23:00  EconomicDataUpdate2Job       → 경제 데이터 재수집만 (Vertex AI 분리)
       ├─ EST: CPI/NFP 22:30 → 30분 버퍼 ✅
       └─ EDT: CPI/NFP 21:30 → 1.5시간 버퍼 ✅

23:30  ParallelAnalysisJob          → 기술적 + 감정 분석 (경제 데이터 수집 후)

00:00  VertexAIPredictionJob (신규) → 모든 데이터 확보 후 예측
       └─ Data Engine 수집 완료 보장 메커니즘 필요

00:30  AutoBuyJob                   → 자동 매수 (유지)
```

---

## TODO 항목

### Phase 1: 스케줄 시간 조정 (즉시 적용 가능)

- [ ] **1-1. `economicDataUpdate2Job` 크론 시간 변경**
  - 파일: `QuartzConfig.kt`
  - 변경: `0 0 22 * * ?` → `0 0 23 * * ?` (22:00 → 23:00)
  - 주석 수정: EST/EDT 시간대 모두 커버한다는 설명 추가
  - 리스크: 낮음 (시간만 변경)

- [ ] **1-2. `parallelAnalysisJob` 크론 시간 변경**
  - 파일: `QuartzConfig.kt`
  - 변경: `0 5 23 * * ?` → `0 30 23 * * ?` (23:05 → 23:30)
  - 사유: 경제 데이터 재수집(23:00) 완료 후 실행되도록 버퍼 확보
  - 리스크: 낮음

- [ ] **1-3. `QuartzConfig.kt` 주석 전체 업데이트**
  - EST/EDT 시간대 차이 명시
  - 각 Job의 의존관계 및 실행 순서 문서화
  - 미국 경제지표 발표 시간 매핑 테이블 추가

### Phase 2: Vertex AI 분리 및 레이스 컨디션 해결

- [ ] **2-1. Vertex AI 전용 Job 분리**
  - 신규 파일: `VertexAIPredictionJobAdapter.kt`
  - `EconomicDataUpdate2JobAdapter`에서 Vertex AI 호출 제거
  - `QuartzConfig.kt`에 새 Job + Trigger 등록 (00:00 실행)
  - `EconomicDataUpdate2JobAdapter`는 경제 데이터 재수집만 담당

- [ ] **2-2. `EconomicDataUpdate2JobAdapter` 단순화**
  - `VertexAIService` 의존성 제거
  - `@ConditionalOnProperty("gcp.enabled")` 제거 (경제 데이터 수집은 GCP와 무관)
  - GCP 비활성 환경에서도 경제 데이터 재수집이 동작하도록 변경

- [ ] **2-3. 데이터 수집 완료 보장 메커니즘 검토**
  - 선택지 A: Data Engine에서 수집 완료 시 Kafka 완료 이벤트 발행 → Core에서 Vertex AI 트리거
  - 선택지 B: Vertex AI Job에서 Data Engine REST API로 수집 상태 폴링
  - 선택지 C: 충분한 시간 간격(현재 30분)으로 암묵적 보장 (단순하지만 불확실)
  - 선택지 D: Data Engine 내부에서 수집 완료 후 Vertex AI 직접 트리거
  - 결정 필요: 복잡도 vs 확실성 트레이드오프

### Phase 3: ISM PMI 등 추가 지표 대응 (선택)

- [ ] **3-1. ISM PMI 발표 시간 대응 검토**
  - ISM PMI: 10:00 AM ET = 00:00 KST(EST) / 23:00 KST(EDT)
  - 현재 23:00 재수집으로는 EST 기간 ISM PMI 미포함
  - 선택지 A: 경제 데이터 재수집을 00:15로 추가 이동 (자동매수 00:30과 충돌 위험)
  - 선택지 B: ISM PMI 발표일(월 1회)만 별도 스케줄
  - 선택지 C: ISM PMI는 다음날 06:05 수집에서 포함 (1일 지연 허용)
  - 결정 필요: 월 1회 지표 대비 파이프라인 복잡도 증가 트레이드오프

- [ ] **3-2. 서머타임 자동 전환 고려**
  - 현재: 고정 크론 스케줄 (KST 기준)
  - 개선 가능: US/Eastern 타임존으로 크론 설정하면 EST/EDT 자동 반영
  - 단, 다른 Job(한국 시간 기반)과의 혼용 시 혼란 가능
  - 결정 필요: 타임존 혼용 vs 고정 시간 + 여유 버퍼

---

## 수집 데이터 정리 (참고)

### `collect_economic_data()` 에서 수집하는 전체 데이터

| 구분 | 소스 | 항목수 | 내용 |
|------|------|--------|------|
| FRED 지표 | FRED API | 16개 | 기준금리, 실업률, CPI, GDP, 국채금리 등 |
| Yahoo Finance 지표 | yfinance | 24개 | S&P500, VIX, 금, 달러인덱스, KOSPI 등 |
| 개별 종목 | yfinance | 35개 | OHLCV + 펀더멘탈 ~40개 필드 |

→ 주가 데이터는 경제 데이터 수집에서 함께 채워짐 ✅
→ 기술적 분석(SMA/RSI/MACD)은 별도 `ParallelAnalysisJob`에서 실행

### Vertex AI 입력 데이터 확인 필요

- [ ] Vertex AI 모델이 실제로 어떤 피처를 사용하는지 확인
  - `daily_stock_data` (경제지표 + 주가) 만 사용?
  - `stock_recommendations` (기술적 분석 결과) 도 사용?
  - → 이에 따라 Vertex AI 실행 시점이 달라짐

---

## 우선순위

| 순위 | 항목 | 난이도 | 영향도 | 비고 |
|------|------|--------|--------|------|
| 1 | Phase 1 (시간 조정) | 낮음 | 높음 | 크론 식 변경만으로 즉시 효과 |
| 2 | Phase 2-1, 2-2 (Job 분리) | 중간 | 높음 | Vertex AI 독립 실행 보장 |
| 3 | Phase 2-3 (완료 보장) | 높음 | 높음 | 아키텍처 결정 필요 |
| 4 | Phase 3 (ISM/서머타임) | 중간 | 낮음 | 월 1회 지표, 선택적 |
