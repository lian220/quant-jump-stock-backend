-- V16: Insert Seed Strategies
-- 15개 시드 전략 데이터 삽입 (모든 카테고리 커버)
-- Created: 2025-02-03

-- =====================================================================
-- MOMENTUM 전략 (3개)
-- =====================================================================

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    '골든크로스',
    '단기(20일) 이동평균선이 장기(50일) 이동평균선을 상향 돌파할 때 매수하는 모멘텀 전략입니다. 추세 전환의 강력한 신호로 활용됩니다.',
    'MOMENTUM',
    true,
    false,
    'ACTIVE',
    '{
        "indicators": {
            "sma_short": {"type": "SMA", "period": 20},
            "sma_long": {"type": "SMA", "period": 50}
        },
        "buy_conditions": [
            {"indicator": "sma_short", "operator": ">", "compare_to": "sma_long"}
        ],
        "sell_conditions": [
            {"indicator": "sma_short", "operator": "<", "compare_to": "sma_long"}
        ]
    }'::jsonb,
    'DAILY',
    NOW(),
    NOW()
);

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    'RSI 과매도 반등',
    'RSI(상대강도지수)가 30 이하 과매도 구간에서 매수하고, 70 이상 과매수 구간에서 매도하는 역추세 전략입니다.',
    'MOMENTUM',
    true,
    false,
    'ACTIVE',
    '{
        "indicators": {
            "rsi": {"type": "RSI", "period": 14}
        },
        "thresholds": {
            "oversold": 30,
            "overbought": 70
        },
        "buy_conditions": [
            {"indicator": "rsi", "operator": "<", "value": 30}
        ],
        "sell_conditions": [
            {"indicator": "rsi", "operator": ">", "value": 70}
        ]
    }'::jsonb,
    'DAILY',
    NOW(),
    NOW()
);

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    'MACD 크로스오버',
    'MACD선이 시그널선을 상향 돌파할 때 매수하는 추세추종 전략입니다. 중장기 추세 파악에 효과적입니다.',
    'MOMENTUM',
    true,
    false,
    'ACTIVE',
    '{
        "indicators": {
            "macd": {
                "type": "MACD",
                "fast_period": 12,
                "slow_period": 26,
                "signal_period": 9
            }
        },
        "buy_conditions": [
            {"indicator": "macd_line", "operator": ">", "compare_to": "signal_line"}
        ],
        "sell_conditions": [
            {"indicator": "macd_line", "operator": "<", "compare_to": "signal_line"}
        ]
    }'::jsonb,
    'DAILY',
    NOW(),
    NOW()
);

-- =====================================================================
-- QUANT_COMPOSITE 전략 (2개)
-- =====================================================================

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    '복합 기술적 분석',
    '골든크로스 + RSI 중립이하 + MACD 매수신호 3가지 조건을 동시에 충족할 때만 매수하는 보수적인 복합 전략입니다.',
    'QUANT_COMPOSITE',
    true,
    false,
    'ACTIVE',
    '{
        "indicators": {
            "sma_short": {"type": "SMA", "period": 20},
            "sma_long": {"type": "SMA", "period": 50},
            "rsi": {"type": "RSI", "period": 14},
            "macd": {"type": "MACD", "fast_period": 12, "slow_period": 26, "signal_period": 9}
        },
        "buy_conditions": [
            {"indicator": "sma_short", "operator": ">", "compare_to": "sma_long", "logic": "AND"},
            {"indicator": "rsi", "operator": "<", "value": 50, "logic": "AND"},
            {"indicator": "macd_line", "operator": ">", "compare_to": "signal_line"}
        ],
        "sell_conditions": [
            {"indicator": "sma_short", "operator": "<", "compare_to": "sma_long"}
        ]
    }'::jsonb,
    'DAILY',
    NOW(),
    NOW()
);

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    '기술+감정 통합 분석',
    '기술적 분석 70%와 뉴스 감정분석 30%를 가중 평균하여 통합 점수가 0.6 이상일 때 매수하는 고급 전략입니다.',
    'QUANT_COMPOSITE',
    true,
    true,
    'ACTIVE',
    '{
        "analysis_weights": {
            "technical": 0.7,
            "sentiment": 0.3
        },
        "technical_indicators": {
            "sma_short": {"type": "SMA", "period": 20},
            "sma_long": {"type": "SMA", "period": 50},
            "rsi": {"type": "RSI", "period": 14},
            "macd": {"type": "MACD", "fast_period": 12, "slow_period": 26, "signal_period": 9}
        },
        "sentiment_source": "news_api",
        "thresholds": {
            "buy_score": 0.6,
            "sell_score": 0.4
        },
        "buy_conditions": [
            {"indicator": "combined_score", "operator": ">=", "value": 0.6}
        ],
        "sell_conditions": [
            {"indicator": "combined_score", "operator": "<", "value": 0.4}
        ]
    }'::jsonb,
    'DAILY',
    NOW(),
    NOW()
);

