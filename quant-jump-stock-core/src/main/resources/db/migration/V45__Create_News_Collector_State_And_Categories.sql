-- V45: 뉴스 수집기 상태 MongoDB → PostgreSQL 이관 + 뉴스 카테고리 정규화

-- 뉴스 수집기 상태 (MongoDB news_collector_state 컬렉션 대체)
CREATE TABLE news_collector_state (
    id                   BIGSERIAL PRIMARY KEY,
    source               VARCHAR(50) NOT NULL UNIQUE,
    last_fetched_at      TIMESTAMP,
    last_fetched_id      VARCHAR(255),
    fetch_count          BIGINT NOT NULL DEFAULT 0,
    consecutive_errors   INT NOT NULL DEFAULT 0,
    total_errors         BIGINT NOT NULL DEFAULT 0,
    avg_response_time_ms BIGINT NOT NULL DEFAULT 0,
    last_error           TEXT,
    last_error_at        TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE news_collector_state IS '뉴스 수집기 상태 (소스별 마지막 수집 시각, 에러 카운트 등)';
COMMENT ON COLUMN news_collector_state.source IS 'NewsSource enum (SAVETICKER, FINNHUB, TELEGRAM 등)';
COMMENT ON COLUMN news_collector_state.last_fetched_at IS '마지막 수집 시각';
COMMENT ON COLUMN news_collector_state.last_fetched_id IS '마지막 수집 항목 ID';
COMMENT ON COLUMN news_collector_state.fetch_count IS '누적 수집 횟수';
COMMENT ON COLUMN news_collector_state.consecutive_errors IS '연속 에러 횟수 (성공 시 0으로 리셋)';
COMMENT ON COLUMN news_collector_state.avg_response_time_ms IS '평균 API 응답 시간 (ms)';

-- 뉴스 카테고리 마스터 (NewsScorer 하드코딩 대체)
CREATE TABLE news_categories (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    name_en    VARCHAR(100),
    weight     DOUBLE PRECISION NOT NULL DEFAULT 0.10,
    is_active  BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE news_categories IS '뉴스 카테고리 마스터 (가중치 기반 중요도 관리)';
COMMENT ON COLUMN news_categories.weight IS '중요도 가중치 (0.0~1.0, NewsScorer에서 참조)';

-- 기본 카테고리 시딩 (현재 NewsScorer categoryWeights 하드코딩 값)
INSERT INTO news_categories (name, name_en, weight, sort_order) VALUES
    ('속보', 'Breaking News', 0.40, 1),
    ('경제지표', 'Economic Indicators', 0.35, 2),
    ('연준', 'Fed', 0.35, 3),
    ('투자 의견', 'Investment Opinion', 0.30, 4),
    ('분석', 'Analysis', 0.25, 5),
    ('에너지', 'Energy', 0.20, 6),
    ('종합', 'General', 0.15, 7),
    ('정보', 'Information', 0.15, 8),
    ('암호화폐', 'Crypto', 0.10, 9),
    ('일정', 'Schedule', 0.10, 10);

-- 사용자 뉴스 구독 (개인화 알림용 - 테이블만 생성, 서비스 로직은 추후)
CREATE TABLE user_news_subscriptions (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_type  VARCHAR(20) NOT NULL,
    subscription_value VARCHAR(100) NOT NULL,
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, subscription_type, subscription_value)
);

COMMENT ON TABLE user_news_subscriptions IS '사용자 뉴스 구독 (카테고리/티커/소스별 개인화 알림)';
COMMENT ON COLUMN user_news_subscriptions.subscription_type IS 'CATEGORY, TICKER, SOURCE';
COMMENT ON COLUMN user_news_subscriptions.subscription_value IS '구독 대상 값 (카테고리명, 티커명, 소스명)';

CREATE INDEX idx_user_news_subs_user ON user_news_subscriptions(user_id);
CREATE INDEX idx_user_news_subs_type_value ON user_news_subscriptions(subscription_type, subscription_value);
