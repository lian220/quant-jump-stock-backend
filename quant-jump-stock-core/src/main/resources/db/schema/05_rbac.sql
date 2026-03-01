-- ============================================
-- 05_rbac.sql
-- Sources: V6
-- ============================================

CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(255),
    is_system BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE roles IS '역할 정보';
COMMENT ON COLUMN roles.id IS '역할 고유 ID (PK)';
COMMENT ON COLUMN roles.name IS '역할 이름 (ADMIN, USER 등)';
COMMENT ON COLUMN roles.description IS '역할 설명';
COMMENT ON COLUMN roles.is_system IS '시스템 역할 여부 (삭제 불가)';
COMMENT ON COLUMN roles.created_at IS '역할 생성 일시';

-- ============================================

CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    category VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_permissions_category ON permissions(category);

COMMENT ON TABLE permissions IS '권한 정보';
COMMENT ON COLUMN permissions.id IS '권한 고유 ID (PK)';
COMMENT ON COLUMN permissions.code IS '권한 코드 (예: USER_CREATE)';
COMMENT ON COLUMN permissions.name IS '권한 표시 이름';
COMMENT ON COLUMN permissions.description IS '권한 설명';
COMMENT ON COLUMN permissions.category IS '권한 카테고리';
COMMENT ON COLUMN permissions.created_at IS '권한 생성 일시';

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

CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_assigned_at ON user_roles(assigned_at);
CREATE INDEX IF NOT EXISTS idx_user_roles_assigned_by ON user_roles(assigned_by);

COMMENT ON TABLE user_roles IS '사용자-역할 매핑';
COMMENT ON COLUMN user_roles.id IS '사용자-역할 매핑 고유 ID (PK)';
COMMENT ON COLUMN user_roles.user_id IS '사용자 ID (FK)';
COMMENT ON COLUMN user_roles.role_id IS '역할 ID (FK)';
COMMENT ON COLUMN user_roles.assigned_at IS 'Timestamp when the role was assigned';
COMMENT ON COLUMN user_roles.assigned_by IS 'User who assigned this role (nullable for system assignments)';
COMMENT ON COLUMN user_roles.created_at IS '역할 할당 일시';

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

CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions(permission_id);

COMMENT ON TABLE role_permissions IS '역할-권한 매핑';
COMMENT ON COLUMN role_permissions.id IS '역할-권한 매핑 고유 ID (PK)';
COMMENT ON COLUMN role_permissions.role_id IS '역할 ID (FK)';
COMMENT ON COLUMN role_permissions.permission_id IS '권한 ID (FK)';
COMMENT ON COLUMN role_permissions.created_at IS '권한 할당 일시';
