-- V39: 백테스트 결과 NUMERIC 컬럼 크기 확장
-- 기존 NUMERIC(10,4)는 최대 ±999999.9999까지만 저장 가능
-- initial_capital이 10,000,000일 때 total_return 등이 overflow 발생

ALTER TABLE backtest_results
    ALTER COLUMN total_return TYPE NUMERIC(20, 4),
    ALTER COLUMN cagr TYPE NUMERIC(20, 4),
    ALTER COLUMN mdd TYPE NUMERIC(20, 4),
    ALTER COLUMN sharpe_ratio TYPE NUMERIC(20, 4),
    ALTER COLUMN sortino_ratio TYPE NUMERIC(20, 4),
    ALTER COLUMN volatility TYPE NUMERIC(20, 4),
    ALTER COLUMN avg_win TYPE NUMERIC(20, 4),
    ALTER COLUMN avg_loss TYPE NUMERIC(20, 4),
    ALTER COLUMN benchmark_return TYPE NUMERIC(20, 4),
    ALTER COLUMN alpha TYPE NUMERIC(20, 4),
    ALTER COLUMN beta TYPE NUMERIC(20, 4);

ALTER TABLE backtest_trades
    ALTER COLUMN pnl_percent TYPE NUMERIC(20, 4);
