-- stocks 테이블에 market, designation 관련 컬럼 추가
-- 프론트엔드 종목 목록/상세 API 지원을 위한 스키마 확장

ALTER TABLE stocks ADD COLUMN market VARCHAR(10) NOT NULL DEFAULT 'US';
ALTER TABLE stocks ADD COLUMN designation_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE stocks ADD COLUMN designation_reason TEXT;
ALTER TABLE stocks ADD COLUMN designated_at TIMESTAMP;

-- 인덱스 생성
CREATE INDEX idx_stocks_market ON stocks(market);
CREATE INDEX idx_stocks_designation_status ON stocks(designation_status);

-- 코멘트
COMMENT ON COLUMN stocks.market IS '시장 구분 (US, KR, CRYPTO)';
COMMENT ON COLUMN stocks.designation_status IS '종목 지정 상태 (NORMAL, CAUTION, WARNING, DANGER, DELISTED)';
COMMENT ON COLUMN stocks.designation_reason IS '지정 사유';
COMMENT ON COLUMN stocks.designated_at IS '지정일시';
