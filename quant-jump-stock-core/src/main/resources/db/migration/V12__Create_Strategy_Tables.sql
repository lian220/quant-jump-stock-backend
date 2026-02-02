-- ============================================
-- Quantiq Strategy & Backtest Schema
-- PRD v2.0: 전략 마켓 + 전략 빌더 + 백테스트
-- ============================================

-- ============================================
-- 1. Strategies Table (전략 기본 정보)
-- ============================================
CREATE TABLE IF NOT EXISTS strategies (
    id BIGSERIAL PRIMARY KEY,

    -- 기본 정보
    name VARCHAR(100) NOT NULL,
    description TEXT,

    -- 분류
    category VARCHAR(50) NOT NULL CHECK (category IN (
        'VALUE',           -- 가치투자
        'MOMENTUM',        -- 모멘텀
        'ASSET_ALLOCATION', -- 자산배분
        'QUANT_COMPOSITE', -- 퀀트 복합
        'SEASONAL',        -- 시즈널
        'CUSTOM'           -- 사용자 정의
    )),

    -- 소유자 (NULL이면 시스템 전략)
    owner_id BIGINT REFERENCES users(id) ON DELETE SET NULL,

    -- 공개 설정
    is_public BOOLEAN DEFAULT FALSE,
    is_premium BOOLEAN DEFAULT FALSE,

    -- 상태
    status VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN (
        'DRAFT',      -- 작성 중
        'ACTIVE',     -- 활성
        'ARCHIVED'    -- 보관
    )),

    -- 조건 블록 (JSONB로 유연하게 저장)
    conditions JSONB NOT NULL DEFAULT '{}',

    -- 리밸런싱 설정
    rebalance_frequency VARCHAR(20) DEFAULT 'MONTHLY' CHECK (rebalance_frequency IN (
        'DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'NONE'
    )),

    -- 통계 (캐시)
    subscriber_count INTEGER DEFAULT 0,
    average_rating DECIMAL(3, 2) DEFAULT 0,

    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE strategies IS '투자 전략 정보';
COMMENT ON COLUMN strategies.category IS '전략 카테고리: VALUE, MOMENTUM, ASSET_ALLOCATION 등';
COMMENT ON COLUMN strategies.conditions IS '전략 조건 블록 (JSON 형식)';
COMMENT ON COLUMN strategies.is_premium IS '프리미엄 전략 여부 (유료 구독 필요)';

-- ============================================
-- 2. Strategy Conditions 스키마 설명 (JSONB)
-- ============================================
-- conditions 필드 구조:
-- {
--   "buy_conditions": [
--     { "type": "PER_FILTER", "operator": "<=", "value": 10 },
--     { "type": "PBR_FILTER", "operator": "<=", "value": 1.0 },
--     { "type": "ROE_FILTER", "operator": ">=", "value": 10 }
--   ],
--   "sell_conditions": [
--     { "type": "STOP_LOSS", "value": -10 },
--     { "type": "TAKE_PROFIT", "value": 20 }
--   ],
--   "filters": [
--     { "type": "SECTOR_FILTER", "include": ["IT", "Healthcare"] }
--   ],
--   "allocation": {
--     "type": "EQUAL_WEIGHT",
--     "max_stocks": 10,
--     "max_weight_per_stock": 20
--   }
-- }

-- ============================================
-- 3. Strategy Subscriptions Table (구독)
-- ============================================
CREATE TABLE IF NOT EXISTS strategy_subscriptions (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    strategy_id BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,

    -- 구독 상태
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN (
        'ACTIVE',     -- 활성 구독
        'PAUSED',     -- 일시 중지
        'CANCELLED'   -- 취소됨
    )),

    -- 알림 설정
    notify_signals BOOLEAN DEFAULT TRUE,
    notify_rebalance BOOLEAN DEFAULT TRUE,

    -- 구독 정보
    subscribed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP,

    -- 유니크 제약 (한 사용자가 같은 전략 중복 구독 방지)
    UNIQUE (user_id, strategy_id)
);

COMMENT ON TABLE strategy_subscriptions IS '전략 구독 관계';

