-- ============================================
-- 02_strategy_updates.sql
-- Sources: V56 + V57 + V58
-- 백테스트 검증 기반 전략 조건 업데이트
-- 실행 전 01_master_data.sql이 적용되어 있어야 합니다.
-- ============================================

-- ============================================
-- V56: 데이터 검증 기반 전략 조건 업데이트 (5개)
-- 근거: 포워드 리턴 분석 (N=10,599), FRED 데이터 검증 (1,318일)
-- ============================================

-- 1. rsi_oversold: RSI 매수 30→42, 매도 70→60
UPDATE strategies
SET conditions = '{
    "rules": [
        {
            "name": "rsi_oversold_buy",
            "signal_type": "buy",
            "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42}
            ],
            "logic": "and",
            "weight": 1.0,
            "description": "RSI 과매도 구간 진입 (데이터 검증 기반 최적 임계값)"
        },
        {
            "name": "rsi_overbought_sell",
            "signal_type": "sell",
            "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 60}
            ],
            "logic": "and",
            "weight": 1.0,
            "description": "RSI 과매수 구간 진입 (데이터 검증 기반)"
        }
    ],
    "risk_management": {
        "stop_loss_pct": 0.05,
        "take_profit_pct": 0.15,
        "max_position_pct": 0.1
    },
    "tags": ["mean-reversion", "oscillator", "data-validated"]
}'::jsonb,
    description = 'RSI(상대강도지수)가 42 이하 과매도 구간에서 매수하고, 60 이상 과매수 구간에서 매도하는 역추세 전략입니다. 포워드 리턴 분석(N=10,599)으로 최적화된 임계값을 사용합니다.',
    version = '2.0',
    updated_at = NOW()
WHERE strategy_id = 'rsi_oversold';

-- 2. mean_reversion: RSI 매수 25→35, 매도 75→65
UPDATE strategies
SET conditions = '{
    "rules": [
        {
            "name": "oversold_bounce",
            "signal_type": "buy",
            "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 35},
                {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
            ],
            "logic": "and",
            "weight": 1.0,
            "description": "과매도 반등 기대 (검증 기반 임계값)"
        },
        {
            "name": "overbought_pullback",
            "signal_type": "sell",
            "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_upper"}
            ],
            "logic": "and",
            "weight": 1.0,
            "description": "과매수 조정 기대"
        }
    ],
    "risk_management": {
        "stop_loss_pct": 0.03,
        "take_profit_pct": 0.08,
        "max_position_pct": 0.08
    },
    "tags": ["mean-reversion", "counter-trend", "data-validated"]
}'::jsonb,
    description = 'RSI 35 이하이고 볼린저 하단 밴드를 하회하면 매수하는 평균 회귀 전략입니다. 데이터 검증으로 임계값이 최적화되었습니다.',
    version = '2.0',
    updated_at = NOW()
WHERE strategy_id = 'mean_reversion';

-- 3. rate_linked_allocation: T10Y 구간 재설계 (2024-2026 고금리 환경 반영)
UPDATE strategies
SET conditions = '{
    "rules": [
        {
            "name": "low_rate_equity",
            "signal_type": "buy",
            "conditions": [
                {"indicator": "treasury_10y", "params": {"source": "fred"}, "operator": "lt", "value": 4.2}
            ],
            "logic": "and",
            "weight": 1.0,
            "description": "금리 안정/하락세: 주식 비중 확대"
        },
        {
            "name": "high_rate_bond",
            "signal_type": "sell",
            "conditions": [
                {"indicator": "treasury_10y", "params": {"source": "fred"}, "operator": "gte", "value": 4.8}
            ],
            "logic": "and",
            "weight": 1.0,
            "description": "고금리 급등: 방어적 포지션"
        }
    ],
    "risk_management": {
        "stop_loss_pct": 0.12,
        "take_profit_pct": 0.25,
        "max_position_pct": 0.8
    },
    "allocation_rules": [
        {"condition": "treasury_10y < 3.5", "equity": 0.8, "bond": 0.2},
        {"condition": "treasury_10y >= 3.5 AND treasury_10y < 4.5", "equity": 0.6, "bond": 0.4},
        {"condition": "treasury_10y >= 4.5", "equity": 0.4, "bond": 0.6}
    ],
    "tags": ["asset-allocation", "dynamic", "macro", "data-validated"]
}'::jsonb,
    description = 'FRED 10년물 국채금리에 따라 주식/채권 비중을 자동 조절하는 동적 자산배분 전략입니다. 2024-2026 고금리 환경을 반영한 현실적 구간(buy<4.2%, sell>=4.8%)을 사용합니다.',
    version = '2.0',
    updated_at = NOW()
