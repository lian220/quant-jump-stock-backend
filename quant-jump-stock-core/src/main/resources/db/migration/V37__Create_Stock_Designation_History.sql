-- V37: 종목 지정 이력 테이블 생성 (통합 과정에서 누락된 테이블 복구)

CREATE TABLE IF NOT EXISTS stock_designation_history (
    id BIGSERIAL PRIMARY KEY,
    stock_id BIGINT NOT NULL REFERENCES stocks(id) ON DELETE CASCADE,
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    reason TEXT,
    changed_by BIGINT,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sdh_stock_id ON stock_designation_history(stock_id);

COMMENT ON TABLE stock_designation_history IS '종목 투자 지정 상태 변경 이력';
