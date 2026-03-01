-- V57: 신규 전략 추가 + 백테스트 검증 기반 전략 업데이트
-- 근거: 5년 백테스트(2021-2026) 3회 반복 결정론 검증 결과
-- 문서: docs/analysis/전략_백테스트_5년_검증.md

-- =====================================================================
-- 0-1. 안전 장치: check_strategy_status 제약 조건 재생성
-- DB에 INACTIVE가 누락된 경우를 대비하여 제약 조건을 재생성
-- =====================================================================
ALTER TABLE strategies DROP CONSTRAINT IF EXISTS check_strategy_status;
ALTER TABLE strategies ADD CONSTRAINT check_strategy_status CHECK (
    status IN ('ACTIVE', 'INACTIVE', 'DRAFT', 'ARCHIVED', 'PENDING_REVIEW', 'APPROVED', 'PUBLISHED', 'REJECTED')
);

-- =====================================================================
-- 0-2. 안전 장치: strategy_id UNIQUE 제약 조건 확인
-- V7에서 partial index만 생성된 경우 ON CONFLICT가 동작하지 않으므로
-- full UNIQUE constraint가 없으면 생성
-- =====================================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'strategies_strategy_id_unique'
        AND conrelid = 'strategies'::regclass
    ) THEN
        ALTER TABLE strategies ADD CONSTRAINT strategies_strategy_id_unique UNIQUE (strategy_id);
    END IF;
END $$;

-- =====================================================================
-- 1. 신규 전략 추가: RSI+MACD 복합 (A등급, CAGR 18.31%)
-- 근거: RSI 과매도 + MACD 반등 확인 이중 필터
--       데드존 42~65 (23pt)가 whipsaw 방지 핵심
--       5년 수익률 +137.90%, 승률 67.3%, MDD -18.28%, PF 2.17
-- =====================================================================
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'rsi_macd_combo',
    'RSI+MACD 복합',
    'RSI 과매도 구간(42 이하)에서 MACD 히스토그램이 양전환할 때만 매수하는 이중 확인 전략입니다. 매도는 RSI 65 이상 과매수 구간에서 실행하며, 매수-매도 간 23포인트 데드존이 불필요한 왕복 거래를 방지합니다. 5년 백테스트(2021-2026) CAGR 18.31%, 승률 67.3%.',
    (SELECT id FROM strategy_categories WHERE code = 'QUANT_COMPOSITE'),
    true,
    false,
    'ACTIVE',
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
    'DAILY',
    '1.0',
    NOW(),
    NOW()
)
ON CONFLICT (strategy_id) DO NOTHING;

-- =====================================================================
-- 2. 신규 전략 추가: 골든크로스+RSI 필터 (B+등급, CAGR 10.54%)
-- 근거: 기존 골든크로스에 RSI<55 필터 추가로 과매수 추격 방지
--       crosses_above/below 조건으로 시그널 빈도 적절
--       5년 수익률 +67.63%, 승률 41.0%, MDD -18.22%, PF 1.74
-- =====================================================================
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'golden_cross_rsi',
    '골든크로스+RSI',
    '20일선이 50일선을 상향 돌파(골든크로스)할 때 RSI가 55 이하인 경우에만 매수하는 필터링 전략입니다. RSI 필터로 고점 추격 매수를 방지하며, 데드크로스 시 매도합니다. 5년 백테스트(2021-2026) CAGR 10.54%, 평균 보유 27.8일.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'),
    true,
    false,
    'ACTIVE',
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
    'DAILY',
    '1.0',
    NOW(),
    NOW()
)
ON CONFLICT (strategy_id) DO NOTHING;

-- =====================================================================
-- 3. 신규 전략 추가: RSI+MACD+SMA200 편향보정 (B+등급, CAGR 14.22%)
-- 근거: 전문가 패널(Taleb/Meadows) 제안 SMA200 레짐 필터
--       RSI<45 + MACD 확인 + SMA200 상위 3중 필터
--       편향 보정 후에도 PF 2.85 (최고), 승률 75.8%, MDD -18.06%
--       RSI+MACD 복합(r=0.64) 대비 편향 보정된 안전한 대안
-- =====================================================================
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'rsi_macd_sma200',
    'RSI+MACD+SMA200 편향보정',
    'RSI 과매도(45 이하) + MACD 히스토그램 양전환 + SMA200 레짐 필터 3중 조건 전략입니다. SMA200 필터가 하락장 매수를 차단하여 생존자 편향을 보정합니다. 매도는 RSI 65 이상에서 실행하며, 데드존 20pt가 whipsaw를 방지합니다. 5년 백테스트(2021-2026) CAGR 14.22%, PF 2.85, 승률 75.8%.',
    (SELECT id FROM strategy_categories WHERE code = 'QUANT_COMPOSITE'),
    true,
    false,
    'ACTIVE',
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
    'DAILY',
    '1.0',
    NOW(),
    NOW()
)
ON CONFLICT (strategy_id) DO NOTHING;

