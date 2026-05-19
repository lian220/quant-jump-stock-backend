-- Phase 1B 모델 도입 — 사용자당 account_type 별 1개 활성 row.
-- A+ (V61) 의 partial unique index `(user_id) WHERE deleted_at IS NULL` 를
-- `(user_id, account_type) WHERE deleted_at IS NULL` 으로 교체하여
-- 같은 사용자가 MOCK + REAL 계좌를 동시 보유 가능하도록 한다.
--
-- 데이터 안전성: V62 까지의 운영 데이터는 사용자당 활성 1 row + 휴지통 1 row 이내.
-- (user_id, account_type) 중복은 존재할 수 없으므로 swap 안전.

-- 1. A+ partial unique index 제거
DROP INDEX IF EXISTS uk_user_kis_accounts_active_user;

-- 2. (user_id, account_type) 조합으로 partial unique index 재생성
--    같은 type 끼리는 1개만 강제, 다른 type 은 공존 허용.
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_kis_accounts_active_user_type
    ON user_kis_accounts (user_id, account_type)
    WHERE deleted_at IS NULL;

-- 휴지통 인덱스 (V61 의 idx_user_kis_accounts_deleted_at) 는 그대로 유지.
-- 휴지통 row 는 type 무관하게 만료 정리 대상.
