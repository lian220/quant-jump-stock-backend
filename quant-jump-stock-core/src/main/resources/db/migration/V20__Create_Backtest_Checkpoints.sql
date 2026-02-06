-- V10__Create_Backtest_Checkpoints.sql
-- 백테스트 체크포인트 테이블 (증분 업데이트 지원)

CREATE TABLE backtest_checkpoints (
    id BIGSERIAL PRIMARY KEY,
    backtest_id BIGINT NOT NULL REFERENCES backtest_results(id) ON DELETE CASCADE,
    checkpoint_date DATE NOT NULL,

    -- 포트폴리오 상태
    cash NUMERIC(15, 2) NOT NULL,
    high_watermark NUMERIC(15, 2) NOT NULL,

    -- 보유 포지션 (JSON)
    positions JSONB NOT NULL DEFAULT '[]',

    -- 수익 곡선 (마지막 체크포인트까지)
    equity_curve JSONB NOT NULL DEFAULT '[]',

    -- 거래 내역 (마지막 체크포인트까지의 거래 수)
    trade_count INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- 동일 backtest에 대해 날짜별로 하나의 체크포인트만 허용
    CONSTRAINT uq_backtest_checkpoint_date UNIQUE (backtest_id, checkpoint_date)
);

COMMENT ON TABLE backtest_checkpoints IS '백테스트 증분 실행을 위한 포트폴리오 상태 체크포인트';
COMMENT ON COLUMN backtest_checkpoints.positions IS '보유 포지션 배열: [{ticker, quantity, entry_price, entry_date}]';
COMMENT ON COLUMN backtest_checkpoints.equity_curve IS '수익 곡선: [{date, equity, drawdown}]';

-- 인덱스
CREATE INDEX idx_backtest_checkpoints_backtest_id ON backtest_checkpoints(backtest_id);
CREATE INDEX idx_backtest_checkpoints_date ON backtest_checkpoints(checkpoint_date DESC);

-- backtest_results에 증분 모드 컬럼 추가
ALTER TABLE backtest_results
ADD COLUMN IF NOT EXISTS is_incremental BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS last_checkpoint_date DATE;

COMMENT ON COLUMN backtest_results.is_incremental IS '증분 업데이트 모드 여부';
COMMENT ON COLUMN backtest_results.last_checkpoint_date IS '마지막 체크포인트 날짜';
