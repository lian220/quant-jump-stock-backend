-- V8: 백테스트 관련 테이블 통합 생성
-- 원본: V9, V20, V21, V22, V23, V29 마이그레이션 통합
-- 모든 ALTER TABLE 변경사항이 최종 상태로 반영된 CREATE TABLE

-- =====================================================================
-- 1. Backtest Results Table (백테스트 실행 결과)
-- =====================================================================
CREATE TABLE backtest_results (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,

    -- 요청 식별 (V22)
    request_id VARCHAR(100),

    -- Test Settings (V23: benchmark default 'SPY')
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    initial_capital NUMERIC(15, 2) NOT NULL,
    benchmark VARCHAR(20) DEFAULT 'SPY',

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

    -- 증분 모드 (V20)
    is_incremental BOOLEAN DEFAULT FALSE,
    last_checkpoint_date DATE,

    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    error_message TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,

    CONSTRAINT check_backtest_status CHECK (
        status IN ('RUNNING', 'COMPLETED', 'FAILED')
    )
);

-- 인덱스
CREATE INDEX idx_backtest_results_strategy_id ON backtest_results(strategy_id);
CREATE INDEX idx_backtest_results_user_id ON backtest_results(user_id);
CREATE INDEX idx_backtest_results_status ON backtest_results(status);
CREATE INDEX idx_backtest_results_created_at ON backtest_results(created_at);
CREATE UNIQUE INDEX idx_backtest_results_request_id ON backtest_results(request_id) WHERE request_id IS NOT NULL;

-- 코멘트
COMMENT ON TABLE backtest_results IS 'Backtest execution results with performance metrics';
COMMENT ON COLUMN backtest_results.is_incremental IS '증분 업데이트 모드 여부';
COMMENT ON COLUMN backtest_results.last_checkpoint_date IS '마지막 체크포인트 날짜';

-- =====================================================================
-- 2. Backtest Trades Table (백테스트 개별 거래)
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

COMMENT ON TABLE backtest_trades IS 'Individual trades executed during backtests';

-- =====================================================================
-- 3. Backtest Checkpoints Table (V20, equity_curve 제거: V21)
-- =====================================================================
CREATE TABLE backtest_checkpoints (
    id BIGSERIAL PRIMARY KEY,
    backtest_id BIGINT NOT NULL REFERENCES backtest_results(id) ON DELETE CASCADE,
    checkpoint_date DATE NOT NULL,

    -- 포트폴리오 상태
    cash NUMERIC(15, 2) NOT NULL,
    high_watermark NUMERIC(15, 2) NOT NULL,

    -- 보유 포지션 (JSON)
    positions JSONB NOT NULL DEFAULT '[]',

    -- 거래 내역 (마지막 체크포인트까지의 거래 수)
    trade_count INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- 동일 backtest에 대해 날짜별로 하나의 체크포인트만 허용
    CONSTRAINT uq_backtest_checkpoint_date UNIQUE (backtest_id, checkpoint_date)
);

CREATE INDEX idx_backtest_checkpoints_backtest_id ON backtest_checkpoints(backtest_id);
CREATE INDEX idx_backtest_checkpoints_date ON backtest_checkpoints(checkpoint_date DESC);

COMMENT ON TABLE backtest_checkpoints IS '백테스트 증분 실행을 위한 포트폴리오 상태 체크포인트 (equity_curve는 backtest_results에서 조회)';
COMMENT ON COLUMN backtest_checkpoints.positions IS '보유 포지션 배열: [{ticker, quantity, entry_price, entry_date}]';

-- =====================================================================
-- 4. Strategy Signals Table (전략 매매 신호)
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

COMMENT ON TABLE strategy_signals IS 'Trading signals generated by strategies';

-- =====================================================================
-- 5. Strategy Subscriptions Table (V29: portfolio_id 추가)
-- =====================================================================
CREATE TABLE strategy_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    strategy_id BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    portfolio_id BIGINT,
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

COMMENT ON TABLE strategy_subscriptions IS 'User subscriptions to trading strategies';
COMMENT ON COLUMN strategy_subscriptions.portfolio_id IS '구독 시 생성된 사용자 포트폴리오 ID';
