-- 유저별 알림 카테고리 설정 테이블
CREATE TABLE user_notification_preferences (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,

    -- 카테고리별 on/off
    news_enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    announcement_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    trading_signal_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    backtest_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    price_alert_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    weekly_digest_enabled   BOOLEAN NOT NULL DEFAULT TRUE,

    -- 채널별 on/off (Web Push)
    push_enabled            BOOLEAN NOT NULL DEFAULT TRUE,

    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_unp_user_id ON user_notification_preferences(user_id);

COMMENT ON TABLE user_notification_preferences IS '유저별 알림 카테고리/채널 설정';