WHERE strategy_id = 'rate_linked_allocation';

-- 4. trend_following: + 수익률 곡선 역전 필터 (T10Y2Y >= 0)
UPDATE strategies
SET conditions = '{
    "rules": [
        {
            "name": "uptrend_entry",
            "signal_type": "buy",
            "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"},
                {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"},
                {"indicator": "t10y2y", "params": {}, "operator": "gte", "value": 0}
            ],
            "logic": "and",
            "weight": 0.85,
            "description": "상승 추세 + 정상 수익률 곡선 (역전 아님)"
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
    "tags": ["trend-following", "long-term", "macro-filtered", "data-validated"]
}'::jsonb,
    description = '장기 추세를 따라 매매하는 전략입니다. 50일선 위에서 매수하되, 수익률 곡선이 역전(T10Y2Y<0)되면 경기침체 방어를 위해 매수를 중단합니다.',
    version = '2.0',
    updated_at = NOW()
WHERE strategy_id = 'trend_following';

-- 5. dual_momentum: + 수익률 곡선 역전 필터 (T10Y2Y >= 0)
UPDATE strategies
SET conditions = '{
    "rules": [
        {
            "name": "dual_momentum_buy",
            "signal_type": "buy",
            "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 50},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0},
                {"indicator": "t10y2y", "params": {}, "operator": "gte", "value": 0}
            ],
            "logic": "and",
            "weight": 1.0,
            "description": "절대+상대 모멘텀 강세 + 정상 수익률 곡선"
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
    "tags": ["momentum", "long-term", "macro-filtered", "data-validated"]
}'::jsonb,
    description = '절대 모멘텀과 상대 모멘텀을 결합한 전략입니다. 200일선 위에서 RSI와 MACD 확인 후 매수하되, 수익률 곡선 역전 시 신규 진입을 중단합니다.',
    version = '2.0',
    updated_at = NOW()
WHERE strategy_id = 'dual_momentum';


-- ============================================
-- V57: 신규 전략 추가 + 백테스트 검증 기반 전략 업데이트
-- 근거: 5년 백테스트(2021-2026) 3회 반복 결정론 검증 결과
-- ============================================

-- 신규 전략 1: RSI+MACD 복합 (A등급, CAGR 18.31%)
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'rsi_macd_combo', 'RSI+MACD 복합',
    'RSI 과매도 구간(42 이하)에서 MACD 히스토그램이 양전환할 때만 매수하는 이중 확인 전략입니다. 매도는 RSI 65 이상 과매수 구간에서 실행하며, 매수-매도 간 23포인트 데드존이 불필요한 왕복 거래를 방지합니다. 5년 백테스트(2021-2026) CAGR 18.31%, 승률 67.3%.',
    (SELECT id FROM strategy_categories WHERE code = 'QUANT_COMPOSITE'), true, false, 'ACTIVE',
    '{
        "rules": [
            {
                "name": "rsi_macd_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42},
                    {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "RSI 과매도 + MACD 히스토그램 양전환 확인"
            },
            {
                "name": "rsi_overbought_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "RSI 과매수 구간 매도 (데드존 42~65 = 23pt)"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.10,
            "take_profit_pct": 0.25,
            "max_position_pct": 0.1
        },
        "tags": ["mean-reversion", "multi-indicator", "backtest-validated"]
    }'::jsonb,
    'DAILY', '1.0', NOW(), NOW()
) ON CONFLICT (strategy_id) DO NOTHING;

