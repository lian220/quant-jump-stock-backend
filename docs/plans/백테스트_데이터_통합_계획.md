# [Plan] Backtest Data Integration
**Plan Status**: 📅 Planned (Not Started)
**Reason**: To enhance backtesting quality by integrating sentiment analysis and technical indicators into the backtest engine.

---

# 백테스트 데이터 통합 기술 전략 (Backtest Data Integration Strategy)

## 1. 개요 (Overview)
본 문서는 백테스트 엔진(Backtest Engine)이 단순히 가격 데이터(OHLCV) 뿐만 아니라, 뉴스 감성 분석(Sentiment Analysis) 및 기술적 분석 추천(Technical Recommendations) 데이터를 활용할 수 있도록 지원하기 위한 기술적 전략을 기술합니다.
또한, **PRD v2.0**에서 요구하는 '포트폴리오형 전략(워런 버핏 등)'을 지원하기 위한 전략 모델 확장을 포함합니다.

## 2. 현재 아키텍처 분석 (Current Architecture Analysis)

### 데이터 소스 (MongoDB 컬렉션)
현재 데이터는 서로 다른 목적과 스키마를 가진 3개의 컬렉션으로 분리되어 저장됩니다.

1.  **`daily_stock_data`** (시장 데이터)
    *   **목적**: 일별 OHLCV (시가, 고가, 저가, 종가, 거래량) 데이터 및 주요 시장 지표 저장.
    *   **스키마**:
        ```json
        {
          "date": "YYYY-MM-DD",
          "stocks": {
            "AAPL": { "open": 150.0, "close": 155.0, ... },
            "TSLA": { ... }
          },
          "fred_indicators": { ... },
          "yfinance_indicators": { ... }
        }
        ```
    *   **현재 사용**: `MongoDataLoader`가 이 컬렉션만 읽어서 백테스트 엔진용 `pd.DataFrame`을 생성합니다.

2.  **`sentiment_analysis`** (대안 데이터)
    *   **목적**: 뉴스/기사 기반의 종목별 AI 감성 분석 점수 저장.
    *   **스키마**:
        ```json
        {
          "date": "YYYY-MM-DD",
          "ticker": "AAPL",
          "article_count": 10,
          "average_sentiment_score": 0.75  // 범위: -1.0 ~ 1.0
        }
        ```
    *   **현재 사용**: 독립적인 분석 결과로만 존재하며, **백테스트에서는 사용되지 않음**.

3.  **`stock_recommendations`** (기술적 분석 결과)
    *   **목적**: 매일 밤 실행되는 기술적 분석 배치 작업의 결과 (골든 크로스, RSI, MACD 등) 저장.
    *   **스키마**:
        ```json
        {
          "date": "YYYY-MM-DD",
          "ticker": "AAPL",
          "is_recommended": true,
          "technical_indicators": { "rsi": 45.5, "golden_cross": true, ... }
        }
        ```
    *   **현재 사용**: 독립적인 추천 결과로만 존재하며, **백테스트에서는 사용되지 않음**.

### 문제점 (The Gap)
1.  **데이터 미사용**: `BacktestEngine`이 OHLCV 이외의 감성/추천 데이터를 로드하지 않아, 복합 전략 백테스트가 불가능합니다.
2.  **전략 모델 한계**: 현재 전략 모델은 기술적 지표 필터링(`SCREENING`)만 가정하고 있으며, PRD가 요구하는 **고정 포트폴리오(`PORTFOLIO`)** 전략을 지원하지 못합니다.

---

## 3. 제안된 통합 전략

### A. 데이터 로더 확장 (`MongoDataLoader`)
`MongoDataLoader.load()`를 수정하여 보조 데이터를 메인 가격 데이터에 "Left Join" 방식으로 병합합니다.

**새로운 DataFrame 구조:**
종목(예: AAPL)별 `pd.DataFrame`에 새로운 컬럼이 추가됩니다:
*   `open`, `high`, `low`, `close`, `volume` (기존)
*   **`sentiment_score`** (신규, `sentiment_analysis`에서 가져옴, 기본값: 0.0)
*   **`sentiment_count`** (신규, `sentiment_analysis`에서 가져옴, 기본값: 0)
*   **`is_recommended`** (신규, `stock_recommendations`에서 가져옴, 기본값: False)
*   **`rec_rsi`** (신규, `stock_recommendations.technical_indicators`에서 가져옴, 필요 시)

