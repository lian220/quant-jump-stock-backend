-- ============================================
-- V1: Users Table (Consolidated)
-- Sources: V1__Initial_Schema.sql (users) + V14__Add_OAuth_Columns_To_Users.sql
-- PostgreSQL 15+
-- ============================================

-- ============================================
-- 1. Users Table
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100),
    email VARCHAR(100) UNIQUE,
    password_hash VARCHAR(255),
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    -- OAuth columns (from V14)
    oauth_provider VARCHAR(20) CHECK (oauth_provider IN ('GOOGLE', 'NAVER')),
    oauth_provider_id VARCHAR(255),
    profile_image_url VARCHAR(500),
    role VARCHAR(20) DEFAULT 'USER' CHECK (role IN ('ADMIN', 'USER', 'MODERATOR')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 2. Indexes
-- ============================================
CREATE INDEX IF NOT EXISTS idx_users_oauth_provider ON users(oauth_provider);
CREATE INDEX IF NOT EXISTS idx_users_oauth_provider_id ON users(oauth_provider_id);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- ============================================
-- 3. Comments
-- ============================================
COMMENT ON TABLE users IS '사용자 정보';
COMMENT ON COLUMN users.user_id IS '사용자 고유 식별자 (비즈니스 키)';
COMMENT ON COLUMN users.status IS '사용자 상태: ACTIVE, INACTIVE, SUSPENDED';
COMMENT ON COLUMN users.oauth_provider IS 'OAuth provider: GOOGLE, NAVER';
COMMENT ON COLUMN users.oauth_provider_id IS 'User ID from OAuth provider';
COMMENT ON COLUMN users.profile_image_url IS 'User profile image URL';
COMMENT ON COLUMN users.role IS 'Simple user role for quick permission check';
