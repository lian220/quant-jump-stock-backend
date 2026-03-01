-- V56: 데이터 검증 기반 전략 조건 업데이트
-- 근거: 포워드 리턴 분석 (N=10,599), FRED 데이터 검증 (1,318일)
-- 변경 대상: 5개 전략 (rsi_oversold, mean_reversion, rate_linked_allocation, trend_following, dual_momentum)
-- 취소: scalping_rsi (백테스트 결과 v2가 더 나쁨 → 기존 유지)

-- =====================================================================
-- 1. rsi_oversold: RSI 매수 30→42, 매도 70→60
-- 근거: RSI<42 → 5일 포워드 리턴 +2.18% (N=10,599)
--       RSI>61 → 5일 포워드 리턴 -0.52%
-- =====================================================================
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

-- =====================================================================
-- 2. mean_reversion: RSI 매수 25→35, 매도 75→65
-- 근거: RSI<25는 발생 빈도가 극히 낮아 거래 기회 부족
--       RSI<35 + BB Lower로 충분한 과매도 필터링
-- =====================================================================
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

-- =====================================================================
-- 3. scalping_rsi: 변경 취소
-- 백테스트 결과: RSI(7)<25가 RSI(7)<20보다 수익률 하락 (7.25%→1.04%)
-- 스캘핑은 극단값이 유효. 기존 조건 유지.
-- =====================================================================

-- =====================================================================
-- 4. rate_linked_allocation: T10Y 구간 재설계
-- 근거: 현재 DGS10=3.97~4.50% (백테스트 기간)
--       기존 구간(buy<2, sell>=4)은 현재 환경에서 항상 매도 상태
--       2024-2026 고금리 시대 반영: buy<4.2(금리 하락), sell>=4.8(금리 급등)
-- =====================================================================
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

-- =====================================================================
-- 5. trend_following: + 수익률 곡선 역전 필터 (T10Y2Y >= 0)
-- 근거: T10Y2Y < 0 (수익률 역전) = 경기침체 경고 신호
--       역전 시 매수를 중단하여 하락장 방어
-- =====================================================================
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

-- =====================================================================
-- 6. dual_momentum: + 수익률 곡선 역전 필터 (T10Y2Y >= 0)
-- 근거: 장기 전략에 매크로 방어 필터 추가
--       역전 시 신규 진입 중단
-- =====================================================================
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
