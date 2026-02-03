# SCRUM-202: 공개 전략 목록 API 명세

## API 엔드포인트

```
GET /api/v1/marketplace/strategies
```

## 요청 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| category | StrategyCategory | N | - | 전략 카테고리 필터 |
| minCagr | BigDecimal | N | - | 최소 CAGR (%) |
| maxMdd | BigDecimal | N | - | 최대 MDD (%) |
| sortBy | String | N | subscribers | 정렬 기준 |
| page | Int | N | 0 | 페이지 번호 (0부터 시작) |
| size | Int | N | 20 | 페이지 크기 |

### category 값
- `VALUE`: 가치투자
- `MOMENTUM`: 모멘텀
- `ASSET_ALLOCATION`: 자산배분
- `QUANT_COMPOSITE`: 퀀트 복합
- `SEASONAL`: 계절성
- `ML_PREDICTION`: AI/ML 예측

### sortBy 값
- `subscribers`: 구독자 수 (기본값)
- `cagr`: CAGR (높은 순)
- `sharpe`: 샤프 비율 (높은 순)
- `recent`: 최신순

## 응답 형식

```json
{
  "strategies": [
    {
      "id": 1,
      "name": "전략 이름",
      "description": "전략 설명",
      "category": "VALUE",
      "isPremium": false,
      "subscriberCount": 100,
      "averageRating": 4.5,
      "rebalanceFrequency": "MONTHLY",
      "backtestResult": {
        "cagr": 15.5,
        "mdd": -12.3,
        "sharpeRatio": 1.8,
        "totalReturn": 50.0,
        "volatility": 18.5,
        "winRate": 65.0,
        "startDate": "2020-01-01",
        "endDate": "2023-12-31"
      },
      "createdAt": "2024-01-01T00:00:00"
    }
  ],
  "pagination": {
    "currentPage": 0,
    "pageSize": 20,
    "totalElements": 100,
    "totalPages": 5,
    "isFirst": true,
    "isLast": false
  }
}
```

## 요청 예시

### 1. 기본 조회 (구독자 수 정렬)
```bash
curl -X GET "http://localhost:10010/api/v1/marketplace/strategies"
```

### 2. 카테고리 필터링
```bash
curl -X GET "http://localhost:10010/api/v1/marketplace/strategies?category=VALUE"
```

### 3. CAGR 필터링 (10% 이상)
```bash
curl -X GET "http://localhost:10010/api/v1/marketplace/strategies?minCagr=10.0"
```

### 4. MDD 필터링 (최대 -15%)
```bash
curl -X GET "http://localhost:10010/api/v1/marketplace/strategies?maxMdd=-15.0"
```

### 5. CAGR 정렬
```bash
curl -X GET "http://localhost:10010/api/v1/marketplace/strategies?sortBy=cagr"
```

### 6. Sharpe 비율 정렬
```bash
curl -X GET "http://localhost:10010/api/v1/marketplace/strategies?sortBy=sharpe"
```

### 7. 복합 필터링
```bash
curl -X GET "http://localhost:10010/api/v1/marketplace/strategies?category=VALUE&minCagr=10.0&maxMdd=-15.0&sortBy=cagr&page=0&size=10"
```

### 8. 페이징
```bash
curl -X GET "http://localhost:10010/api/v1/marketplace/strategies?page=1&size=10"
```

## Swagger UI

API 문서는 Swagger UI에서 확인 가능합니다:
```
http://localhost:10010/swagger-ui.html
```

## 구현 파일

### Controller
- `adapter/input/rest/marketplace/MarketplaceController.kt`

### Service
- `application/marketplace/MarketplaceService.kt`

### DTO
- `application/marketplace/StrategyListRequest.kt`
- `application/marketplace/StrategyListResponse.kt`

### Repository
- `adapter/output/persistence/jpa/StrategyJpaRepository.kt` (확장)

## 성능 고려사항

1. **인덱스 필요**
   - `strategies.is_public`
   - `strategies.status`
   - `strategies.category`
   - `strategies.subscriber_count`
   - `backtest_results.cagr`
   - `backtest_results.sharpe_ratio`

2. **N+1 쿼리 주의**
   - 백테스트 결과는 lazy loading으로 처리
   - 필요 시 join fetch 고려

3. **캐싱 고려**
   - 인기 전략 목록은 캐싱 권장
   - Redis 등의 캐시 서버 활용

## 테스트 방법

1. **서버 실행**
   ```bash
   ./gradlew bootRun
   ```

2. **Swagger UI 접속**
   ```
   http://localhost:10010/swagger-ui.html
   ```

3. **API 테스트**
   - Swagger UI에서 Try it out 버튼 클릭
   - 파라미터 입력 후 Execute

4. **curl 테스트**
   - 위의 요청 예시 참고

## 완료 기준

- [x] Controller 구현
- [x] Service 비즈니스 로직 구현
- [x] Repository 쿼리 메서드 추가
- [x] DTO 클래스 작성
- [x] Swagger 문서화
- [x] 빌드 검증 (컴파일 오류 없음)
- [x] API 명세 문서 작성
- [ ] 통합 테스트 (서버 실행 후 수동 검증 필요)
- [ ] 데이터베이스 인덱스 추가 (선택)
