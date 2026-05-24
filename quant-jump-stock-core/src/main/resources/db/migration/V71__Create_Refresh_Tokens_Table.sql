-- =========================================================================
-- V71: Refresh Token Store (Phase 1A 보안 PRE Task 12 본 구현)
-- =========================================================================
-- 목적:
--   1) Refresh token rotation 미구현 단계에서도 logout 시 즉시 revocation
--   2) RFC 9700 / OWASP A07 2025 권고: stateless JWT 한계를 보완
--   3) 추후 reuse detection / token family revocation 의 데이터 기반
--
-- 설계:
--   - jti: JWT ID (UUID v4) — refresh token claim "jti" 와 1:1 매핑, PK
--   - user_db_id: users.id 외래키 (사용자 단위 일괄 revoke 용)
--   - is_revoked: logout 시 TRUE, refresh 검증 시 FALSE 필요
--   - expires_at: refresh token 만료 (기본 14d), 만료 이후 cleanup 가능
--
-- 운영:
--   - jti PK 자체로 O(log n) 조회 (`isActive(jti)`)
--   - user_db_id partial 인덱스 — logout 의 `revokeAllByUser` 가 항상 is_revoked=false 만 대상이므로
--     공간/쓰기 비용 절감 + 인덱스 selectivity 향상
--   - expires_at 인덱스 — 주기적 cleanup job 효율화 (만료된 row 일괄 삭제)
--
-- 향후 확장 (별도 마이그레이션으로):
--   - V72+ : device_id / user_agent / ip_address — "내 활성 세션" 화면 (Phase 2)
--   - V72+ : parent_jti / session_id — rotation + reuse detection (RFC 9700 token family) (Phase 3)
--   - V72+ : auth_audit_log 별도 테이블 — 보안 감사 (Phase 4)
-- =========================================================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
    jti           UUID PRIMARY KEY,
    user_db_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    issued_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ NOT NULL,
    is_revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at    TIMESTAMPTZ
);

-- logout revokeAllByUser 전용 partial index: 살아있는 토큰만 인덱싱
-- (revoke 된 토큰은 대부분이고 logout 대상이 아니므로 인덱스에서 제외)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_active
    ON refresh_tokens(user_db_id)
    WHERE is_revoked = FALSE;

-- cleanup job 전용: 만료된 살아있는 row 찾기 (= 정리 대상)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at
    ON refresh_tokens(expires_at)
    WHERE is_revoked = FALSE;

COMMENT ON TABLE refresh_tokens IS 'JWT refresh token revocation store (Phase 1A 보안 PRE Task 12, V71)';
COMMENT ON COLUMN refresh_tokens.jti IS 'JWT ID claim (UUID v4) - refresh token 과 1:1 매핑';
COMMENT ON COLUMN refresh_tokens.is_revoked IS 'logout 시 TRUE - validate 시 FALSE 필요';
COMMENT ON COLUMN refresh_tokens.user_db_id IS 'users.id FK - logout 시 사용자 단위 일괄 revoke 용';
COMMENT ON COLUMN refresh_tokens.expires_at IS 'refresh token 만료 시각 (기본 issued_at + 14d)';
