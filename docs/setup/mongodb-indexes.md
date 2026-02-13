# MongoDB 인덱스 설정 가이드

## news_sentiment 컬렉션 인덱스

뉴스 감정 분석 데이터 조회 성능을 위한 인덱스 설정.

### 필수 인덱스

```javascript
// MongoDB Shell 접속
mongosh "mongodb://quantiq_user:quantiq_password@localhost:27017/stock_trading?authSource=admin"

// 1. 종목 + 날짜별 조회 (추천 시스템용)
db.news_sentiment.createIndex(
  { ticker: 1, date: -1 },
  { name: "ticker_date_idx" }
)

// 2. 종목 + 발행시간 (최신 뉴스 우선)
db.news_sentiment.createIndex(
  { ticker: 1, published_at: -1 },
  { name: "ticker_published_idx" }
)

// 3. URL 중복 방지 (unique)
db.news_sentiment.createIndex(
  { ticker: 1, url: 1 },
  { name: "ticker_url_unique_idx", unique: true }
)

// 4. TTL 인덱스 (90일 후 자동 삭제)
db.news_sentiment.createIndex(
  { created_at: 1 },
  { expireAfterSeconds: 7776000, name: "ttl_90days_idx" }  // 90일 = 7776000초
)

// 5. 감정 점수 + 관련도 (고품질 뉴스 필터)
db.news_sentiment.createIndex(
  { ticker: 1, "sentiment.score": -1, "sentiment.relevance_score": -1 },
  { name: "ticker_quality_idx" }
)
```

### 인덱스 확인

```javascript
// 생성된 인덱스 목록 확인
db.news_sentiment.getIndexes()

// 인덱스 사용 통계
db.news_sentiment.aggregate([
  { $indexStats: {} }
])
```

### 인덱스 삭제 (필요시)

```javascript
// 특정 인덱스 삭제
db.news_sentiment.dropIndex("ticker_date_idx")

// 모든 인덱스 삭제 (_id 제외)
db.news_sentiment.dropIndexes()
```

## 실행 방법

### 1. Docker 환경 (로컬 개발)

```bash
# MongoDB 컨테이너 접속
docker exec -it quantiq-mongodb mongosh -u quantiq_user -p quantiq_password --authenticationDatabase admin

# 또는 외부에서
mongosh "mongodb://quantiq_user:quantiq_password@localhost:27017/stock_trading?authSource=admin"

# 인덱스 생성 스크립트 실행
use stock_trading
db.news_sentiment.createIndex({ ticker: 1, date: -1 }, { name: "ticker_date_idx" })
db.news_sentiment.createIndex({ ticker: 1, published_at: -1 }, { name: "ticker_published_idx" })
db.news_sentiment.createIndex({ ticker: 1, url: 1 }, { name: "ticker_url_unique_idx", unique: true })
db.news_sentiment.createIndex({ created_at: 1 }, { expireAfterSeconds: 7776000, name: "ttl_90days_idx" })
db.news_sentiment.createIndex({ ticker: 1, "sentiment.score": -1, "sentiment.relevance_score": -1 }, { name: "ticker_quality_idx" })
```

### 2. GCP 프로덕션 환경

```bash
# GCE VM 접속
gcloud compute ssh quantiq-vm-01 --zone=asia-northeast3-a

# MongoDB 컨테이너 접속
sudo docker exec -it mongodb mongosh -u quantiq_user -p quantiq_password --authenticationDatabase admin

# 위의 인덱스 생성 명령 실행
```

## 성능 모니터링

```javascript
// 느린 쿼리 로그 활성화
db.setProfilingLevel(1, { slowms: 100 })

// 프로파일링 결과 확인
db.system.profile.find().limit(10).sort({ ts: -1 }).pretty()

// 특정 쿼리 실행 계획 확인
db.news_sentiment.find({ ticker: "AAPL" }).explain("executionStats")
```

## 예상 데이터 규모

- **종목 수**: 35개
- **일 평균 뉴스**: 10건/종목
- **보관 기간**: 90일
- **총 문서 수**: 35 × 10 × 90 = 31,500건
- **예상 용량**: 약 30-50MB (압축 후)

## TTL 정책

- **90일 자동 삭제**: `created_at` 기준으로 90일 지난 문서 자동 삭제
- **디스크 공간 절약**: 오래된 뉴스는 자동 정리
- **추천 근거 보존**: 최근 3개월 뉴스만 보관 (충분)

## 주의사항

1. **unique 인덱스**: ticker + url 조합으로 중복 방지
2. **TTL 작동**: MongoDB가 백그라운드로 60초마다 체크
3. **인덱스 크기**: 인덱스도 메모리 사용, 필요한 것만 생성
4. **복합 인덱스 순서**: 쿼리 패턴에 맞게 필드 순서 중요
