-- ============================================
-- Stocks 테이블 생성
-- MongoDB stocks 컬렉션을 PostgreSQL로 마이그레이션
-- ============================================

CREATE TABLE stocks (
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
CREATE INDEX idx_stocks_ticker
    ON stocks(ticker);

CREATE INDEX idx_stocks_is_active
    ON stocks(is_active)
    WHERE is_active = TRUE;

CREATE INDEX idx_stocks_sector
    ON stocks(sector)
    WHERE sector IS NOT NULL;

CREATE INDEX idx_stocks_industry
    ON stocks(industry)
    WHERE industry IS NOT NULL;

CREATE INDEX idx_stocks_is_etf
    ON stocks(is_etf)
    WHERE is_etf = TRUE;

-- 테이블 코멘트
COMMENT ON TABLE stocks IS '주식 메타데이터 (티커, 이름, ETF 여부 등)';
COMMENT ON COLUMN stocks.ticker IS '주식 티커 심볼 (예: AAPL, TSLA)';
COMMENT ON COLUMN stocks.stock_name IS '주식 한글명 (예: 애플)';
COMMENT ON COLUMN stocks.stock_name_en IS '주식 영문명 (예: Apple Inc.)';
COMMENT ON COLUMN stocks.is_etf IS 'ETF 여부';
COMMENT ON COLUMN stocks.leverage_ticker IS '레버리지 상품 티커';
COMMENT ON COLUMN stocks.exchange IS '거래소 (예: NASDAQ, NYSE)';
COMMENT ON COLUMN stocks.sector IS '섹터 (예: Technology, Healthcare)';
COMMENT ON COLUMN stocks.industry IS '산업 (예: Consumer Electronics)';
COMMENT ON COLUMN stocks.is_active IS '활성화 여부 (거래 가능 여부)';
