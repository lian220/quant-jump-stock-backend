-- ============================================
-- V6: 경제 지표 메타데이터 테이블 생성
-- ============================================
-- 목적: FRED 및 Yahoo Finance 경제 지표 설정 관리
-- 작성일: 2026-02-01
-- ============================================

-- FRED 경제 지표 테이블
CREATE TABLE IF NOT EXISTS fred_indicators (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    unit VARCHAR(50),
    frequency VARCHAR(20), -- 'daily', 'monthly', 'quarterly', 'annual'
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fred_code_not_empty CHECK (code <> '')
);

-- Yahoo Finance 지표 테이블
CREATE TABLE IF NOT EXISTS yfinance_indicators (
    id SERIAL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    indicator_type VARCHAR(50) DEFAULT 'ETF', -- 'INDEX', 'ETF', 'COMMODITY', 'CURRENCY'
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ticker_not_empty CHECK (ticker <> '')
);

-- 인덱스 생성
CREATE INDEX idx_fred_is_active ON fred_indicators(is_active);
CREATE INDEX idx_fred_category ON fred_indicators(category);
CREATE INDEX idx_yfinance_is_active ON yfinance_indicators(is_active);
CREATE INDEX idx_yfinance_type ON yfinance_indicators(indicator_type);

-- 코멘트 추가
COMMENT ON TABLE fred_indicators IS 'FRED(Federal Reserve Economic Data) 경제 지표 메타데이터';
COMMENT ON TABLE yfinance_indicators IS 'Yahoo Finance 시장 지표 메타데이터';

COMMENT ON COLUMN fred_indicators.code IS 'FRED API 지표 코드 (예: GDP, UNRATE)';
COMMENT ON COLUMN fred_indicators.frequency IS '데이터 발표 주기';
COMMENT ON COLUMN yfinance_indicators.ticker IS 'Yahoo Finance 티커 심볼 (예: SPY, QQQ)';
COMMENT ON COLUMN yfinance_indicators.indicator_type IS '지표 유형 (INDEX/ETF/COMMODITY/CURRENCY)';

-- 초기 데이터 삽입 - 비활성화됨
-- 필요시 수동으로 데이터 삽입
