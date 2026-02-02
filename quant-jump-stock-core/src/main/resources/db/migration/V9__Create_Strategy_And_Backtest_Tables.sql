-- V9: Create Strategy and Backtest related tables
-- This migration creates tables for trading strategies, backtesting, and strategy subscriptions

-- =====================================================================
-- 1. Strategies Table
-- =====================================================================
CREATE TABLE strategies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    owner_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    is_public BOOLEAN DEFAULT FALSE,
    is_premium BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    conditions JSONB NOT NULL DEFAULT '{}'::jsonb,
    rebalance_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    subscriber_count INTEGER DEFAULT 0,
    average_rating NUMERIC(3, 2) DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT check_strategy_category CHECK (
        category IN ('VALUE', 'MOMENTUM', 'ASSET_ALLOCATION', 'QUANT_COMPOSITE', 'SEASONAL', 'CUSTOM')
    ),
    CONSTRAINT check_strategy_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')
    ),
    CONSTRAINT check_rebalance_frequency CHECK (
        rebalance_frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'NONE')
    )
);

CREATE INDEX idx_strategies_owner_id ON strategies(owner_id);
CREATE INDEX idx_strategies_status ON strategies(status);
CREATE INDEX idx_strategies_category ON strategies(category);
CREATE INDEX idx_strategies_is_public ON strategies(is_public);
CREATE INDEX idx_strategies_created_at ON strategies(created_at);

-- =====================================================================
-- 2. Backtest Results Table
-- =====================================================================
CREATE TABLE backtest_results (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,

    -- Test Settings
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    initial_capital NUMERIC(15, 2) NOT NULL,
    benchmark VARCHAR(20) DEFAULT 'KOSPI',

    -- Performance Metrics
    final_value NUMERIC(15, 2) NOT NULL,
    total_return NUMERIC(10, 4) NOT NULL,
    cagr NUMERIC(10, 4) NOT NULL,
    mdd NUMERIC(10, 4) NOT NULL,
    sharpe_ratio NUMERIC(10, 4),
    sortino_ratio NUMERIC(10, 4),
    volatility NUMERIC(10, 4),
    win_rate NUMERIC(5, 2),

    -- Trade Statistics
    total_trades INTEGER DEFAULT 0,
    winning_trades INTEGER DEFAULT 0,
    losing_trades INTEGER DEFAULT 0,
    avg_win NUMERIC(10, 4),
    avg_loss NUMERIC(10, 4),

    -- Benchmark Comparison
    benchmark_return NUMERIC(10, 4),
    alpha NUMERIC(10, 4),
    beta NUMERIC(10, 4),

    -- Equity Curve (JSON)
    equity_curve JSONB,

    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    error_message TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,

    CONSTRAINT check_backtest_status CHECK (
        status IN ('RUNNING', 'COMPLETED', 'FAILED')
    )
);

CREATE INDEX idx_backtest_results_strategy_id ON backtest_results(strategy_id);
CREATE INDEX idx_backtest_results_user_id ON backtest_results(user_id);
CREATE INDEX idx_backtest_results_status ON backtest_results(status);
CREATE INDEX idx_backtest_results_created_at ON backtest_results(created_at);

-- =====================================================================
-- 3. Backtest Trades Table
-- =====================================================================
CREATE TABLE backtest_trades (
    id BIGSERIAL PRIMARY KEY,
    backtest_id BIGINT NOT NULL REFERENCES backtest_results(id) ON DELETE CASCADE,
    trade_date DATE NOT NULL,
    ticker VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,
    quantity INTEGER NOT NULL,
    price NUMERIC(15, 4) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    commission NUMERIC(10, 2) DEFAULT 0,

    -- P&L (for SELL trades)
    pnl NUMERIC(15, 2),
    pnl_percent NUMERIC(10, 4),
    holding_days INTEGER,

    signal_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT check_backtest_trade_side CHECK (
        side IN ('BUY', 'SELL')
    )
);

CREATE INDEX idx_backtest_trades_backtest_id ON backtest_trades(backtest_id);
CREATE INDEX idx_backtest_trades_ticker ON backtest_trades(ticker);
CREATE INDEX idx_backtest_trades_trade_date ON backtest_trades(trade_date);

-- =====================================================================
-- 4. Strategy Signals Table
-- =====================================================================
CREATE TABLE strategy_signals (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    signal_date DATE NOT NULL,
    signal_type VARCHAR(20) NOT NULL,
    ticker VARCHAR(20),
    target_weight NUMERIC(5, 2),
    reason TEXT,
    conditions_snapshot JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT check_signal_type CHECK (
        signal_type IN ('BUY', 'SELL', 'REBALANCE', 'HOLD')
    )
);

CREATE INDEX idx_strategy_signals_strategy_id ON strategy_signals(strategy_id);
CREATE INDEX idx_strategy_signals_signal_date ON strategy_signals(signal_date);
CREATE INDEX idx_strategy_signals_signal_type ON strategy_signals(signal_type);
CREATE INDEX idx_strategy_signals_ticker ON strategy_signals(ticker);

-- =====================================================================
-- 5. Strategy Subscriptions Table
-- =====================================================================
CREATE TABLE strategy_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    strategy_id BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notify_signals BOOLEAN DEFAULT TRUE,
    notify_rebalance BOOLEAN DEFAULT TRUE,
    subscribed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    cancelled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_strategy_subscriptions_user_strategy UNIQUE (user_id, strategy_id),
    CONSTRAINT check_subscription_status CHECK (
        status IN ('ACTIVE', 'PAUSED', 'CANCELLED')
    )
);

CREATE INDEX idx_strategy_subscriptions_user_id ON strategy_subscriptions(user_id);
CREATE INDEX idx_strategy_subscriptions_strategy_id ON strategy_subscriptions(strategy_id);
CREATE INDEX idx_strategy_subscriptions_status ON strategy_subscriptions(status);

-- =====================================================================
-- Comments
-- =====================================================================
COMMENT ON TABLE strategies IS 'Trading strategy definitions with conditions and rebalancing rules';
COMMENT ON TABLE backtest_results IS 'Backtest execution results with performance metrics';
COMMENT ON TABLE backtest_trades IS 'Individual trades executed during backtests';
COMMENT ON TABLE strategy_signals IS 'Trading signals generated by strategies';
COMMENT ON TABLE strategy_subscriptions IS 'User subscriptions to trading strategies';
