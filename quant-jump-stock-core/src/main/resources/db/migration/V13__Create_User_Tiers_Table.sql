-- V13: Create user_tiers table for subscription management
-- Tracks user subscription tiers and backtest limits

-- =====================================================================
-- 1. Create user_tiers table
-- =====================================================================
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

-- =====================================================================
-- 2. Create indexes
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_user_tiers_user_id ON user_tiers(user_id);
CREATE INDEX IF NOT EXISTS idx_user_tiers_tier ON user_tiers(tier);
CREATE INDEX IF NOT EXISTS idx_user_tiers_expires_at ON user_tiers(expires_at);

-- =====================================================================
-- 3. Comments
-- =====================================================================
COMMENT ON TABLE user_tiers IS 'User subscription tier management';
COMMENT ON COLUMN user_tiers.tier IS 'Subscription tier: FREE, PREMIUM, PREMIUM_YEARLY';
COMMENT ON COLUMN user_tiers.started_at IS 'When the premium subscription started';
COMMENT ON COLUMN user_tiers.expires_at IS 'When the premium subscription expires';
COMMENT ON COLUMN user_tiers.backtest_count_today IS 'Number of backtests performed today (for FREE tier limit)';
COMMENT ON COLUMN user_tiers.backtest_count_reset_at IS 'Date when backtest count was last reset';

-- =====================================================================
-- 4. Initialize user_tiers for existing users
-- =====================================================================
INSERT INTO user_tiers (user_id, tier)
SELECT id, 'FREE'
FROM users
WHERE id NOT IN (SELECT user_id FROM user_tiers)
ON CONFLICT (user_id) DO NOTHING;
