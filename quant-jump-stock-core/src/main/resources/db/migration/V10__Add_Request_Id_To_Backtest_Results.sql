-- V10: backtest_results 테이블에 request_id 컬럼 추가
-- 클라이언트가 백테스트 요청 시 받은 UUID로 결과를 조회할 수 있도록 함

ALTER TABLE backtest_results ADD COLUMN request_id VARCHAR(36);
CREATE UNIQUE INDEX idx_backtest_results_request_id ON backtest_results(request_id) WHERE request_id IS NOT NULL;
