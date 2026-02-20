# 🔬 분석 기능 아키텍처

**상태:** 현재 분석 중 | **버전:** 1.0 | **마지막 업데이트:** 2025-01-29

---

## 📊 현재 분석 시스템 개요

```
┌─────────────────────────────────────────────────────────────┐
│                  Quantiq Data Engine                        │
│                   (Python/FastAPI)                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Kafka Consumer ◄─── 분석 요청 (quantiq.analysis.request)  │
│      ↓                                                      │
│  ┌─────────────────────────────────────────────────────┐  │
│  │  분석 처리 엔진                                      │  │
│  ├─────────────────────────────────────────────────────┤  │
│  │ 1️⃣ 경제 데이터 수집                               │  │
│  │    └─ FRED, Yahoo Finance, KRX                     │  │
│  │                                                     │  │
│  │ 2️⃣ 기술적 분석 (Technical Analysis)               │  │
│  │    ├─ SMA 계산                                     │  │
│  │    ├─ RSI 계산                                     │  │
│  │    ├─ MACD 계산                                    │  │
│  │    └─ 신호 생성 (Golden Cross, MACD Buy)          │  │
│  │                                                     │  │
│  │ 3️⃣ 감정 분석 (Sentiment Analysis) ⚠️ 미구현       │  │
│  │    ├─ 뉴스 감정도 분석                             │  │
│  │    └─ 소셜 미디어 분석                             │  │
│  └─────────────────────────────────────────────────────┘  │
│      ↓                                                      │
│  MongoDB 저장                                              │
│  ├─ stock_recommendations (추천 신호)                     │
│  ├─ ticker_sentiment_analysis (감정 분석)                 │
│  ├─ daily_stock_data (일일 통합 데이터)                   │
│  └─ stock_analysis_results (분석 결과)                    │
│      ↓                                                      │
│  Kafka Publisher ──→ 분석 완료 이벤트                     │
│                    (quantiq.analysis.completed)           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔧 현재 구현 상태

### ✅ 완료된 기능

#### 1. 경제 데이터 수집 (data_collector.py)
```python
collect_economic_data(start_date, end_date)
  ├─ FRED 지표 로드 (경제 지수)
  ├─ Yahoo Finance 지표 로드 (주가)
  ├─ 활성 주식 조회
  ├─ 단기 공매도 데이터 수집
  └─ 통합 DataFrame 생성
```

**저장 위치:** MongoDB `daily_stock_data`
```javascript
{
  date: "2024-01-15",
  stocks: {
    "AAPL": 150.25,
    "MSFT": 375.50,
    "GLD": 185.80  // 경제 지표
  }
}
```

#### 2. 기술적 분석 (technical_analysis.py)
```python
TechnicalAnalysisService
  ├─ calculate_sma(series, period)     // 단순이동평균
  ├─ calculate_rsi(series, period=14)  // 상대강도지수
  ├─ calculate_macd(series)             // MACD
  └─ analyze_stocks(start_date, end_date)  // 통합 분석
```

**분석 프로세스:**
```
1. 최근 180일 데이터 수집
2. 각 종목별:
   - SMA 20/50 계산
   - RSI 계산
   - MACD 계산
3. 신호 생성:
   - Golden Cross: SMA20 > SMA50
   - MACD Buy: MACD > Signal
   - RSI 과매도: RSI < 50
4. 추천 여부:
   is_recommended = golden_cross AND (rsi < 50) AND macd_buy
```

**저장 위치:** MongoDB `stock_recommendations`
```javascript
{
  date: "2024-01-15",
  ticker: "AAPL",
  stock_name: "Apple Inc.",
  technical_indicators: {
    sma20: 175.30,
    sma50: 174.50,
    rsi: 28,
    macd: 0.05,
    signal: -0.02,
    golden_cross: true,
    macd_buy_signal: true
  },
  is_recommended: true,
  updated_at: ISODate("2024-01-15T16:00:00Z")
}
```

### ❌ 미구현 기능

#### 1. 감정 분석 (sentiment_analysis.py) ⚠️
```python
SentimentAnalysisService
  ├─ NewsAPI 뉴스 조회
  ├─ NLP 감정 분석
  ├─ 종목별 감정도 계산
  └─ fetch_and_store_sentiment()  // ← 미구현
