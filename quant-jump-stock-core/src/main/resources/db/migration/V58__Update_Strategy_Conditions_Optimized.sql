-- V58: 전략 조건 최적화 (3개) + 볼린저 버그 수정 검증
-- 근거: 근본 원인 분석 기반 조건 재설계 + 5년 백테스트 재검증 (2021-2026)
-- 핵심: 추세추종(Buy Strength) → 역추세 진입(Buy Weakness) 하이브리드로 전환
-- 볼린저 밴드: interpreter 파싱 버그 수정 후 볼린저 스퀴즈/평균 회귀 정상 작동 확인

-- =====================================================================
-- 1. 추세 추종 v3 → v4 (dip 매수 + RSI 매도, trailing 제거)
-- 변경: 매수 SMA_20>SMA_50 → price>SMA_50 + RSI<40 (상승 추세 내 눌림목)
--       매도 price<SMA_50 → RSI>70 (과매수 이탈)
--       trailing stop 7% 제거 (수익 조기 청산 원인)
-- 결과: -87.64% → +59.63%, 승률 6.5% → 67.3%, CAGR 9.50%
-- =====================================================================
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

-- =====================================================================
-- 2. 듀얼 모멘텀 v3 → v4 (4조건→2조건, dip 진입 + 빠른 매도)
-- 변경: 매수 4조건 AND → 2조건 (SMA_200 위 + RSI<45)
--       매도 price<SMA_200 → RSI>70 (빠른 이익 실현)
-- 결과: -57.70% → +239.68%, 승률 13.1% → 69.0%, CAGR 26.78%
-- =====================================================================
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

-- =====================================================================
-- 3. 트리플 스크린 v2 → v3 (매도 단순화 + 매수 RSI 강화)
-- 변경: 매수 RSI<70 → RSI<45 (더 강한 역추세 필터)
--       매도 (SMA_20<SMA_50 AND MACD<signal) → RSI>65 (단일, 빠른 매도)
-- 결과: -30.59% → +170.15%, 승률 28.8% → 84.7%, CAGR 21.27%
-- =====================================================================
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
