# ✅ 분석 기능 검증 체크리스트

**목표:** 마이그레이션 전 현재 분석 시스템이 정상 작동하는지 확인

**예상 시간:** 30-45분

**상태:** 🔴 미검증 (시작하기)

---

## 1️⃣ MongoDB 연결 확인 (5분)

### 1.1 MongoDB 서비스 상태

```bash
# MongoDB 컨테이너 실행 확인
docker-compose ps | grep mongodb

# 예상 출력:
# quantiq-mongodb   mongo:latest   Up (healthy)   0.0.0.0:27017->27017/tcp
```

**체크항목:**
- [ ] MongoDB 컨테이너 실행 중
- [ ] 포트 27017 열려있음

### 1.2 MongoDB 연결 테스트

```bash
# MongoDB 셸 접속
docker-compose exec mongodb mongosh

# 데이터베이스 확인
show databases

# stock_trading DB 선택
use stock_trading

# 컬렉션 목록
show collections

# 예상 출력:
# daily_stock_data
# fred_indicators
# stock_recommendations
# stocks
# yfinance_indicators
```

**체크항목:**
- [ ] MongoDB 셸 접속 성공
- [ ] stock_trading 데이터베이스 존재
- [ ] 필수 컬렉션 5개 존재:
  - [ ] stocks
  - [ ] daily_stock_data
  - [ ] stock_recommendations
  - [ ] fred_indicators
  - [ ] yfinance_indicators

### 1.3 데이터 존재 확인

```bash
# stocks 컬렉션 데이터
db.stocks.countDocuments()
db.stocks.findOne()

# daily_stock_data 컬렉션 데이터
db.daily_stock_data.countDocuments()
db.daily_stock_data.findOne()

# stock_recommendations 컬렉션 데이터
db.stock_recommendations.countDocuments()
db.stock_recommendations.findOne()

# 각 컬렉션의 최신 데이터
db.stock_recommendations.find({}).sort({date: -1}).limit(3).pretty()
```

**체크항목:**
- [ ] stocks 데이터 > 0개
- [ ] daily_stock_data 데이터 > 0개
- [ ] stock_recommendations 데이터 > 0개

---

## 2️⃣ 경제 데이터 수집 확인 (10분)

### 2.1 FRED 지표 설정 확인

```bash
docker-compose exec mongodb mongosh
use stock_trading

# FRED 지표 확인
db.fred_indicators.find({}).pretty()

# 예상 결과:
# {
#   _id: ObjectId(...),
#   code: "DGS10",
#   name: "10-Year Treasury Yield",
#   is_active: true
# }
```

**체크항목:**
- [ ] fred_indicators 컬렉션 존재
- [ ] FRED 지표 1개 이상 활성화됨

**만약 비어있다면:**
```bash
# FRED 지표 추가
db.fred_indicators.insertMany([
  { code: "DGS10", name: "10-Year Treasury Yield", is_active: true },
  { code: "DEXUSEU", name: "USD/EUR", is_active: true }
])
```

### 2.2 Yahoo Finance 지표 확인

```bash
# yfinance 지표 확인
db.yfinance_indicators.find({}).pretty()

# 예상 결과:
# {
#   _id: ObjectId(...),
#   ticker: "GLD",
#   name: "Gold ETF",
#   is_active: true
# }
```

**체크항목:**
- [ ] yfinance_indicators 컬렉션 존재
- [ ] Yahoo Finance 지표 1개 이상 활성화됨

**만약 비어있다면:**
```bash
# Yahoo Finance 지표 추가
db.yfinance_indicators.insertMany([
  { ticker: "GLD", name: "Gold ETF", is_active: true },
  { ticker: "USO", name: "Oil ETF", is_active: true }
])
```

### 2.3 활성 주식 확인

```bash
# 활성 주식 확인
db.stocks.find({is_active: true}).pretty()

# 활성 주식 수
db.stocks.countDocuments({is_active: true})

# 예상: 5개 이상
```

**체크항목:**
- [ ] 활성 주식 3개 이상 존재
- [ ] 각 주식의 ticker, stock_name, is_active 필드 있음

**만약 비어있다면:**
```bash
# 테스트 주식 추가
db.stocks.insertMany([
  { ticker: "AAPL", stock_name: "Apple Inc.", is_active: true },
  { ticker: "MSFT", stock_name: "Microsoft Corp", is_active: true },
  { ticker: "GOOGL", stock_name: "Alphabet Inc.", is_active: true }
])
```

### 2.4 일일 데이터 확인

```bash
# 최근 일일 데이터 확인
db.daily_stock_data.find({}).sort({date: -1}).limit(1).pretty()

# 일일 데이터 개수
db.daily_stock_data.countDocuments()

# 날짜 범위 확인
db.daily_stock_data.aggregate([
  { $group: { _id: null, min: { $min: "$date" }, max: { $max: "$date" } } }
]).pretty()
```