```

**계획된 저장 위치:** MongoDB `ticker_sentiment_analysis`
```javascript
{
  ticker: "AAPL",
  date: "2024-01-15",
  sentiment_score: 0.75,  // -1.0 ~ 1.0
  news_count: 25,
  positive: 18,
  neutral: 5,
  negative: 2,
  recent_news: [
    { title: "Apple beats Q1 earnings", sentiment: 0.95 },
    { title: "Supply chain concerns", sentiment: -0.30 }
  ]
}
```

---

## 🔄 분석 흐름

### 시퀀스 다이어그램

```
quantiq-core (Spring Boot)
    ↓
 Kafka: quantiq.analysis.request
    ↓
quantiq-data-engine (Python)
    │
    ├─ 1. MongoDB 연결 확인
    │    └─ db.stocks (활성 종목 조회)
    │
    ├─ 2. 경제 데이터 수집
    │    ├─ FRED API 호출
    │    ├─ Yahoo Finance 호출
    │    └─ MongoDB: daily_stock_data 저장
    │
    ├─ 3. 기술적 분석 실행
    │    ├─ daily_stock_data 조회
    │    ├─ 지표 계산 (SMA, RSI, MACD)
    │    ├─ 신호 생성
    │    └─ MongoDB: stock_recommendations 저장
    │
    ├─ 4. 감정 분석 실행 (향후)
    │    ├─ NewsAPI 호출
    │    ├─ NLP 감정 분석
    │    └─ MongoDB: ticker_sentiment_analysis 저장
    │
    └─ 5. 완료 이벤트 발행
         └─ Kafka: quantiq.analysis.completed
```

---

## 📁 MongoDB 컬렉션 구조

### 현재 운영 중

#### 1. `daily_stock_data` (일일 통합 데이터)
```javascript
{
  _id: ObjectId(),
  date: "2024-01-15",        // YYYY-MM-DD
  stocks: {
    "AAPL": 150.25,
    "MSFT": 375.50,
    "GLD": 185.80,
    "금리": 4.35
  },
  source: ["yahoo", "fred"],
  updated_at: ISODate()
}

인덱스:
- { date: 1 }
- { "date": 1, "stocks.ticker": 1 }
```

#### 2. `stock_recommendations` (기술적 추천)
```javascript
{
  _id: ObjectId(),
  ticker: "AAPL",
  date: "2024-01-15",
  stock_name: "Apple Inc.",
  technical_indicators: {
    sma20: 175.30,
    sma50: 174.50,
    rsi: 28,
    macd: 0.05,
    signal: -0.02,
    golden_cross: true,
    macd_buy_signal: true
  },
  is_recommended: true,
  confidence: 0.85,
  updated_at: ISODate()
}

인덱스:
- { ticker: 1, date: 1 }
- { date: 1, is_recommended: 1 }
```

#### 3. `stocks` (종목 정보)
```javascript
{
  _id: ObjectId(),
  ticker: "AAPL",
  stock_name: "Apple Inc.",
  is_active: true,
  created_at: ISODate()
}

인덱스:
- { ticker: 1 }
- { is_active: 1 }
```

#### 4. `fred_indicators` (경제 지표)
```javascript
{
  _id: ObjectId(),
  code: "DGS10",
  name: "10-Year Treasury Yield",
  is_active: true
}
```

#### 5. `yfinance_indicators` (Yahoo Finance 지표)
```javascript
{
  _id: ObjectId(),
  ticker: "GLD",
  name: "Gold ETF",
  is_active: true
}
```

### 계획 중 (추가 필요)

#### `ticker_sentiment_analysis` (감정 분석)
```javascript
{
  _id: ObjectId(),
  ticker: "AAPL",
  date: "2024-01-15",
  sentiment_score: 0.75,
  news_count: 25,
  positive: 18,
  neutral: 5,
  negative: 2,
  recent_news: [...]
}
```

#### `stock_analysis_results` (최종 분석 결과)
```javascript
{
  _id: ObjectId(),
  ticker: "AAPL",
  date: "2024-01-15",
  combined_score: 0.82,
  technical_score: 0.85,
  sentiment_score: 0.75,
  recommendation: "BUY",
  confidence: 0.82,
  reasons: [
    "Golden Cross 형성",
    "RSI 과매도",
    "긍정적 뉴스"
  ]
}
```

---

## 🚀 실행 흐름 (Kafka 기반)

### 분석 요청 메시지 포맷

```json
{
  "type": "ALL",           // ALL, TECHNICAL, SENTIMENT
  "timestamp": 1704067200,
  "start_date": "2024-01-01",
  "end_date": "2024-01-15",
  "stocks": ["AAPL", "MSFT", "GOOGL"]  // optional
}
```

### 처리 절차

```python
1. Kafka 메시지 수신
   ├─ type 파싱 (ALL, TECHNICAL, SENTIMENT)
   └─ 파라미터 검증

