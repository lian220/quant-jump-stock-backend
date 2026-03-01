-- ============================================
-- 08_tiers.sql
-- Sources: V9 (user_tiers) + V53 (tier_configurations)
-- ============================================

CREATE TABLE IF NOT EXISTS user_tiers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    tier VARCHAR(20) DEFAULT 'FREE' CHECK (tier IN ('FREE', 'PREMIUM', 'PREMIUM_YEARLY')),
    started_at TIMESTAMP,
    expires_at TIMESTAMP,
    backtest_count_today INTEGER DEFAULT 0,
    backtest_count_reset_at DATE DEFAULT CURRENT_DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_tiers_tier ON user_tiers(tier);
CREATE INDEX IF NOT EXISTS idx_user_tiers_expires_at ON user_tiers(expires_at);

COMMENT ON TABLE user_tiers IS 'User subscription tier management';
COMMENT ON COLUMN user_tiers.id IS '사용자 티어 고유 ID (PK)';
COMMENT ON COLUMN user_tiers.user_id IS '사용자 ID (FK, UNIQUE)';
COMMENT ON COLUMN user_tiers.tier IS 'Subscription tier: FREE, PREMIUM, PREMIUM_YEARLY';
COMMENT ON COLUMN user_tiers.started_at IS 'When the premium subscription started';
COMMENT ON COLUMN user_tiers.expires_at IS 'When the premium subscription expires';
COMMENT ON COLUMN user_tiers.backtest_count_today IS 'Number of backtests performed today (for FREE tier limit)';
COMMENT ON COLUMN user_tiers.backtest_count_reset_at IS 'Date when backtest count was last reset';
COMMENT ON COLUMN user_tiers.created_at IS '티어 레코드 생성 일시';
COMMENT ON COLUMN user_tiers.updated_at IS '티어 정보 수정 일시';

-- ============================================

CREATE TABLE IF NOT EXISTS tier_configurations (
    id BIGSERIAL PRIMARY KEY,
    tier VARCHAR(20) NOT NULL UNIQUE,
    max_subscription_count INTEGER NOT NULL DEFAULT 3,
    max_backtest_daily INTEGER NOT NULL DEFAULT 3,
    max_backtest_per_strategy INTEGER NOT NULL DEFAULT 5,
    is_unlimited_subscription BOOLEAN NOT NULL DEFAULT FALSE,
    is_unlimited_backtest BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100)
);

COMMENT ON TABLE tier_configurations IS '티어별 제한 설정 (하드코딩 제거 + 어드민 관리)';
COMMENT ON COLUMN tier_configurations.max_subscription_count IS '전략 구독 최대 수 (is_unlimited_subscription=true면 무시)';
COMMENT ON COLUMN tier_configurations.max_backtest_daily IS '일일 백테스트 최대 수';
COMMENT ON COLUMN tier_configurations.max_backtest_per_strategy IS '전략당 백테스트 보관 최대 수';
