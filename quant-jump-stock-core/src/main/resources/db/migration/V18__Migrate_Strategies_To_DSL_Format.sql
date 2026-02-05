-- V18: 전략 스키마를 Data Engine DSL 형식으로 통일
-- 기존 V16 시드 데이터 + JSON 시드 전략을 DSL 형식으로 마이그레이션
-- Created: 2025-02-05

-- =====================================================================
-- 1. strategy_id, version 컬럼 추가
-- =====================================================================
ALTER TABLE strategies ADD COLUMN IF NOT EXISTS strategy_id VARCHAR(100);
ALTER TABLE strategies ADD COLUMN IF NOT EXISTS version VARCHAR(20) DEFAULT '1.0';

-- strategy_id UNIQUE 제약 (nullable이므로 partial index)
CREATE UNIQUE INDEX IF NOT EXISTS idx_strategies_strategy_id_unique
    ON strategies(strategy_id) WHERE strategy_id IS NOT NULL;

-- 컬럼 코멘트 추가
COMMENT ON COLUMN strategies.strategy_id IS '전략 고유 식별자 (영문, snake_case). Data Engine DSL에서 참조하는 ID';
COMMENT ON COLUMN strategies.version IS '전략 버전 (semver 형식: 1.0, 1.1 등)';

-- =====================================================================
-- 2. 기존 시드 데이터 삭제 (owner_id가 NULL인 시스템 전략만)
-- =====================================================================
DELETE FROM strategies WHERE owner_id IS NULL;

-- =====================================================================
-- 3. DSL 형식 시드 전략 삽입 (총 18개)
-- =====================================================================

-- =====================================================================
-- MOMENTUM 전략 (6개)
-- =====================================================================

