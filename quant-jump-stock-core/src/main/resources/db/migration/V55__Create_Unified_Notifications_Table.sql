-- 통합 알림 테이블
CREATE TABLE notifications (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type          VARCHAR(30) NOT NULL,
    priority      VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    title         VARCHAR(500) NOT NULL,
    message       TEXT,
    action_url    VARCHAR(500),
    metadata      JSONB,
    is_read       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_type ON notifications(type);

-- 기존 뉴스 알림 데이터 마이그레이션
INSERT INTO notifications (user_id, type, priority, title, message, action_url, metadata, is_read, created_at)
SELECT
    user_id,
    'NEWS',
    CASE
        WHEN importance >= 0.7 THEN 'HIGH'
        WHEN importance >= 0.4 THEN 'NORMAL'
        ELSE 'LOW'
    END,
    title,
    message,
    source_url,
    jsonb_build_object('newsId', news_id, 'categoryName', category_name, 'importance', importance),
    is_read,
    created_at
FROM user_news_notifications;