-- 신규 전략 2: 골든크로스+RSI 필터 (B+등급, CAGR 10.54%)
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'golden_cross_rsi', '골든크로스+RSI',
    '20일선이 50일선을 상향 돌파(골든크로스)할 때 RSI가 55 이하인 경우에만 매수하는 필터링 전략입니다. RSI 필터로 고점 추격 매수를 방지하며, 데드크로스 시 매도합니다. 5년 백테스트(2021-2026) CAGR 10.54%, 평균 보유 27.8일.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'), true, false, 'ACTIVE',
    '{
        "rules": [
            {
                "name": "golden_cross_rsi_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 55}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "골든크로스 + RSI 비과매수 필터"
            },
            {
                "name": "death_cross_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_below", "value": "sma_50"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "데드크로스 매도"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.10,
            "take_profit_pct": 0.25,
            "max_position_pct": 0.1
        },
        "tags": ["momentum", "trend-following", "backtest-validated"]
    }'::jsonb,
    'DAILY', '1.0', NOW(), NOW()
) ON CONFLICT (strategy_id) DO NOTHING;

-- 신규 전략 3: RSI+MACD+SMA200 편향보정 (B+등급, CAGR 14.22%)
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'rsi_macd_sma200', 'RSI+MACD+SMA200 편향보정',
    'RSI 과매도(45 이하) + MACD 히스토그램 양전환 + SMA200 레짐 필터 3중 조건 전략입니다. SMA200 필터가 하락장 매수를 차단하여 생존자 편향을 보정합니다. 매도는 RSI 65 이상에서 실행하며, 데드존 20pt가 whipsaw를 방지합니다. 5년 백테스트(2021-2026) CAGR 14.22%, PF 2.85, 승률 75.8%.',
    (SELECT id FROM strategy_categories WHERE code = 'QUANT_COMPOSITE'), true, false, 'ACTIVE',
    '{
        "rules": [
            {
                "name": "rsi_macd_sma200_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 45},
                    {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0},
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "RSI 과매도 + MACD 양전환 + SMA200 레짐 확인"
            },
            {
                "name": "rsi_overbought_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "RSI 과매수 구간 매도 (데드존 45~65 = 20pt)"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.10,
            "take_profit_pct": 0.25,
            "max_position_pct": 0.1
        },
        "tags": ["mean-reversion", "multi-indicator", "regime-filtered", "backtest-validated"]
    }'::jsonb,
    'DAILY', '1.0', NOW(), NOW()
) ON CONFLICT (strategy_id) DO NOTHING;

-- 신규 전략 4: Yield Curve+RSI 매크로 전략 (B+등급, CAGR 12.89%)
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'yield_curve_rsi', 'Yield Curve+RSI',
    '장단기 금리차(T10Y2Y)가 양수(정상 수익률곡선)이고 RSI 과매도(40 이하)이며 SMA50>SMA200(골든크로스) 상태에서만 매수하는 매크로 전략입니다. 수익률곡선 역전 시(-0.5 이하) 강제 매도하여 경기침체를 방어합니다. RSI 전략과의 낮은 상관관계(r=0.20~0.49)로 포트폴리오 분산 효과가 우수합니다. 5년 백테스트(2021-2026) CAGR 12.89%, PF 2.62, 승률 75.7%.',
    (SELECT id FROM strategy_categories WHERE code = 'ASSET_ALLOCATION'), true, false, 'ACTIVE',
    '{
        "rules": [
            {
                "name": "yield_rsi_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "t10y2y", "params": {}, "operator": "gt", "value": 0},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 40},
                    {"indicator": "sma", "params": {"period": 50}, "operator": "gt", "value": "sma_200"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "정상 수익률곡선 + RSI 과매도 + 골든크로스 상태"
            },
            {
                "name": "rsi_overbought_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "RSI 과매수 구간 매도"
            },
            {
                "name": "yield_curve_inversion_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "t10y2y", "params": {}, "operator": "lt", "value": -0.5}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "수익률곡선 역전 시 강제 매도 (경기침체 방어)"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.12,
            "take_profit_pct": 0.30,
            "max_position_pct": 0.1
        },
        "tags": ["macro", "yield-curve", "regime-filtered", "backtest-validated"]
    }'::jsonb,
    'DAILY', '1.0', NOW(), NOW()
) ON CONFLICT (strategy_id) DO NOTHING;

