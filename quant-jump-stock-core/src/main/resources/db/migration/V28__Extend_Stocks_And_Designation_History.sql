-- V28: stocks 테이블 확장 + stock_designation_history 테이블 생성
-- SCRUM-317/318: 종목 마스터 관리 - 시장, 지정상태, 지정이력

-- stocks 테이블에 시장 구분 컬럼 추가
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS market VARCHAR(10) DEFAULT 'KR';

-- stocks 테이블에 지정 상태 관련 컬럼 추가
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS designation_status VARCHAR(20) DEFAULT 'NORMAL';
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS designation_reason TEXT;
ALTER TABLE stocks ADD COLUMN IF NOT EXISTS designated_at TIMESTAMP;

-- 제약조건 추가
ALTER TABLE stocks ADD CONSTRAINT check_market CHECK (
    market IN ('US', 'KR', 'CRYPTO')
);

ALTER TABLE stocks ADD CONSTRAINT check_designation_status CHECK (
    designation_status IN ('NORMAL', 'CAUTION', 'WARNING', 'DANGER', 'DELISTED')
);

-- 종목 지정 상태 변경 이력 테이블
CREATE TABLE IF NOT EXISTS stock_designation_history (
    id BIGSERIAL PRIMARY KEY,
    stock_id BIGINT NOT NULL REFERENCES stocks(id),
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    reason TEXT,
    changed_by BIGINT REFERENCES users(id),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sdh_stock_id ON stock_designation_history(stock_id);
CREATE INDEX idx_sdh_changed_at ON stock_designation_history(changed_at);

-- 기존 미국 주식 종목들의 market을 US로 업데이트
UPDATE stocks SET market = 'US' WHERE exchange IN ('NYSE', 'NASDAQ', 'AMEX');

COMMENT ON COLUMN stocks.market IS '시장 구분: US(미국) | KR(한국) | CRYPTO(암호화폐)';
COMMENT ON COLUMN stocks.designation_status IS '지정 상태: NORMAL | CAUTION | WARNING | DANGER | DELISTED';
COMMENT ON COLUMN stocks.designation_reason IS '지정 사유';
COMMENT ON COLUMN stocks.designated_at IS '지정일시';
COMMENT ON TABLE stock_designation_history IS '종목 지정 상태 변경 이력';
