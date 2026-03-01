-- ============================================
-- 11_news.sql
-- Sources: V45 + V46 + V47 (FK ON DELETE CASCADE baked in)
-- ============================================

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
COMMENT ON COLUMN news_collector_state.consecutive_errors IS '연속 에러 횟수 (성공 시 0으로 리셋)';

-- ============================================

CREATE TABLE news_categories (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    name_en         VARCHAR(100) NOT NULL,
    category_group  VARCHAR(30) NOT NULL,
    description     VARCHAR(300),
    icon            VARCHAR(50),
    weight          DOUBLE PRECISION NOT NULL DEFAULT 0.10,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_news_categories_group ON news_categories(category_group);

COMMENT ON TABLE news_categories IS '뉴스 카테고리 마스터 (투자자 관점 분류 + 가중치)';
COMMENT ON COLUMN news_categories.category_group IS 'UI 그룹: MARKET(시장), COMPANY(기업), ASSET(자산), MACRO(매크로), INFO(정보)';
COMMENT ON COLUMN news_categories.weight IS '중요도 가중치 (0.0~1.0, NewsScorer에서 참조)';
COMMENT ON COLUMN news_categories.icon IS '프론트엔드 아이콘 식별자 (Lucide 등)';

-- ============================================

CREATE TABLE news_source_tag_mappings (
    id              BIGSERIAL PRIMARY KEY,
    source          VARCHAR(50) NOT NULL,
    source_tag      VARCHAR(200) NOT NULL,
    category_id     BIGINT NOT NULL REFERENCES news_categories(id) ON DELETE CASCADE,  -- V47: CASCADE
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(source, source_tag)
);

CREATE INDEX idx_source_tag_mappings_lookup ON news_source_tag_mappings(source, source_tag);

COMMENT ON TABLE news_source_tag_mappings IS '소스별 raw 태그 → 정규화 카테고리 매핑';
COMMENT ON COLUMN news_source_tag_mappings.source IS 'NewsSource enum (SAVETICKER, TELEGRAM 등)';
COMMENT ON COLUMN news_source_tag_mappings.source_tag IS '소스 API에서 오는 원본 태그';

-- ============================================

CREATE TABLE user_news_subscriptions (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_type  VARCHAR(20) NOT NULL,
    subscription_value VARCHAR(100) NOT NULL,
    display_name       VARCHAR(200),
    notify_channel     VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, subscription_type, subscription_value, notify_channel)
);

CREATE INDEX idx_user_news_subs_user ON user_news_subscriptions(user_id);
CREATE INDEX idx_user_news_subs_type_value ON user_news_subscriptions(subscription_type, subscription_value);
CREATE INDEX idx_user_news_subs_channel ON user_news_subscriptions(notify_channel, is_active);

COMMENT ON TABLE user_news_subscriptions IS '사용자 뉴스 구독 (카테고리/티커/소스별 개인화 알림)';
COMMENT ON COLUMN user_news_subscriptions.subscription_type IS 'CATEGORY, TICKER, SOURCE';
COMMENT ON COLUMN user_news_subscriptions.notify_channel IS 'IN_APP, PUSH, TELEGRAM, SLACK, EMAIL';

-- ============================================

CREATE TABLE user_news_notifications (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    news_id       VARCHAR(100),
    category_name VARCHAR(100),
    title         VARCHAR(500) NOT NULL,
    message       TEXT,
    importance    DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    source_url    TEXT,
    is_read       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_news_notif_user_read ON user_news_notifications(user_id, is_read);
CREATE INDEX idx_user_news_notif_created ON user_news_notifications(created_at DESC);

COMMENT ON TABLE user_news_notifications IS '사용자 인앱 뉴스 알림';
COMMENT ON COLUMN user_news_notifications.news_id IS 'MongoDB 뉴스 문서 ID';
COMMENT ON COLUMN user_news_notifications.importance IS '뉴스 중요도 점수 (0.0~1.0)';