-- golden_cross: SL 5%→10%, TP 15%→25%
UPDATE strategies
SET conditions = '{
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
        "stop_loss_pct": 0.10,
        "take_profit_pct": 0.25,
        "max_position_pct": 0.1
    },
    "tags": ["trend-following", "momentum", "classic", "backtest-validated"]
}'::jsonb,
    description = '단기(20일) 이동평균선이 장기(50일) 이동평균선을 상향 돌파할 때 매수하는 모멘텀 전략입니다. SL 10%, TP 25%로 미국 대형주 변동성에 맞게 조정되었습니다.',
    version = '2.0',
    updated_at = NOW()
WHERE strategy_id = 'golden_cross';

-- momentum_breakout: +SMA50 필터, SL 4%→8%, TP 12%→20%
UPDATE strategies
SET conditions = '{
    "rules": [
        {
            "name": "momentum_buy",
            "signal_type": "buy",
            "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "ema_20"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 50},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"}
            ],
            "logic": "and",
            "weight": 0.9,
            "description": "상승 모멘텀 확인 + SMA50 트렌드 필터"
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
        "stop_loss_pct": 0.08,
        "take_profit_pct": 0.20,
        "max_position_pct": 0.1
    },
    "tags": ["momentum", "trend-following", "backtest-validated"]
}'::jsonb,
    description = '가격이 20일 EMA와 50일 SMA 위에 있고 RSI 50 이상이면 매수하는 모멘텀 전략입니다. SMA50 트렌드 필터가 하락장 매수를 방지합니다.',
    version = '2.0',
    updated_at = NOW()
WHERE strategy_id = 'momentum_breakout';

-- triple_screen v2: 매도 RSI>30 제거, SL 5%→10%, TP 20%→30%
UPDATE strategies
SET conditions = '{
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
                {"indicator": "macd", "params": {}, "operator": "lt", "value": "macd_signal"}
            ],
            "logic": "and",
            "weight": 1.0,
            "description": "추세 이탈 + MACD 하락 매도 (불필요한 RSI>30 조건 제거)"
        }
    ],
    "risk_management": {
        "stop_loss_pct": 0.10,
        "take_profit_pct": 0.30,
        "max_position_pct": 0.08
    },
    "tags": ["multi-indicator", "trend-following", "backtest-validated"]
}'::jsonb,
    description = '골든크로스 + RSI 비과매수 + MACD 상승 3가지 조건 동시 충족 시 매수하는 복합 전략입니다. 매도 조건에서 불필요한 RSI>30을 제거하고 SL/TP를 확대하여 성과를 개선했습니다.',
    version = '2.0',
    updated_at = NOW()
WHERE strategy_id = 'triple_screen';

-- trend_following v3: SL/TP 확대 (jsonb_set)
UPDATE strategies
SET conditions = jsonb_set(
    jsonb_set(
        jsonb_set(
            conditions,
            '{risk_management,stop_loss_pct}', '0.10'
        ),
        '{risk_management,take_profit_pct}', '0.30'
    ),
    '{risk_management,trailing_stop_pct}', '0.07'
),
    version = '3.0',
    updated_at = NOW()
WHERE strategy_id = 'trend_following';

-- dual_momentum v3: SL/TP 확대 (jsonb_set)
UPDATE strategies
SET conditions = jsonb_set(
    jsonb_set(
        conditions,
        '{risk_management,stop_loss_pct}', '0.12'
    ),
    '{risk_management,take_profit_pct}', '0.35'
),
    version = '3.0',
    updated_at = NOW()
WHERE strategy_id = 'dual_momentum';

-- MACD 크로스오버 비활성화 (F등급, -96.99%)
UPDATE strategies
SET status = 'INACTIVE',
    description = '[비활성화] MACD 크로스오버 전략. 5년 백테스트 -96.99%, 승률 12.6%로 수학적 손익분기(25%) 미달. 모든 개선 시도(SMA200 필터, SL 확대, trailing stop) 실패. 독립 전략으로 부적합하며 보조 필터로만 활용 가능.',
    version = '2.0',
    updated_at = NOW()
WHERE strategy_id = 'macd_crossover';

-- 모멘텀 브레이크아웃 비활성화 (F등급, -53.87%)
UPDATE strategies
SET status = 'INACTIVE',
    description = '[비활성화] 모멘텀 브레이크아웃 전략. SMA50 트렌드 필터 추가 후에도 5년 백테스트 -53.87%, 승률 28.8%, MDD -69.04%. 단순 모멘텀 추종은 현재 10종목 유니버스에서 구조적으로 부적합.',
    version = '2.0',
    updated_at = NOW()