-- =====================================================================
-- VALUE 전략 (2개)
-- =====================================================================

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    '저PER 가치주',
    'PER이 10 미만이고 업종 평균 대비 30% 이상 저평가된 종목을 발굴하는 전통적인 가치투자 전략입니다.',
    'VALUE',
    true,
    false,
    'ACTIVE',
    '{
        "indicators": {
            "per": {"type": "fundamental", "field": "PER"},
            "sector_avg_per": {"type": "sector_average", "field": "PER"}
        },
        "buy_conditions": [
            {"indicator": "per", "operator": "<", "value": 10, "logic": "AND"},
            {"indicator": "per", "operator": "<", "compare_to": "sector_avg_per", "multiplier": 0.7}
        ],
        "sell_conditions": [
            {"indicator": "per", "operator": ">", "value": 20}
        ]
    }'::jsonb,
    'MONTHLY',
    NOW(),
    NOW()
);

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    '고배당 가치주',
    '배당수익률 4% 이상, PBR 1 미만의 안정적인 배당주에 투자하는 인컴형 가치투자 전략입니다.',
    'VALUE',
    true,
    false,
    'ACTIVE',
    '{
        "indicators": {
            "dividend_yield": {"type": "fundamental", "field": "DIV_YIELD"},
            "pbr": {"type": "fundamental", "field": "PBR"}
        },
        "buy_conditions": [
            {"indicator": "dividend_yield", "operator": ">", "value": 4.0, "logic": "AND"},
            {"indicator": "pbr", "operator": "<", "value": 1.0}
        ],
        "sell_conditions": [
            {"indicator": "dividend_yield", "operator": "<", "value": 2.0}
        ]
    }'::jsonb,
    'QUARTERLY',
    NOW(),
    NOW()
);

-- =====================================================================
-- ASSET_ALLOCATION 전략 (3개)
-- =====================================================================

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    '주식-채권 밸런스 60/40',
    '전통적인 60% 주식 + 40% 채권 포트폴리오입니다. 목표 비중에서 5% 이상 벗어나면 리밸런싱합니다.',
    'ASSET_ALLOCATION',
    true,
    false,
    'ACTIVE',
    '{
        "portfolio": {
            "equity": {"weight": 0.6, "ticker": "KODEX200", "name": "코덱스200"},
            "bond": {"weight": 0.4, "ticker": "KOSEF국고채10년", "name": "코세프국고채10년"}
        },
        "rebalance_trigger": {
            "threshold": 0.05,
            "description": "목표 비중에서 5% 이상 벗어나면 리밸런싱"
        }
    }'::jsonb,
    'QUARTERLY',
    NOW(),
    NOW()
);

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    '금리 연동 동적 배분',
    'FRED 10년물 국채금리에 따라 주식/채권 비중을 자동 조절하는 동적 자산배분 전략입니다.',
    'ASSET_ALLOCATION',
    true,
    false,
    'ACTIVE',
    '{
        "indicators": {
            "treasury_10y": {"source": "fred", "code": "DGS10"}
        },
        "allocation_rules": [
            {"condition": "treasury_10y < 2", "equity": 0.8, "bond": 0.2, "description": "저금리: 주식 80%"},
            {"condition": "treasury_10y >= 2 AND treasury_10y < 4", "equity": 0.6, "bond": 0.4, "description": "중립: 주식 60%"},
            {"condition": "treasury_10y >= 4", "equity": 0.4, "bond": 0.6, "description": "고금리: 주식 40%"}
        ],
        "tickers": {
            "equity": "KODEX200",
            "bond": "KOSEF국고채10년"
        }
    }'::jsonb,
    'MONTHLY',
    NOW(),
    NOW()
);

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    '올웨더 포트폴리오',
    '레이 달리오의 사계절 포트폴리오를 한국 ETF로 구현한 전략입니다. 어떤 경제 환경에서도 안정적인 수익을 추구합니다.',
    'ASSET_ALLOCATION',
    true,
    true,
    'ACTIVE',
    '{
        "portfolio": {
            "stocks": {"weight": 0.30, "ticker": "KODEX200", "name": "주식"},
            "long_term_bonds": {"weight": 0.40, "ticker": "KOSEF국고채10년", "name": "장기채권"},
            "mid_term_bonds": {"weight": 0.15, "ticker": "KBSTAR중기우량회사채", "name": "중기채권"},
            "gold": {"weight": 0.075, "ticker": "KODEX골드선물(H)", "name": "금"},
            "commodities": {"weight": 0.075, "ticker": "KODEX WTI원유선물(H)", "name": "원자재"}
        },
        "rebalance_trigger": {
            "threshold": 0.05
        }
    }'::jsonb,
    'YEARLY',
    NOW(),
    NOW()
);