-- ============================================
-- 4. Backtest Results Table (백테스트 결과)
-- ============================================
CREATE TABLE IF NOT EXISTS backtest_results (
    id BIGSERIAL PRIMARY KEY,

    -- 연결 정보
    strategy_id BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,

    -- 테스트 설정
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    initial_capital DECIMAL(15, 2) NOT NULL,
    benchmark VARCHAR(20) DEFAULT 'KOSPI',  -- KOSPI, SPY, BTC 등

    -- 성과 지표
    final_value DECIMAL(15, 2) NOT NULL,
    total_return DECIMAL(10, 4) NOT NULL,           -- 총 수익률 (%)
    cagr DECIMAL(10, 4) NOT NULL,                   -- 연평균 복리 수익률 (%)
    mdd DECIMAL(10, 4) NOT NULL,                    -- 최대 낙폭 (%)
    sharpe_ratio DECIMAL(10, 4),                    -- 샤프 비율
    sortino_ratio DECIMAL(10, 4),                   -- 소르티노 비율
    volatility DECIMAL(10, 4),                      -- 변동성 (%)
    win_rate DECIMAL(5, 2),                         -- 승률 (%)

    -- 거래 통계
    total_trades INTEGER DEFAULT 0,
    winning_trades INTEGER DEFAULT 0,
    losing_trades INTEGER DEFAULT 0,
    avg_win DECIMAL(10, 4),                         -- 평균 수익 (%)
    avg_loss DECIMAL(10, 4),                        -- 평균 손실 (%)

    -- 벤치마크 비교
    benchmark_return DECIMAL(10, 4),                -- 벤치마크 수익률 (%)
    alpha DECIMAL(10, 4),                           -- 알파 (초과 수익)
    beta DECIMAL(10, 4),                            -- 베타 (시장 민감도)

    -- 수익 곡선 데이터 (JSON)
    equity_curve JSONB,

    -- 상태
    status VARCHAR(20) DEFAULT 'COMPLETED' CHECK (status IN (
        'RUNNING',    -- 실행 중
        'COMPLETED',  -- 완료
        'FAILED'      -- 실패
    )),
    error_message TEXT,

    -- 타임스탬프
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

COMMENT ON TABLE backtest_results IS '백테스트 실행 결과';
COMMENT ON COLUMN backtest_results.cagr IS 'Compound Annual Growth Rate (연평균 복리 수익률)';
COMMENT ON COLUMN backtest_results.mdd IS 'Maximum Drawdown (최대 낙폭)';
COMMENT ON COLUMN backtest_results.sharpe_ratio IS '위험 대비 수익률 지표';
COMMENT ON COLUMN backtest_results.equity_curve IS '일별 자산 가치 시계열 데이터';

-- ============================================
-- 5. Backtest Trades Table (백테스트 거래 내역)
-- ============================================
CREATE TABLE IF NOT EXISTS backtest_trades (
    id BIGSERIAL PRIMARY KEY,

    backtest_id BIGINT NOT NULL REFERENCES backtest_results(id) ON DELETE CASCADE,

    -- 거래 정보
    trade_date DATE NOT NULL,
    ticker VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity INTEGER NOT NULL,
    price DECIMAL(15, 4) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    commission DECIMAL(10, 2) DEFAULT 0,

    -- 수익 정보 (매도 시)
    pnl DECIMAL(15, 2),                             -- 손익 금액
    pnl_percent DECIMAL(10, 4),                     -- 손익 비율 (%)
    holding_days INTEGER,                           -- 보유 기간

    -- 신호 정보
    signal_reason VARCHAR(255),                     -- 매매 사유

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE backtest_trades IS '백테스트 거래 내역';
COMMENT ON COLUMN backtest_trades.pnl IS 'Profit and Loss (손익)';

-- ============================================
-- 6. Strategy Signals Table (전략 신호 기록)
-- ============================================
CREATE TABLE IF NOT EXISTS strategy_signals (
    id BIGSERIAL PRIMARY KEY,

    strategy_id BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,

    -- 신호 정보
    signal_date DATE NOT NULL,
    signal_type VARCHAR(20) NOT NULL CHECK (signal_type IN (
        'BUY', 'SELL', 'REBALANCE', 'HOLD'
    )),
    ticker VARCHAR(20),

    -- 상세 정보
    target_weight DECIMAL(5, 2),                    -- 목표 비중 (%)
    reason TEXT,                                    -- 신호 발생 사유

    -- 메타데이터
    conditions_snapshot JSONB,                      -- 조건 충족 상세

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE strategy_signals IS '전략 매매 신호 기록';

-- ============================================
-- 7. User Tiers Table (사용자 티어/구독)
-- ============================================
CREATE TABLE IF NOT EXISTS user_tiers (
    id BIGSERIAL PRIMARY KEY,

    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,

    tier VARCHAR(20) DEFAULT 'FREE' CHECK (tier IN (
        'FREE',       -- 무료
        'PREMIUM',    -- 프리미엄 (월간)
        'PREMIUM_YEARLY'  -- 프리미엄 (연간)
    )),

    -- 구독 정보
    started_at TIMESTAMP,
    expires_at TIMESTAMP,

    -- 사용량 추적
    backtest_count_today INTEGER DEFAULT 0,
    backtest_count_reset_at DATE DEFAULT CURRENT_DATE,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE user_tiers IS '사용자 구독 티어';
COMMENT ON COLUMN user_tiers.backtest_count_today IS '오늘 백테스트 실행 횟수 (무료 사용자 제한용)';

-- ============================================
-- 8. Indexes
-- ============================================

-- strategies
CREATE INDEX idx_strategies_category ON strategies(category);
CREATE INDEX idx_strategies_owner ON strategies(owner_id);
CREATE INDEX idx_strategies_public ON strategies(is_public) WHERE is_public = TRUE;
CREATE INDEX idx_strategies_status ON strategies(status);

-- strategy_subscriptions
CREATE INDEX idx_subscriptions_user ON strategy_subscriptions(user_id);
CREATE INDEX idx_subscriptions_strategy ON strategy_subscriptions(strategy_id);
CREATE INDEX idx_subscriptions_status ON strategy_subscriptions(status);

-- backtest_results
CREATE INDEX idx_backtest_strategy ON backtest_results(strategy_id);
CREATE INDEX idx_backtest_user ON backtest_results(user_id);
CREATE INDEX idx_backtest_created ON backtest_results(created_at DESC);

-- backtest_trades
CREATE INDEX idx_backtest_trades_backtest ON backtest_trades(backtest_id);
CREATE INDEX idx_backtest_trades_date ON backtest_trades(trade_date);

-- strategy_signals
CREATE INDEX idx_signals_strategy ON strategy_signals(strategy_id);
CREATE INDEX idx_signals_date ON strategy_signals(signal_date DESC);

-- user_tiers
CREATE INDEX idx_user_tiers_tier ON user_tiers(tier);
CREATE INDEX idx_user_tiers_expires ON user_tiers(expires_at);

-- ============================================
-- 9. Initial Seed Data (기본 전략)
-- ============================================

-- 시스템 전략 1: 듀얼 모멘텀
INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency)
VALUES (
    '듀얼 모멘텀 전략',
    '미국 주식과 채권의 상대 모멘텀을 비교하여 더 강한 자산에 투자하는 전략입니다. 12개월 수익률을 기준으로 매월 리밸런싱합니다.',
    'MOMENTUM',
    TRUE,
    FALSE,
    'ACTIVE',
    '{
        "buy_conditions": [
            {"type": "MOMENTUM_12M", "operator": ">", "value": 0, "description": "12개월 수익률 양수"}
        ],
        "sell_conditions": [
            {"type": "MOMENTUM_12M", "operator": "<", "value": 0, "description": "12개월 수익률 음수 시 현금"}
        ],
        "allocation": {
            "type": "RELATIVE_MOMENTUM",
            "assets": ["SPY", "TLT", "BIL"],
            "lookback_months": 12
        }
    }',
    'MONTHLY'
);

-- 시스템 전략 2: PER+PBR 가치투자
INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency)
VALUES (
    'PER+PBR 가치투자',
    '저평가된 주식을 발굴하는 기본적인 가치투자 전략입니다. PER 10 이하, PBR 1 이하, ROE 10% 이상인 종목을 선별합니다.',
    'VALUE',
    TRUE,
    FALSE,
    'ACTIVE',
    '{
        "buy_conditions": [
            {"type": "PER_FILTER", "operator": "<=", "value": 10},
            {"type": "PBR_FILTER", "operator": "<=", "value": 1.0},
            {"type": "ROE_FILTER", "operator": ">=", "value": 10}
        ],
        "sell_conditions": [
            {"type": "STOP_LOSS", "value": -15},
            {"type": "TAKE_PROFIT", "value": 30},
            {"type": "PER_FILTER", "operator": ">", "value": 20, "description": "고평가 시 매도"}
        ],
        "allocation": {
            "type": "EQUAL_WEIGHT",
            "max_stocks": 10,
            "max_weight_per_stock": 15
        }
    }',
    'QUARTERLY'
);

