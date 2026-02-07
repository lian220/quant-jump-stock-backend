-- V26: strategies 테이블에 trading_costs JSONB 컬럼 추가
-- SCRUM-262: trading_costs JSONB 스키마 정의

ALTER TABLE strategies ADD COLUMN IF NOT EXISTS trading_costs JSONB DEFAULT '{}'::jsonb;

COMMENT ON COLUMN strategies.trading_costs IS '거래 비용 설정 (commission, tax, slippage)';

-- 기존 시드 전략에 기본 trading_costs 설정 마이그레이션
UPDATE strategies
SET trading_costs = jsonb_build_object(
    'commission', jsonb_build_object('rate', 0.015, 'min_fee', 0),
    'tax', jsonb_build_object('rate', 0.23, 'applies_to', 'sell'),
    'slippage', jsonb_build_object('model', 'adaptive', 'fixed_bps', 5, 'volume_impact', 0.1)
)
WHERE trading_costs = '{}'::jsonb
  AND owner_id IS NULL;
