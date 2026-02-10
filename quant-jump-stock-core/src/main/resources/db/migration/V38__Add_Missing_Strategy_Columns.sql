-- V38: strategies 테이블 누락 컬럼 추가 (통합 과정에서 누락)

ALTER TABLE strategies ADD COLUMN IF NOT EXISTS stock_selection_type VARCHAR(20) NOT NULL DEFAULT 'SCREENING';
ALTER TABLE strategies ADD COLUMN IF NOT EXISTS investment_philosophy TEXT;
ALTER TABLE strategies ADD COLUMN IF NOT EXISTS risk_settings JSONB DEFAULT '{}'::jsonb;
ALTER TABLE strategies ADD COLUMN IF NOT EXISTS position_sizing JSONB DEFAULT '{}'::jsonb;
ALTER TABLE strategies ADD COLUMN IF NOT EXISTS trading_costs JSONB DEFAULT '{}'::jsonb;

-- status CHECK 제약 업데이트 (Entity에 새 상태 추가됨)
ALTER TABLE strategies DROP CONSTRAINT IF EXISTS check_strategy_status;
ALTER TABLE strategies ADD CONSTRAINT check_strategy_status
    CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'PUBLISHED', 'REJECTED', 'ACTIVE', 'ARCHIVED'));

COMMENT ON COLUMN strategies.stock_selection_type IS '종목 선택 방식: SCREENING(조건 필터링) / PORTFOLIO(고정 포트폴리오)';
COMMENT ON COLUMN strategies.investment_philosophy IS '투자 철학 설명';
COMMENT ON COLUMN strategies.risk_settings IS '리스크 관리 설정 (JSON)';
COMMENT ON COLUMN strategies.position_sizing IS '포지션 사이징 설정 (JSON)';
COMMENT ON COLUMN strategies.trading_costs IS '거래 비용 설정 (JSON)';
