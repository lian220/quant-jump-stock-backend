# Incident — MongoDB Atlas M0 용량 초과로 쓰기 전면 차단 (2026-08-13 ~ 2026-08-18)

> **유형**: prod 데이터 수집 중단 (silent write failure)
> **MTTD**: 5일 (Slack 실패 알림은 매일 왔으나 원인 미확인 상태로 방치)
> **MTTR**: 인덱스 정리로 즉시 해소 (조사 포함 약 1시간)
> **재발 방지**: 미완 — 보존정책·저장 실패 fail-fast 미적용 (§7)

---

## 1. 요약

| 항목 | 내용 |
|------|------|
| **발생 시점** | 2026-08-13 22:44 KST (Atlas 첫 quota 에러) |
| **발견 시점** | 2026-08-18 (사용자가 반복되는 Slack 알림 원인 조사 요청) |
| **영향 기간** | 5일 (8/13 ~ 8/18) |
| **영향 범위** | `daily_stock_data` 최신일이 2026-08-12에 고정. 종목 추천 미산출, 감정 분석 저장 실패. 매 평일 23:20 KST 실패 알림 발송 |
| **데이터 손실** | 없음 (수집 자체는 성공, 저장만 실패. 재수집으로 백필 가능) |
| **복구** | 2026-08-18 미사용 인덱스 7개 드롭 → 77.5MB 회수 → 쓰기 잠금 해제 |

## 2. 사용자에게 보인 증상

```
잡스 [오후 11:20]
❌ 종합 리포트 생성 실패 — 경제 데이터 미수집
daily_stock_data (date=2026-08-14) 가 존재하지 않습니다.
경제 데이터 수집이 선행되어야 종합 리포트를 생성할 수 있습니다.
- 분석 날짜: 2026-08-14
```

평일마다 날짜만 바뀌며 동일 알림 반복.

## 3. 원인

Atlas 무료 티어(M0)의 512MB 저장 한도에 도달해 **클러스터 전체 쓰기가 거부**된 상태였다.

```
core.database - WARNING - MongoDB index creation warning: you are over your space quota,
using 512 MB of 512 MB. Writes are blocked on your cluster. (AtlasError 8000)

features.economic_data.repository - ERROR - Daily data upsert 실패 (date=2026-08-14): ...
features.economic_data.service - INFO - 경제 데이터 수집 완료: FRED=5개, Yahoo=24개,
                                        Stocks=42개 종목, 0일치 저장
```

수집(FRED·yfinance·42종목) 자체는 매번 성공했으나 MongoDB 저장이 전부 실패했다.
40분 뒤 종목 추천 잡이 직전 거래일 문서를 찾지 못해 `DailyDataNotCollectedError`를 발생시킨 것이
Slack 알림의 정체다. 즉 **알림이 가리킨 "경제 데이터 미수집"은 증상이고, 진짜 원인은 저장소 용량**이었다.

### 왜 5일이나 몰랐나

`collect_economic_data()`가 **저장 0건이어도 `success: True`를 반환**한다.
파이프라인은 "수집 완료 ✅"로 다음 단계를 진행했고, 실패는 40분 뒤 리포트 단계에서
간접적으로만 드러났다. 로그를 열어보기 전에는 정상 동작과 구분되지 않았다.

## 4. 용량 실측 (2026-08-18, 조치 전)

| 구분 | 크기 |
|------|------|
| storage (데이터) | 150.5 MB |
| index | **243.7 MB** |
| 합계 | 394.2 MB |

**인덱스가 데이터보다 컸다.** 컬렉션별로는 다음과 같다.

| 컬렉션 | docs | data | index |
|--------|------|------|-------|
| `sentiment_analysis` | 3,832 | 102.6 MB | 0.5 MB |
| `stock_predictions` | 316,629 | 63.9 MB | 121.0 MB |
| `news` | 39,401 | 62.5 MB | 3.4 MB |
| `daily_stock_data` | 7,479 | 46.6 MB | 67.5 MB |
| `stock_recommendations` | 62,459 | 16.3 MB | 50.9 MB |
| `stock_analysis_results` | 2,753 | 1.4 MB | 0.3 MB |

