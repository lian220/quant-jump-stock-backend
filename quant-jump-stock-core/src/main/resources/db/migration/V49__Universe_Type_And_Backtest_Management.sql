-- V49: 유니버스 타입 + 백테스트 관리 (SCRUM-344)
-- 전략-유니버스 분리 + Canonical/User Custom 백테스트 분류

-- backtest_results: 유니버스 타입 + 백테스트 분류
ALTER TABLE backtest_results
  ADD COLUMN IF NOT EXISTS universe_type VARCHAR(20) DEFAULT 'MARKET',
  ADD COLUMN IF NOT EXISTS backtest_type VARCHAR(20) DEFAULT 'USER_CUSTOM';

-- strategies: 유니버스 설정 + 대표 백테스트 참조
ALTER TABLE strategies
  ADD COLUMN IF NOT EXISTS recommended_universe_type VARCHAR(20) DEFAULT 'MARKET',
  ADD COLUMN IF NOT EXISTS supported_universe_types JSONB DEFAULT '["MARKET","PORTFOLIO","FIXED"]',
  ADD COLUMN IF NOT EXISTS canonical_backtest_id BIGINT REFERENCES backtest_results(id) ON DELETE SET NULL;

-- 인덱스: Canonical 백테스트 빠른 조회
CREATE INDEX IF NOT EXISTS idx_backtest_canonical
  ON backtest_results(backtest_type, strategy_id)
  WHERE backtest_type = 'CANONICAL';

-- 인덱스: 사용자별 전략별 커스텀 백테스트 최신순 조회
CREATE INDEX IF NOT EXISTS idx_backtest_user_strategy
  ON backtest_results(user_id, strategy_id, created_at DESC)
  WHERE backtest_type = 'USER_CUSTOM';

-- 인덱스: strategies canonical_backtest_id FK
CREATE INDEX IF NOT EXISTS idx_strategies_canonical_backtest
  ON strategies(canonical_backtest_id)
  WHERE canonical_backtest_id IS NOT NULL;
