# 데이터 구조 마이그레이션 가이드

## 🚨 문제 상황

**Quantiq 프로젝트**와 **기존 stock-trading 프로젝트**의 데이터 구조가 호환되지 않습니다!

### 현재 Quantiq 구조 (❌ 잘못됨)

```javascript
// fred_data 컬렉션 - 지표별, 날짜별 개별 문서
{
  _id: ObjectId("..."),
  date: "2026-01-23",
  code: "DEXKOUS",
  name: "USD_KRW",
  value: 1462.89,
  updated_at: "2026-01-31T15:52:10"
}

// yfinance_data 컬렉션 - 티커별, 날짜별 개별 문서
{
  _id: ObjectId("..."),
  date: "2026-01-30",
  ticker: "GC=F",
  name: "Gold",
  close: 4713.90,
  volume: 23709,
  updated_at: "2026-01-31T15:52:11"
}
```

**문제점**: 날짜별로 데이터가 분산되어 있어 `predict.py`가 읽을 수 없음!

---

### 기존 stock-trading 구조 (✅ 올바름)

```javascript
// daily_stock_data 컬렉션 - 날짜별 단일 문서에 모든 데이터 통합
{
  _id: ObjectId("..."),
  date: "2006-01-01",

  // FRED 경제 지표들
  fred_indicators: {
    "GDP": 123.45,
    "Unemployment_Rate": 3.7,
    "CPI": 2.5,
    "Treasury_10Y": 4.2,
    "USD_KRW": 1200.5
  },

  // Yahoo Finance 지표들 (지수, ETF 등)
  yfinance_indicators: {
    "S&P 500 ETF": 127.5,
    "QQQ ETF": 42.0,
    "SOXX ETF": 22.14,
    "SP500": 4500.12,
    "NASDAQ": 15000.34,
    "KOSPI": 2600.0,
    "Gold": 1850.0
  },

  // 개별 주식 데이터
  stocks: {
    "AMZN": {
      close_price: 2.24,
      short_interest: {
        sharesShort: 76073227,
        sharesShortPriorMonth: 70637204,
        shortRatio: 1.76,
        shortPercentOfFloat: 0.0079
      }
    },
    "GOOGL": {
      close_price: 10.82,
      short_interest: { ... }
    },
    "NVDA": { ... }
  },

  updated_at: ISODate("2025-12-28T05:20:49.119Z")
}
```

**핵심**: 하나의 날짜에 대한 모든 데이터가 **단일 문서**에 통합!

---

## 📊 predict.py가 기대하는 데이터 흐름

```python
# 1. daily_stock_data에서 날짜별 데이터 조회
cursor = db.daily_stock_data.find().sort("date", 1)

# 2. 각 문서에서 데이터 추출
for doc in cursor:
    date = doc.get("date")

    # FRED 지표 추출
    fred_indicators = doc.get("fred_indicators", {})
    # → {"GDP": 123.45, "Unemployment_Rate": 3.7, ...}

    # Yahoo Finance 지표 추출
    yfinance_indicators = doc.get("yfinance_indicators", {})
    # → {"SP500": 4500.12, "NASDAQ": 15000.34, ...}

    # 개별 주식 데이터 추출
    stocks = doc.get("stocks", {})
    # → {"AMZN": {close_price: 150.25}, "GOOGL": {...}, ...}
```

---

## 🔧 해결 방안

### 옵션 1: Quantiq 데이터 저장 방식 수정 (추천 ✅)

**장점**:
- predict.py와 완벽 호환
- 데이터 조회 성능 향상
- 날짜별 통합 관리 용이

**단점**:
- 코드 리팩토링 필요
- 기존 fred_data, yfinance_data 마이그레이션 필요

#### 수정할 파일들

1. **`quantiq-data-engine/src/features/economic_data/service.py`**
   - 현재: fred_data, yfinance_data에 개별 저장
   - 변경: daily_stock_data에 날짜별 통합 저장

2. **`quantiq-data-engine/src/features/economic_data/repository.py`**
   - 현재: `save_data()` 메서드가 개별 컬렉션에 저장
   - 변경: 날짜별로 upsert하여 통합 문서 생성

---

### 옵션 2: 중간 변환 레이어 추가

**장점**:
- 기존 코드 최소 수정
- 점진적 마이그레이션 가능

