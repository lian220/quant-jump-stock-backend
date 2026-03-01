-- ============================================
-- 07_backtest.sql
-- Sources: V8 + V39 (widen numerics) + V44 (risk metrics) + V48 (benchmarks) + V49 (universe/backtest type)
-- ============================================

CREATE TABLE backtest_results (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,

    request_id VARCHAR(100),

    -- Test Settings
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    initial_capital NUMERIC(15, 2) NOT NULL,
    benchmark VARCHAR(20) DEFAULT 'SPY',
    benchmarks JSONB DEFAULT '["SPY"]',         -- V48: 다중 벤치마크

    -- Performance Metrics (NUMERIC(20,4) from V39)
    final_value NUMERIC(15, 2) NOT NULL,
    total_return NUMERIC(20, 4) NOT NULL,
    cagr NUMERIC(20, 4) NOT NULL,
    mdd NUMERIC(20, 4) NOT NULL,
    sharpe_ratio NUMERIC(20, 4),
    sortino_ratio NUMERIC(20, 4),
    volatility NUMERIC(20, 4),
    win_rate NUMERIC(5, 2),

    -- Trade Statistics
    total_trades INTEGER DEFAULT 0,
    winning_trades INTEGER DEFAULT 0,
    losing_trades INTEGER DEFAULT 0,
    avg_win NUMERIC(20, 4),
    avg_loss NUMERIC(20, 4),

    -- Benchmark Comparison
    benchmark_return NUMERIC(20, 4),
    alpha NUMERIC(20, 4),
    beta NUMERIC(20, 4),

    -- Equity Curve
    equity_curve JSONB,

    -- Incremental Mode
    is_incremental BOOLEAN DEFAULT FALSE,
    last_checkpoint_date DATE,

    -- Advanced Metrics (V44)
    profit_factor DECIMAL(10,4),
    expectancy DECIMAL(10,4),
    kelly_percentage DECIMAL(10,4),
    risk_reward_ratio DECIMAL(10,4),
    calmar_ratio DECIMAL(10,4),

    -- Trading Cost Aggregates (V44)
    total_commission DECIMAL(15,2),
    total_slippage DECIMAL(15,2),
    total_tax DECIMAL(15,2),
    net_profit_after_costs DECIMAL(15,2),

    -- Extended Trade Statistics (V44)
    avg_holding_period DECIMAL(10,2),
    best_trade DECIMAL(10,4),
    worst_trade DECIMAL(10,4),
    max_consecutive_wins INT,
    max_consecutive_losses INT,
    stop_loss_count INT DEFAULT 0,
    take_profit_count INT DEFAULT 0,
    trailing_stop_count INT DEFAULT 0,

    -- Risk/Position/Cost Settings Snapshot (V44)
    risk_settings JSONB DEFAULT '{}',
    position_sizing JSONB DEFAULT '{}',
    trading_costs JSONB DEFAULT '{}',

    -- Universe & Backtest Type (V49)
    universe_type VARCHAR(20) DEFAULT 'MARKET',
    backtest_type VARCHAR(20) DEFAULT 'USER_CUSTOM',

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
CREATE UNIQUE INDEX idx_backtest_results_request_id ON backtest_results(request_id) WHERE request_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_backtest_canonical ON backtest_results(backtest_type, strategy_id) WHERE backtest_type = 'CANONICAL';
CREATE INDEX IF NOT EXISTS idx_backtest_user_strategy ON backtest_results(user_id, strategy_id, created_at DESC) WHERE backtest_type = 'USER_CUSTOM';

COMMENT ON TABLE backtest_results IS 'Backtest execution results with performance metrics';
COMMENT ON COLUMN backtest_results.id IS '백테스트 결과 고유 ID (PK)';
COMMENT ON COLUMN backtest_results.strategy_id IS '백테스트 대상 전략 ID (FK)';
COMMENT ON COLUMN backtest_results.user_id IS '백테스트 실행 사용자 ID (FK)';
COMMENT ON COLUMN backtest_results.start_date IS '백테스트 시작일';
COMMENT ON COLUMN backtest_results.end_date IS '백테스트 종료일';
COMMENT ON COLUMN backtest_results.initial_capital IS '초기 투자금';
COMMENT ON COLUMN backtest_results.benchmark IS '벤치마크 지수 (기본: SPY)';
COMMENT ON COLUMN backtest_results.benchmarks IS '다중 벤치마크 티커 리스트 (최대 3개). 예: ["^GSPC", "^KS11", "STRATEGY:105"]';
COMMENT ON COLUMN backtest_results.final_value IS '최종 포트폴리오 가치';
COMMENT ON COLUMN backtest_results.total_return IS '총 수익률';
COMMENT ON COLUMN backtest_results.cagr IS '연평균 복리 수익률 (CAGR)';
COMMENT ON COLUMN backtest_results.mdd IS '최대 낙폭 (MDD, Maximum Drawdown)';
COMMENT ON COLUMN backtest_results.sharpe_ratio IS '샤프 비율 (위험 대비 수익)';
COMMENT ON COLUMN backtest_results.sortino_ratio IS '소르티노 비율 (하방 위험 대비 수익)';
COMMENT ON COLUMN backtest_results.volatility IS '변동성 (표준편차)';
COMMENT ON COLUMN backtest_results.win_rate IS '승률 (%)';
COMMENT ON COLUMN backtest_results.is_incremental IS '증분 업데이트 모드 여부';
COMMENT ON COLUMN backtest_results.last_checkpoint_date IS '마지막 체크포인트 날짜';
COMMENT ON COLUMN backtest_results.profit_factor IS '손익비 (총 이익 / 총 손실)';
COMMENT ON COLUMN backtest_results.expectancy IS '기대값 (거래당 평균 수익률 %)';
COMMENT ON COLUMN backtest_results.kelly_percentage IS '켈리 비율 (최적 베팅 비율 %)';
COMMENT ON COLUMN backtest_results.calmar_ratio IS 'Calmar Ratio (CAGR / MDD)';
COMMENT ON COLUMN backtest_results.stop_loss_count IS '손절로 청산된 거래 수';
COMMENT ON COLUMN backtest_results.take_profit_count IS '익절로 청산된 거래 수';
COMMENT ON COLUMN backtest_results.trailing_stop_count IS '트레일링 스탑으로 청산된 거래 수';
COMMENT ON COLUMN backtest_results.risk_settings IS '리스크 설정 JSON (stopLoss, takeProfit, trailingStop)';
COMMENT ON COLUMN backtest_results.universe_type IS '유니버스 타입 (MARKET, PORTFOLIO, FIXED)';
COMMENT ON COLUMN backtest_results.backtest_type IS '백테스트 분류 (CANONICAL, USER_CUSTOM)';
COMMENT ON COLUMN backtest_results.status IS '백테스트 상태 (RUNNING, COMPLETED, FAILED)';
COMMENT ON COLUMN backtest_results.equity_curve IS '자산 곡선 데이터 (JSONB)';
COMMENT ON COLUMN backtest_results.created_at IS '백테스트 시작 일시';
COMMENT ON COLUMN backtest_results.completed_at IS '백테스트 완료 일시';

-- ============================================

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

    pnl NUMERIC(15, 2),
    pnl_percent NUMERIC(20, 4),   -- V39
    holding_days INTEGER,

    -- V44
    exit_reason VARCHAR(20),
    slippage_amount DECIMAL(10,2),
    tax_amount DECIMAL(10,2),
    execution_price DECIMAL(15,4),

    signal_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT check_backtest_trade_side CHECK (side IN ('BUY', 'SELL'))
);