-- 3.1 골든크로스
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'golden_cross',
    '골든크로스',
    '단기(20일) 이동평균선이 장기(50일) 이동평균선을 상향 돌파할 때 매수하는 모멘텀 전략입니다. 추세 전환의 강력한 신호로 활용됩니다.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "golden_cross_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "단기 이평선이 장기 이평선 상향 돌파"
            },
            {
                "name": "death_cross_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_below", "value": "sma_50"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "단기 이평선이 장기 이평선 하향 돌파"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.15,
            "max_position_pct": 0.1
        },
        "tags": ["trend-following", "momentum", "classic"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.2 RSI 과매도 반등
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'rsi_oversold',
    'RSI 과매도 반등',
    'RSI(상대강도지수)가 30 이하 과매도 구간에서 매수하고, 70 이상 과매수 구간에서 매도하는 역추세 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "rsi_oversold_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 30}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "RSI 과매도 구간 진입"
            },
            {
                "name": "rsi_overbought_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 70}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "RSI 과매수 구간 진입"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.03,
            "take_profit_pct": 0.1,
            "max_position_pct": 0.1
        },
        "tags": ["mean-reversion", "oscillator"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.3 MACD 크로스오버
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'macd_crossover',
    'MACD 크로스오버',
    'MACD선이 시그널선을 상향 돌파할 때 매수하는 추세추종 전략입니다. 중장기 추세 파악에 효과적입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "macd_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "macd", "params": {}, "operator": "crosses_above", "value": "macd_signal"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "MACD 상향 돌파"
            },
            {
                "name": "macd_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "macd", "params": {}, "operator": "crosses_below", "value": "macd_signal"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "MACD 하향 돌파"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.04,
            "take_profit_pct": 0.12,
            "max_position_pct": 0.1
        },
        "tags": ["trend-following", "momentum"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.4 볼린저 밴드 스퀴즈
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'bollinger_squeeze',
    '볼린저 밴드 스퀴즈',
    '가격이 볼린저 하단 밴드 아래면 매수, 상단 밴드 위면 매도하는 평균회귀 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "bollinger_lower_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "가격이 볼린저 하단 밴드 하회"
            },
            {
                "name": "bollinger_upper_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_upper"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "가격이 볼린저 상단 밴드 상회"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.03,
            "take_profit_pct": 0.08,
            "max_position_pct": 0.1
        },
        "tags": ["mean-reversion", "volatility"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.5 모멘텀 브레이크아웃
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'momentum_breakout',
    '모멘텀 브레이크아웃',
    '가격이 20일 EMA 위에 있고 RSI가 50 이상이면 상승 모멘텀으로 판단하는 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "momentum_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "ema_20"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 50}
                ],
                "logic": "and",
                "weight": 0.9,
                "description": "상승 모멘텀 확인"
            },
            {
                "name": "momentum_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "ema_20"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 50}
                ],
                "logic": "and",
                "weight": 0.9,
                "description": "하락 모멘텀 확인"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.04,
            "take_profit_pct": 0.12,
            "max_position_pct": 0.1
        },
        "tags": ["momentum", "trend-following"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.6 추세 추종
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'trend_following',
    '추세 추종',
    '장기 추세를 따라 매매하는 전략입니다. 50일선 위에서 매수, 아래서 매도합니다.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "uptrend_entry",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"},
                    {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"}
                ],
                "logic": "and",
                "weight": 0.85,
                "description": "상승 추세 진입"
            },
            {
                "name": "downtrend_exit",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "sma_50"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "하락 추세 이탈"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.07,
            "take_profit_pct": 0.25,
            "max_position_pct": 0.12,
            "trailing_stop": true,
            "trailing_stop_pct": 0.05
        },
        "tags": ["trend-following", "long-term"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- =====================================================================
-- QUANT_COMPOSITE 전략 (4개)
-- =====================================================================

-- 3.7 트리플 스크린
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'triple_screen',
    '트리플 스크린',
    '골든크로스 + RSI 비과매수 + MACD 상승 3가지 조건을 동시에 충족할 때만 매수하는 보수적인 복합 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'QUANT_COMPOSITE'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "triple_screen_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 70},
                    {"indicator": "macd", "params": {}, "operator": "gt", "value": "macd_signal"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "3중 필터 조건 충족"
            },
            {
                "name": "triple_screen_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "lt", "value": "sma_50"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 30},
                    {"indicator": "macd", "params": {}, "operator": "lt", "value": "macd_signal"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "3중 매도 조건 충족"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.2,
            "max_position_pct": 0.08
        },
        "tags": ["multi-indicator", "trend-following"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.8 평균 회귀
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'mean_reversion',
    '평균 회귀',
    '가격이 SMA에서 크게 벗어나면 평균으로 회귀할 것을 기대하는 역추세 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'QUANT_COMPOSITE'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "oversold_bounce",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 25},
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "극단적 과매도 반등 기대"
            },
            {
                "name": "overbought_pullback",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 75},
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_upper"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "극단적 과매수 조정 기대"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.02,
            "take_profit_pct": 0.06,
            "max_position_pct": 0.08
        },
        "tags": ["mean-reversion", "counter-trend"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.9 듀얼 모멘텀
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'dual_momentum',
    '듀얼 모멘텀',
    '절대 모멘텀과 상대 모멘텀을 결합한 전략입니다. 200일선 위에서 RSI와 MACD 확인 후 매수합니다.',
    (SELECT id FROM strategy_categories WHERE code = 'QUANT_COMPOSITE'),
    true,
    true,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "dual_momentum_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 50},
                    {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "절대+상대 모멘텀 강세"
            },
            {
                "name": "dual_momentum_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "sma_200"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "장기 추세 이탈"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.08,
            "take_profit_pct": 0.3,
            "max_position_pct": 0.15
        },
        "tags": ["momentum", "long-term"]
    }'::jsonb,
    'MONTHLY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.10 RSI 스캘핑
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'scalping_rsi',
    'RSI 스캘핑',
    '단기 RSI(7일) 극단값에서 빠른 진입/청산하는 단타 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'QUANT_COMPOSITE'),
    true,
    true,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "quick_oversold",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 7}, "operator": "lt", "value": 20}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "단기 RSI 극과매도"
            },
            {
                "name": "quick_overbought",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 7}, "operator": "gt", "value": 80}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "단기 RSI 극과매수"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.015,
            "take_profit_pct": 0.03,
            "max_position_pct": 0.05
        },
        "tags": ["scalping", "short-term", "mean-reversion"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- =====================================================================
-- VALUE 전략 (2개)
-- =====================================================================

-- 3.11 저PER 가치주
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'low_per_value',
    '저PER 가치주',
    'PER이 10 미만이고 업종 평균 대비 30% 이상 저평가된 종목을 발굴하는 전통적인 가치투자 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'VALUE'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "low_per_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "per", "params": {"type": "fundamental"}, "operator": "lt", "value": 10},
                    {"indicator": "per_sector_ratio", "params": {}, "operator": "lt", "value": 0.7}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "저PER + 업종 대비 저평가"
            },
            {
                "name": "high_per_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "per", "params": {"type": "fundamental"}, "operator": "gt", "value": 20}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "PER 고평가 구간"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.1,
            "take_profit_pct": 0.3,
            "max_position_pct": 0.1
        },
        "tags": ["value", "fundamental", "long-term"]
    }'::jsonb,
    'MONTHLY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.12 고배당 가치주
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'high_dividend',
    '고배당 가치주',
    '배당수익률 4% 이상, PBR 1 미만의 안정적인 배당주에 투자하는 인컴형 가치투자 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'VALUE'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "high_dividend_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "dividend_yield", "params": {"type": "fundamental"}, "operator": "gt", "value": 4.0},
                    {"indicator": "pbr", "params": {"type": "fundamental"}, "operator": "lt", "value": 1.0}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "고배당 + 저PBR"
            },
            {
                "name": "low_dividend_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "dividend_yield", "params": {"type": "fundamental"}, "operator": "lt", "value": 2.0}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "배당수익률 하락"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.1,
            "take_profit_pct": 0.25,
            "max_position_pct": 0.1
        },
        "tags": ["value", "dividend", "income"]
    }'::jsonb,
    'QUARTERLY',
    '1.0',
    NOW(),
    NOW()
);

