-- V43: prediction_results 테이블에 가격 메트릭 컬럼 추가
--
-- 추가 필드:
-- - current_price: 현재가 (최신 종가)
-- - target_price: 목표가 (AI 예측 가격)
-- - upside_percent: 상승여력 (%)
-- - price_recommendation: 가격 추천 (강력매수/매수/보유/매도)

ALTER TABLE prediction_results
ADD COLUMN current_price DECIMAL(10, 2),
ADD COLUMN target_price DECIMAL(10, 2),
ADD COLUMN upside_percent DECIMAL(6, 2),
ADD COLUMN price_recommendation VARCHAR(20);

-- 인덱스 추가 (상승여력 기준 정렬용)
CREATE INDEX idx_prediction_results_upside ON prediction_results(analysis_date DESC, upside_percent DESC);

COMMENT ON COLUMN prediction_results.current_price IS '현재가 (최신 종가)';
COMMENT ON COLUMN prediction_results.target_price IS 'AI 예측 목표가';
COMMENT ON COLUMN prediction_results.upside_percent IS '상승여력 (%)';
COMMENT ON COLUMN prediction_results.price_recommendation IS '가격 추천 (강력매수/매수/보유/매도)';
