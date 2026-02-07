-- V25: strategies 테이블에 position_sizing JSONB 컬럼 추가
-- SCRUM-261: position_sizing JSONB 스키마 정의

ALTER TABLE strategies ADD COLUMN IF NOT EXISTS position_sizing JSONB DEFAULT '{}'::jsonb;

COMMENT ON COLUMN strategies.position_sizing IS '포지션 사이징 설정 (method, max_position_pct, max_positions, volatility_scaling, lookback_days, target_volatility, kelly_fraction)';

-- 기존 시드 전략의 risk_management.max_position_pct를 position_sizing으로 마이그레이션
UPDATE strategies
SET position_sizing = jsonb_build_object(
    'method', 'fixed_percentage',
    'max_position_pct', COALESCE(
        (conditions->'risk_management'->>'max_position_pct')::numeric * 100,
        10.0
    ),
    'max_positions', 10,
    'volatility_scaling', false,
    'lookback_days', 20,
    'target_volatility', 0.15,
    'kelly_fraction', 0.5
)
WHERE position_sizing = '{}'::jsonb
  AND conditions->'risk_management'->>'max_position_pct' IS NOT NULL;
