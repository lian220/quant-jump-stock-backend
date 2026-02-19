# Backend 기술 개선사항

## 🔥 긴급 작업 (이번 주)

### 추천 로직 개선 (부분 점수 시스템)
- [ ] **Backend**: `RecommendationService.kt` — `.filter { it.isRecommended }` 제거
  - Composite Score 기준으로만 필터링 (`compositeScore >= minCompositeScore`)
- [ ] **Data Engine**: `sync_service.py` — `is_recommended` 재계산
  - 새 메서드: `_determine_recommended(composite_score, grade)`
  - BETA 임계값: 0.8점, 통합 후: 2.0점
- [ ] **Frontend**: `recommendations/page.tsx` — Beta 배너 문구 업데이트
  - "최소 0.8점 이상" 안내
- [ ] **테스트**: 운영 데이터 마이그레이션 후 E2E 테스트
  - 예상: 추천 종목 2~3개 → 10~15개
  - 상세: [추천시스템.md](./docs/features/추천시스템.md)

---

## 아키텍처 개선
- [ ] 헥사고날 아키텍처 마이그레이션 (현재 65/100 → 목표 100/100)
  - 상세: [백엔드_아키텍처.md](./docs/architecture/refactor/백엔드_아키텍처.md)
- [ ] ArchUnit 위반 해소 (27건 → 0건)
- [ ] Application → JPA 직접 의존성 제거 (11개 서비스)

## 데이터
- [ ] Stock 데이터 PostgreSQL 마이그레이션 후속 (Adapter 패턴 적용)
- [ ] MongoDB 단계적 제거 (dual-write → RDB only → 삭제)
- [ ] Alpha Vantage 다중 API 키 운영 체계 도입 (종목 수 확장 대응)
  - [ ] 환경변수 확장: `ALPHA_VANTAGE_API_KEY` → `ALPHA_VANTAGE_API_KEYS`(쉼표 구분)
  - [ ] Key Pool/라운드로빈 호출 로직 추가 (분당 호출 제한 분산)
  - [ ] 키별 실패율/429 응답 모니터링 및 자동 제외(일시) 로직 추가
  - [ ] 키 관리 문서 업데이트: [알파_밴티지_에이피아이_키.md](./docs/setup/알파_밴티지_에이피아이_키.md)

## 기능
- [ ] Vertex AI CustomJob 파라미터 기능 추가
- [ ] 이메일/전화번호 인증 구현
- [ ] 뉴스 감정 분석 통합 (종목 추천 근거)
  - [x] MongoDB `news_sentiment` 컬렉션 설계 (title, summary, url, sentiment, relevance_score)
  - [ ] 인덱스 생성 (ticker+date, ticker+published_at, TTL 90일) → [docs/setup/몽고디비_인덱스.md](./docs/setup/몽고디비_인덱스.md)
  - [x] Data Engine: SentimentAnalysisService 수정 (원본 기사 저장 추가)
  - [x] 뉴스 API 통합 (Alpha Vantage NEWS_SENTIMENT)
  - [x] 감정 분석: Alpha Vantage 내장 감정 점수 사용
  - [x] 기존 스케줄러 통합 (SentimentAnalysisHandler 활용)
  - [ ] daily_stock_data에 sentiment_summary 필드 추가 (선택)
  - [ ] Core API: NewsSentimentMongoRepository 구현
  - [ ] RecommendationController: GET /recommendations/{ticker}/news-analysis 추가
  - [ ] Frontend: 뉴스 분석 컴포넌트 (감정 뱃지, 상위 뉴스 5개 표시)
  - [ ] **테스트**: 스케줄러 실행 후 news_sentiment 컬렉션 확인

## 보안/안정성 (코드 리뷰 2026-02-19)
- [ ] 백테스트 Rate Limiting DB 레벨 잠금 (TOCTOU 레이스 컨디션)
  - `BacktestController.kt` — 동시 요청 시 FREE 티어 제한(5개) 초과 가능
  - SELECT FOR UPDATE 또는 DB unique constraint로 해결
- [ ] DB 복합 인덱스 추가 (Flyway 마이그레이션)
  - `backtest_results(user_id, strategy_id, status)` — countUserCustom 쿼리
  - `backtest_results(status, created_at)` — findLatestCompleted 쿼리
  - `prediction_results(user_id, created_date)` — findHighConfidenceBuySignals 쿼리
- [ ] Pub/Sub 핸들러 async/sync 패턴 통일
  - 현재: EconomicData(sync), TechnicalAnalysis(async), Sentiment(sync) 혼재
  - 전체 async 또는 전체 sync로 통일
- [ ] 외부 API 호출 재시도 로직 (exponential backoff)
  - FRED API, Yahoo Finance, SaveTicker API — 단일 실패 시 전체 수집 중단됨
  - tenacity 또는 자체 retry decorator 적용

## 성능
- [ ] JPA N+1 쿼리 최적화 (@EntityGraph 적용)
  - `BacktestResultJpaRepository.findByIdWithTrades()` — 관련 엔티티 별도 로드
  - `MarketplaceService` 배치 전략 로드 시 다중 쿼리 발생
- [ ] Kafka consumer 안정성 개선

## 테스트
- [ ] 단위 테스트 커버리지 (0% → 80%)