**체크항목:**
- [ ] daily_stock_data 데이터 10개 이상 존재
- [ ] 최근 30일 이상의 데이터 있음
- [ ] 날짜 포맷이 "YYYY-MM-DD"임

---

## 3️⃣ 기술적 분석 결과 확인 (10분)

### 3.1 분석 결과 존재 확인

```bash
# stock_recommendations 데이터 확인
db.stock_recommendations.find({}).pretty()

# 데이터 개수
db.stock_recommendations.countDocuments()

# 최근 결과 (상세)
db.stock_recommendations.find({}).sort({date: -1}).limit(5).pretty()
```

**체크항목:**
- [ ] stock_recommendations 데이터 있음
- [ ] 각 문서에 다음 필드 포함:
  - [ ] ticker
  - [ ] date (YYYY-MM-DD)
  - [ ] stock_name
  - [ ] technical_indicators (객체)
  - [ ] is_recommended (boolean)
  - [ ] updated_at (ISODate)

### 3.2 기술적 지표 확인

```bash
# 특정 종목의 최근 분석 결과
db.stock_recommendations.findOne(
  { ticker: "AAPL" },
  { sort: { date: -1 } }
)

# 예상 결과:
# {
#   _id: ObjectId(...),
#   ticker: "AAPL",
#   date: "2025-01-29",
#   stock_name: "Apple Inc.",
#   technical_indicators: {
#     sma20: 175.30,
#     sma50: 174.50,
#     rsi: 28,
#     macd: 0.05,
#     signal: -0.02,
#     golden_cross: true,
#     macd_buy_signal: true
#   },
#   is_recommended: true,
#   updated_at: ISODate(...)
# }
```

**체크항목:**
- [ ] technical_indicators 객체 존재
- [ ] 다음 지표들 계산됨:
  - [ ] sma20 (숫자)
  - [ ] sma50 (숫자)
  - [ ] rsi (0-100 범위)
  - [ ] macd (숫자)
  - [ ] signal (숫자)
  - [ ] golden_cross (boolean)
  - [ ] macd_buy_signal (boolean)

### 3.3 신호 생성 로직 검증

```bash
# 추천 종목 확인
db.stock_recommendations.countDocuments({is_recommended: true})

# 추천 vs 비추천 비율
db.stock_recommendations.aggregate([
  {
    $group: {
      _id: "$is_recommended",
      count: { $sum: 1 }
    }
  }
]).pretty()

# 예상:
# { _id: true, count: 15 }   // 추천
# { _id: false, count: 35 }  // 비추천
```

**체크항목:**
- [ ] 추천 비율 합리적 (5-30%)
- [ ] Golden Cross, MACD, RSI 신호 조합 정상

---

## 4️⃣ Kafka 통합 확인 (10분)

### 4.1 Kafka 서비스 상태

```bash
# Kafka 컨테이너 확인
docker-compose ps | grep kafka

# 예상 출력:
# quantiq-kafka   confluentinc/cp-kafka:7.x   Up   9092:9092
```

**체크항목:**
- [ ] Kafka 컨테이너 실행 중
- [ ] 포트 9092 (또는 29092) 열려있음

### 4.2 Kafka 토픽 확인

```bash
# 토픽 목록 확인
docker-compose exec kafka kafka-topics.sh \
  --list \
  --bootstrap-server kafka:29092

# 예상 토픽:
# quantiq.analysis.request
# quantiq.analysis.completed
```

**체크항목:**
- [ ] quantiq.analysis.request 토픽 존재
- [ ] quantiq.analysis.completed 토픽 존재

### 4.3 메시지 발행 테스트

```bash
# 메시지 컨슈머 대기 (터미널 1)
docker-compose exec kafka kafka-console-consumer.sh \
  --bootstrap-server kafka:29092 \
  --topic quantiq.analysis.request \
  --from-beginning

# 메시지 발행 (터미널 2)
echo '{"type":"TECHNICAL","start_date":"2025-01-20","end_date":"2025-01-29"}' | \
docker-compose exec -T kafka kafka-console-producer.sh \
  --broker-list kafka:29092 \
  --topic quantiq.analysis.request

# 예상: 터미널 1에서 메시지 수신됨
```

**체크항목:**
- [ ] 메시지 발행 성공
- [ ] 메시지 수신 성공
- [ ] 메시지 포맷 정상

---

## 5️⃣ quantiq-data-engine 상태 확인 (5분)

### 5.1 서비스 실행

```bash
# 컨테이너 상태 확인
docker-compose ps | grep data-engine

# 예상:
# quantiq-data-engine   quantiq-data-engine:latest   Up   0.0.0.0:8001->8000/tcp
```

**체크항목:**
- [ ] quantiq-data-engine 컨테이너 실행 중
- [ ] 포트 8001 (또는 8000) 열려있음

### 5.2 API 헬스 체크

```bash
# 헬스 체크
curl http://localhost:8001/health

# 예상 응답:
# {"status":"alive"}

# 루트 엔드포인트
curl http://localhost:8001/

# 예상 응답:
# {"status":"Quantiq Data Engine is running","kafka_topic":"quantiq.analysis.request"}
```

