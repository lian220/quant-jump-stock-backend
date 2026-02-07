-- V27: strategies 테이블에 stock_selection_type, investment_philosophy 컬럼 추가
-- SCRUM-269: 전략 종목선정 모델 이원화 (SCREENING / PORTFOLIO)

-- 종목선정 방식 컬럼 추가
ALTER TABLE strategies ADD COLUMN IF NOT EXISTS stock_selection_type VARCHAR(20) NOT NULL DEFAULT 'SCREENING';

-- 투자 철학 컬럼 추가 (AI 매매 판단 시 참고)
ALTER TABLE strategies ADD COLUMN IF NOT EXISTS investment_philosophy TEXT;

-- 제약조건 추가
ALTER TABLE strategies ADD CONSTRAINT check_stock_selection_type CHECK (
    stock_selection_type IN ('SCREENING', 'PORTFOLIO')
);

COMMENT ON COLUMN strategies.stock_selection_type IS '종목선정 방식: SCREENING(조건 필터링) | PORTFOLIO(고정 포트폴리오)';
COMMENT ON COLUMN strategies.investment_philosophy IS '투자 철학 (AI 매매 판단 시 참고)';

-- 기존 시드 전략 중 포트폴리오 기반 전략을 PORTFOLIO로 업데이트
-- ASSET_ALLOCATION 카테고리 전략들은 고정 포트폴리오 기반
UPDATE strategies s
SET stock_selection_type = 'PORTFOLIO'
FROM strategy_categories sc
WHERE s.category_id = sc.id
  AND sc.code = 'ASSET_ALLOCATION';