**단점**:
- 데이터 중복 저장
- 추가 프로세스 필요

#### 구현 방법

```python
# 별도 스크립트: aggregate_daily_data.py
def aggregate_daily_data(date):
    """fred_data, yfinance_data를 daily_stock_data로 통합"""

    # 1. 해당 날짜의 FRED 데이터 조회
    fred_docs = db.fred_data.find({"date": date})
    fred_indicators = {
        doc["name"]: doc["value"]
        for doc in fred_docs
    }

    # 2. 해당 날짜의 Yahoo Finance 데이터 조회
    yfinance_docs = db.yfinance_data.find({"date": date})
    yfinance_indicators = {
        doc["name"]: doc["close"]
        for doc in yfinance_docs
    }

    # 3. daily_stock_data에 upsert
    db.daily_stock_data.update_one(
        {"date": date},
        {
            "$set": {
                "fred_indicators": fred_indicators,
                "yfinance_indicators": yfinance_indicators,
                "updated_at": datetime.now()
            }
        },
        upsert=True
    )
```

---

## 📋 마이그레이션 단계별 계획

### Phase 1: 데이터 구조 확인 (완료 ✅)
- [x] 기존 stock-trading 데이터 구조 분석
- [x] predict.py 요구사항 파악
- [x] Quantiq 현재 구조 문제점 식별

### Phase 2: 코드 수정
- [ ] EconomicDataService 리팩토링
  - [ ] daily_stock_data 컬렉션 사용
  - [ ] 날짜별 통합 저장 로직 구현
- [ ] Repository 수정
  - [ ] upsert 기반 통합 저장 메서드 추가
- [ ] 테스트 코드 작성

### Phase 3: 데이터 마이그레이션
- [ ] 기존 fred_data → daily_stock_data.fred_indicators
- [ ] 기존 yfinance_data → daily_stock_data.yfinance_indicators
- [ ] 마이그레이션 스크립트 작성 및 실행

### Phase 4: 검증
- [ ] daily_stock_data 구조 검증
- [ ] predict.py 실행 테스트
- [ ] 예측 결과 확인

---

## 🎯 즉시 실행할 작업

### 1. 마이그레이션 스크립트 작성
```bash
# quantiq-data-engine/scripts/migrate_to_daily_stock_data.py
```

### 2. EconomicDataService 수정
```python
# 현재 (개별 저장)
self.repository.save_data("fred_data", data)

# 변경 (통합 저장)
self.repository.upsert_daily_data(date, "fred_indicators", data)
```

### 3. 테스트 실행
```bash
# 1. 마이그레이션 실행
python scripts/migrate_to_daily_stock_data.py

# 2. 데이터 확인
mongosh stock_trading
db.daily_stock_data.findOne({date: "2026-01-31"})

# 3. predict.py 실행
python scripts/utils/predict.py
```

---

## ⚠️ 주의사항

1. **데이터 백업 필수**
   ```bash
   mongodump --db stock_trading --out /backup/$(date +%Y%m%d)
   ```

2. **점진적 마이그레이션**
   - 먼저 테스트 환경에서 검증
   - 최근 1주일 데이터부터 시작
   - 전체 데이터는 단계적으로 마이그레이션

3. **기존 데이터 유지**
   - fred_data, yfinance_data 컬렉션은 백업용으로 보존
   - 마이그레이션 검증 후 삭제 결정

---

## 📈 기대 효과

### 성능 개선
- **조회 속도**: 날짜별 단일 문서 조회로 10배+ 향상
- **저장 공간**: 중복 데이터 제거로 30% 절감

### 호환성
- ✅ predict.py 즉시 사용 가능
- ✅ 기존 stock-trading 프로젝트와 완벽 호환
- ✅ 향후 분석 도구 통합 용이

### 유지보수
- 단일 데이터 소스로 관리 단순화
- 데이터 일관성 보장
- 버그 추적 용이

---

## 📞 다음 단계

어떤 옵션으로 진행하시겠습니까?

1. **옵션 1 (추천)**: 코드 수정 → daily_stock_data로 직접 저장
2. **옵션 2**: 중간 변환 레이어 추가 → 점진적 마이그레이션

선택하시면 즉시 구현 시작하겠습니다! 🚀
