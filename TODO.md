# Backend 기술 부채 TODO

> **스코프 (Scope)**: Backend(Core + Data Engine) 단독 기술 부채 — 아키텍처/성능/보안/테스트
> **문서 역할**: Backend 서비스 내부 리팩토링·개선 항목 관리
>
> **여기 없는 것 (다른 곳에서 관리)**:
> - 제품 기능 우선순위 + 크로스 서비스 작업 → [../docs/할일.md](../docs/할일.md) (SSOT)
> - 백로그/이슈 트래킹 → Jira (`SCRUM-*`)
>
> **관련 문서**:
> - [백엔드_아키텍처.md](./docs/architecture/refactor/백엔드_아키텍처.md) — 14주 리팩토링 로드맵 (65/100 → 100/100)
> - [어댑터_패턴.md](./docs/architecture/refactor/어댑터_패턴.md) — Persistence Adapter 가이드
> - [테스트_가이드.md](./docs/architecture/refactor/테스트_가이드.md) — Kotest + Testcontainers

---

## 아키텍처 개선
- [ ] 헥사고날 아키텍처 마이그레이션 (현재 65/100 → 목표 100/100)
  - 상세: [백엔드_아키텍처.md](./docs/architecture/refactor/백엔드_아키텍처.md)
- [ ] ArchUnit 위반 해소 (27건 → 0건)
- [ ] Application → JPA 직접 의존성 제거 (11개 서비스)
- [ ] 도메인 모델 MongoDB 어노테이션 오염 제거 (8개 파일)

## 데이터
- [ ] Stock 데이터 PostgreSQL 마이그레이션 후속 (Adapter 패턴 적용)
- [ ] MongoDB 단계적 제거 (dual-write → RDB only → 삭제)
- [ ] Alpha Vantage 다중 API 키 운영 체계 도입 (종목 수 확장 대응)
  - [ ] 환경변수 확장: `ALPHA_VANTAGE_API_KEY` → `ALPHA_VANTAGE_API_KEYS`(쉼표 구분)
  - [ ] Key Pool/라운드로빈 호출 로직 추가 (분당 호출 제한 분산)
  - [ ] 키별 실패율/429 응답 모니터링 및 자동 제외(일시) 로직 추가
  - [ ] 키 관리 문서 업데이트: [알파_밴티지_에이피아이_키.md](./docs/setup/알파_밴티지_에이피아이_키.md)

## 기능 (Backend 단독)
- [ ] Vertex AI CustomJob 파라미터 기능 추가
- [ ] 이메일/전화번호 인증 구현
- [ ] 뉴스 감정 분석 Core API 연동 (제품 기능은 [../docs/할일.md](../docs/할일.md) 참조)
  - [x] MongoDB `news_sentiment` 컬렉션 설계
  - [x] Data Engine: SentimentAnalysisService 수정 (원본 기사 저장 추가)
  - [x] 뉴스 API 통합 (Alpha Vantage NEWS_SENTIMENT)
  - [x] 감정 분석: Alpha Vantage 내장 감정 점수 사용
  - [x] 기존 스케줄러 통합 (SentimentAnalysisHandler 활용)
  - [ ] 인덱스 생성 (ticker+date, ticker+published_at, TTL 90일) → [docs/setup/몽고디비_인덱스.md](./docs/setup/몽고디비_인덱스.md)
  - [ ] daily_stock_data에 sentiment_summary 필드 추가 (선택)
  - [ ] Core API: NewsSentimentMongoRepository 구현
  - [ ] RecommendationController: GET /recommendations/{ticker}/news-analysis 추가
  - [ ] **테스트**: 스케줄러 실행 후 news_sentiment 컬렉션 확인

## 보안/안정성 (코드 리뷰 2026-02-19)
- [ ] 백테스트 Rate Limiting DB 레벨 잠금 (TOCTOU 레이스 컨디션)
  - `BacktestController.kt` — 동시 요청 시 FREE 티어 제한(5개) 초과 가능
  - SELECT FOR UPDATE 또는 DB unique constraint로 해결
- [ ] DB 복합 인덱스 추가 (Flyway 마이그레이션)
  - `backtest_results(user_id, strategy_id, status)` — countUserCustom 쿼리
  - `backtest_results(status, created_at)` — findLatestCompleted 쿼리
  - `prediction_results(user_id, created_date)` — findHighConfidenceBuySignals 쿼리
  - ⚠️ 운영 적용 시 `CREATE INDEX CONCURRENTLY` 사용 필수 (표준 CREATE INDEX는 테이블 배타 락)
  - Flyway 마이그레이션은 트랜잭션 밖에서 실행해야 함 (`executeInTransaction=false` 설정 필요)
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
- [ ] Pub/Sub 안정성 개선 (dead-letter topics, ack deadline, 재시도 설정)

## 테스트
- [ ] 단위 테스트 커버리지 (0% → 80%)
- [ ] Data Engine 테스트 커버리지 (0% → 80%) — refactorplan/ARCHITECTURE.md 참조