-- =====================================================================
-- ASSET_ALLOCATION 전략 (3개)
-- =====================================================================

-- 3.13 주식-채권 밸런스 60/40
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'balanced_60_40',
    '주식-채권 밸런스 60/40',
    '전통적인 60% 주식 + 40% 채권 포트폴리오입니다. 목표 비중에서 5% 이상 벗어나면 리밸런싱합니다.',
    (SELECT id FROM strategy_categories WHERE code = 'ASSET_ALLOCATION'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "rebalance_check",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "portfolio_drift", "params": {}, "operator": "gt", "value": 0.05}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "목표 비중에서 5% 이상 이탈 시 리밸런싱"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.15,
            "take_profit_pct": 0.3,
            "max_position_pct": 0.6
        },
        "allocation": {
            "equity": {"weight": 0.6, "ticker": "KODEX200", "name": "코덱스200"},
            "bond": {"weight": 0.4, "ticker": "KOSEF국고채10년", "name": "코세프국고채10년"}
        },
        "tags": ["asset-allocation", "balanced", "classic"]
    }'::jsonb,
    'QUARTERLY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.14 금리 연동 동적 배분
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'rate_linked_allocation',
    '금리 연동 동적 배분',
    'FRED 10년물 국채금리에 따라 주식/채권 비중을 자동 조절하는 동적 자산배분 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'ASSET_ALLOCATION'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "low_rate_equity",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "treasury_10y", "params": {"source": "fred"}, "operator": "lt", "value": 2}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "저금리: 주식 80%"
            },
            {
                "name": "high_rate_bond",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "treasury_10y", "params": {"source": "fred"}, "operator": "gte", "value": 4}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "고금리: 주식 40%"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.12,
            "take_profit_pct": 0.25,
            "max_position_pct": 0.8
        },
        "allocation_rules": [
            {"condition": "treasury_10y < 2", "equity": 0.8, "bond": 0.2},
            {"condition": "treasury_10y >= 2 AND treasury_10y < 4", "equity": 0.6, "bond": 0.4},
            {"condition": "treasury_10y >= 4", "equity": 0.4, "bond": 0.6}
        ],
        "tags": ["asset-allocation", "dynamic", "macro"]
    }'::jsonb,
    'MONTHLY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.15 올웨더 포트폴리오
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'all_weather',
    '올웨더 포트폴리오',
    '레이 달리오의 사계절 포트폴리오를 한국 ETF로 구현한 전략입니다. 어떤 경제 환경에서도 안정적인 수익을 추구합니다.',
    (SELECT id FROM strategy_categories WHERE code = 'ASSET_ALLOCATION'),
    true,
    true,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "rebalance_trigger",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "portfolio_drift", "params": {}, "operator": "gt", "value": 0.05}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "목표 비중에서 5% 이상 이탈 시 리밸런싱"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.1,
            "take_profit_pct": 0.2,
            "max_position_pct": 0.4
        },
        "allocation": {
            "stocks": {"weight": 0.30, "ticker": "KODEX200", "name": "주식"},
            "long_term_bonds": {"weight": 0.40, "ticker": "KOSEF국고채10년", "name": "장기채권"},
            "mid_term_bonds": {"weight": 0.15, "ticker": "KBSTAR중기우량회사채", "name": "중기채권"},
            "gold": {"weight": 0.075, "ticker": "KODEX골드선물(H)", "name": "금"},
            "commodities": {"weight": 0.075, "ticker": "KODEX WTI원유선물(H)", "name": "원자재"}
        },
        "tags": ["asset-allocation", "all-weather", "ray-dalio"]
    }'::jsonb,
    'YEARLY',
    '1.0',
    NOW(),
    NOW()
);

