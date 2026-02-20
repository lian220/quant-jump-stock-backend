-- 티어별 제한 설정 테이블 (SCRUM-168 확장: 하드코딩 제거 + 어드민 관리)
CREATE TABLE IF NOT EXISTS tier_configurations (
    id BIGSERIAL PRIMARY KEY,
    tier VARCHAR(20) NOT NULL UNIQUE,
    max_subscription_count INTEGER NOT NULL DEFAULT 3,      -- 전략 구독 최대 수 (is_unlimited_subscription=true면 무시)
    max_backtest_daily INTEGER NOT NULL DEFAULT 3,          -- 일일 백테스트 최대 수
    max_backtest_per_strategy INTEGER NOT NULL DEFAULT 5,   -- 전략당 백테스트 보관 최대 수
    is_unlimited_subscription BOOLEAN NOT NULL DEFAULT FALSE,
    is_unlimited_backtest BOOLEAN NOT NULL DEFAULT FALSE,
    description TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100)
);

INSERT INTO tier_configurations (id, tier, max_subscription_count, max_backtest_daily, max_backtest_per_strategy, is_unlimited_subscription, is_unlimited_backtest, description)
VALUES
    (1, 'FREE',           2, 3, 5, false, false, '무료 티어'),
    (2, 'PREMIUM',        0, 0, 0, true,  true,  '프리미엄 티어 - 무제한'),
    (3, 'PREMIUM_YEARLY', 0, 0, 0, true,  true,  '프리미엄 연간 - 무제한')
ON CONFLICT (tier) DO NOTHING;

-- UNLIMITED_USERS 하드코딩 제거: 해당 계정을 PREMIUM으로 업그레이드
UPDATE user_tiers ut
SET tier = 'PREMIUM', started_at = NOW()
WHERE EXISTS (
    SELECT 1 FROM users u
    WHERE u.id = ut.user_id
    AND u.user_id IN ('n_user_74cc63b0', 'lian_test', 'list_test')
)
AND ut.tier = 'FREE';