2. 경제 데이터 수집 (항상)
   └─ collect_economic_data()

3. 기술적 분석 (type in [ALL, TECHNICAL])
   ├─ technical_service.analyze_stocks()
   └─ publish_event("TECHNICAL_COMPLETED")

4. 감정 분석 (type in [ALL, SENTIMENT]) ⚠️ 미구현
   ├─ sentiment_service.fetch_and_store_sentiment()
   └─ publish_event("SENTIMENT_COMPLETED")

5. 최종 완료 이벤트
   └─ publish_event("ANALYSIS_COMPLETED")
```

---

## 📊 현재 문제점 & 개선 방안

### 1️⃣ 감정 분석 미구현

**현재:** `sentiment_analysis.py` 파일 없음

**필요:**
```python
class SentimentAnalysisService:
    def __init__(self):
        self.newsapi_key = settings.NEWSAPI_KEY
        self.nlp_model = load_nlp_model()  # 한글/영문

    def fetch_news(self, ticker):
        # NewsAPI에서 뉴스 조회
        pass

    def analyze_sentiment(self, text):
        # NLP 감정 분석
        # 반환: -1.0 ~ 1.0 (음 ~ 양)
        pass

    def fetch_and_store_sentiment(self):
        # 모든 활성 종목의 감정 분석
        # MongoDB ticker_sentiment_analysis에 저장
        pass
```

### 2️⃣ 신호 생성 로직 단순

**현재:**
```
is_recommended = golden_cross AND (rsi < 50) AND macd_buy
```

**개선 사항:**
- ✅ 더 많은 지표 추가 (BBands, Stochastic, Volume)
- ✅ 감정 점수 통합
- ✅ 신뢰도 계산 (0.0 ~ 1.0)
- ✅ 여러 신호 조합 (강함/중간/약함)

### 3️⃣ 성능 최적화

**현재 이슈:**
- 180일 데이터 모두 계산 (느림)
- 각 종목 순차 처리
- 중복 계산 (매일 같은 데이터)

**개선 방안:**
- ✅ 증분 업데이트 (마지막 날짜부터만)
- ✅ 병렬 처리 (멀티프로세싱)
- ✅ 캐싱 (지표 저장)

---

## 🔄 마이그레이션 전략

### Phase 1: 분석 기능 검증 & 안정화 (현재)
```
목표: 기존 분석 기능이 정상 작동하는지 확인

tasks:
  ☐ quantiq-data-engine 정상 실행
  ☐ Kafka 메시지 처리 정상
  ☐ MongoDB 데이터 저장 정상
  ☐ stock_recommendations 수집 확인
  ☐ 분석 결과 검증 (손으로)
```

### Phase 2: 감정 분석 구현 (2-3일)
```
목표: sentiment_analysis.py 구현

tasks:
  ☐ NewsAPI 통합
  ☐ NLP 모델 선택 (KoBERT, Transformer)
  ☐ 감정 분석 로직 구현
  ☐ MongoDB 저장
  ☐ Kafka 이벤트 통합
```

### Phase 3: 신호 생성 강화 (1-2일)
```
목표: 더 정확한 추천 신호 생성

tasks:
  ☐ 추가 지표 계산 (BBands, Stochastic)
  ☐ 감정 점수 통합
  ☐ 신뢰도 계산 로직
  ☐ 신호 강도 분류 (STRONG, MEDIUM, WEAK)
  ☐ 성능 테스트
```

### Phase 4: 성능 최적화 (1-2일)
```
목표: 분석 속도 개선

tasks:
  ☐ 증분 업데이트 로직
  ☐ 병렬 처리 구현
  ☐ 캐싱 전략
  ☐ 벤치마크 테스트
```

### Phase 5: RDB 마이그레이션 (4-5일)
```
목표: 사용자/거래 데이터를 PostgreSQL로 이관

tasks:
  ☐ RDB 스키마 생성
  ☐ 데이터 마이그레이션
  ☐ 이중 쓰기 모드 운영
  ☐ 점진적 전환
```

---

## 🎯 다음 단계

### 즉시 (Today)

**1. 현재 분석 시스템 검증**
```bash
# 1. MongoDB 연결 확인
docker-compose exec mongodb mongosh
use stock_trading
db.stocks.find().count()
db.stock_recommendations.find().count()