-- =====================================================================
-- SEASONAL 전략 (3개)
-- =====================================================================

-- 3.16 1월 효과
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'january_effect',
    '1월 효과',
    '연말 세금 매도 후 1월에 소형주가 반등하는 계절적 패턴을 활용합니다. 12월 20일 매수, 1월 31일 매도.',
    (SELECT id FROM strategy_categories WHERE code = 'SEASONAL'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "january_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "calendar", "params": {"month": 12, "day_gte": 20}, "operator": "eq", "value": true}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "12월 20일 이후 매수"
            },
            {
                "name": "january_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "calendar", "params": {"month": 1, "day_gte": 31}, "operator": "eq", "value": true}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "1월 31일 매도"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.08,
            "take_profit_pct": 0.15,
            "max_position_pct": 0.15
        },
        "schedule": {
            "buy_date": {"month": 12, "day": 20},
            "sell_date": {"month": 1, "day": 31}
        },
        "target": {"ticker": "KODEX코스닥150", "name": "코스닥150 ETF"},
        "tags": ["seasonal", "january-effect", "small-cap"]
    }'::jsonb,
    'YEARLY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.17 Sell in May
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'sell_in_may',
    'Sell in May',
    '"5월에 팔고 떠나라" 격언을 활용한 전략입니다. 5-10월 채권 비중 증가, 11-4월 주식 비중 증가.',
    (SELECT id FROM strategy_categories WHERE code = 'SEASONAL'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "summer_defensive",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "calendar", "params": {"month_in": [5,6,7,8,9,10]}, "operator": "eq", "value": true}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "5-10월 방어적 포지션"
            },
            {
                "name": "winter_aggressive",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "calendar", "params": {"month_in": [11,12,1,2,3,4]}, "operator": "eq", "value": true}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "11-4월 공격적 포지션"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.1,
            "take_profit_pct": 0.2,
            "max_position_pct": 0.8
        },
        "schedule": {
            "summer": {"months": [5,6,7,8,9,10], "allocation": {"equity": 0.2, "bond": 0.8}},
            "winter": {"months": [11,12,1,2,3,4], "allocation": {"equity": 0.8, "bond": 0.2}}
        },
        "tags": ["seasonal", "halloween-effect"]
    }'::jsonb,
    'MONTHLY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.18 분기말 모멘텀 리밸런싱
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'quarterly_momentum',
    '분기말 모멘텀 리밸런싱',
    '분기 마지막 주에 최근 3개월 수익률 상위 10종목으로 포트폴리오를 재구성하는 모멘텀 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'SEASONAL'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "quarterly_rebalance",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "calendar", "params": {"day_in": ["03-25","06-25","09-25","12-25"]}, "operator": "eq", "value": true},
                    {"indicator": "momentum_3m", "params": {"rank": "top", "n": 10}, "operator": "eq", "value": true}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "분기말 상위 모멘텀 종목 매수"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.1,
            "take_profit_pct": 0.25,
            "max_position_pct": 0.1
        },
        "schedule": {
            "rebalance_dates": ["03-25", "06-25", "09-25", "12-25"]
        },
        "selection": {
            "criteria": "momentum_3m",
            "direction": "desc",
            "top_n": 10,
            "universe": "KOSPI200"
        },
        "tags": ["seasonal", "momentum", "quarterly"]
    }'::jsonb,
    'QUARTERLY',
    '1.0',
    NOW(),
    NOW()
);