-- 시스템 전략 3: 영구 포트폴리오
INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency)
VALUES (
    '영구 포트폴리오 (올웨더)',
    '해리 브라운의 영구 포트폴리오를 기반으로 한 자산배분 전략입니다. 주식, 채권, 금, 현금에 각각 25%씩 배분합니다.',
    'ASSET_ALLOCATION',
    TRUE,
    FALSE,
    'ACTIVE',
    '{
        "buy_conditions": [],
        "sell_conditions": [],
        "allocation": {
            "type": "FIXED_WEIGHT",
            "assets": {
                "SPY": 25,
                "TLT": 25,
                "GLD": 25,
                "BIL": 25
            },
            "rebalance_threshold": 5
        }
    }',
    'QUARTERLY'
);

-- 시스템 전략 4: RSI 과매도 전략 (프리미엄)
INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency)
VALUES (
    'RSI 과매도 반등 전략',
    'RSI가 30 이하로 과매도 구간에 진입한 종목을 매수하고, RSI 70 이상에서 매도하는 역추세 전략입니다.',
    'MOMENTUM',
    TRUE,
    TRUE,
    'ACTIVE',
    '{
        "buy_conditions": [
            {"type": "RSI_FILTER", "operator": "<=", "value": 30, "period": 14},
            {"type": "SMA_ABOVE", "short": 20, "long": 200, "description": "장기 상승 추세"}
        ],
        "sell_conditions": [
            {"type": "RSI_FILTER", "operator": ">=", "value": 70, "period": 14},
            {"type": "STOP_LOSS", "value": -10}
        ],
        "allocation": {
            "type": "EQUAL_WEIGHT",
            "max_stocks": 5,
            "max_weight_per_stock": 20
        }
    }',
    'NONE'
);