# 2. 최근 분석 결과 확인
db.stock_recommendations.find({}).sort({date: -1}).limit(10)

# 3. 분석 데이터 상태 체크
db.daily_stock_data.find({}).sort({date: -1}).limit(5)
```

**2. 분석 파이프라인 테스트**
```bash
# Kafka 메시지 발행
docker-compose exec kafka kafka-console-producer.sh \
  --broker-list kafka:29092 \
  --topic quantiq.analysis.request

# 메시지 입력
{"type": "TECHNICAL", "start_date": "2024-01-01", "end_date": "2024-01-29"}

# 로그 확인
docker-compose logs -f quantiq-data-engine | grep -i technical
```

### Week 1 (이번 주)

**1. 분석 아키텍처 문서화** ✅ (완료)

**2. 감정 분석 구현** (2-3일)
- [ ] sentiment_analysis.py 작성
- [ ] NewsAPI 통합
- [ ] NLP 모델 선택 및 로드
- [ ] ticker_sentiment_analysis 컬렉션 저장 로직

**3. 신호 생성 개선** (1-2일)
- [ ] 추가 지표 계산
- [ ] 신뢰도 계산
- [ ] 신호 강도 분류

### Week 2 (다음 주)

**1. 성능 최적화** (1-2일)
- [ ] 증분 업데이트
- [ ] 병렬 처리
- [ ] 벤치마크

**2. RDB 마이그레이션 시작** (3-4일)
- [ ] DATABASE_STRATEGY.md 검토
- [ ] EXECUTION_GUIDE.md 따라 Day 1-2 준비
- [ ] Day 3 마이그레이션 실행

---

## 📋 점검 항목

### 분석 기능 정상성 체크리스트

```
데이터 수집:
  ☐ FRED 데이터 수집 성공
  ☐ Yahoo Finance 데이터 수집 성공
  ☐ daily_stock_data 저장 정상

기술적 분석:
  ☐ SMA 계산 정확
  ☐ RSI 계산 정확
  ☐ MACD 계산 정확
  ☐ stock_recommendations 저장 정상

신호 생성:
  ☐ Golden Cross 감지 정확
  ☐ MACD Buy 신호 정확
  ☐ RSI 과매도 감지 정확
  ☐ is_recommended 정확

Kafka 통합:
  ☐ 메시지 수신 성공
  ☐ 메시지 처리 성공
  ☐ 완료 이벤트 발행 성공

MongoDB:
  ☐ 연결 정상
  ☐ 데이터 저장 정상
  ☐ 인덱스 활성화됨
```

---

## 📚 관련 문서

### 아키텍처
- [시스템 아키텍처](../architecture/시스템_아키텍처.md) - 전체 시스템 구조
- [이벤트 기반 아키텍처](../architecture/이벤트_기반_아키텍처.md) - Kafka 이벤트
- [이벤트 스키마](../architecture/이벤트_스키마.md) - Kafka 토픽 스키마
- [스케줄러 아키텍처](../architecture/스케줄러_아키텍처.md) - Quartz 스케줄러

### 데이터베이스
- [데이터베이스 스키마](../database/SCHEMA.md) - 전체 스키마
- [데이터베이스 관계](../database/RELATIONSHIPS.md) - ERD 및 관계
- [하이브리드 데이터베이스 전략](../architecture/하이브리드_데이터베이스_전략.md) - PostgreSQL + MongoDB

### 설정 가이드
- [환경설정 가이드](../setup/환경설정_가이드.md) - 개발 환경 설정
- [명령어 가이드](../setup/명령어_가이드.md) - CLI 명령어
- [로컬 테스트 가이드](../setup/로컬_테스트_가이드.md) - 테스트 방법

### TODO 및 로드맵
- [Phase3 스펙](../todo/Phase3_스펙.md) - 차별화 기능 상세
- [기능 로드맵](../todo/기능_로드맵.md) - 전체 개발 로드맵
- [다음 작업](../todo/다음_작업.md) - 현재 진행 중인 작업

### 분석 결과
- [데이터 형식 분석](../analysis/데이터_형식_분석.md) - 데이터 구조
- [FRED 데이터 분석](../analysis/FRED_데이터_분석_2026-02-01.md) - 경제 지표

---

**마지막 업데이트:** 2025-01-29
**상태:** 분석 완료, 개선 계획 수립
**다음 액션:** 현재 분석 시스템 검증
