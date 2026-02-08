-- ============================================
-- V6: RBAC (Role-Based Access Control) Tables (Consolidated)
-- Sources: V7__Create_RBAC_Tables.sql + V11__Add_User_Roles_Audit_Columns.sql
--          + V15__Create_RBAC_Tables_And_Initial_Data.sql (DDL only)
-- PostgreSQL 15+
-- ============================================

-- ============================================
-- 1. Roles Table
-- ============================================
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(255),
    is_system BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_roles_name ON roles(name);

COMMENT ON TABLE roles IS '역할 정보';
COMMENT ON COLUMN roles.is_system IS '시스템 역할 여부 (삭제 불가)';

-- ============================================
-- 2. Permissions Table
-- ============================================
CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    category VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_permissions_code ON permissions(code);
CREATE INDEX IF NOT EXISTS idx_permissions_category ON permissions(category);

COMMENT ON TABLE permissions IS '권한 정보';
COMMENT ON COLUMN permissions.code IS '권한 코드 (예: USER_CREATE)';

-- ============================================
-- 3. User Roles (Many-to-Many)
-- Includes audit columns from V11
-- ============================================
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    UNIQUE (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_assigned_at ON user_roles(assigned_at);
CREATE INDEX IF NOT EXISTS idx_user_roles_assigned_by ON user_roles(assigned_by);

COMMENT ON TABLE user_roles IS '사용자-역할 매핑';
COMMENT ON COLUMN user_roles.assigned_at IS 'Timestamp when the role was assigned';
COMMENT ON COLUMN user_roles.assigned_by IS 'User who assigned this role (nullable for system assignments)';

-- ============================================
-- 4. Role Permissions (Many-to-Many)
-- ============================================
CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    UNIQUE (role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions(permission_id);

COMMENT ON TABLE role_permissions IS '역할-권한 매핑';
