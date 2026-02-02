-- ============================================
-- V13: Role-Based Access Control (RBAC)
-- Admin/Backoffice와 Front User 분리 관리
-- ============================================

-- ============================================
-- 1. Roles Table (역할 정의)
-- ============================================
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL,
    description VARCHAR(255),
    is_system BOOLEAN DEFAULT FALSE,  -- 시스템 기본 역할 (삭제 불가)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE roles IS '역할 정의 테이블';
COMMENT ON COLUMN roles.is_system IS '시스템 기본 역할 여부 (삭제 불가)';

-- ============================================
-- 2. Permissions Table (권한 정의)
-- ============================================
CREATE TABLE IF NOT EXISTS permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) UNIQUE NOT NULL,  -- MANAGE_USERS, VIEW_ANALYTICS 등
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    category VARCHAR(50),  -- USER, STRATEGY, BACKTEST, SYSTEM 등
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE permissions IS '권한 정의 테이블';
COMMENT ON COLUMN permissions.code IS '권한 코드 (코드에서 사용)';
COMMENT ON COLUMN permissions.category IS '권한 카테고리';

-- ============================================
-- 3. Role-Permissions Mapping (역할-권한 매핑)
-- ============================================
CREATE TABLE IF NOT EXISTS role_permissions (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (role_id, permission_id)
);

COMMENT ON TABLE role_permissions IS '역할별 권한 매핑';

-- ============================================
-- 4. User-Roles Mapping (사용자-역할 매핑)
-- ============================================
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE (user_id, role_id)
);

COMMENT ON TABLE user_roles IS '사용자별 역할 매핑 (다중 역할 가능)';

-- ============================================
-- 5. Initial Roles (기본 역할)
-- ============================================
INSERT INTO roles (name, description, is_system) VALUES
    ('SUPER_ADMIN', '최고 관리자 - 모든 권한', TRUE),
    ('ADMIN', '관리자 - 사용자/전략/시스템 관리', TRUE),
    ('MODERATOR', '운영자 - 전략 검토/승인', TRUE),
    ('USER', '일반 사용자 - Front 서비스 이용', TRUE);

-- ============================================
-- 6. Initial Permissions (기본 권한)
-- ============================================

-- 사용자 관리 권한
INSERT INTO permissions (code, name, description, category) VALUES
    ('USER_VIEW', '사용자 조회', '사용자 목록 및 상세 조회', 'USER'),
    ('USER_CREATE', '사용자 생성', '새 사용자 생성', 'USER'),
    ('USER_UPDATE', '사용자 수정', '사용자 정보 수정', 'USER'),
    ('USER_DELETE', '사용자 삭제', '사용자 삭제/비활성화', 'USER'),
    ('USER_ROLE_ASSIGN', '역할 할당', '사용자에게 역할 할당', 'USER');

-- 전략 관리 권한
INSERT INTO permissions (code, name, description, category) VALUES
    ('STRATEGY_VIEW', '전략 조회', '모든 전략 조회', 'STRATEGY'),
    ('STRATEGY_CREATE', '전략 생성', '새 전략 생성', 'STRATEGY'),
    ('STRATEGY_UPDATE', '전략 수정', '전략 수정', 'STRATEGY'),
    ('STRATEGY_DELETE', '전략 삭제', '전략 삭제', 'STRATEGY'),
    ('STRATEGY_APPROVE', '전략 승인', '전략 공개 승인', 'STRATEGY'),
    ('STRATEGY_PUBLISH', '시스템 전략 발행', '시스템 공식 전략 발행', 'STRATEGY');

-- 백테스트 관리 권한
INSERT INTO permissions (code, name, description, category) VALUES
    ('BACKTEST_RUN', '백테스트 실행', '백테스트 실행', 'BACKTEST'),
    ('BACKTEST_VIEW_ALL', '전체 백테스트 조회', '모든 사용자 백테스트 조회', 'BACKTEST'),
    ('BACKTEST_UNLIMITED', '무제한 백테스트', '일일 제한 없이 백테스트', 'BACKTEST');

