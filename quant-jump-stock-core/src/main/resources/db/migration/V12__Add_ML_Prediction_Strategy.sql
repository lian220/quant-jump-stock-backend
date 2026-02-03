-- V12: Add ML_PREDICTION category and default ML prediction strategies
-- This migration adds support for AI/ML-based prediction strategies

-- =====================================================================
-- 1. Update Strategy Category Check Constraint
-- =====================================================================
ALTER TABLE strategies DROP CONSTRAINT IF EXISTS check_strategy_category;

ALTER TABLE strategies ADD CONSTRAINT check_strategy_category CHECK (
    category IN ('VALUE', 'MOMENTUM', 'ASSET_ALLOCATION', 'QUANT_COMPOSITE', 'SEASONAL', 'CUSTOM', 'ML_PREDICTION')
);

-- =====================================================================
-- 2. Insert Default ML Prediction Strategy (System Strategy)
-- =====================================================================
INSERT INTO strategies (
    name,
    description,
    category,
    owner_id,
    is_public,
    is_premium,
    status,
    conditions,
    rebalance_frequency,
    subscriber_count,
    average_rating
) VALUES (
    'AI Stock Predictor',
    'XGBoost 기반 AI 주식 예측 전략. 기술적 지표(SMA, EMA, MACD, RSI, Bollinger Bands)와 경제 지표(GDP, CPI, FRED 데이터)를 활용하여 주가 방향성을 예측합니다. Vertex AI에서 학습된 모델을 사용합니다.',
    'ML_PREDICTION',
    NULL,  -- System owned (no specific user)
    true,  -- Public strategy
    false, -- Free for all users
    'ACTIVE',
    '{
        "model_type": "xgboost",
        "features": {
            "technical": ["sma_5", "sma_20", "sma_60", "ema_12", "ema_26", "macd", "macd_signal", "rsi_14", "bb_upper", "bb_lower", "bb_middle"],
            "economic": ["gdp_growth", "cpi", "unemployment_rate", "interest_rate"]
        },
        "prediction_horizon_days": 5,
        "min_confidence_threshold": 0.7,
        "training_config": {
            "vertex_ai_enabled": true,
            "retrain_frequency": "WEEKLY",
            "lookback_days": 365
        }
    }'::jsonb,
    'WEEKLY',
    0,
    0.00
);

-- =====================================================================
-- 3. Insert Technical Analysis ML Strategy
-- =====================================================================
INSERT INTO strategies (
    name,
    description,
    category,
    owner_id,
    is_public,
    is_premium,
    status,
    conditions,
    rebalance_frequency,
    subscriber_count,
    average_rating
) VALUES (
    'Technical ML Momentum',
    '기술적 지표 기반 모멘텀 예측 ML 전략. RSI, MACD, 볼린저 밴드 등의 기술적 지표를 ML 모델에 입력하여 단기 모멘텀을 예측합니다.',
    'ML_PREDICTION',
    NULL,
    true,
    true,  -- Premium strategy
    'ACTIVE',
    '{
        "model_type": "xgboost",
        "features": {
            "technical": ["rsi_14", "rsi_7", "macd", "macd_signal", "macd_hist", "bb_position", "volume_ratio", "price_momentum_5d", "price_momentum_20d"],
            "economic": []
        },
        "prediction_horizon_days": 3,
        "min_confidence_threshold": 0.75,
        "training_config": {
            "vertex_ai_enabled": true,
            "retrain_frequency": "DAILY",
            "lookback_days": 180
        }
    }'::jsonb,
    'DAILY',
    0,
    0.00
);

-- =====================================================================
-- Comments
-- =====================================================================
COMMENT ON COLUMN strategies.conditions IS 'JSON configuration for strategy including ML model parameters, features, and training config';
