-- V42: 통합 예측 결과 테이블 생성 (AI + 감정 + 기술적 분석)
-- 작성일: 2026-02-13
-- 목적: MongoDB 분석 결과를 PostgreSQL로 통합하여 Core API에서 조회 가능하도록 함

CREATE TABLE prediction_results (
    id BIGSERIAL PRIMARY KEY,

    -- 종목 정보
    ticker VARCHAR(20) NOT NULL,
    stock_name VARCHAR(255) NOT NULL,
    analysis_date DATE NOT NULL,

    -- AI 예측 점수 (Vertex AI)
    ai_predicted_price DECIMAL(10, 2),           -- 예측 가격
    ai_rise_probability DECIMAL(5, 4),           -- 상승 확률 (0.0000 ~ 1.0000)
    ai_score DECIMAL(5, 2) DEFAULT 0,            -- AI 점수 (0 ~ 5): rise_probability × 5

    -- 감정 분석 점수 (Alpha Vantage)
    sentiment_score DECIMAL(5, 4),               -- 감정 점수 (-1.0000 ~ 1.0000)
    sentiment_news_count INTEGER,                -- 뉴스 개수
    sentiment_normalized_score DECIMAL(5, 2) DEFAULT 0,  -- 정규화 감정 점수 (0 ~ 5): (sentiment + 1) / 2 × 5

    -- 기술적 분석 점수
    tech_sma20 DECIMAL(10, 2),                   -- SMA(20)
    tech_sma50 DECIMAL(10, 2),                   -- SMA(50)
    tech_rsi DECIMAL(5, 2),                      -- RSI(14)
    tech_macd DECIMAL(10, 2),                    -- MACD Line
    tech_signal DECIMAL(10, 2),                  -- Signal Line
    tech_golden_cross BOOLEAN DEFAULT FALSE,     -- 골든크로스 여부
    tech_macd_buy_signal BOOLEAN DEFAULT FALSE,  -- MACD 매수 신호
    tech_score DECIMAL(5, 2) DEFAULT 0,          -- 기술적 점수 (0 ~ 3.5)
    tech_signals_count INTEGER DEFAULT 0,        -- 충족된 기술 신호 개수 (0 ~ 3)

    -- Composite Score (종합 점수)
    composite_score DECIMAL(6, 2) NOT NULL,      -- 종합 점수 (0 ~ 7.5): 0.3×ai + 0.4×tech + 0.3×sentiment
    composite_grade VARCHAR(10),                 -- 등급: S, A, B, C, D

    -- 추천 여부
    is_recommended BOOLEAN DEFAULT FALSE,        -- 매수 추천 여부
    recommendation_reason TEXT,                  -- 추천 이유

    -- 메타데이터
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 제약 조건: 종목-날짜 조합은 유일
    UNIQUE (ticker, analysis_date)
);

-- 인덱스: 날짜 기준 조회 (최신순)
CREATE INDEX idx_prediction_results_date
ON prediction_results(analysis_date DESC);

-- 인덱스: 종합 점수 기준 조회 (고득점순)
CREATE INDEX idx_prediction_results_composite
ON prediction_results(composite_score DESC);

-- 인덱스: 추천 종목 조회 (매수 신호 + 고득점순)
CREATE INDEX idx_prediction_results_recommended
ON prediction_results(is_recommended, composite_score DESC)
WHERE is_recommended = TRUE;

-- 테이블 설명
COMMENT ON TABLE prediction_results IS '통합 예측 결과 (AI + 감정 + 기술적 분석)';

-- 컬럼 설명
COMMENT ON COLUMN prediction_results.ai_score IS 'AI 예측 점수 (0~5): rise_probability × 5';
COMMENT ON COLUMN prediction_results.sentiment_normalized_score IS '감정 분석 점수 (0~5): (sentiment + 1) / 2 × 5';
COMMENT ON COLUMN prediction_results.tech_score IS '기술적 점수 (0~3.5): 1.5×골든크로스 + 1.0×RSI<50 + 1.0×MACD매수';
COMMENT ON COLUMN prediction_results.composite_score IS '종합 점수 (0~7.5): 0.3×ai + 0.4×tech + 0.3×sentiment';
COMMENT ON COLUMN prediction_results.composite_grade IS '등급 기준: S(6.0+), A(4.5+), B(3.0+), C(1.5+), D(<1.5)';

-- 등급 기준 참조
-- S: 6.0 ~ 7.5 (매우 강한 매수 신호) - emerald
-- A: 4.5 ~ 5.9 (강한 매수 신호) - cyan
-- B: 3.0 ~ 4.4 (보통 매수 신호) - yellow
-- C: 1.5 ~ 2.9 (약한 매수 신호) - orange
-- D: 0.0 ~ 1.4 (매수 신호 없음) - red
