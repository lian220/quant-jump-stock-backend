-- ============================================
-- V15: 휴대폰/이메일 인증 필드 추가
-- ============================================

-- 휴대폰 번호
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);

-- 휴대폰 인증 여부
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN DEFAULT FALSE;

-- 이메일 인증 여부
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE;

-- 휴대폰 번호 인덱스
CREATE INDEX IF NOT EXISTS idx_users_phone_number ON users(phone_number) WHERE phone_number IS NOT NULL;

COMMENT ON COLUMN users.phone_number IS '휴대폰 번호 (E.164 또는 국내 형식)';
COMMENT ON COLUMN users.phone_verified IS '휴대폰 인증 완료 여부';
COMMENT ON COLUMN users.email_verified IS '이메일 인증 완료 여부';
