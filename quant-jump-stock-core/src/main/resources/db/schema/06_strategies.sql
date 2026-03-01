-- ============================================
-- 06_strategies.sql
-- Sources: V7 + V49 (universe type columns for strategies)
-- ============================================

CREATE TABLE strategy_categories (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    icon VARCHAR(50),
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_strategy_categories_is_active ON strategy_categories(is_active);

COMMENT ON TABLE strategy_categories IS '전략 카테고리 마스터 테이블';
COMMENT ON COLUMN strategy_categories.id IS '카테고리 고유 ID (PK)';
COMMENT ON COLUMN strategy_categories.code IS '카테고리 코드 (영문)';
COMMENT ON COLUMN strategy_categories.name IS '카테고리 이름 (한글)';
COMMENT ON COLUMN strategy_categories.icon IS '카테고리 아이콘 (optional)';
COMMENT ON COLUMN strategy_categories.sort_order IS '정렬 순서';
COMMENT ON COLUMN strategy_categories.is_active IS '활성 상태';
COMMENT ON COLUMN strategy_categories.created_at IS '카테고리 생성 일시';
COMMENT ON COLUMN strategy_categories.updated_at IS '카테고리 수정 일시';

-- ============================================

CREATE TABLE strategies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,

    category_id BIGINT NOT NULL,
    strategy_id VARCHAR(50) UNIQUE,
    version VARCHAR(20) DEFAULT '1.0',

    owner_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    is_public BOOLEAN DEFAULT FALSE,
    is_premium BOOLEAN DEFAULT FALSE,
    is_system BOOLEAN DEFAULT FALSE,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    conditions JSONB NOT NULL DEFAULT '{}'::jsonb,

    take_profit NUMERIC(10, 4),
    stop_loss NUMERIC(10, 4),
    rebalance_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',

    subscriber_count INTEGER DEFAULT 0,
    average_rating NUMERIC(3, 2) DEFAULT 0.00,

    risk_settings JSONB DEFAULT '{}'::jsonb,
    position_sizing JSONB DEFAULT '{}'::jsonb,
    trading_costs JSONB DEFAULT '{}'::jsonb,

    stock_selection_type VARCHAR(20) NOT NULL DEFAULT 'SCREENING',
    investment_philosophy TEXT,

    -- V49: 유니버스 타입
    recommended_universe_type VARCHAR(20) DEFAULT 'MARKET',
    supported_universe_types JSONB DEFAULT '["MARKET","PORTFOLIO","FIXED"]',
    canonical_backtest_id BIGINT,   -- FK added after backtest_results created (see 07_backtest.sql)

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT check_strategy_status CHECK (
        status IN ('ACTIVE', 'INACTIVE', 'DRAFT', 'ARCHIVED', 'PENDING_REVIEW', 'APPROVED', 'PUBLISHED', 'REJECTED')
    ),
    CONSTRAINT check_rebalance_frequency CHECK (
        rebalance_frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY', 'NONE')
    ),
    CONSTRAINT check_stock_selection_type CHECK (
        stock_selection_type IN ('SCREENING', 'PORTFOLIO')
    ),
    CONSTRAINT fk_strategies_category FOREIGN KEY (category_id) REFERENCES strategy_categories(id)
);

CREATE INDEX idx_strategies_owner_id ON strategies(owner_id);
CREATE INDEX idx_strategies_status ON strategies(status);
CREATE INDEX idx_strategies_category_id ON strategies(category_id);
CREATE INDEX idx_strategies_is_public ON strategies(is_public);
CREATE INDEX idx_strategies_created_at ON strategies(created_at);
-- UNIQUE index for strategy_id covered by inline UNIQUE constraint above
CREATE INDEX IF NOT EXISTS idx_strategies_canonical_backtest
    ON strategies(canonical_backtest_id) WHERE canonical_backtest_id IS NOT NULL;

COMMENT ON TABLE strategies IS 'Trading strategy definitions with conditions and rebalancing rules';
COMMENT ON COLUMN strategies.id IS '전략 고유 ID (PK)';
COMMENT ON COLUMN strategies.name IS '전략 이름 (표시용)';
COMMENT ON COLUMN strategies.description IS '전략 상세 설명';
COMMENT ON COLUMN strategies.strategy_id IS '전략 고유 식별자 (영문, snake_case). Data Engine DSL에서 참조하는 ID';
COMMENT ON COLUMN strategies.version IS '전략 버전 (semver 형식: 1.0, 1.1 등)';
COMMENT ON COLUMN strategies.owner_id IS '전략 소유자 사용자 ID (NULL: 시스템 전략)';
COMMENT ON COLUMN strategies.is_public IS '공개 여부 (true: 마켓플레이스 노출)';
COMMENT ON COLUMN strategies.is_premium IS '프리미엄 전략 여부 (true: 유료)';
COMMENT ON COLUMN strategies.status IS '전략 상태 (DRAFT, ACTIVE, ARCHIVED)';
COMMENT ON COLUMN strategies.conditions IS '매수 조건 JSONB (SCREENING 모드: 유니버스 필터링 조건, 예: 저PER, 골든크로스 등. PORTFOLIO 모드에서는 미사용)';
COMMENT ON COLUMN strategies.rebalance_frequency IS '리밸런싱 주기 (DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY, NONE)';
COMMENT ON COLUMN strategies.risk_settings IS '리스크 관리 설정 (stop_loss, take_profit, trailing_stop, time_stop)';
COMMENT ON COLUMN strategies.position_sizing IS '포지션 사이징 설정 (method, max_position_pct, max_positions, volatility_scaling, lookback_days, target_volatility, kelly_fraction)';
COMMENT ON COLUMN strategies.trading_costs IS '거래 비용 설정 (commission, tax, slippage)';
COMMENT ON COLUMN strategies.stock_selection_type IS '종목선정 방식: SCREENING(조건 필터링) | PORTFOLIO(고정 포트폴리오)';
COMMENT ON COLUMN strategies.investment_philosophy IS '투자 철학 (AI 매매 판단 시 참고)';
COMMENT ON COLUMN strategies.subscriber_count IS '구독자 수';
COMMENT ON COLUMN strategies.average_rating IS '평균 평점 (0.00~5.00)';
COMMENT ON COLUMN strategies.created_at IS '생성 일시';
COMMENT ON COLUMN strategies.updated_at IS '수정 일시';

