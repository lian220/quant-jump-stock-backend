-- ============================================
-- Performance Indexes
-- ============================================

-- Users 테이블 인덱스
CREATE INDEX IF NOT EXISTS idx_users_user_id ON users(user_id);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- Trading Configs 인덱스
CREATE INDEX IF NOT EXISTS idx_trading_configs_enabled ON trading_configs(user_id, enabled);

-- Account Balances 인덱스 (매우 자주 쿼리)
CREATE INDEX IF NOT EXISTS idx_account_balances_user_id ON account_balances(user_id);

-- Stock Holdings 인덱스
CREATE INDEX IF NOT EXISTS idx_stock_holdings_user_id ON stock_holdings(user_id);
CREATE INDEX IF NOT EXISTS idx_stock_holdings_ticker ON stock_holdings(ticker);

-- Trades 인덱스 (복합 인덱스)
CREATE INDEX IF NOT EXISTS idx_trades_user_ticker_date ON trades(user_id, ticker, executed_at DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_trades_status ON trades(status);
CREATE INDEX IF NOT EXISTS idx_trades_kis_order_id ON trades(kis_order_id) WHERE kis_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trades_executed_at ON trades(executed_at DESC NULLS LAST) WHERE status = 'EXECUTED';

-- Trade Signals Executed 인덱스
CREATE INDEX IF NOT EXISTS idx_trade_signals_user_timestamp ON trade_signals_executed(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_trade_signals_recommendation_id ON trade_signals_executed(recommendation_id);
