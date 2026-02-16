# MongoDB 스키마 문서

## 개요

MongoDB는 시계열 데이터와 비정형 분석 데이터를 저장하는 데 사용됩니다.

**데이터베이스:** `stock_trading`

---

## 컬렉션 목록

| 컬렉션 | 용도 | 문서 수 (추정) | 보관 기간 |
|--------|------|---------------|-----------|
| `daily_stock_data` | 일별 OHLCV + 경제 지표 | ~7,500 (1년) | 영구 |
| `sentiment_analysis` | 뉴스 감정 분석 | ~31,500 (90일) | 90일 (TTL) |
| `stock_recommendations` | 기술적 추천 | ~35 × 365 | 1년 |
| `news_sentiment` | 뉴스 원문 + 감정 | ~31,500 (90일) | 90일 (TTL) |

---

## 1. daily_stock_data 컬렉션

**용도:** 백테스트 및 전략 실행에 사용되는 핵심 데이터

### 스키마 구조

```javascript
{
  "_id": ObjectId("..."),
  "date": "2026-02-16",  // ISO 8601 날짜 (YYYY-MM-DD)

  // 종목별 OHLCV + 펀더멘탈
  "stocks": {
    "AAPL": {
      "open": 228.5,
      "high": 233.1,
      "low": 227.8,
      "close": 231.42,
      "volume": 48523100,
      "close_price": 231.42,  // 레거시 호환
      "info": {  // 펀더멘탈 (yfinance.info)
        "trailingPE": 28.5,
        "priceToBook": 5.2,
        "dividendYield": 0.0053,
        "marketCap": 3560000000000,
        "returnOnEquity": 1.47,
        "earningsGrowth": 0.08,
        "debtToEquity": 140.97,
        "forwardPE": 26.3
      }
    },
    "NVDA": { ... },
    // ... 35개 종목
  },

  // yfinance 지표 (인덱스, ETF)
  "yfinance_indicators": {
    "^GSPC": {  // S&P 500 지수
      "open": 5950.2,
      "high": 5980.5,
      "low": 5940.1,
      "close": 5970.8,
      "volume": 3800000000,
      "close_price": 5970.8,
      "name": "S&P 500 지수"
    },
    "^IXIC": { ... },  // NASDAQ
    "^DJI": { ... },   // Dow Jones
    "SPY": { ... },    // S&P 500 ETF
    "QQQ": { ... }     // NASDAQ ETF
  },

  // FRED 경제 지표
  "fred_indicators": {
    "FEDFUNDS": {  // 기준금리
      "value": 4.33,
      "name": "기준금리"
    },
    "DGS10": {  // 10년물 국채 수익률
      "value": 4.15,
      "name": "10년물 국채 수익률"
    },
    "UNRATE": {  // 실업률
      "value": 3.7,
      "name": "실업률"
    }
    // ... 추가 경제 지표
  }
}
```

### 인덱스 ⭐

**성능 최적화:** 백테스트 속도 약 85% 개선

```javascript
// 1. date 필드 (기본)
{ "date": 1 }  // idx_date

// 2. date + 종목별 (복합 인덱스)
{ "date": 1, "stocks.AAPL": 1 }   // idx_date_stocks_AAPL
{ "date": 1, "stocks.NVDA": 1 }   // idx_date_stocks_NVDA
{ "date": 1, "stocks.MSFT": 1 }   // idx_date_stocks_MSFT
{ "date": 1, "stocks.GOOGL": 1 }  // idx_date_stocks_GOOGL
{ "date": 1, "stocks.AMZN": 1 }   // idx_date_stocks_AMZN
{ "date": 1, "stocks.META": 1 }   // idx_date_stocks_META
{ "date": 1, "stocks.TSLA": 1 }   // idx_date_stocks_TSLA
{ "date": 1, "stocks.AMD": 1 }    // idx_date_stocks_AMD
{ "date": 1, "stocks.NFLX": 1 }   // idx_date_stocks_NFLX
{ "date": 1, "stocks.AVGO": 1 }   // idx_date_stocks_AVGO

// 3. date + 벤치마크 (복합 인덱스)
{ "date": 1, "yfinance_indicators.^GSPC": 1 }  // idx_date_yfinance_caret_GSPC
{ "date": 1, "yfinance_indicators.^IXIC": 1 }  // idx_date_yfinance_caret_IXIC
{ "date": 1, "yfinance_indicators.^DJI": 1 }   // idx_date_yfinance_caret_DJI
{ "date": 1, "yfinance_indicators.SPY": 1 }    // idx_date_yfinance_SPY
{ "date": 1, "yfinance_indicators.QQQ": 1 }    // idx_date_yfinance_QQQ
```

**인덱스 생성 방법:**
```bash
python scripts/create_mongo_indexes.py "mongodb+srv://..."
```

