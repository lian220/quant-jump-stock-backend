-- V44: 리스크 관리 시스템 - 고급 지표 및 거래 비용 컬럼 추가 (SCRUM-330)

-- backtest_results 테이블: 고급 성과 지표
ALTER TABLE backtest_results ADD COLUMN profit_factor DECIMAL(10,4);
ALTER TABLE backtest_results ADD COLUMN expectancy DECIMAL(10,4);
ALTER TABLE backtest_results ADD COLUMN kelly_percentage DECIMAL(10,4);
ALTER TABLE backtest_results ADD COLUMN risk_reward_ratio DECIMAL(10,4);
ALTER TABLE backtest_results ADD COLUMN calmar_ratio DECIMAL(10,4);

-- backtest_results 테이블: 거래 비용 집계
ALTER TABLE backtest_results ADD COLUMN total_commission DECIMAL(15,2);
ALTER TABLE backtest_results ADD COLUMN total_slippage DECIMAL(15,2);
ALTER TABLE backtest_results ADD COLUMN total_tax DECIMAL(15,2);
ALTER TABLE backtest_results ADD COLUMN net_profit_after_costs DECIMAL(15,2);

-- backtest_results 테이블: 거래 통계
ALTER TABLE backtest_results ADD COLUMN avg_holding_period DECIMAL(10,2);
ALTER TABLE backtest_results ADD COLUMN best_trade DECIMAL(10,4);
ALTER TABLE backtest_results ADD COLUMN worst_trade DECIMAL(10,4);
ALTER TABLE backtest_results ADD COLUMN max_consecutive_wins INT;
ALTER TABLE backtest_results ADD COLUMN max_consecutive_losses INT;
ALTER TABLE backtest_results ADD COLUMN stop_loss_count INT DEFAULT 0;
ALTER TABLE backtest_results ADD COLUMN take_profit_count INT DEFAULT 0;
ALTER TABLE backtest_results ADD COLUMN trailing_stop_count INT DEFAULT 0;

-- backtest_results 테이블: 리스크 설정 기록 (JSONB)
ALTER TABLE backtest_results ADD COLUMN risk_settings JSONB DEFAULT '{}';
ALTER TABLE backtest_results ADD COLUMN position_sizing JSONB DEFAULT '{}';
ALTER TABLE backtest_results ADD COLUMN trading_costs JSONB DEFAULT '{}';

-- backtest_trades 테이블: 청산 사유 및 비용 상세
ALTER TABLE backtest_trades ADD COLUMN exit_reason VARCHAR(20);
ALTER TABLE backtest_trades ADD COLUMN slippage_amount DECIMAL(10,2);
ALTER TABLE backtest_trades ADD COLUMN tax_amount DECIMAL(10,2);
ALTER TABLE backtest_trades ADD COLUMN execution_price DECIMAL(15,4);

-- ============================================================================
-- 컬럼 코멘트
-- ============================================================================

-- backtest_results: 고급 성과 지표
COMMENT ON COLUMN backtest_results.profit_factor IS '손익비 (총 이익 / 총 손실)';
COMMENT ON COLUMN backtest_results.expectancy IS '기대값 (거래당 평균 수익률 %)';
COMMENT ON COLUMN backtest_results.kelly_percentage IS '켈리 비율 (최적 베팅 비율 %)';
COMMENT ON COLUMN backtest_results.risk_reward_ratio IS '리스크-리워드 비율 (평균 이익 / 평균 손실)';
COMMENT ON COLUMN backtest_results.calmar_ratio IS 'Calmar Ratio (CAGR / MDD)';

-- backtest_results: 거래 비용 집계
COMMENT ON COLUMN backtest_results.total_commission IS '총 수수료 합계 (원)';
COMMENT ON COLUMN backtest_results.total_slippage IS '총 슬리피지 합계 (원)';
COMMENT ON COLUMN backtest_results.total_tax IS '총 세금 합계 (원, 매도 시 발생)';
COMMENT ON COLUMN backtest_results.net_profit_after_costs IS '비용 차감 후 순이익 (원)';

-- backtest_results: 거래 통계
COMMENT ON COLUMN backtest_results.avg_holding_period IS '평균 보유 기간 (일)';
COMMENT ON COLUMN backtest_results.best_trade IS '최고 수익 거래 (%)';
COMMENT ON COLUMN backtest_results.worst_trade IS '최대 손실 거래 (%)';
COMMENT ON COLUMN backtest_results.max_consecutive_wins IS '최대 연속 승리 횟수';
COMMENT ON COLUMN backtest_results.max_consecutive_losses IS '최대 연속 패배 횟수';
COMMENT ON COLUMN backtest_results.stop_loss_count IS '손절로 청산된 거래 수';
COMMENT ON COLUMN backtest_results.take_profit_count IS '익절로 청산된 거래 수';
COMMENT ON COLUMN backtest_results.trailing_stop_count IS '트레일링 스탑으로 청산된 거래 수';

-- backtest_results: 리스크 설정 기록
COMMENT ON COLUMN backtest_results.risk_settings IS '리스크 설정 JSON (stopLoss, takeProfit, trailingStop)';
COMMENT ON COLUMN backtest_results.position_sizing IS '포지션 사이징 설정 JSON (method, maxPositionPct 등)';
COMMENT ON COLUMN backtest_results.trading_costs IS '거래 비용 설정 JSON (commission, tax, slippage)';

-- backtest_trades: 청산 사유 및 비용 상세
COMMENT ON COLUMN backtest_trades.exit_reason IS '청산 사유 (SIGNAL, STOP_LOSS, TAKE_PROFIT, TRAILING_STOP, REBALANCE, FORCED, EXPIRED)';
COMMENT ON COLUMN backtest_trades.slippage_amount IS '슬리피지 금액 (원)';
COMMENT ON COLUMN backtest_trades.tax_amount IS '세금 금액 (원)';
COMMENT ON COLUMN backtest_trades.execution_price IS '실제 체결가 (슬리피지 반영)';

-- ============================================================================
-- 기존 테이블 컬럼 코멘트 보정
-- ============================================================================

-- strategies.conditions: 매수 조건으로 역할이 구체화됨 (SCREENING 모드에서 사용)
COMMENT ON COLUMN strategies.conditions IS '매수 조건 JSONB (SCREENING 모드: 유니버스 필터링 조건, 예: 저PER, 골든크로스 등. PORTFOLIO 모드에서는 미사용)';
