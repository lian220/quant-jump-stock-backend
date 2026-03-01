-- ============================================
-- 01_users.sql
-- Sources: V1 + V40 (phone column)
-- ============================================

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100),
    email VARCHAR(100) UNIQUE,
    password_hash VARCHAR(255),
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    oauth_provider VARCHAR(20) CHECK (oauth_provider IN ('GOOGLE', 'NAVER')),
    oauth_provider_id VARCHAR(255),
    profile_image_url VARCHAR(500),
    role VARCHAR(20) DEFAULT 'USER' CHECK (role IN ('ADMIN', 'USER', 'MODERATOR')),
    phone VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_oauth_provider ON users(oauth_provider);
CREATE INDEX IF NOT EXISTS idx_users_oauth_provider_id ON users(oauth_provider_id);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

COMMENT ON TABLE users IS '사용자 정보';
COMMENT ON COLUMN users.id IS '사용자 고유 ID (PK, 내부 식별자)';
COMMENT ON COLUMN users.user_id IS '사용자 고유 식별자 (비즈니스 키)';
COMMENT ON COLUMN users.name IS '사용자 이름';
COMMENT ON COLUMN users.email IS '이메일 주소 (로그인 ID)';
COMMENT ON COLUMN users.password_hash IS '비밀번호 해시 (BCrypt)';
COMMENT ON COLUMN users.status IS '사용자 상태: ACTIVE, INACTIVE, SUSPENDED';
COMMENT ON COLUMN users.oauth_provider IS 'OAuth provider: GOOGLE, NAVER';
COMMENT ON COLUMN users.oauth_provider_id IS 'User ID from OAuth provider';
COMMENT ON COLUMN users.profile_image_url IS 'User profile image URL';
COMMENT ON COLUMN users.role IS 'Simple user role for quick permission check';
COMMENT ON COLUMN users.phone IS '전화번호';
COMMENT ON COLUMN users.created_at IS '계정 생성 일시';
COMMENT ON COLUMN users.updated_at IS '계정 정보 수정 일시';
