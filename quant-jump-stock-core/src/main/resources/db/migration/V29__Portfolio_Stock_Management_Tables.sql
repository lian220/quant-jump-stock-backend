-- V29: 포트폴리오 종목 관리 테이블 생성
-- SCRUM-311/312: 전략 기본 종목 + 사용자 포트폴리오 + 포트폴리오 종목

-- 전략 기본 종목 (PORTFOLIO 타입 전략의 기본 구성 종목)
CREATE TABLE IF NOT EXISTS strategy_default_stocks (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    stock_id BIGINT NOT NULL REFERENCES stocks(id),
    target_weight DECIMAL(5,2) NOT NULL DEFAULT 0,
    memo TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(strategy_id, stock_id)
);

CREATE INDEX idx_sds_strategy_id ON strategy_default_stocks(strategy_id);

-- 사용자 포트폴리오
CREATE TABLE IF NOT EXISTS user_portfolios (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    strategy_id BIGINT REFERENCES strategies(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_up_user_id ON user_portfolios(user_id);
CREATE INDEX idx_up_strategy_id ON user_portfolios(strategy_id);

-- 포트폴리오 종목
CREATE TABLE IF NOT EXISTS portfolio_stocks (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL REFERENCES user_portfolios(id) ON DELETE CASCADE,
    stock_id BIGINT NOT NULL REFERENCES stocks(id),
    target_weight DECIMAL(5,2) NOT NULL DEFAULT 0,
    is_from_strategy BOOLEAN NOT NULL DEFAULT false,
    memo TEXT,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(portfolio_id, stock_id)
);

CREATE INDEX idx_ps_portfolio_id ON portfolio_stocks(portfolio_id);

-- strategy_subscriptions 테이블에 portfolio_id 컬럼 추가
ALTER TABLE strategy_subscriptions ADD COLUMN IF NOT EXISTS portfolio_id BIGINT REFERENCES user_portfolios(id);

COMMENT ON TABLE strategy_default_stocks IS '전략 기본 종목 (PORTFOLIO 타입 전략의 기본 구성)';
COMMENT ON COLUMN strategy_default_stocks.target_weight IS '목표 비중 (0~100%)';
COMMENT ON TABLE user_portfolios IS '사용자 포트폴리오';
COMMENT ON TABLE portfolio_stocks IS '포트폴리오 종목';
COMMENT ON COLUMN portfolio_stocks.is_from_strategy IS '전략 기본 종목에서 복사된 여부';
COMMENT ON COLUMN strategy_subscriptions.portfolio_id IS '구독 시 생성된 사용자 포트폴리오 ID';
