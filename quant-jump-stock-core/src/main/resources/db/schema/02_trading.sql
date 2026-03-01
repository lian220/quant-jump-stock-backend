-- ============================================
-- 02_trading.sql
-- Sources: V2
-- ============================================

CREATE TABLE IF NOT EXISTS trading_configs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    enabled BOOLEAN DEFAULT FALSE,
    auto_trading_enabled BOOLEAN DEFAULT FALSE,
    min_composite_score DECIMAL(5, 2) DEFAULT 2.0,
    max_stocks_to_buy INTEGER DEFAULT 5,
    max_amount_per_stock DECIMAL(12, 2) DEFAULT 10000.0,
    stop_loss_percent DECIMAL(5, 2) DEFAULT -7.0,
    take_profit_percent DECIMAL(5, 2) DEFAULT 5.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT valid_scores CHECK (min_composite_score >= 0 AND min_composite_score <= 10),
    CONSTRAINT valid_stocks CHECK (max_stocks_to_buy > 0),
    CONSTRAINT valid_amount CHECK (max_amount_per_stock > 0)
);

COMMENT ON TABLE trading_configs IS '사용자별 거래 설정';
COMMENT ON COLUMN trading_configs.id IS '거래 설정 고유 ID (PK)';
COMMENT ON COLUMN trading_configs.user_id IS '사용자 ID (FK, UNIQUE)';
COMMENT ON COLUMN trading_configs.enabled IS '거래 기능 활성화 여부';
COMMENT ON COLUMN trading_configs.auto_trading_enabled IS '자동 매매 활성화 여부';
COMMENT ON COLUMN trading_configs.min_composite_score IS '신호 신뢰도 최소값 (0-10)';
COMMENT ON COLUMN trading_configs.max_stocks_to_buy IS '최대 매수 종목 수';
COMMENT ON COLUMN trading_configs.max_amount_per_stock IS '종목당 최대 투자 금액';
COMMENT ON COLUMN trading_configs.stop_loss_percent IS '손절 기준 (%, 음수)';
COMMENT ON COLUMN trading_configs.take_profit_percent IS '익절 기준 (%)';
COMMENT ON COLUMN trading_configs.created_at IS '설정 생성 일시';
COMMENT ON COLUMN trading_configs.updated_at IS '설정 수정 일시';

-- ============================================

CREATE TABLE IF NOT EXISTS account_balances (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    cash DECIMAL(15, 2) NOT NULL DEFAULT 0,
    total_value DECIMAL(15, 2) NOT NULL DEFAULT 0,
    locked_cash DECIMAL(15, 2) DEFAULT 0,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT valid_cash CHECK (cash >= 0),
    CONSTRAINT valid_total CHECK (total_value >= 0),
    CONSTRAINT valid_locked CHECK (locked_cash >= 0)
);

COMMENT ON TABLE account_balances IS '사용자 계좌 잔액 (매우 자주 업데이트됨)';
COMMENT ON COLUMN account_balances.id IS '계좌 잔액 고유 ID (PK)';
COMMENT ON COLUMN account_balances.user_id IS '사용자 ID (FK, UNIQUE)';
COMMENT ON COLUMN account_balances.cash IS '현금 잔액';
COMMENT ON COLUMN account_balances.total_value IS '총 자산 가치 (현금 + 주식)';
COMMENT ON COLUMN account_balances.version IS 'Optimistic locking 버전번호';
COMMENT ON COLUMN account_balances.locked_cash IS '미체결 주문으로 인해 잠긴 현금';
COMMENT ON COLUMN account_balances.created_at IS '레코드 생성 일시';
COMMENT ON COLUMN account_balances.updated_at IS '잔액 갱신 일시';

-- ============================================

CREATE TABLE IF NOT EXISTS stock_holdings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    quantity INTEGER NOT NULL,
    average_price DECIMAL(10, 2) NOT NULL,
    total_cost DECIMAL(15, 2) NOT NULL,
    current_value DECIMAL(15, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, ticker),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT valid_quantity CHECK (quantity > 0),
    CONSTRAINT valid_price CHECK (average_price > 0)
);

