-- ============================================
-- 10_prediction.sql
-- Sources: V42 + V43 (price metrics columns baked in)
-- ============================================

CREATE TABLE prediction_results (
    id BIGSERIAL PRIMARY KEY,

    ticker VARCHAR(20) NOT NULL,
    stock_name VARCHAR(255) NOT NULL,
    analysis_date DATE NOT NULL,

    -- AI 예측 점수 (Vertex AI)
    ai_predicted_price DECIMAL(10, 2),
    ai_rise_probability DECIMAL(5, 4),
    ai_score DECIMAL(5, 2) DEFAULT 0,

    -- 감정 분석 점수
    sentiment_score DECIMAL(5, 4),
    sentiment_news_count INTEGER,
    sentiment_normalized_score DECIMAL(5, 2) DEFAULT 0,

    -- 기술적 분석
    tech_sma20 DECIMAL(10, 2),
    tech_sma50 DECIMAL(10, 2),
    tech_rsi DECIMAL(5, 2),
    tech_macd DECIMAL(10, 2),
    tech_signal DECIMAL(10, 2),
    tech_golden_cross BOOLEAN DEFAULT FALSE,
    tech_macd_buy_signal BOOLEAN DEFAULT FALSE,
    tech_score DECIMAL(5, 2) DEFAULT 0,
    tech_signals_count INTEGER DEFAULT 0,

    -- Composite Score
    composite_score DECIMAL(6, 2) NOT NULL,
    composite_grade VARCHAR(10),

    -- 가격 메트릭 (V43)
    current_price DECIMAL(10, 2),
    target_price DECIMAL(10, 2),
    upside_percent DECIMAL(6, 2),
    price_recommendation VARCHAR(20),

    -- 추천
    is_recommended BOOLEAN DEFAULT FALSE,
    recommendation_reason TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (ticker, analysis_date)
);

CREATE INDEX idx_prediction_results_date ON prediction_results(analysis_date DESC);
CREATE INDEX idx_prediction_results_composite ON prediction_results(composite_score DESC);
CREATE INDEX idx_prediction_results_recommended ON prediction_results(is_recommended, composite_score DESC) WHERE is_recommended = TRUE;
CREATE INDEX idx_prediction_results_upside ON prediction_results(analysis_date DESC, upside_percent DESC);

COMMENT ON TABLE prediction_results IS '통합 예측 결과 (AI + 감정 + 기술적 분석)';
COMMENT ON COLUMN prediction_results.ai_score IS 'AI 예측 점수 (0~5): rise_probability × 5';
COMMENT ON COLUMN prediction_results.sentiment_normalized_score IS '감정 분석 점수 (0~5): (sentiment + 1) / 2 × 5';
COMMENT ON COLUMN prediction_results.tech_score IS '기술적 점수 (0~3.5): 1.5×골든크로스 + 1.0×RSI<50 + 1.0×MACD매수';
COMMENT ON COLUMN prediction_results.composite_score IS '종합 점수 (0~7.5): 0.3×ai + 0.4×tech + 0.3×sentiment';
COMMENT ON COLUMN prediction_results.composite_grade IS '등급 기준: S(6.0+), A(4.5+), B(3.0+), C(1.5+), D(<1.5)';
COMMENT ON COLUMN prediction_results.current_price IS '현재가 (최신 종가)';
COMMENT ON COLUMN prediction_results.target_price IS 'AI 예측 목표가';
COMMENT ON COLUMN prediction_results.upside_percent IS '상승여력 (%)';
COMMENT ON COLUMN prediction_results.price_recommendation IS '가격 추천 (강력매수/매수/보유/매도)';