-- =====================================================================
-- ML_PREDICTION 전략 (2개)
-- =====================================================================

-- 3.19 Vertex AI 예측
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'vertex_ai_prediction',
    'Vertex AI 예측',
    'Google Vertex AI 머신러닝 모델이 예측한 상승 확률이 70% 이상인 종목에 투자하는 AI 기반 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'ML_PREDICTION'),
    true,
    true,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "ai_buy_signal",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "ml_prediction", "params": {"model": "vertex_ai", "endpoint": "stock-prediction-v1"}, "operator": "gte", "value": 0.7}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "AI 상승 확률 70% 이상"
            },
            {
                "name": "ai_sell_signal",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "ml_prediction", "params": {"model": "vertex_ai", "endpoint": "stock-prediction-v1"}, "operator": "lt", "value": 0.3}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "AI 상승 확률 30% 미만"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.15,
            "max_position_pct": 0.1
        },
        "model": {
            "provider": "vertex_ai",
            "endpoint": "stock-prediction-v1",
            "features": ["price_momentum", "volume_trend", "technical_indicators", "market_sentiment"]
        },
        "tags": ["ml", "ai", "vertex-ai"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 3.20 AI + 기술적 분석 하이브리드
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'ai_technical_hybrid',
    'AI + 기술적 분석 하이브리드',
    'AI 예측(50%)과 기술적 분석(50%)을 앙상블하여 더 안정적인 매매 신호를 생성하는 고급 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'ML_PREDICTION'),
    true,
    true,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "hybrid_buy_signal",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "ensemble_score", "params": {"ai_weight": 0.5, "technical_weight": 0.5}, "operator": "gte", "value": 0.65}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "앙상블 점수 65% 이상"
            },
            {
                "name": "hybrid_sell_signal",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "ensemble_score", "params": {"ai_weight": 0.5, "technical_weight": 0.5}, "operator": "lt", "value": 0.35}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "앙상블 점수 35% 미만"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.15,
            "max_position_pct": 0.1
        },
        "ensemble": {
            "ai_prediction": {"weight": 0.5, "source": "vertex_ai"},
            "technical_analysis": {"weight": 0.5, "indicators": ["golden_cross", "rsi", "macd"]}
        },
        "tags": ["ml", "ai", "hybrid", "ensemble"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- =====================================================================
-- 요약
-- =====================================================================
-- 총 20개 DSL 형식 시드 전략 삽입 완료:
--   - MOMENTUM: 6개 (골든크로스, RSI, MACD, 볼린저, 모멘텀 브레이크아웃, 추세 추종)
--   - QUANT_COMPOSITE: 4개 (트리플 스크린, 평균 회귀, 듀얼 모멘텀, RSI 스캘핑)
--   - VALUE: 2개 (저PER, 고배당)
--   - ASSET_ALLOCATION: 3개 (60/40, 금리연동, 올웨더)
--   - SEASONAL: 3개 (1월효과, Sell in May, 분기말)
--   - ML_PREDICTION: 2개 (Vertex AI, AI 하이브리드)
--
-- 프리미엄 전략: 5개 (듀얼 모멘텀, RSI 스캘핑, 올웨더, Vertex AI, AI 하이브리드)
-- 무료 전략: 15개
--
-- conditions JSONB 스키마 (DSL 형식):
-- {
--   "rules": [
--     {
--       "name": "rule_name",
--       "signal_type": "buy|sell",
--       "conditions": [{"indicator": "...", "params": {...}, "operator": "...", "value": ...}],
--       "logic": "and|or",
--       "weight": 0.0-1.0,
--       "description": "..."
--     }
--   ],
--   "risk_management": {...},
--   "tags": ["tag1", "tag2"],
--   "allocation": {...}  -- 자산배분 전략 전용
-- }