## 5. 조치 — 미사용 인덱스 7개 드롭

쓰기가 차단된 상태에서도 인덱스 드롭은 허용되므로 이를 먼저 실행했다.

| 컬렉션 | 인덱스 | 크기 | 드롭 근거 |
|--------|--------|------|-----------|
| `daily_stock_data` | `date_predictions_idx` | 64.5 MB | `predictions`로 필터하는 쿼리 없음 |
| `daily_stock_data` | `date_analysis_idx` | 1.6 MB | `analysis`로 필터하는 쿼리 없음 |
| `daily_stock_data` | `date_recommendations_idx` | 0.4 MB | 동일 |
| `daily_stock_data` | `date_sentiment_idx` | 0.3 MB | 동일 |
| `daily_stock_data` | `idx_date` | 0.2 MB | `date_unique`와 완전 중복 |
| `stock_recommendations` | `date_ticker_user_idx` | 5.7 MB | `user_id`로 조회하는 쿼리 없음 |
| `stock_recommendations` | `user_date_idx` | 4.9 MB | 동일 |

**결과: 394.2MB → 316.7MB.** 임시 문서 insert로 쓰기 잠금 해제를 검증했다.
`daily_stock_data` 인덱스는 67.5MB → 0.6MB가 됐다.

> ⚠️ **판단 기준 정정**: 최초에 "해당 필드가 문서에 존재하지 않으므로 인덱스가 무의미하다"는
> 근거로 드롭하려 했으나, dry-run 검증에서 필드가 실제로 잔존함이 드러났다
> (`analysis` 98건, `recommendations` 26건, `sentiment` 34건, `user_id` 953건).
> 올바른 기준은 **필드 존재 여부가 아니라 그 필드로 필터·정렬하는 쿼리의 존재 여부**다.
> 재검증 후 드롭은 안전한 것으로 확인됐다.

백필은 `_resolve_date_range()`가 `마지막 수집일 + 1`부터 재수집하는 구조라 자동으로 이뤄진다.

## 6. 조사 중 함께 확인된 사항

### 6-1. 컬렉션 단위로는 버릴 것이 없다

prod의 6개 컬렉션 전부 읽는 코드가 존재한다. 낭비는 컬렉션이 아니라 **필드·보존기간·인덱스** 층위에 있다.

| 대상 | 크기 | 상태 | 다른 곳에 사본 |
|------|------|------|----------------|
| `sentiment_analysis.articles` | 102.6 MB | 읽는 코드 0건 | **없음 (유일본)** |
| `stock_predictions` 365일 이전 | ~177 MB | 조회 창 밖 (96%) | **없음 (유일본)** |
| `daily_stock_data.predictions` | 24.7 MB | 읽는 코드 0건 | 있음 → `stock_predictions` |
| `daily_stock_data.analysis` | 1.4 MB | 읽는 코드 0건 | 있음 → `stock_analysis_results` |

- `articles`는 감정 **점수** 산출에는 관여하지 않는다. 점수는 외부 API 응답에서 즉시 계산되며,
  저장된 기사 배열은 이후 어디서도 읽히지 않는다. 멱등성 체크도 `article_count`만 참조한다.
- PostgreSQL `prediction_results`는 2026-02-01부터 5,024행(42종목)뿐이고 `actual_price`
  컬럼이 없다. **Mongo의 백업이 아니므로** `stock_predictions` 이력 삭제 시 복구 불가다.

### 6-2. 삭제해도 코드가 다시 채운다

| 필드 | 쓰기 지점 |
|------|-----------|
| `sentiment_analysis.articles` | `services/sentiment_analysis.py:243` |
| `daily_stock_data.predictions` | `ml/predict_optimized.py:1708` |
| `daily_stock_data.analysis` | `ml/predict_optimized.py:2257` |

필드 제거 마이그레이션은 반드시 위 쓰기 코드 제거와 함께 배포해야 한다.

