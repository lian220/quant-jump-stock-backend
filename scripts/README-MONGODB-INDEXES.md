# MongoDB 인덱스 생성 가이드

## 개요

`stock_analysis_results`와 `stock_predictions` 컬렉션에 복합 유니크 인덱스를 생성하여 데이터 무결성과 조회 성능을 보장합니다.

## 생성되는 인덱스

### 1. stock_analysis_results

| 인덱스명 | 필드 | 타입 | 설명 |
|---------|------|------|------|
| idx_date_ticker | date(1), ticker(1) | UNIQUE | 복합 유니크 키 |
| idx_ticker | ticker(1) | 일반 | 티커 조회 최적화 |
| idx_date | date(-1) | 일반 | 시계열 조회 최적화 (내림차순) |

### 2. stock_predictions

| 인덱스명 | 필드 | 타입 | 설명 |
|---------|------|------|------|
| idx_date_ticker | date(1), ticker(1) | UNIQUE | 복합 유니크 키 |
| idx_ticker | ticker(1) | 일반 | 티커 조회 최적화 |
| idx_date | date(-1) | 일반 | 시계열 조회 최적화 (내림차순) |

## 실행 방법

### 방법 1: Docker 컨테이너 내부에서 실행 (권장)

```bash
# 1. MongoDB 컨테이너 접속
docker exec -it mongodb bash

# 2. mongosh로 스크립트 실행
mongosh stock_trading --username quantiq_user --password quantiq_password --authenticationDatabase admin < /docker-entrypoint-initdb.d/init-mongodb-indexes.js

# 또는 컨테이너 밖에서 직접 실행
docker exec -i mongodb mongosh stock_trading --username quantiq_user --password quantiq_password --authenticationDatabase admin < scripts/init-mongodb-indexes.js
```

### 방법 2: 로컬 mongosh 사용

```bash
# MongoDB 실행 중인지 확인
docker ps | grep mongodb

# mongosh 실행
mongosh mongodb://quantiq_user:quantiq_password@localhost:27017/stock_trading?authSource=admin < scripts/init-mongodb-indexes.js
```

### 방법 3: MongoDB Compass GUI 사용

1. MongoDB Compass 실행
2. 연결 문자열: `mongodb://quantiq_user:quantiq_password@localhost:27017/?authSource=admin`
3. `stock_trading` 데이터베이스 선택
4. 각 컬렉션 → Indexes 탭 → Create Index
   - `stock_analysis_results`: `{ "date": 1, "ticker": 1 }` (unique: true)
   - `stock_predictions`: `{ "date": 1, "ticker": 1 }` (unique: true)

## 인덱스 확인

```bash
# mongosh에서 실행
use stock_trading;

// stock_analysis_results 인덱스 확인
db.stock_analysis_results.getIndexes();

// stock_predictions 인덱스 확인
db.stock_predictions.getIndexes();
```

### 예상 출력

```javascript
// stock_analysis_results
[
  { v: 2, key: { _id: 1 }, name: '_id_' },
  { v: 2, key: { date: 1, ticker: 1 }, name: 'idx_date_ticker', unique: true },
  { v: 2, key: { ticker: 1 }, name: 'idx_ticker' },
  { v: 2, key: { date: -1 }, name: 'idx_date' }
]
```

## 주의사항

### 1. 기존 중복 데이터 처리

인덱스 생성 전 중복 데이터가 있으면 생성이 실패합니다:

```bash
# 중복 데이터 확인
db.stock_analysis_results.aggregate([
  { $group: { _id: { date: "$date", ticker: "$ticker" }, count: { $sum: 1 } } },
  { $match: { count: { $gt: 1 } } }
]);

# 중복 데이터 제거 (최신 것만 남기기)
db.stock_analysis_results.aggregate([
  { $sort: { created_at: -1 } },
  { $group: {
      _id: { date: "$date", ticker: "$ticker" },
      docs: { $push: "$$ROOT" }
  }},
  { $project: {
      duplicates: { $slice: ["$docs", 1, { $size: "$docs" }] }
  }},
  { $unwind: "$duplicates" },
  { $replaceRoot: { newRoot: "$duplicates" } }
]).forEach(doc => {
  db.stock_analysis_results.deleteOne({ _id: doc._id });
});
```

### 2. 성능 고려사항

- **background: true**: 백그라운드 생성으로 서비스 중단 최소화
- 인덱스 생성 시간: 컬렉션 크기에 비례 (수만 건 기준 1-2분)
- 디스크 공간: 인덱스는 데이터 크기의 약 10-20% 추가 사용

### 3. Spring Boot 자동 인덱스 생성

Spring Boot 실행 시 `@CompoundIndex` 어노테이션에 의해 자동으로 인덱스가 생성됩니다.
단, 수동으로 먼저 생성해두면 더 빠르고 안전합니다.

```yaml
# application.yml
spring:
  data:
    mongodb:
      auto-index-creation: true  # 기본값: true
```

## 문제 해결

### 오류: "E11000 duplicate key error"

```bash
# 원인: 중복 데이터가 존재
# 해결: 위 "기존 중복 데이터 처리" 섹션 참조
```

### 오류: "Index already exists with different options"

```bash
# 원인: 기존 인덱스와 옵션이 다름
# 해결: 기존 인덱스 삭제 후 재생성
db.stock_analysis_results.dropIndex("idx_date_ticker");
```

### 인덱스 생성이 너무 느림

```bash
# 원인: 대용량 데이터 + foreground 생성
# 해결: background 옵션 확인 (스크립트에 이미 포함됨)
```

## 검증 체크리스트

- [ ] Docker MongoDB 컨테이너 실행 중
- [ ] 인덱스 스크립트 실행 완료
- [ ] `getIndexes()` 명령으로 인덱스 확인
- [ ] Spring Boot 재시작 후 정상 동작 확인
- [ ] Python 스크립트 실행 시 중복 오류 없음

## 관련 파일

- **인덱스 스크립트**: `scripts/init-mongodb-indexes.js`
- **Kotlin 도메인**: `core/src/main/kotlin/com/quantjumpstock/core/domain/StockAnalysis.kt`
- **Kotlin 도메인**: `core/src/main/kotlin/com/quantjumpstock/core/domain/StockPrediction.kt`
- **Python 스크립트**: `scripts/ml/predict_optimized.py`
