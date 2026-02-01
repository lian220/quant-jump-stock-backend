-- ============================================
-- Quantiq Initial Schema
-- PostgreSQL 15+
-- ============================================

-- ============================================
-- 1. Users Table
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100),
    email VARCHAR(100) UNIQUE,
    password_hash VARCHAR(255),
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE users IS '사용자 정보';
COMMENT ON COLUMN users.user_id IS '사용자 고유 식별자 (비즈니스 키)';
COMMENT ON COLUMN users.status IS '사용자 상태: ACTIVE, INACTIVE, SUSPENDED';

-- ============================================
-- 2. Trading Configs Table
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
COMMENT ON COLUMN trading_configs.min_composite_score IS '신호 신뢰도 최소값 (0-10)';

-- ============================================
-- 3. Account Balances Table
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
COMMENT ON COLUMN account_balances.version IS 'Optimistic locking 버전번호';
COMMENT ON COLUMN account_balances.locked_cash IS '미체결 주문으로 인해 잠긴 현금';

-- ============================================
-- 4. Stock Holdings Table
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

-- ============================================
-- 5. Trades Table
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
COMMENT ON COLUMN trades.kis_order_id IS '외부 거래소 주문 ID (KIS)';

-- ============================================
-- 6. Trade Signals Executed Table
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
COMMENT ON COLUMN trade_signals_executed.recommendation_id IS 'MongoDB stock_recommendations._id';
COMMENT ON COLUMN trade_signals_executed.execution_decision IS '실행 여부 및 결과';