### 6-3. 파이프라인 구조상의 문제 2건

- **동일 동기화 중복 실행**: `sync_latest_recommendations()`가 기술적 분석 직후(`handlers.py:279`)와
  40분 뒤 추천 잡(`handlers.py:510`)에서 각각 호출된다. 1차는 Vertex AI 작업이 방금 제출된 시점이라
  **AI 예측이 없는 상태로** PG에 쓰고, 2차가 덮어쓴다.
- **Vertex AI 완료 미대기**: 핸들러(`handlers.py:847`)는 GPU 작업을 제출만 하고 즉시 성공 처리한다.
  추천 잡은 40분 뒤 고정 시각에 실행되므로, 학습이 지연되면 이전 날짜 예측이 쓰인다
  (`comprehensive_report.py:144-157`의 7일 fallback이 이 위험을 전제로 존재).

### 6-4. 부수 확인

- `news` 컬렉션은 2026-04-15 이후 신규 문서가 없다. 수집 스케줄러 2개가 **PAUSED** 상태.
  읽기(Core API `/api/v1/news/*`)는 정상 동작 중이다.
- `sync_service.py:219`가 projection 없이 문서를 전부 로드한 뒤 필드 2개만 사용한다.
- Terraform 주석의 스케줄 시각(08:00 / 17:00 / 17:30 ET)이 실제 cron
  (17:05 / 09:40 / 10:20 ET)과 불일치한다. 문구만 낡음.
- `features/economic_data/repository.py:62 find_active_stocks()`는 존재하지 않는 Mongo `stocks`
  컬렉션을 참조하는 dead code(호출자 0). Core `StockPredictionMongoRepository`도 주입 0건.

## 7. 재발 방지 (미적용)

| # | 항목 | 상태 |
|---|------|------|
| R1 | `collect_economic_data()`가 `dates_saved == 0`이면 실패 반환 + Slack 알림 | ☐ |
| R2 | Atlas 용량 임계치(80%) 모니터링 알림 | ☐ |
| R3 | `stock_predictions` 보존정책 — 365일 초과분 GCS export 후 삭제 (~177MB) | ☐ |
| R4 | `sentiment_analysis.articles` 처리 방침 결정 (전량 삭제 / 상위 N건만 보존) | ☐ |
| R5 | `daily_stock_data.predictions`·`analysis` 이중 쓰기 제거 (~26MB) | ☐ |
| R6 | 인덱스 추가 시 쿼리 근거 명시 규칙 | ☐ |

> **삭제해서는 안 되는 것**: `daily_stock_data` 본체는 Vertex AI가 전체 이력(2006~)을 읽어
> 90일 시퀀스로 학습하므로 과거 데이터도 실사용 중이다. `stock_recommendations` 과거분도
> 백테스트가 임의 기간을 조회한다(`application/backtest/data_loader_mongo.py:218`).

## 8. 타임라인

| 시각 (KST) | 사건 |
|------------|------|
| 2026-08-13 22:44 | Atlas 첫 quota 에러. 이후 모든 쓰기 거부 |
| 2026-08-13 ~ 08-17 | 매 평일 수집 성공·저장 0건. 23:20 실패 알림 반복 |
| 2026-08-18 08:4x | 원인 조사 시작, 용량 초과 확인 |
| 2026-08-18 08:5x | 미사용 인덱스 7개 드롭 → 316.7MB, 쓰기 재개 검증 |
| 2026-08-18 22:40 (예정) | 저녁 파이프라인이 8/13~8/17 자동 백필 |

## 9. 참고

- 파이프라인 전체 구조: [ANALYSIS_ARCHITECTURE.md](../features/ANALYSIS_ARCHITECTURE.md)
- Cloud Run 운영 규칙: [Cloud_Run_운영_규칙.md](../infra/Cloud_Run_운영_규칙.md)
- 실제 접속 정보는 Secret Manager(`{DB_SECRET_NAME}`)에서 확인. 문서에 하드코딩 금지.
