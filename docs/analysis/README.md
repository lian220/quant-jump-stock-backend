# 분석 기능 아키텍처

**최종 업데이트**: 2026-02-28

---

## 개요

QuantIQ의 분석 파이프라인은 **Cloud Scheduler → Pub/Sub → Data Engine(Python)** 구조로 동작한다.
Cloud Scheduler가 Pub/Sub 메시지를 발행하면 Data Engine이 실제 분석을 수행하고 결과를 MongoDB에 저장한다.
이브닝 파이프라인(23:40)은 `source="pipeline"`으로 완료 이벤트 기반 체이닝을 수행한다.

```text
┌──────────────────┐    Pub/Sub     ┌───────────────────────┐     MongoDB
│ Cloud Scheduler  │ ─────────────→ │  Data Engine (Python) │ ──────────→
│ (GCP)            │  토픽별 요청   │  Pub/Sub Handlers     │  결과 저장
└──────────────────┘                └───────────────────────┘
```

---

## 분석 카테고리

QuantIQ는 4가지 분석 방식을 독립적으로 운영하며, 향후 **Composite Score**로 통합 예정:

- **[기술적 분석](./technical/)**: SMA, RSI, MACD 등 차트 기반 지표
- **[감성 분석](./sentiment/)**: 뉴스/소셜미디어 감정 점수
- **[AI 예측](./ai/)**: Vertex AI Transformer 모델 기반 주가 예측
- **[지표 분석](./indicator/)**: FRED 경제 지표 (CPI, 금리 등)
- **[통합 분석](./composite/)**: 위 4가지를 결합한 종합 점수 (개발 중)

---

## 기능별 상세 문서

| # | 기능 | 트리거 | Pub/Sub 토픽 | MongoDB 컬렉션 | 문서 |
|---|------|--------|------------|----------------|------|
| 1 | 경제 데이터 수집 | evening-pipeline 23:40 (체이닝 1단계) | `economic-data-update-request` | `daily_stock_data` | **[데이터수집.md](../features/데이터수집.md)** |
| 2 | 기술적 분석 | 체이닝 2단계 (수집 완료 시 자동) | `analysis-technical-request` | `stock_recommendations` | **[기술적분석.md](./technical/기술적분석.md)** |
| 3 | 뉴스 감정 분석 | 체이닝 2단계 (수집 완료 시 자동) | `analysis-sentiment-request` | `sentiment_analysis` | **[뉴스감정분석.md](./sentiment/뉴스감정분석.md)** |
| 4 | AI 예측 (Vertex AI) | 체이닝 3단계 (기술적 분석 완료 시 자동) | `vertex-ai-run-request` | `stock_predictions` | **[인공지능분석.md](./ai/인공지능분석.md)** |
| 5 | 종목 추천 | stock-recommendation 00:20 (standalone) | `analysis-recommendation-request` | `stock_recommendations` | — |

---

## 일일 분석 타임라인 (KST)

```text
── Cloud Scheduler 이브닝 파이프라인 (23:40, source="pipeline") ──
[1단계] 경제 데이터 수집  → FRED + Yahoo Finance → daily_stock_data
[2단계] 기술적 분석 + 감정 분석 (병렬, 자동 트리거)
        ├─ SMA/RSI/MACD → stock_recommendations
        └─ Alpha Vantage 뉴스 → sentiment_analysis
[3단계] Vertex AI 예측 (기술적 분석 완료 시 자동)
        └─ Transformer Fine-tuning → stock_predictions

── Cloud Scheduler (standalone) ──
00:20  종목 추천  (stock-recommendation, Composite Score)

── Core Quartz (트레이딩) ──
00:30  자동 매수  (AutoBuyJobAdapter)
06:30  미체결 주문 정리  (CleanupOrdersJobAdapter)
07:00  포트폴리오 수익 보고  (PortfolioProfitReportJobAdapter)
매1분  자동 매도 체크  (AutoSellJobAdapter, 미국 장중만)
```

---

## MongoDB 컬렉션 요약

| 컬렉션 | 설명 | 업데이트 주기 | 스케줄러 |
|--------|------|--------------|---------|
| `daily_stock_data` | FRED + Yahoo Finance 통합 경제 지표 | 파이프라인 1단계 | Cloud Scheduler → Data Engine |
| `stock_predictions` | Vertex AI 주가 예측 결과 | 파이프라인 3단계 | 체이닝 자동 트리거 |
| `stock_recommendations` | 기술적 분석 기반 매수 추천 (+ recommendation_score) | 파이프라인 2단계 | 체이닝 자동 트리거 |
| `sentiment_analysis` | 뉴스 감정 분석 결과 | 파이프라인 2단계 | 체이닝 자동 트리거 |
| `stocks` | 종목 마스터 데이터 | 수동 | - |
| `fred_indicators` | FRED 지표 정의 | 수동 | - |
| `yfinance_indicators` | Yahoo Finance 지표 정의 | 수동 | - |

---

## 관련 문서

- [스케줄러 아키텍처](../architecture/스케줄러_아키텍처.md) — Quartz 설정 및 구조
- [스케줄러 운영 가이드](../setup/스케줄러_운영_가이드.md) — 수동 트리거, 모니터링, 트러블슈팅
- [이벤트 기반 아키텍처](../architecture/이벤트_기반_아키텍처.md) — Pub/Sub 이벤트 상세