CREATE INDEX idx_backtest_trades_backtest_id ON backtest_trades(backtest_id);
CREATE INDEX idx_backtest_trades_ticker ON backtest_trades(ticker);
CREATE INDEX idx_backtest_trades_trade_date ON backtest_trades(trade_date);

COMMENT ON TABLE backtest_trades IS 'Individual trades executed during backtests';
COMMENT ON COLUMN backtest_trades.id IS '백테스트 거래 고유 ID (PK)';
COMMENT ON COLUMN backtest_trades.backtest_id IS '백테스트 결과 ID (FK)';
COMMENT ON COLUMN backtest_trades.exit_reason IS '청산 사유 (SIGNAL, STOP_LOSS, TAKE_PROFIT, TRAILING_STOP, REBALANCE, FORCED, EXPIRED)';
COMMENT ON COLUMN backtest_trades.execution_price IS '실제 체결가 (슬리피지 반영)';

-- ============================================

CREATE TABLE backtest_checkpoints (
    id BIGSERIAL PRIMARY KEY,
    backtest_id BIGINT NOT NULL REFERENCES backtest_results(id) ON DELETE CASCADE,
    checkpoint_date DATE NOT NULL,
    cash NUMERIC(15, 2) NOT NULL,
    high_watermark NUMERIC(15, 2) NOT NULL,
    positions JSONB NOT NULL DEFAULT '[]',
    trade_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_backtest_checkpoint_date UNIQUE (backtest_id, checkpoint_date)
);

CREATE INDEX idx_backtest_checkpoints_backtest_id ON backtest_checkpoints(backtest_id);
CREATE INDEX idx_backtest_checkpoints_date ON backtest_checkpoints(checkpoint_date DESC);

COMMENT ON TABLE backtest_checkpoints IS '백테스트 증분 실행을 위한 포트폴리오 상태 체크포인트';
COMMENT ON COLUMN backtest_checkpoints.positions IS '보유 포지션 배열: [{ticker, quantity, entry_price, entry_date}]';

-- ============================================
-- V49: strategies 테이블에 canonical_backtest_id FK 추가
-- (backtest_results 생성 이후에 추가)
-- ============================================
ALTER TABLE strategies
    ADD CONSTRAINT fk_strategies_canonical_backtest
    FOREIGN KEY (canonical_backtest_id) REFERENCES backtest_results(id) ON DELETE SET NULL;
