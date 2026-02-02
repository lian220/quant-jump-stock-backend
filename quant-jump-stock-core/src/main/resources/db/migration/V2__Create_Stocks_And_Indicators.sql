-- ============================================
-- Stocks 및 Indicators 테이블 생성
-- ============================================

-- ============================================
-- 1. Stocks 테이블
-- ============================================
CREATE TABLE IF NOT EXISTS stocks (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL,
    stock_name VARCHAR(200) NOT NULL,
    stock_name_en VARCHAR(200),
    is_etf BOOLEAN NOT NULL DEFAULT FALSE,
    leverage_ticker VARCHAR(20),
    exchange VARCHAR(50),
    sector VARCHAR(100),
    industry VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_stocks_ticker UNIQUE(ticker)
);

-- 인덱스 생성
CREATE INDEX idx_stocks_ticker ON stocks(ticker);
CREATE INDEX idx_stocks_is_active ON stocks(is_active) WHERE is_active = TRUE;
CREATE INDEX idx_stocks_sector ON stocks(sector) WHERE sector IS NOT NULL;
CREATE INDEX idx_stocks_industry ON stocks(industry) WHERE industry IS NOT NULL;
CREATE INDEX idx_stocks_is_etf ON stocks(is_etf) WHERE is_etf = TRUE;

-- 테이블 코멘트
COMMENT ON TABLE stocks IS '주식 메타데이터';
COMMENT ON COLUMN stocks.ticker IS '주식 티커 심볼 (예: AAPL, TSLA)';
COMMENT ON COLUMN stocks.stock_name IS '주식 한글명';
COMMENT ON COLUMN stocks.stock_name_en IS '주식 영문명';
COMMENT ON COLUMN stocks.is_etf IS 'ETF 여부';
COMMENT ON COLUMN stocks.leverage_ticker IS '레버리지 상품 티커';

-- ============================================
-- 2. FRED Indicators 테이블
-- ============================================
CREATE TABLE IF NOT EXISTS fred_indicators (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    unit VARCHAR(50),
    frequency VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fred_code_not_empty CHECK (code <> '')
);

-- 인덱스 생성
CREATE INDEX idx_fred_is_active ON fred_indicators(is_active);
CREATE INDEX idx_fred_category ON fred_indicators(category);

-- 코멘트
COMMENT ON TABLE fred_indicators IS 'FRED 경제 지표 메타데이터';
COMMENT ON COLUMN fred_indicators.code IS 'FRED API 지표 코드';
COMMENT ON COLUMN fred_indicators.frequency IS '데이터 발표 주기';

-- ============================================
-- 3. Yahoo Finance Indicators 테이블
-- ============================================
CREATE TABLE IF NOT EXISTS yfinance_indicators (
    id SERIAL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    indicator_type VARCHAR(50) DEFAULT 'ETF',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ticker_not_empty CHECK (ticker <> '')
);

-- 인덱스 생성
CREATE INDEX idx_yfinance_is_active ON yfinance_indicators(is_active);
CREATE INDEX idx_yfinance_type ON yfinance_indicators(indicator_type);

-- 코멘트
COMMENT ON TABLE yfinance_indicators IS 'Yahoo Finance 시장 지표 메타데이터';
COMMENT ON COLUMN yfinance_indicators.ticker IS 'Yahoo Finance 티커 심볼';
COMMENT ON COLUMN yfinance_indicators.indicator_type IS '지표 유형 (INDEX/ETF/COMMODITY/CURRENCY)';
