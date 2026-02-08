-- ============================================
-- V5: KIS (한국투자증권) Tables (Consolidated)
-- Sources: V5__Create_User_KIS_Accounts.sql + V6__Create_KIS_Tokens_Table.sql
-- PostgreSQL 15+
-- ============================================

-- ============================================
-- 1. User KIS Accounts Table
-- ============================================
CREATE TABLE IF NOT EXISTS user_kis_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    app_key VARCHAR(100) NOT NULL,
    app_secret_encrypted VARCHAR(500) NOT NULL,
    account_number VARCHAR(20) NOT NULL,
    account_product_code VARCHAR(2) NOT NULL DEFAULT '01',
    account_type VARCHAR(10) NOT NULL DEFAULT 'MOCK',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_kis_account_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_kis_accounts_account_type ON user_kis_accounts(account_type);
CREATE INDEX IF NOT EXISTS idx_user_kis_accounts_enabled ON user_kis_accounts(enabled);

COMMENT ON TABLE user_kis_accounts IS '사용자별 KIS(한국투자증권) 계정 정보';
COMMENT ON COLUMN user_kis_accounts.app_key IS 'KIS API 앱 키';
COMMENT ON COLUMN user_kis_accounts.app_secret_encrypted IS 'KIS API 시크릿 (AES-256 암호화)';
COMMENT ON COLUMN user_kis_accounts.account_number IS 'KIS 계좌번호 (앞 8자리)';
COMMENT ON COLUMN user_kis_accounts.account_product_code IS '계좌 상품 코드 (01: 해외주식)';
COMMENT ON COLUMN user_kis_accounts.account_type IS '계정 타입 (REAL: 실전, MOCK: 모의)';

-- ============================================
-- 2. KIS Tokens Table
-- ============================================
CREATE TABLE IF NOT EXISTS kis_tokens (
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

CREATE INDEX IF NOT EXISTS idx_kis_tokens_expiration ON kis_tokens(expiration_time);
CREATE INDEX IF NOT EXISTS idx_kis_tokens_active ON kis_tokens(is_active) WHERE is_active = TRUE;

COMMENT ON TABLE kis_tokens IS 'KIS API Access Token 저장 (사용자별, 계정 타입별)';
COMMENT ON COLUMN kis_tokens.user_id IS '사용자 ID (users 테이블 참조)';
COMMENT ON COLUMN kis_tokens.account_type IS '계정 타입: MOCK(모의투자), REAL(실전투자)';
COMMENT ON COLUMN kis_tokens.access_token IS 'KIS API Access Token (JWT)';
COMMENT ON COLUMN kis_tokens.expiration_time IS '토큰 만료 시간';
COMMENT ON COLUMN kis_tokens.is_active IS '활성화 여부';
