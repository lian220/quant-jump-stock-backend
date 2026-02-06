-- V21__Remove_Equity_Curve_From_Checkpoints.sql
-- 체크포인트에서 equity_curve 컬럼 제거 (중복 저장 방지)
-- equity_curve는 backtest_results.equity_curve에만 저장됨

-- equity_curve 컬럼 제거
ALTER TABLE backtest_checkpoints DROP COLUMN IF EXISTS equity_curve;

-- 코멘트 업데이트
COMMENT ON TABLE backtest_checkpoints IS '백테스트 증분 실행을 위한 포트폴리오 상태 체크포인트 (equity_curve는 backtest_results에서 조회)';