**체크항목:**
- [ ] /health 엔드포인트 응답 정상 (200)
- [ ] / 엔드포인트 응답 정상 (200)

### 5.3 로그 확인

```bash
# 최근 로그 확인
docker-compose logs -n 50 quantiq-data-engine

# 에러 로그 확인
docker-compose logs quantiq-data-engine | grep -i error

# 분석 로그 확인
docker-compose logs quantiq-data-engine | grep -i "analysis"
```

**체크항목:**
- [ ] 심각한 에러 없음
- [ ] MongoDB 연결 성공 로그 있음
- [ ] Kafka 구독 성공 로그 있음

---

## 6️⃣ 분석 파이프라인 전체 테스트 (10분)

### 6.1 분석 요청 발행

```bash
# 터미널에서 분석 요청 발행
echo '{"type":"ALL","start_date":"2025-01-01","end_date":"2025-01-29"}' | \
docker-compose exec -T kafka kafka-console-producer.sh \
  --broker-list kafka:29092 \
  --topic quantiq.analysis.request
```

### 6.2 실행 로그 모니터링

```bash
# 로그 실시간 확인
docker-compose logs -f quantiq-data-engine | grep -E "(Starting|complete|error|stored)"

# 예상 로그 시퀀스:
# Starting Technical Analysis...
# Analysis complete. X stocks recommended.
# TECHNICAL_COMPLETED event published
# ANALYSIS_COMPLETED event published
```

**체크항목:**
- [ ] 분석 시작 로그 보임
- [ ] 분석 완료 로그 보임
- [ ] 에러 로그 없음

### 6.3 결과 확인

```bash
# 최신 분석 결과 확인 (3분 후)
docker-compose exec mongodb mongosh
use stock_trading

db.stock_recommendations.find({}).sort({date: -1}).limit(5)

# 날짜가 오늘자여야 함
```

**체크항목:**
- [ ] 새로운 분석 결과가 저장됨
- [ ] 날짜가 오늘 또는 최근 날짜
- [ ] 지표들이 정상 범위의 값

---

## 📊 검증 결과 요약

### 체크리스트 완료율 계산

```
1️⃣ MongoDB 연결: ___/5
2️⃣ 경제 데이터: ___/8
3️⃣ 분석 결과: ___/6
4️⃣ Kafka 통합: ___/4
5️⃣ 서비스 상태: ___/5
6️⃣ 파이프라인 테스트: ___/3

전체: ___/31 (___%)
```

### 결론

**합격 기준:** 31개 체크항목 중 28개 이상 통과 (90% 이상)

**결과:**
- [ ] ✅ 통과 (마이그레이션 진행 가능)
- [ ] ⚠️ 경고 (일부 수정 필요)
- [ ] ❌ 실패 (마이그레이션 연기 필요)

---

## 🔧 문제 해결

### 문제: MongoDB 연결 실패

```bash
# 1. MongoDB 컨테이너 상태 확인
docker-compose ps mongodb

# 2. 로그 확인
docker-compose logs mongodb

# 3. 재시작
docker-compose restart mongodb

# 4. 포트 충돌 확인
lsof -i :27017
```

### 문제: 분석 데이터 없음

```bash
# 1. 활성 주식 확인
db.stocks.countDocuments({is_active: true})

# 2. 테스트 데이터 추가
db.stocks.insertMany([
  { ticker: "AAPL", stock_name: "Apple", is_active: true },
  { ticker: "MSFT", stock_name: "Microsoft", is_active: true }
])

# 3. 분석 수동 실행 (개발자만)
python -c "from src.services.technical_analysis import TechnicalAnalysisService; TechnicalAnalysisService().analyze_stocks()"
```

### 문제: Kafka 메시지 미수신

```bash
# 1. Kafka 상태 확인
docker-compose exec kafka kafka-broker-api-versions.sh \
  --bootstrap-server kafka:29092

# 2. 토픽 확인/생성
docker-compose exec kafka kafka-topics.sh \
  --bootstrap-server kafka:29092 \
  --create \
  --topic quantiq.analysis.request \
  --partitions 1 \
  --replication-factor 1

# 3. 컨슈머 그룹 확인
docker-compose exec kafka kafka-consumer-groups.sh \
  --bootstrap-server kafka:29092 \
  --list
```

---

## 📝 검증 기록

**검증자:** ____________________

**검증 날짜:** ____________________

**검증 결과:** ☐ 통과 / ☐ 경고 / ☐ 실패

**특이사항:**

_________________________________________________________________

_________________________________________________________________

**다음 단계:**

- [ ] 마이그레이션 진행 (통과 시)
- [ ] 문제 해결 후 재검증 (경고 시)
- [ ] 개선 후 재검증 (실패 시)

---

**마지막 업데이트:** 2025-01-29
**버전:** 1.0
