-- A+ 모델 도입 — 단일 계좌 + 7일 휴지통 (soft delete).
-- 기존 user_id 단일 unique 제약을 partial unique index 로 교체하여
-- 활성 row 1개 강제 + 휴지통 row 와 공존 허용한다.

-- 1. soft delete 마커 컬럼 추가
ALTER TABLE user_kis_accounts
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;

COMMENT ON COLUMN user_kis_accounts.deleted_at IS
    'Soft delete 휴지통 마커. NULL=활성, NOT NULL=휴지통 (7일 후 Cloud Scheduler 가 hard delete)';

-- 2. 기존 user_id 단일 unique 제약 제거 (인라인 UNIQUE → 자동명 user_kis_accounts_user_id_key)
ALTER TABLE user_kis_accounts
    DROP CONSTRAINT IF EXISTS user_kis_accounts_user_id_key;

-- 3. 활성 row 1개만 강제하는 partial unique index
--    휴지통 row (deleted_at IS NOT NULL) 와 활성 row (deleted_at IS NULL) 공존 허용.
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_kis_accounts_active_user
    ON user_kis_accounts (user_id)
    WHERE deleted_at IS NULL;

-- 4. 휴지통 만료 정리 쿼리 가속 (Cloud Scheduler 가 매일 1회 사용)
CREATE INDEX IF NOT EXISTS idx_user_kis_accounts_deleted_at
    ON user_kis_accounts (deleted_at)
    WHERE deleted_at IS NOT NULL;
