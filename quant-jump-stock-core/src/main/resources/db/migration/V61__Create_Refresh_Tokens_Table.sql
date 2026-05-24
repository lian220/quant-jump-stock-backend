-- =========================================================================
-- V61: Refresh Token Store (Phase 1A 보안 PRE Task 12 본 구현)
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
--   - jti 인덱스 PK 자체로 O(log n) 조회
--   - user_db_id 인덱스로 logout 시 user 단위 일괄 revoke
--   - expires_at 인덱스로 추후 주기적 cleanup job 효율화
-- =========================================================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
    jti           UUID PRIMARY KEY,
    user_db_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    issued_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ NOT NULL,
    is_revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_db_id ON refresh_tokens(user_db_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

COMMENT ON TABLE refresh_tokens IS 'JWT refresh token revocation store (Phase 1A 보안 PRE Task 12)';
COMMENT ON COLUMN refresh_tokens.jti IS 'JWT ID claim (UUID v4) - refresh token 과 1:1 매핑';
COMMENT ON COLUMN refresh_tokens.is_revoked IS 'logout 시 TRUE - validate 시 FALSE 필요';
