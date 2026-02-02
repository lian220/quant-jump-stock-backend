-- ============================================
-- KIS Access Token 테이블 생성
-- MongoDB access_tokens 컬렉션을 PostgreSQL로 마이그레이션
-- ============================================

CREATE TABLE kis_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_type VARCHAR(10) NOT NULL CHECK (account_type IN ('MOCK', 'REAL')),
    access_token TEXT NOT NULL,
    expiration_time TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_kis_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_user_account_type
        UNIQUE(user_id, account_type)
);

-- 인덱스 생성
CREATE INDEX idx_kis_tokens_user_account
    ON kis_tokens(user_id, account_type);

CREATE INDEX idx_kis_tokens_expiration
    ON kis_tokens(expiration_time);

CREATE INDEX idx_kis_tokens_active
    ON kis_tokens(is_active)
    WHERE is_active = TRUE;

-- 테이블 코멘트
COMMENT ON TABLE kis_tokens IS 'KIS API Access Token 저장 (사용자별, 계정 타입별)';
COMMENT ON COLUMN kis_tokens.user_id IS '사용자 ID (users 테이블 참조)';
COMMENT ON COLUMN kis_tokens.account_type IS '계정 타입: MOCK(모의투자), REAL(실전투자)';
COMMENT ON COLUMN kis_tokens.access_token IS 'KIS API Access Token (JWT)';
COMMENT ON COLUMN kis_tokens.expiration_time IS '토큰 만료 시간';
COMMENT ON COLUMN kis_tokens.is_active IS '활성화 여부';
