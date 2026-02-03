-- V14: Add OAuth and profile columns to users table
-- Supports OAuth login (Google, Naver) and user profile

-- =====================================================================
-- 1. Add OAuth columns
-- =====================================================================
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS oauth_provider VARCHAR(20) CHECK (oauth_provider IN ('GOOGLE', 'NAVER')),
    ADD COLUMN IF NOT EXISTS oauth_provider_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(500);

-- =====================================================================
-- 2. Add role column (separate from RBAC roles)
-- =====================================================================
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'USER' CHECK (role IN ('ADMIN', 'USER', 'MODERATOR'));

-- =====================================================================
-- 3. Create indexes
-- =====================================================================
CREATE INDEX IF NOT EXISTS idx_users_oauth_provider ON users(oauth_provider);
CREATE INDEX IF NOT EXISTS idx_users_oauth_provider_id ON users(oauth_provider_id);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

-- =====================================================================
-- 4. Comments
-- =====================================================================
COMMENT ON COLUMN users.oauth_provider IS 'OAuth provider: GOOGLE, NAVER';
COMMENT ON COLUMN users.oauth_provider_id IS 'User ID from OAuth provider';
COMMENT ON COLUMN users.profile_image_url IS 'User profile image URL';
COMMENT ON COLUMN users.role IS 'Simple user role for quick permission check';
