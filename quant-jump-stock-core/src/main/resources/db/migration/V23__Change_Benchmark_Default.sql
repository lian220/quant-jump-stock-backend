-- V23: 벤치마크 기본값 KOSPI → SPY로 변경
-- 시스템에 KOSPI 데이터가 없으므로, 실제 사용 가능한 SPY로 변경

-- 기본값 변경
ALTER TABLE backtest_results ALTER COLUMN benchmark SET DEFAULT 'SPY';

-- 기존 KOSPI 데이터를 SPY로 마이그레이션
UPDATE backtest_results SET benchmark = 'SPY' WHERE benchmark = 'KOSPI';
