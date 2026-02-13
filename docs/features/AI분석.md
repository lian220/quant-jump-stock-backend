# AI 분석 (Vertex AI 예측)

**최종 업데이트**: 2026-02-13

---

## 개요

Google Vertex AI Custom Training Jobs를 활용하여 Transformer 모델 기반의 주가 예측을 수행하는 기능.
매일 22:00(KST)에 `EconomicDataUpdate2JobAdapter`의 2단계로 실행되며, 경제 데이터 수집 직후 자동으로 트리거된다.

---

## 스케줄러

| 항목 | 값 |
|------|-----|
| **Quartz Job** | `EconomicDataUpdate2JobAdapter` (2단계) |
| **Job 이름** | `economicDataUpdate2Job` |
| **Cron 표현식** | `0 0 22 * * ?` (Asia/Seoul) |
| **실행 시간** | 매일 22:00 KST (경제 데이터 수집 완료 후) |
| **실행 조건** | `gcp.enabled=true` |

> 1단계(경제 데이터 수집)가 완료된 후 자동으로 2단계(Vertex AI 예측)가 실행됨.
> 상세는 [데이터수집.md](./데이터수집.md) 참조.

---

## 파이프라인

```
22:00  EconomicDataUpdate2JobAdapter.execute()
       │
       ├─ [1단계] 경제 데이터 수집 (→ 데이터수집.md)
       │
       └─ [2단계] runVertexAIPrediction()
                  │
                  ├─ Kafka 발행: vertex.ai.run.request
                  │   → Data Engine: VertexAIHandler.handle()
                  │   → PredictionService.run_prediction()
                  │
                  ├─ ML 패키지 업로드 (GCS)
                  │   → predict_optimized.py → gs://quantiq-ml-models/
                  │
                  └─ Vertex AI CustomJob 생성
                      → GPU 기반 모델 학습/Fine-tuning
                      → MongoDB: stock_predictions 저장
```

---

## 구현 상세

### Core (Kotlin)

| 파일 | 경로 |
|------|------|
| Job Adapter | `adapter/input/scheduler/EconomicDataUpdate2JobAdapter.kt` |
| Vertex AI UseCase | `domain/vertexai/port/input/VertexAIUseCase` |

### Data Engine (Python)

| 파일 | 경로 |
|------|------|
| Kafka 핸들러 | `adapter/input/kafka/handlers.py` → `VertexAIHandler` |
| Vertex AI 서비스 | `adapter/output/gcp/vertexai_service.py` → `VertexAIService` |
| 예측 오케스트레이터 | `application/ml/prediction_service.py` → `PredictionService` |
| ML 학습 스크립트 | `src/ml/predict_optimized.py` (2,197줄) |
| Kafka 토픽 | `vertex.ai.run.request` |

### 모델 아키텍처

**Transformer (Multi-Input)**

```
Input 1: 주가 시계열 (90일 lookback)
    → 4x Transformer Encoder (8-head attention, FF dim 256)
    → Dense(64)
                    ↓
                  Merge → Dense(128) → GlobalAveragePooling1D → Output
                    ↑
Input 2: 경제 지표 시계열 (90일 lookback)
    → 4x Transformer Encoder (8-head attention, FF dim 256)
    → Dense(64)
```

| 파라미터 | 값 |
|----------|-----|
| Lookback | 90일 |
| Forecast Horizon | 14일 (14일 후 종가 예측) |
| Attention Heads | 8 |
| FF Dimension | 256 |
| Optimizer | Adam (lr=0.0001) |
| Loss | MSE |
| Scaler | MinMaxScaler (주가, 경제 지표 별도) |

### 학습 모드

| 모드 | Epochs | 소요 시간 | 사용 시점 |
|------|--------|-----------|-----------|
| **Full Training** | 50 | 25~30분 | 최초 학습 또는 모델 초기화 |
| **Fine-tuning** | 5 | 3~5분 | 일일 업데이트 (기존 모델 로드) |

일일 스케줄러는 **Fine-tuning 모드**로 실행 (기존 모델 대비 83% 시간 단축).

### 데이터 흐름 (predict_optimized.py)

1. MongoDB 연결 → `daily_stock_data`에서 최근 24개월 데이터 로드
2. PostgreSQL → `stocks`(활성 종목), `fred_indicators`, `yfinance_indicators` 로드
3. 데이터 전처리: NaN 처리 (ffill → bfill → 0 fill), MinMaxScaler
4. Transformer 모델 학습 (또는 Fine-tuning)
5. 전체 기간 예측 수행
6. 역변환(inverse_transform) → 실제 가격 복원
7. MongoDB `stock_predictions`에 저장
8. Slack 알림 발송 (성공/실패)

---

## 데이터 스키마

### MongoDB: `stock_predictions`

```json
{
  "_id": "697b539afbb7eeabaa3449ca",
  "date": "2026-01-28T00:00:00.000Z",
  "ticker": "GOOGL",
  "predicted_price": 336.91,
  "actual_price": 327.93,
  "forecast_horizon": 14,
  "created_at": "2026-01-28T14:16:08.933Z",
  "updated_at": "2026-01-28T14:16:08.933Z"
}
```

**예측 대상**: S&P 500 상위 종목 (PostgreSQL `stocks` 테이블 활성 종목)

---

## 외부 API / 인프라

### Google Vertex AI

| 항목 | 값 |
|------|-----|
| **플랫폼** | Google Cloud Vertex AI Custom Training Jobs |
| **GPU** | NVIDIA Tesla T4 (기본), L4 (DWS 지원) |
| **머신 타입** | n1-standard-4 |
| **컨테이너** | `us-docker.pkg.dev/vertex-ai/training/pytorch-gpu.1-13:latest` |
| **모델 저장소** | GCS (`gs://quantiq-ml-models/models/transformer_stock_model.h5`) |
| **예상 비용** | ~$0.03/day (~$1/month) |
| **활성화 조건** | `gcp.enabled=true` 환경변수 |

### 환경변수 (Vertex AI Job에 전달)

| 변수 | 용도 |
|------|------|
| `VERTEX_AI_MONGODB_URI` | MongoDB 접속 |
| `VERTEX_AI_DB_HOST/PORT/NAME/USER/PASSWORD` | PostgreSQL 접속 |
| `VERTEX_AI_SLACK_WEBHOOK_URL_TRADING` | Slack 알림 |
| `VERTEX_AI_REQUEST_ID` | 요청 추적 ID |

---

## 구현 상태

| 항목 | 상태 |
|------|------|
| Transformer 모델 구현 | ✅ 완료 |
| Fine-tuning 모드 | ✅ 완료 |
| Vertex AI CustomJob 통합 | ✅ 완료 |
| GCS 모델 업로드/로드 | ✅ 완료 |
| MongoDB 예측 결과 저장 | ✅ 완료 |
| Slack 알림 | ✅ 완료 |
| Kafka 이벤트 발행/수신 | ✅ 완료 |

---

## 관련 문서

- [데이터수집.md](./데이터수집.md) — 1단계: AI 예측의 입력 데이터 수집
- [기술적분석.md](./기술적분석.md) — 예측 결과 검증용 기술적 지표
- [스케줄러 아키텍처](../architecture/스케줄러_아키텍처.md)
- [스케줄러 운영 가이드](../setup/스케줄러_운영_가이드.md)
