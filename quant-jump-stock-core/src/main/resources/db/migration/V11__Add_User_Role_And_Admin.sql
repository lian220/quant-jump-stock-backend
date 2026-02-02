-- ============================================
-- V11: Add User Role and Create Admin User
-- ============================================

-- 1. Add role column to users table
ALTER TABLE users
ADD COLUMN role VARCHAR(20) DEFAULT 'USER' CHECK (role IN ('ADMIN', 'USER', 'MODERATOR'));

COMMENT ON COLUMN users.role IS '사용자 역할: ADMIN, USER, MODERATOR';

-- 2. Create index for role queries
CREATE INDEX idx_users_role ON users(role);

-- 3. Insert admin user
-- ID: lian, Password: sfn0008 (BCrypt hashed)
INSERT INTO users (user_id, name, email, password_hash, status, role, created_at, updated_at)
VALUES (
    'lian',
    '관리자',
    'admin@quantjump.com',
    '$2b$10$11lv/wxNNQ0xGmcb6Ju2R.8px/tGMfZhni.lwgkveMRDCNACS/yxm',
    'ACTIVE',
    'ADMIN',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (user_id) DO UPDATE SET
    role = 'ADMIN',
    password_hash = '$2b$10$11lv/wxNNQ0xGmcb6Ju2R.8px/tGMfZhni.lwgkveMRDCNACS/yxm',
    updated_at = CURRENT_TIMESTAMP;