### 주요 쿼리 패턴

```javascript
// 백테스트용 데이터 조회
db.daily_stock_data.find({
  "date": { "$gte": "2025-02-16", "$lte": "2026-02-16" }
}, {
  "date": 1,
  "stocks.AAPL": 1,
  "stocks.NVDA": 1,
  "yfinance_indicators.SPY": 1
}).sort({ "date": 1 })

// 벤치마크 데이터 조회
db.daily_stock_data.find({
  "date": { "$gte": "2025-02-16", "$lte": "2026-02-16" }
}, {
  "date": 1,
  "yfinance_indicators.^GSPC": 1
}).sort({ "date": 1 })
```

### 데이터 규모

- **보관 기간**: 1년 (현재 2025-02-05 ~ 2026-02-16, 약 376일)
- **종목 수**: 35개 (미국 주식)
- **문서 수**: ~7,348개
- **컬렉션 크기**: 약 28 MB
- **인덱스 크기**: 약 147 MB

### 업데이트 주기

- **일별 업데이트**: 장 마감 후 자동 수집
- **수집 시간**: KST 오전 9시 (Quartz 스케줄러)
- **실패 시 재시도**: 3회

---

## 2. sentiment_analysis 컬렉션

**용도:** 종목별 일별 감정 분석 집계

### 스키마 구조

```javascript
{
  "_id": ObjectId("..."),
  "ticker": "AAPL",
  "date": "2026-02-16",
  "average_sentiment_score": 0.65,  // -1.0 ~ 1.0
  "article_count": 15,
  "positive_count": 10,
  "negative_count": 3,
  "neutral_count": 2,
  "created_at": ISODate("2026-02-16T05:30:00Z"),
  "updated_at": ISODate("2026-02-16T05:30:00Z")
}
```

### 인덱스

```javascript
{ "ticker": 1, "date": -1 }  // 종목별 시계열 조회
```

---

## 3. stock_recommendations 컬렉션

**용도:** 기술적 분석 기반 추천

### 스키마 구조

```javascript
{
  "_id": ObjectId("..."),
  "ticker": "AAPL",
  "date": "2026-02-16",
  "is_recommended": true,
  "rsi": 45.2,
  "recommendation_score": 0.82,  // 0.0 ~ 1.0
  "technical_indicators": {
    "sma_20": 230.5,
    "sma_50": 228.3,
    "volume_ratio": 1.2
  },
  "created_at": ISODate("2026-02-16T05:30:00Z")
}
```

### 인덱스

```javascript
{ "ticker": 1, "date": -1 }
{ "is_recommended": 1, "date": -1 }
```

---

## 4. news_sentiment 컬렉션

**용도:** 뉴스 원문 + 감정 분석 (중복 방지)

### 스키마 구조

```javascript
{
  "_id": ObjectId("..."),
  "ticker": "AAPL",
  "url": "https://...",
  "title": "Apple announces...",
  "published_at": ISODate("2026-02-16T10:30:00Z"),
  "sentiment": {
    "score": 0.8,
    "label": "positive",
    "relevance_score": 0.9
  },
  "translated_title": "애플, 신제품 발표...",
  "created_at": ISODate("2026-02-16T11:00:00Z")
}
```

### 인덱스

```javascript
{ "ticker": 1, "date": -1 }
{ "ticker": 1, "published_at": -1 }
{ "ticker": 1, "url": 1 }  // unique
{ "created_at": 1 }  // TTL: 90일 자동 삭제
```

---

## 마이그레이션 가이드

### 신규 필드 추가

```javascript
// stocks.{ticker}.info 필드 추가 (펀더멘탈)
db.daily_stock_data.updateMany(
  { "stocks.AAPL.info": { $exists: false } },
  { $set: { "stocks.AAPL.info": {} } }
)
```

### 레거시 데이터 정리

```javascript
// close_price만 있는 레거시 데이터 확인
db.daily_stock_data.find({
  "stocks.AAPL.close": { $exists: false },
  "stocks.AAPL.close_price": { $exists: true }
}).count()
```

---

## 백업 및 복원

### 백업

```bash
# 전체 백업
mongodump --uri="mongodb+srv://..." --db=stock_trading --out=/backup/

# 특정 컬렉션만
mongodump --uri="mongodb+srv://..." --db=stock_trading --collection=daily_stock_data --out=/backup/
```

### 복원

```bash
mongorestore --uri="mongodb+srv://..." --db=stock_trading /backup/stock_trading/
```

---

## 참고 문서

- [MongoDB 인덱스 설정 가이드](../setup/mongodb-indexes.md)
- [백테스트 성능 분석](../../quant-jump-stock-data-engine/docs/backtest_performance_analysis.md)