-- =====================================================================
-- 4. 신규 전략 추가: Yield Curve+RSI 매크로 전략 (B+등급, CAGR 12.89%)
-- 근거: 전문가 패널(Christensen/Meadows) 제안 매크로 레짐 전략
--       T10Y2Y > 0 (정상 수익률곡선) 시에만 매수
--       다른 RSI 전략과 상관관계 r=0.20~0.49 (최고 분산 효과)
--       수익률곡선 역전(< -0.5) 시 강제 매도 (경기침체 방어)
--       5년 수익률 +86.80%, 승률 75.7%, MDD -23.22%, PF 2.62
-- =====================================================================
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'yield_curve_rsi',
    'Yield Curve+RSI',
    '장단기 금리차(T10Y2Y)가 양수(정상 수익률곡선)이고 RSI 과매도(40 이하)이며 SMA50>SMA200(골든크로스) 상태에서만 매수하는 매크로 전략입니다. 수익률곡선 역전 시(-0.5 이하) 강제 매도하여 경기침체를 방어합니다. RSI 전략과의 낮은 상관관계(r=0.20~0.49)로 포트폴리오 분산 효과가 우수합니다. 5년 백테스트(2021-2026) CAGR 12.89%, PF 2.62, 승률 75.7%.',
    (SELECT id FROM strategy_categories WHERE code = 'ASSET_ALLOCATION'),
    true,
    false,
    'ACTIVE',
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
    'DAILY',
    '1.0',
    NOW(),
    NOW()
)
ON CONFLICT (strategy_id) DO NOTHING;

-- =====================================================================
-- 5. 기존 전략 업데이트: golden_cross (SL 5%→10%, TP 15%→25%)
-- (이하 기존 전략 업데이트 및 비활성화 - 번호 유지)
-- 근거: SL 확대로 -47.38% → +10.33% 흑자 전환 (B등급)
-- =====================================================================
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

-- =====================================================================
-- 4. 기존 전략 업데이트: momentum_breakout (+SMA50 필터, SL 4%→8%, TP 12%→20%)
-- 근거: SMA50 트렌드 필터로 -99.66% → -0.17% 개선 (C등급)
-- =====================================================================
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

-- =====================================================================
-- 5. 기존 전략 업데이트: triple_screen (매도 RSI>30 제거, SL 5%→10%, TP 20%→30%)
-- 근거: RSI>30 매도 조건이 거의 항상 true → 무의미. 제거 후 -92.50% → -16.36%
-- =====================================================================
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

-- =====================================================================
-- 6. 기존 전략 업데이트: trend_following (SL 7%→10%, TP 25%→30%, trailing 5%→7%)
-- 근거: V56에서 T10Y2Y 필터 추가. 추가로 SL/TP 확대하여 개선
-- =====================================================================
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

-- =====================================================================
-- 7. 기존 전략 업데이트: dual_momentum (SL 8%→12%, TP 30%→35%)
-- 근거: V56에서 T10Y2Y 필터 추가. 추가로 SL/TP 확대
-- =====================================================================
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

-- =====================================================================
-- 8. MACD 크로스오버 전략 비활성화 (F등급, -91.16%)
-- 근거: 모든 개선 시도 후에도 승률 13.5% (손익분기 25% 미달)
--       전문가 패널 만장일치 폐기 권장
-- =====================================================================
UPDATE strategies
SET status = 'INACTIVE',
    description = '[비활성화] MACD 크로스오버 전략. 5년 백테스트 -96.99%, 승률 12.6%로 수학적 손익분기(25%) 미달. 모든 개선 시도(SMA200 필터, SL 확대, trailing stop) 실패. 독립 전략으로 부적합하며 보조 필터로만 활용 가능.',
    version = '2.0',
    updated_at = NOW()
WHERE strategy_id = 'macd_crossover';

-- =====================================================================
-- 9. 모멘텀 브레이크아웃 전략 비활성화 (F등급, -53.87%)
-- 근거: SMA50 필터 추가 후에도 -53.87%, 승률 28.8%, MDD -69.04%
--       모멘텀 추종형 전략이 현재 유니버스에서 구조적으로 부적합
-- =====================================================================
UPDATE strategies
SET status = 'INACTIVE',
    description = '[비활성화] 모멘텀 브레이크아웃 전략. SMA50 트렌드 필터 추가 후에도 5년 백테스트 -53.87%, 승률 28.8%, MDD -69.04%. 단순 모멘텀 추종은 현재 10종목 유니버스에서 구조적으로 부적합.',
    version = '2.0',
    updated_at = NOW()
WHERE strategy_id = 'momentum_breakout';