-- 시스템 전략 5: 배당 성장주 (프리미엄)
INSERT INTO strategies (name, description, category, is_public, is_premium, status, conditions, rebalance_frequency)
VALUES (
    '배당 성장주 전략',
    '배당수익률 3% 이상이면서 최근 5년간 배당금이 꾸준히 증가한 종목에 투자하는 전략입니다.',
    'VALUE',
    TRUE,
    TRUE,
    'ACTIVE',
    '{
        "buy_conditions": [
            {"type": "DIVIDEND_YIELD", "operator": ">=", "value": 3},
            {"type": "DIVIDEND_GROWTH_5Y", "operator": ">", "value": 0},
            {"type": "PAYOUT_RATIO", "operator": "<=", "value": 70}
        ],
        "sell_conditions": [
            {"type": "DIVIDEND_CUT", "description": "배당 감소 시"},
            {"type": "STOP_LOSS", "value": -20}
        ],
        "allocation": {
            "type": "DIVIDEND_WEIGHT",
            "max_stocks": 20,
            "max_weight_per_stock": 10
        }
    }',
    'YEARLY'
);

-- ============================================
-- 10. Update Trigger Function
-- ============================================
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply triggers
CREATE TRIGGER update_strategies_timestamp
    BEFORE UPDATE ON strategies
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER update_user_tiers_timestamp
    BEFORE UPDATE ON user_tiers
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();
