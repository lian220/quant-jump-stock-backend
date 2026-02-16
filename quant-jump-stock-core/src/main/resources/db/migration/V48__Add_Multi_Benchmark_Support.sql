-- V48: 다중 벤치마크 지원 (SCRUM-337)
-- 기존 단일 benchmark(VARCHAR) → 다중 benchmarks(JSONB) 확장
-- 하위호환: 기존 benchmark 컬럼 유지, benchmarks 컬럼 추가

-- 1. benchmarks 컬럼 추가 (JSONB 배열, 최대 3개)
ALTER TABLE backtest_results
    ADD COLUMN IF NOT EXISTS benchmarks JSONB DEFAULT '["SPY"]';

-- 2. 기존 데이터 마이그레이션: benchmark → benchmarks
UPDATE backtest_results
SET benchmarks = jsonb_build_array(COALESCE(benchmark, 'SPY'))
WHERE benchmarks IS NULL OR benchmarks = '["SPY"]';

COMMENT ON COLUMN backtest_results.benchmarks IS '다중 벤치마크 티커 리스트 (최대 3개). 예: ["^GSPC", "^KS11", "STRATEGY:105"]';
