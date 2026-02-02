-- V4__Create_User_KIS_Accounts.sql
-- 사용자별 KIS 계정 정보 테이블 생성

CREATE TABLE user_kis_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    app_key VARCHAR(100) NOT NULL,
    app_secret_encrypted VARCHAR(500) NOT NULL,  -- 암호화된 Secret
    account_number VARCHAR(20) NOT NULL,
    account_product_code VARCHAR(2) NOT NULL DEFAULT '01',
    account_type VARCHAR(10) NOT NULL DEFAULT 'MOCK',  -- REAL, MOCK
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_kis_account_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 인덱스 생성
CREATE INDEX idx_user_kis_accounts_user_id ON user_kis_accounts(user_id);
CREATE INDEX idx_user_kis_accounts_account_type ON user_kis_accounts(account_type);
CREATE INDEX idx_user_kis_accounts_enabled ON user_kis_accounts(enabled);

-- UserEntity에 kisAccount 관계 추가 (주석으로 안내)
-- @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
-- var kisAccount: UserKisAccountEntity? = null

COMMENT ON TABLE user_kis_accounts IS '사용자별 KIS(한국투자증권) 계정 정보';
COMMENT ON COLUMN user_kis_accounts.app_key IS 'KIS API 앱 키';
COMMENT ON COLUMN user_kis_accounts.app_secret_encrypted IS 'KIS API 시크릿 (AES-256 암호화)';
COMMENT ON COLUMN user_kis_accounts.account_number IS 'KIS 계좌번호 (앞 8자리)';
COMMENT ON COLUMN user_kis_accounts.account_product_code IS '계좌 상품 코드 (01: 해외주식)';
COMMENT ON COLUMN user_kis_accounts.account_type IS '계정 타입 (REAL: 실전, MOCK: 모의)';
