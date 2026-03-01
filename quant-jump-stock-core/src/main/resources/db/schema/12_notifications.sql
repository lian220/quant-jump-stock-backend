-- ============================================
-- 12_notifications.sql
-- Sources: V55
-- ============================================

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

COMMENT ON TABLE notifications IS '통합 알림 테이블';
