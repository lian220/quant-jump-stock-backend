-- V7: 전략 관련 테이블 통합 생성
-- 원본: V9, V12, V17, V18, V24, V25, V26, V27, V31 마이그레이션 통합
-- 모든 ALTER TABLE 변경사항이 최종 상태로 반영된 CREATE TABLE

-- =====================================================================
-- 1. Strategy Categories Table (전략 카테고리 마스터)
-- =====================================================================
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

COMMENT ON TABLE strategy_categories IS '전략 카테고리 마스터 테이블';
COMMENT ON COLUMN strategy_categories.code IS '카테고리 코드 (영문)';
COMMENT ON COLUMN strategy_categories.name IS '카테고리 이름 (한글)';
COMMENT ON COLUMN strategy_categories.icon IS '카테고리 아이콘 (optional)';
COMMENT ON COLUMN strategy_categories.sort_order IS '정렬 순서';

CREATE INDEX idx_strategy_categories_code ON strategy_categories(code);
CREATE INDEX idx_strategy_categories_is_active ON strategy_categories(is_active);

-- 기본 카테고리 시드 데이터
INSERT INTO strategy_categories (code, name, description, icon, sort_order) VALUES
    ('VALUE', '가치투자', '저PER, 저PBR 등 가치 지표 기반 투자 전략', 'chart-bar', 1),
    ('MOMENTUM', '모멘텀', '가격 추세 및 모멘텀 기반 투자 전략', 'trending-up', 2),
    ('ASSET_ALLOCATION', '자산배분', '다양한 자산군에 분산 투자하는 전략', 'pie-chart', 3),
    ('QUANT_COMPOSITE', '퀀트 복합', '여러 팩터를 복합적으로 활용하는 퀀트 전략', 'calculator', 4),
    ('SEASONAL', '시즌널', '계절성 및 시기별 패턴 기반 투자 전략', 'calendar', 5),
    ('CUSTOM', '사용자 정의', '사용자가 직접 정의한 맞춤형 전략', 'settings', 6),
    ('ML_PREDICTION', 'AI 예측', 'AI/ML 기반 주가 예측 전략', 'brain', 7);

-- =====================================================================
-- 2. Strategies Table (전략 정의)
-- =====================================================================
CREATE TABLE strategies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,

    -- 카테고리 (V17: category_id FK로 전환)
    category_id BIGINT NOT NULL REFERENCES strategy_categories(id),

    -- 전략 식별 (V18: DSL 형식)
    strategy_id VARCHAR(50),
    version VARCHAR(20) DEFAULT '1.0',

    -- 소유/공개 설정
    owner_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    is_public BOOLEAN DEFAULT FALSE,
    is_premium BOOLEAN DEFAULT FALSE,
    is_system BOOLEAN DEFAULT FALSE,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,

    -- 상태 (V31: 확장된 상태값)
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    -- 전략 조건 (JSONB)
    conditions JSONB NOT NULL DEFAULT '{}'::jsonb,

    -- 매매 설정
    take_profit NUMERIC(10, 4),
    stop_loss NUMERIC(10, 4),
    rebalance_frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',

    -- 통계
    subscriber_count INTEGER DEFAULT 0,
    average_rating NUMERIC(3, 2) DEFAULT 0.00,

    -- 리스크/포지션/비용 설정 (V24, V25, V26)
    risk_settings JSONB DEFAULT '{}'::jsonb,
    position_sizing JSONB DEFAULT '{}'::jsonb,
    trading_costs JSONB DEFAULT '{}'::jsonb,

    -- 종목선정 방식 (V27)
    stock_selection_type VARCHAR(20) NOT NULL DEFAULT 'SCREENING',
    investment_philosophy TEXT,

    -- 타임스탬프
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- 제약조건
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

-- 인덱스
CREATE INDEX idx_strategies_owner_id ON strategies(owner_id);
CREATE INDEX idx_strategies_status ON strategies(status);
CREATE INDEX idx_strategies_category_id ON strategies(category_id);
CREATE INDEX idx_strategies_is_public ON strategies(is_public);
CREATE INDEX idx_strategies_created_at ON strategies(created_at);

-- strategy_id UNIQUE 제약 (nullable이므로 partial index)
CREATE UNIQUE INDEX idx_strategies_strategy_id_unique
    ON strategies(strategy_id) WHERE strategy_id IS NOT NULL;

-- 코멘트
COMMENT ON TABLE strategies IS 'Trading strategy definitions with conditions and rebalancing rules';
COMMENT ON COLUMN strategies.strategy_id IS '전략 고유 식별자 (영문, snake_case). Data Engine DSL에서 참조하는 ID';
COMMENT ON COLUMN strategies.version IS '전략 버전 (semver 형식: 1.0, 1.1 등)';
COMMENT ON COLUMN strategies.conditions IS 'JSON configuration for strategy including ML model parameters, features, and training config';
COMMENT ON COLUMN strategies.risk_settings IS '리스크 관리 설정 (stop_loss, take_profit, trailing_stop, time_stop)';
COMMENT ON COLUMN strategies.position_sizing IS '포지션 사이징 설정 (method, max_position_pct, max_positions, volatility_scaling, lookback_days, target_volatility, kelly_fraction)';
COMMENT ON COLUMN strategies.trading_costs IS '거래 비용 설정 (commission, tax, slippage)';
COMMENT ON COLUMN strategies.stock_selection_type IS '종목선정 방식: SCREENING(조건 필터링) | PORTFOLIO(고정 포트폴리오)';
COMMENT ON COLUMN strategies.investment_philosophy IS '투자 철학 (AI 매매 판단 시 참고)';