-- =====================================================================
-- SEASONAL 전략 (3개)
-- =====================================================================

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    '1월 효과 (January Effect)',
    '연말 세금 매도 후 1월에 소형주가 반등하는 계절적 패턴을 활용합니다. 12월 20일 매수, 1월 31일 매도.',
    'SEASONAL',
    true,
    false,
    'ACTIVE',
    '{
        "schedule": {
            "buy_date": {"month": 12, "day": 20},
            "sell_date": {"month": 1, "day": 31}
        },
        "target": {
            "type": "small_cap",
            "ticker": "KODEX코스닥150",
            "name": "코스닥150 ETF"
        }
    }'::jsonb,
    'YEARLY',
    NOW(),
    NOW()
);

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    'Sell in May (할로윈 효과)',
    '"5월에 팔고 떠나라" 격언을 활용한 전략입니다. 5-10월 채권 비중↑, 11-4월 주식 비중↑',
    'SEASONAL',
    true,
    false,
    'ACTIVE',
    '{
        "schedule": {
            "summer": {
                "months": [5, 6, 7, 8, 9, 10],
                "allocation": {"equity": 0.2, "bond": 0.8}
            },
            "winter": {
                "months": [11, 12, 1, 2, 3, 4],
                "allocation": {"equity": 0.8, "bond": 0.2}
            }
        },
        "tickers": {
            "equity": "KODEX200",
            "bond": "KOSEF국고채10년"
        }
    }'::jsonb,
    'MONTHLY',
    NOW(),
    NOW()
);

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    '분기말 모멘텀 리밸런싱',
    '분기 마지막 주에 최근 3개월 수익률 상위 10종목으로 포트폴리오를 재구성하는 모멘텀 전략입니다.',
    'SEASONAL',
    true,
    false,
    'ACTIVE',
    '{
        "schedule": {
            "rebalance_dates": ["03-25", "06-25", "09-25", "12-25"]
        },
        "selection": {
            "criteria": "momentum_3m",
            "direction": "desc",
            "top_n": 10,
            "universe": "KOSPI200"
        },
        "position_sizing": {
            "method": "equal_weight",
            "max_position": 0.1
        }
    }'::jsonb,
    'QUARTERLY',
    NOW(),
    NOW()
);

-- =====================================================================
-- ML_PREDICTION 전략 (2개)
-- =====================================================================

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    'Vertex AI 예측',
    'Google Vertex AI 머신러닝 모델이 예측한 상승 확률이 70% 이상인 종목에 투자하는 AI 기반 전략입니다.',
    'ML_PREDICTION',
    true,
    true,
    'ACTIVE',
    '{
        "model": {
            "provider": "vertex_ai",
            "endpoint": "stock-prediction-v1",
            "version": "latest"
        },
        "features": [
            "price_momentum",
            "volume_trend",
            "technical_indicators",
            "market_sentiment"
        ],
        "thresholds": {
            "buy_probability": 0.7,
            "sell_probability": 0.3
        },
        "buy_conditions": [
            {"indicator": "predicted_up_probability", "operator": ">=", "value": 0.7}
        ],
        "sell_conditions": [
            {"indicator": "predicted_up_probability", "operator": "<", "value": 0.3}
        ]
    }'::jsonb,
    'DAILY',
    NOW(),
    NOW()
);

INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency, created_at, updated_at)
VALUES (
    'AI + 기술적 분석 하이브리드',
    'AI 예측(50%)과 기술적 분석(50%)을 앙상블하여 더 안정적인 매매 신호를 생성하는 고급 전략입니다.',
    'ML_PREDICTION',
    true,
    true,
    'ACTIVE',
    '{
        "ensemble": {
            "ai_prediction": {
                "weight": 0.5,
                "source": "vertex_ai",
                "model": "stock-prediction-v1"
            },
            "technical_analysis": {
                "weight": 0.5,
                "indicators": ["golden_cross", "rsi", "macd"]
            }
        },
        "thresholds": {
            "buy_score": 0.65,
            "sell_score": 0.35
        },
        "buy_conditions": [
            {"indicator": "ensemble_score", "operator": ">=", "value": 0.65}
        ],
        "sell_conditions": [
            {"indicator": "ensemble_score", "operator": "<", "value": 0.35}
        ]
    }'::jsonb,
    'DAILY',
    NOW(),
    NOW()
);

-- =====================================================================
-- 요약 로그
-- =====================================================================
-- 총 15개 시드 전략 삽입 완료:
--   - MOMENTUM: 3개 (골든크로스, RSI, MACD)
--   - QUANT_COMPOSITE: 2개 (복합 기술적, 기술+감정)
--   - VALUE: 2개 (저PER, 고배당)
--   - ASSET_ALLOCATION: 3개 (60/40, 금리연동, 올웨더)
--   - SEASONAL: 3개 (1월효과, Sell in May, 분기말)
--   - ML_PREDICTION: 2개 (Vertex AI, AI+기술 하이브리드)
--
-- 프리미엄 전략: 4개 (기술+감정, 올웨더, Vertex AI, AI하이브리드)
-- 무료 전략: 11개