### B. 도메인 모델 업데이트 (`models.py`)
1.  **지표 확장**: `IndicatorType` 열거형(Enum)을 확장하여 새로운 데이터 필드를 지원합니다.
    ```python
    class IndicatorType(str, Enum):
        # ... 기존 타입들 ...
        SENTIMENT_SCORE = "sentiment_score"  # 신규
        IS_RECOMMENDED = "is_recommended"    # 신규
    ```

2.  **전략 구조 확장**: `StrategyDefinition`에 종목 선정 방식을 명시하는 필드를 추가합니다.
    ```python
    class StockSelectionType(str, Enum):
        SCREENING = "screening"   # 조건으로 종목 찾기 (기존 방식)
        PORTFOLIO = "portfolio"   # 고정 종목 리스트 (신규 지원)

    class StrategyDefinition(BaseModel):
        # ... 기존 필드 ...
        stock_selection_type: StockSelectionType = Field(default=StockSelectionType.SCREENING)
        investment_philosophy: Optional[str] = Field(description="투자 철학 (AI 참고용)")
    ```

### C. 전략 인터프리터 로직 (`interpreter.py`)
*   `_calculate_all_indicators`: 새로운 지표 타입(`SENTIMENT_SCORE`, `IS_RECOMMENDED`) 처리 로직 추가.
*   `_filter_stocks` (신설/수정): `stock_selection_type`이 `PORTFOLIO`인 경우, 조건 필터링을 건너뛰고 지정된 종목 리스트를 그대로 사용하도록 분기 처리.

---

## 4. 구현 단계

### 1단계: `Strategy Models` 업데이트
`src/domain/strategy/models.py` 수정:
1.  `IndicatorType`에 `SENTIMENT_SCORE`, `IS_RECOMMENDED` 추가.
2.  `StockSelectionType` Enum 추가 및 `StrategyDefinition` 필드 확장.

### 2단계: `MongoDataLoader` 수정
`src/application/backtest/data_loader_mongo.py` 수정:
1.  `load()` 내에서 `daily_stock_data` 로드 후, `sentiment_analysis` 및 `stock_recommendations` 컬렉션 추가 조회.
2.  `ticker`와 `date` 기준으로 데이터를 병합 (`pd.merge`).
3.  결측치(NaN)에 대한 기본값 처리 (`0.0`, `False`).

### 3단계: `Strategy Interpreter` 업데이트
`src/domain/strategy/interpreter.py` 수정:
1.  새로운 데이터를 `IndicatorType` 매핑으로 연결.
2.  `stock_selection_type`에 따른 실행 로직 분기 (유니버스 필터링 vs 고정 포트폴리오).

### 4단계: 검증 (테스트 전략)
새로운 기능을 검증하기 위한 통합 테스트 전략 생성:

**Case 1: 복합 필터링 (Screening)**
```json
{
  "name": "감성 & 기술적 분석 결합 전략",
  "stock_selection_type": "screening",
  "rules": [
    {
      "signal_type": "buy",
      "conditions": [
        { "indicator": "sentiment_score", "operator": "gt", "value": 0.5 },
        { "indicator": "is_recommended", "operator": "eq", "value": 1.0 }
      ]
    }
  ]
}
```

**Case 2: 고정 포트폴리오 (Portfolio)**
```json
{
  "name": "워런 버핏 추종 전략",
  "stock_selection_type": "portfolio",
  "description": "애플, 뱅크오브아메리카 등 고정 종목 매수",
  "rules": [
    {
      "signal_type": "buy",
      "conditions": [ ...매수 타이밍 로직... ]
    }
  ]
}
```

## 5. 예상되는 데이터베이스 스키마 변경
*   **MongoDB**: 스키마 변경 없음. (애플리케이션 레벨 조인)
*   **PostgreSQL**: `strategies` 테이블에 `stock_selection_type`, `investment_philosophy` 컬럼 추가 필요 (PRD v2.0 요구사항).