COMMENT ON TABLE stock_holdings IS '사용자의 주식 보유 내역';
COMMENT ON COLUMN stock_holdings.id IS '보유 주식 고유 ID (PK)';
COMMENT ON COLUMN stock_holdings.user_id IS '사용자 ID (FK)';
COMMENT ON COLUMN stock_holdings.ticker IS '종목 티커';
COMMENT ON COLUMN stock_holdings.quantity IS '보유 수량';
COMMENT ON COLUMN stock_holdings.average_price IS '평균 매수 단가';
COMMENT ON COLUMN stock_holdings.total_cost IS '총 매수 금액';
COMMENT ON COLUMN stock_holdings.current_value IS '현재 평가 금액';
COMMENT ON COLUMN stock_holdings.created_at IS '최초 매수 일시';
COMMENT ON COLUMN stock_holdings.updated_at IS '보유 정보 갱신 일시';

-- ============================================

CREATE TABLE IF NOT EXISTS trades (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    side VARCHAR(10) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity INTEGER NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    total_amount DECIMAL(15, 2) NOT NULL,
    commission DECIMAL(10, 2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'EXECUTED', 'FAILED', 'CANCELLED')),
    kis_order_id VARCHAR(100),
    executed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT valid_quantity CHECK (quantity > 0),
    CONSTRAINT valid_price CHECK (price > 0),
    CONSTRAINT valid_amount CHECK (total_amount > 0)
);

COMMENT ON TABLE trades IS '거래 기록 (감사, 정산용)';
COMMENT ON COLUMN trades.id IS '거래 고유 ID (PK)';
COMMENT ON COLUMN trades.user_id IS '사용자 ID (FK)';
COMMENT ON COLUMN trades.ticker IS '종목 티커';
COMMENT ON COLUMN trades.side IS '매매 방향 (BUY, SELL)';
COMMENT ON COLUMN trades.quantity IS '거래 수량';
COMMENT ON COLUMN trades.price IS '거래 단가';
COMMENT ON COLUMN trades.total_amount IS '총 거래 금액';
COMMENT ON COLUMN trades.commission IS '수수료';
COMMENT ON COLUMN trades.status IS '거래 상태 (PENDING, EXECUTED, FAILED, CANCELLED)';
COMMENT ON COLUMN trades.kis_order_id IS '외부 거래소 주문 ID (KIS)';
COMMENT ON COLUMN trades.executed_at IS '체결 일시';
COMMENT ON COLUMN trades.created_at IS '주문 생성 일시';
COMMENT ON COLUMN trades.updated_at IS '주문 상태 갱신 일시';

-- ============================================

CREATE TABLE IF NOT EXISTS trade_signals_executed (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    recommendation_id VARCHAR(100) NOT NULL,
    ticker VARCHAR(10) NOT NULL,
    signal VARCHAR(20) NOT NULL CHECK (signal IN ('BUY', 'SELL', 'HOLD')),
    confidence DECIMAL(3, 2) NOT NULL,
    execution_decision VARCHAR(20) NOT NULL CHECK (execution_decision IN ('EXECUTED', 'SKIPPED', 'FAILED')),
    skip_reason VARCHAR(255),
    executed_trade_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (executed_trade_id) REFERENCES trades(id) ON DELETE SET NULL,
    CONSTRAINT valid_confidence CHECK (confidence >= 0 AND confidence <= 1)
);

COMMENT ON TABLE trade_signals_executed IS '거래 신호 실행 로그 (감사, 재현용)';
COMMENT ON COLUMN trade_signals_executed.id IS '신호 실행 로그 고유 ID (PK)';
COMMENT ON COLUMN trade_signals_executed.user_id IS '사용자 ID (FK)';
COMMENT ON COLUMN trade_signals_executed.recommendation_id IS 'MongoDB stock_recommendations._id';
COMMENT ON COLUMN trade_signals_executed.ticker IS '종목 티커';
COMMENT ON COLUMN trade_signals_executed.signal IS '신호 유형 (BUY, SELL, HOLD)';
COMMENT ON COLUMN trade_signals_executed.confidence IS '신호 신뢰도 (0.00~1.00)';
COMMENT ON COLUMN trade_signals_executed.execution_decision IS '실행 여부 및 결과';
COMMENT ON COLUMN trade_signals_executed.skip_reason IS '미실행 시 사유';
COMMENT ON COLUMN trade_signals_executed.executed_trade_id IS '실행된 거래 ID (FK)';
COMMENT ON COLUMN trade_signals_executed.created_at IS '신호 처리 일시';
