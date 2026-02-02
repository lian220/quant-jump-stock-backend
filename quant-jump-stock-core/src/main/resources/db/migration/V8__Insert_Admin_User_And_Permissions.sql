-- ============================================
-- Admin 사용자 및 권한 설정
-- ============================================

-- ============================================
-- 1. Roles 생성
-- ============================================
INSERT INTO roles (name, description, is_system) VALUES
    ('SUPER_ADMIN', '슈퍼 관리자 - 모든 권한', TRUE),
    ('ADMIN', '관리자 - 시스템 관리 권한', TRUE),
    ('MODERATOR', '운영자 - 콘텐츠 관리 권한', TRUE),
    ('USER', '일반 사용자', TRUE)
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 2. Permissions 생성
-- ============================================
INSERT INTO permissions (code, name, description, category) VALUES
    -- User Management
    ('USER_VIEW', '사용자 조회', '사용자 정보 조회', 'USER_MANAGEMENT'),
    ('USER_CREATE', '사용자 생성', '새 사용자 생성', 'USER_MANAGEMENT'),
    ('USER_UPDATE', '사용자 수정', '사용자 정보 수정', 'USER_MANAGEMENT'),
    ('USER_DELETE', '사용자 삭제', '사용자 삭제', 'USER_MANAGEMENT'),
    ('USER_ROLE_ASSIGN', '역할 할당', '사용자에게 역할 할당', 'USER_MANAGEMENT'),

    -- Strategy Management
    ('STRATEGY_VIEW', '전략 조회', '거래 전략 조회', 'STRATEGY'),
    ('STRATEGY_CREATE', '전략 생성', '거래 전략 생성', 'STRATEGY'),
    ('STRATEGY_UPDATE', '전략 수정', '거래 전략 수정', 'STRATEGY'),
    ('STRATEGY_DELETE', '전략 삭제', '거래 전략 삭제', 'STRATEGY'),
    ('STRATEGY_APPROVE', '전략 승인', '거래 전략 승인', 'STRATEGY'),
    ('STRATEGY_PUBLISH', '전략 게시', '거래 전략 게시', 'STRATEGY'),

    -- Backtest
    ('BACKTEST_RUN', '백테스트 실행', '백테스트 실행', 'BACKTEST'),
    ('BACKTEST_VIEW_ALL', '모든 백테스트 조회', '모든 사용자 백테스트 조회', 'BACKTEST'),
    ('BACKTEST_UNLIMITED', '무제한 백테스트', '백테스트 횟수 제한 없음', 'BACKTEST'),

    -- Analytics
    ('ANALYTICS_VIEW', '분석 조회', '거래 분석 데이터 조회', 'ANALYTICS'),
    ('ANALYTICS_EXPORT', '분석 내보내기', '분석 데이터 내보내기', 'ANALYTICS'),

    -- System
    ('SYSTEM_SETTINGS', '시스템 설정', '시스템 설정 관리', 'SYSTEM'),
    ('SYSTEM_LOGS', '시스템 로그', '시스템 로그 조회', 'SYSTEM'),
    ('SYSTEM_MAINTENANCE', '시스템 유지보수', '시스템 유지보수 작업', 'SYSTEM'),

    -- Subscription & Payment
    ('SUBSCRIPTION_VIEW', '구독 조회', '구독 정보 조회', 'SUBSCRIPTION'),
    ('SUBSCRIPTION_MANAGE', '구독 관리', '구독 관리', 'SUBSCRIPTION'),
    ('PAYMENT_VIEW', '결제 조회', '결제 정보 조회', 'PAYMENT'),
    ('PAYMENT_REFUND', '결제 환불', '결제 환불 처리', 'PAYMENT'),

    -- Trading
    ('TRADING_VIEW', '거래 조회', '거래 내역 조회', 'TRADING'),
    ('TRADING_EXECUTE', '거래 실행', '거래 주문 실행', 'TRADING')
ON CONFLICT (code) DO NOTHING;

-- ============================================
-- 3. Role-Permission 매핑
-- ============================================

-- SUPER_ADMIN: 모든 권한
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ADMIN: 시스템 관리 권한 (USER, STRATEGY, BACKTEST, ANALYTICS, SYSTEM)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
AND p.category IN ('USER_MANAGEMENT', 'STRATEGY', 'BACKTEST', 'ANALYTICS', 'SYSTEM', 'SUBSCRIPTION')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- MODERATOR: 콘텐츠 관리 권한 (STRATEGY, ANALYTICS)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'MODERATOR'
AND p.category IN ('STRATEGY', 'ANALYTICS')
AND p.code NOT IN ('STRATEGY_DELETE', 'STRATEGY_APPROVE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- USER: 기본 사용자 권한 (조회 및 자신의 데이터 관리)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'USER'
AND p.code IN ('STRATEGY_VIEW', 'BACKTEST_RUN', 'ANALYTICS_VIEW', 'TRADING_VIEW', 'TRADING_EXECUTE', 'SUBSCRIPTION_VIEW')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================
-- 4. Admin 사용자 생성
-- ============================================
-- 비밀번호: sfn0008
-- BCrypt 해시 (strength 10): $2a$10$YourActualHashHere
-- ⚠️ 보안: 첫 로그인 후 반드시 비밀번호를 변경하세요!

-- pgcrypto extension 활성화 (BCrypt 지원)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Admin 사용자 생성 (BCrypt로 비밀번호 해싱)
INSERT INTO users (user_id, name, email, password_hash, status)
VALUES (
    'lian',
    'Admin User',
    'admin@quantiq.com',
    crypt('sfn0008', gen_salt('bf', 10)),  -- BCrypt 해싱
    'ACTIVE'
)
ON CONFLICT (user_id) DO NOTHING;

-- Admin 사용자에게 ADMIN 역할 할당
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.user_id = 'lian'
AND r.name = 'ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ============================================
-- 5. Trading Config 초기화 (선택사항)
-- ============================================
INSERT INTO trading_configs (user_id, enabled, auto_trading_enabled)
SELECT id, FALSE, FALSE
FROM users
WHERE user_id = 'lian'
ON CONFLICT (user_id) DO NOTHING;

-- ============================================
-- 6. Account Balance 초기화 (선택사항)
-- ============================================
INSERT INTO account_balances (user_id, cash, total_value)
SELECT id, 0.00, 0.00
FROM users
WHERE user_id = 'lian'
ON CONFLICT (user_id) DO NOTHING;
