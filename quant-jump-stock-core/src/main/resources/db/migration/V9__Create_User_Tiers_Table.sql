-- ============================================
-- V9: User Tiers Table (Consolidated)
-- Source: V13__Create_User_Tiers_Table.sql (DDL only)
-- PostgreSQL 15+
-- ============================================

-- ============================================
-- 1. User Tiers Table
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

-- ============================================
-- 2. Indexes
-- ============================================
CREATE INDEX IF NOT EXISTS idx_user_tiers_user_id ON user_tiers(user_id);
CREATE INDEX IF NOT EXISTS idx_user_tiers_tier ON user_tiers(tier);
CREATE INDEX IF NOT EXISTS idx_user_tiers_expires_at ON user_tiers(expires_at);

-- ============================================
-- 3. Comments
-- ============================================
COMMENT ON TABLE user_tiers IS 'User subscription tier management';
COMMENT ON COLUMN user_tiers.tier IS 'Subscription tier: FREE, PREMIUM, PREMIUM_YEARLY';
COMMENT ON COLUMN user_tiers.started_at IS 'When the premium subscription started';
COMMENT ON COLUMN user_tiers.expires_at IS 'When the premium subscription expires';
COMMENT ON COLUMN user_tiers.backtest_count_today IS 'Number of backtests performed today (for FREE tier limit)';
COMMENT ON COLUMN user_tiers.backtest_count_reset_at IS 'Date when backtest count was last reset';
