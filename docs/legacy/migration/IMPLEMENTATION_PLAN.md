# daily_stock_data 통합 저장 구현 계획

## 🎯 목표
FRED 및 Yahoo Finance 데이터를 `daily_stock_data` 컬렉션에 날짜별로 통합 저장

## 📋 작업 단계

### Phase 1: Repository 레이어 수정
- [ ] `EconomicDataRepository`에 `upsert_daily_data` 메서드 추가
- [ ] 날짜별 데이터 병합 로직 구현
- [ ] 기존 `save_data` 메서드 유지 (호환성)

### Phase 2: Service 레이어 수정
- [ ] `EconomicDataService.collect_economic_data()` 수정
- [ ] FRED 데이터를 딕셔너리로 수집
- [ ] Yahoo Finance 데이터를 딕셔너리로 수집
- [ ] 날짜별로 통합하여 저장

### Phase 3: 테스트
- [ ] 단위 테스트 작성
- [ ] 통합 테스트 실행
- [ ] MongoDB 데이터 구조 검증

### Phase 4: 기존 데이터 마이그레이션
- [ ] 마이그레이션 스크립트 작성
- [ ] 기존 fred_data → daily_stock_data.fred_indicators
- [ ] 기존 yfinance_data → daily_stock_data.yfinance_indicators

---

## 🔧 구현 상세

### 1. Repository 메서드 추가

```python
# quantiq-data-engine/src/features/economic_data/repository.py

def upsert_daily_data(
    self,
    date: str,
    indicator_type: str,  # "fred_indicators" or "yfinance_indicators"
    data: Dict[str, Any]
) -> bool:
    """날짜별 daily_stock_data에 지표 데이터를 upsert"""
    try:
        collection = self.db["daily_stock_data"]

        # 해당 날짜의 기존 문서 조회 또는 생성
        result = collection.update_one(
            {"date": date},
            {
                "$set": {
                    f"{indicator_type}": data,
                    "updated_at": datetime.now()
                }
            },
            upsert=True
        )

        return result.acknowledged
    except Exception as e:
        logger.error(f"Daily data upsert 실패: {e}")
        return False
```

### 2. Service 로직 수정

```python
# quantiq-data-engine/src/features/economic_data/service.py

def collect_economic_data(self) -> Dict[str, Any]:
    """경제 데이터를 수집하여 daily_stock_data에 저장"""

    # 날짜 범위
    end_date = datetime.now()
    start_date = end_date - timedelta(days=30)

    # 날짜별 데이터 딕셔너리
    daily_data = defaultdict(lambda: {
        "fred_indicators": {},
        "yfinance_indicators": {}
    })

    # FRED 데이터 수집
    fred_indicators = self._load_fred_indicators()
    for code, name in fred_indicators.items():
        df = self._fetch_fred_data(code, start_date_str, end_date_str)
        if df is not None:
            for date, row in df.iterrows():
                date_str = date.strftime("%Y-%m-%d")
                daily_data[date_str]["fred_indicators"][name] = float(row["value"])

    # Yahoo Finance 데이터 수집
    yfinance_indicators = self._load_yfinance_indicators()
    for name, ticker in yfinance_indicators.items():
        df = self._fetch_yahoo_data(ticker, start_date_str, end_date_str)
        if df is not None:
            for date, row in df.iterrows():
                date_str = date.strftime("%Y-%m-%d")
                daily_data[date_str]["yfinance_indicators"][name] = float(row["Close"])

    # daily_stock_data에 저장
    saved_dates = 0
    for date_str, data in daily_data.items():
        if self.repository.upsert_daily_data(date_str, data):
            saved_dates += 1

    return {
        "success": True,
        "dates_saved": saved_dates,
        "fred_collected": len([d for d in daily_data.values() if d["fred_indicators"]]),
        "yahoo_collected": len([d for d in daily_data.values() if d["yfinance_indicators"]])
    }
```

### 3. 예상 데이터 구조

```javascript
// daily_stock_data 컬렉션
{
  _id: ObjectId("..."),
  date: "2026-01-31",

  fred_indicators: {
    "Treasury_10Y": 4.2,
    "USD_KRW": 1462.89
  },

  yfinance_indicators: {
    "SP500": 4500.12,
    "Dow_Jones": 38000.45,
    "NASDAQ": 15000.34,
    "KOSPI": 2600.0,
    "Gold": 4713.90
  },

  stocks: {},  // 나중에 추가될 주식 데이터

  updated_at: ISODate("2026-01-31T15:52:11Z")
}
```

---

## ⚡ 시작하겠습니다!

바로 구현 시작할까요?
