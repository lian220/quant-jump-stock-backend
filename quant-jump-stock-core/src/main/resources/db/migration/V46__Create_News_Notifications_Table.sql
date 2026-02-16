-- V46: 인앱 뉴스 알림 테이블

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

COMMENT ON TABLE user_news_notifications IS '사용자 인앱 뉴스 알림';
COMMENT ON COLUMN user_news_notifications.news_id IS 'MongoDB 뉴스 문서 ID';
COMMENT ON COLUMN user_news_notifications.category_name IS '매칭된 카테고리명';
COMMENT ON COLUMN user_news_notifications.importance IS '뉴스 중요도 점수 (0.0~1.0)';

CREATE INDEX idx_user_news_notif_user_read ON user_news_notifications(user_id, is_read);
CREATE INDEX idx_user_news_notif_created ON user_news_notifications(created_at DESC);