-- 분석/통계 권한
INSERT INTO permissions (code, name, description, category) VALUES
    ('ANALYTICS_VIEW', '분석 대시보드', '분석 대시보드 조회', 'ANALYTICS'),
    ('ANALYTICS_EXPORT', '데이터 내보내기', '분석 데이터 내보내기', 'ANALYTICS');

-- 시스템 관리 권한
INSERT INTO permissions (code, name, description, category) VALUES
    ('SYSTEM_SETTINGS', '시스템 설정', '시스템 설정 관리', 'SYSTEM'),
    ('SYSTEM_LOGS', '시스템 로그', '시스템 로그 조회', 'SYSTEM'),
    ('SYSTEM_MAINTENANCE', '시스템 유지보수', '시스템 유지보수 작업', 'SYSTEM');

-- 구독/결제 관리 권한
INSERT INTO permissions (code, name, description, category) VALUES
    ('SUBSCRIPTION_VIEW', '구독 조회', '구독 정보 조회', 'SUBSCRIPTION'),
    ('SUBSCRIPTION_MANAGE', '구독 관리', '구독 생성/수정/취소', 'SUBSCRIPTION'),
    ('PAYMENT_VIEW', '결제 조회', '결제 내역 조회', 'PAYMENT'),
    ('PAYMENT_REFUND', '환불 처리', '환불 처리', 'PAYMENT');

-- ============================================
-- 7. Role-Permission Mapping (역할별 권한 할당)
-- ============================================

-- SUPER_ADMIN: 모든 권한
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'SUPER_ADMIN';

-- ADMIN: 시스템 설정 제외한 대부분 권한
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ADMIN'
  AND p.code NOT IN ('SYSTEM_SETTINGS', 'SYSTEM_MAINTENANCE');

-- MODERATOR: 전략 검토/승인 + 사용자 조회
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'MODERATOR'
  AND p.code IN (
    'USER_VIEW',
    'STRATEGY_VIEW', 'STRATEGY_APPROVE',
    'BACKTEST_VIEW_ALL',
    'ANALYTICS_VIEW'
  );

-- USER: 기본 권한 (자신의 데이터만)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'USER'
  AND p.code IN (
    'STRATEGY_VIEW', 'STRATEGY_CREATE',
    'BACKTEST_RUN'
  );

-- ============================================
-- 8. Migrate Existing Users to RBAC
-- ============================================

-- 기존 ADMIN 역할 사용자 → ADMIN role 할당
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.role = 'ADMIN' AND r.name = 'ADMIN';

-- 기존 MODERATOR 역할 사용자 → MODERATOR role 할당
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.role = 'MODERATOR' AND r.name = 'MODERATOR';

-- 기존 USER 역할 사용자 → USER role 할당
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.role = 'USER' AND r.name = 'USER';

-- role 없는 사용자 → USER role 할당
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.role IS NULL AND r.name = 'USER'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ============================================
-- 9. Indexes
-- ============================================
CREATE INDEX idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission ON role_permissions(permission_id);
CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);
CREATE INDEX idx_permissions_category ON permissions(category);
CREATE INDEX idx_permissions_code ON permissions(code);

-- ============================================
-- 10. Helper View: User with Permissions
-- ============================================
CREATE OR REPLACE VIEW user_permissions_view AS
SELECT DISTINCT
    u.id AS user_id,
    u.user_id AS username,
    r.name AS role_name,
    p.code AS permission_code,
    p.category AS permission_category
FROM users u
JOIN user_roles ur ON u.id = ur.user_id
JOIN roles r ON ur.role_id = r.id
JOIN role_permissions rp ON r.id = rp.role_id
JOIN permissions p ON rp.permission_id = p.id;

COMMENT ON VIEW user_permissions_view IS '사용자별 권한 조회 뷰';

-- ============================================
-- 11. (Optional) Deprecate old role column
-- ============================================
-- 기존 users.role 컬럼은 유지하되 deprecated 표시
COMMENT ON COLUMN users.role IS '[DEPRECATED] user_roles 테이블 사용 권장. 하위 호환성을 위해 유지.';