WHERE strategy_id = 'momentum_breakout';


-- ============================================
-- V58: 전략 조건 최적화 (3개) - 최종 버전
-- 근거: 근본 원인 분석 기반 조건 재설계 + 5년 백테스트 재검증
-- ============================================

-- trend_following v4: dip 매수 + RSI 매도, trailing 제거
-- 결과: -87.64% → +59.63%, 승률 6.5% → 67.3%, CAGR 9.50%
UPDATE strategies
SET conditions = '{
    "rules": [
        {
            "name": "uptrend_dip_entry",
            "signal_type": "buy",
            "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 40},
                {"indicator": "t10y2y", "params": {}, "operator": "gte", "value": 0}
            ],
            "logic": "and",
            "weight": 1.0
        },
        {
            "name": "overbought_exit",
            "signal_type": "sell",
            "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 70}
            ],
            "logic": "and",
            "weight": 1.0
        }
    ],
    "risk_management": {
        "stop_loss_pct": 0.12,
        "take_profit_pct": 0.30,
        "max_position_pct": 0.12
    }
}',
    description = '상승 추세(price>SMA50) 내에서 RSI 눌림목(RSI<40)에 매수하고 과매수(RSI>70)에서 매도하는 하이브리드 전략입니다. 금리 역전(T10Y2Y<0) 시 매수를 보류합니다. 5년 백테스트 CAGR 9.50%, 승률 67.3%, MDD -14.15%.',
    version = '4.0',
    updated_at = NOW()
WHERE strategy_id = 'trend_following';

-- dual_momentum v4: 4조건→2조건, dip 진입 + 빠른 매도
-- 결과: -57.70% → +239.68%, 승률 13.1% → 69.0%, CAGR 26.78%
UPDATE strategies
SET conditions = '{
    "rules": [
        {
            "name": "trend_dip_buy",
            "signal_type": "buy",
            "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 45}
            ],
            "logic": "and",
            "weight": 1.0
        },
        {
            "name": "overbought_or_trend_break_sell",
            "signal_type": "sell",
            "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 70}
            ],
            "logic": "and",
            "weight": 1.0
        }
    ],
    "risk_management": {
        "stop_loss_pct": 0.12,
        "take_profit_pct": 0.30,
        "max_position_pct": 0.15
    }
}',
    description = '장기 상승 추세(price>SMA200) 내에서 RSI 눌림목(RSI<45)에 매수하고 과매수(RSI>70)에서 매도하는 듀얼 모멘텀 전략입니다. 기존 4조건 AND 게이트를 2조건으로 단순화하여 고점 매수를 방지합니다. 5년 백테스트 CAGR 26.78%, 승률 69.0%, MDD -27.02%.',
    version = '4.0',
    updated_at = NOW()
WHERE strategy_id = 'dual_momentum';

-- triple_screen v3: 매수 RSI<70→RSI<45, 매도 단순화
-- 결과: -30.59% → +170.15%, 승률 28.8% → 84.7%, CAGR 21.27%
UPDATE strategies
SET conditions = '{
    "rules": [
        {
            "name": "triple_screen_buy",
            "signal_type": "buy",
            "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 45},
                {"indicator": "macd", "params": {}, "operator": "gt", "value": "macd_signal"}
            ],
            "logic": "and",
            "weight": 1.0
        },
        {
            "name": "overbought_exit",
            "signal_type": "sell",
            "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65}
            ],
            "logic": "and",
            "weight": 1.0
        }
    ],
    "risk_management": {
        "stop_loss_pct": 0.10,
        "take_profit_pct": 0.25,
        "max_position_pct": 0.08
    }
}',
    description = '트리플 스크린(SMA20>SMA50 추세 + MACD 모멘텀 + RSI<45 역추세 진입)에서 매수하고 RSI>65 과매수에서 빠르게 매도하는 전략입니다. 기존 복합 매도 조건을 RSI 단일 조건으로 단순화하여 이익 실현 속도를 개선했습니다. 5년 백테스트 CAGR 21.27%, 승률 84.7%, MDD -9.28%.',
    version = '3.0',
    updated_at = NOW()
WHERE strategy_id = 'triple_screen';
