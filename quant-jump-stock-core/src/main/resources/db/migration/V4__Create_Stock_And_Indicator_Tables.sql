-- ============================================
-- V4: Stocks, Indicators, Stock Designation History 테이블 생성
-- Consolidated from: V2, V10, V28, V30
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
    market VARCHAR(10) DEFAULT 'KR',
    designation_status VARCHAR(20) DEFAULT 'NORMAL',
    designation_reason TEXT,
    designated_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_stocks_ticker UNIQUE(ticker),
    CONSTRAINT check_market CHECK (market IN ('US', 'KR', 'CRYPTO')),
    CONSTRAINT check_designation_status CHECK (designation_status IN ('NORMAL', 'CAUTION', 'WARNING', 'DANGER', 'DELISTED'))
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
COMMENT ON COLUMN stocks.market IS '시장 구분: US(미국) | KR(한국) | CRYPTO(암호화폐)';
COMMENT ON COLUMN stocks.designation_status IS '지정 상태: NORMAL | CAUTION | WARNING | DANGER | DELISTED';
COMMENT ON COLUMN stocks.designation_reason IS '지정 사유';
COMMENT ON COLUMN stocks.designated_at IS '지정일시';

-- ============================================
-- 2. FRED Indicators 테이블
-- ============================================
CREATE TABLE IF NOT EXISTS fred_indicators (
    id BIGSERIAL PRIMARY KEY,
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
COMMENT ON COLUMN fred_indicators.id IS 'Primary key - BIGINT for consistency with other entities';
COMMENT ON COLUMN fred_indicators.code IS 'FRED API 지표 코드';
COMMENT ON COLUMN fred_indicators.frequency IS '데이터 발표 주기';

-- ============================================
-- 3. Yahoo Finance Indicators 테이블
-- ============================================
CREATE TABLE IF NOT EXISTS yfinance_indicators (
    id BIGSERIAL PRIMARY KEY,
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
COMMENT ON COLUMN yfinance_indicators.id IS 'Primary key - BIGINT for consistency with other entities';
COMMENT ON COLUMN yfinance_indicators.ticker IS 'Yahoo Finance 티커 심볼';
COMMENT ON COLUMN yfinance_indicators.indicator_type IS '지표 유형 (INDEX/ETF/COMMODITY/CURRENCY)';

-- ============================================
-- 4. Stock Designation History 테이블
-- ============================================
CREATE TABLE IF NOT EXISTS stock_designation_history (
    id BIGSERIAL PRIMARY KEY,
    stock_id BIGINT NOT NULL REFERENCES stocks(id) ON DELETE CASCADE,
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    reason TEXT,
    changed_by BIGINT REFERENCES users(id),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sdh_stock_id ON stock_designation_history(stock_id);
CREATE INDEX idx_sdh_changed_at ON stock_designation_history(changed_at);

COMMENT ON TABLE stock_designation_history IS '종목 지정 상태 변경 이력';