-- ============================================

CREATE TABLE strategy_signals (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    signal_date DATE NOT NULL,
    signal_type VARCHAR(20) NOT NULL,
    ticker VARCHAR(20),
    target_weight NUMERIC(5, 2),
    reason TEXT,
    conditions_snapshot JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT check_signal_type CHECK (
        signal_type IN ('BUY', 'SELL', 'REBALANCE', 'HOLD')
    )
);

CREATE INDEX idx_strategy_signals_strategy_id ON strategy_signals(strategy_id);
CREATE INDEX idx_strategy_signals_signal_date ON strategy_signals(signal_date);
CREATE INDEX idx_strategy_signals_signal_type ON strategy_signals(signal_type);
CREATE INDEX idx_strategy_signals_ticker ON strategy_signals(ticker);

COMMENT ON TABLE strategy_signals IS 'Trading signals generated by strategies';
COMMENT ON COLUMN strategy_signals.id IS '전략 신호 고유 ID (PK)';
COMMENT ON COLUMN strategy_signals.strategy_id IS '전략 ID (FK)';
COMMENT ON COLUMN strategy_signals.signal_date IS '신호 발생일';
COMMENT ON COLUMN strategy_signals.signal_type IS '신호 유형 (BUY, SELL, REBALANCE, HOLD)';
COMMENT ON COLUMN strategy_signals.ticker IS '대상 종목 티커';
COMMENT ON COLUMN strategy_signals.target_weight IS '목표 비중 (%)';
COMMENT ON COLUMN strategy_signals.reason IS '신호 발생 사유';
COMMENT ON COLUMN strategy_signals.conditions_snapshot IS '신호 발생 시점 조건 스냅샷 (JSONB)';
COMMENT ON COLUMN strategy_signals.created_at IS '신호 생성 일시';

-- ============================================
-- strategy_subscriptions: portfolio_id FK는 09_portfolio.sql에서 추가
-- ============================================

CREATE TABLE strategy_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    strategy_id BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
    portfolio_id BIGINT,   -- FK added in 09_portfolio.sql after user_portfolios created
    preferred_universe_type VARCHAR(20) DEFAULT 'MARKET' NOT NULL,  -- V51
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notify_signals BOOLEAN DEFAULT TRUE,
    notify_rebalance BOOLEAN DEFAULT TRUE,
    subscribed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    cancelled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_strategy_subscriptions_user_strategy UNIQUE (user_id, strategy_id),
    CONSTRAINT check_subscription_status CHECK (
        status IN ('ACTIVE', 'PAUSED', 'CANCELLED')
    )
);

CREATE INDEX idx_strategy_subscriptions_user_id ON strategy_subscriptions(user_id);
CREATE INDEX idx_strategy_subscriptions_strategy_id ON strategy_subscriptions(strategy_id);
CREATE INDEX idx_strategy_subscriptions_status ON strategy_subscriptions(status);

COMMENT ON TABLE strategy_subscriptions IS 'User subscriptions to trading strategies';
COMMENT ON COLUMN strategy_subscriptions.id IS '구독 고유 ID (PK)';
COMMENT ON COLUMN strategy_subscriptions.user_id IS '구독자 사용자 ID (FK)';
COMMENT ON COLUMN strategy_subscriptions.strategy_id IS '구독 전략 ID (FK)';
COMMENT ON COLUMN strategy_subscriptions.portfolio_id IS '구독 시 생성된 사용자 포트폴리오 ID';
COMMENT ON COLUMN strategy_subscriptions.preferred_universe_type IS '구독 시 선택한 Universe 타입';
COMMENT ON COLUMN strategy_subscriptions.status IS '구독 상태 (ACTIVE, PAUSED, CANCELLED)';
COMMENT ON COLUMN strategy_subscriptions.notify_signals IS '매매 신호 알림 수신 여부';
COMMENT ON COLUMN strategy_subscriptions.notify_rebalance IS '리밸런싱 알림 수신 여부';
COMMENT ON COLUMN strategy_subscriptions.subscribed_at IS '구독 시작 일시';
COMMENT ON COLUMN strategy_subscriptions.cancelled_at IS '구독 취소 일시';
COMMENT ON COLUMN strategy_subscriptions.created_at IS '레코드 생성 일시';
