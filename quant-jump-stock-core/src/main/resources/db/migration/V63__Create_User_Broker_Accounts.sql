-- Phase 1B v2.1 — 다중 증권사 다중 계좌 (Broker 추상화).
-- (user_id, broker, account_type, account_number) 4-tuple 단위 unique.
-- 사용자가 KIS + Toss + ... × MOCK/REAL × 계좌번호 N개를 동시 보유 가능.
--
-- 본 SQL 은 스키마만 생성. 데이터 마이그레이션 (user_kis_accounts → user_broker_accounts) 은
-- V64 에서 PRECHECK 와 함께 수행하여 schema 단계와 데이터 단계의 롤백 경계를 분리한다.

CREATE TABLE user_broker_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    broker VARCHAR(20) NOT NULL,                       -- 'KIS' | 'TOSS' | ...
    account_type VARCHAR(10) NOT NULL,                 -- 'MOCK' | 'REAL'
    account_number VARCHAR(20) NOT NULL,
    account_product_code VARCHAR(2) NOT NULL DEFAULT '01',
    account_alias VARCHAR(50),                         -- 사용자 별명 (선택, UX)
    app_key VARCHAR(100) NOT NULL,
    app_secret_encrypted VARCHAR(1024) NOT NULL,       -- GCM only (v2 컬럼)
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP                                -- soft delete 휴지통 (7일)
);

COMMENT ON TABLE user_broker_accounts IS
    '사용자의 증권사 계좌. (user_id, broker, account_type, account_number) unique. soft delete 7일 휴지통. Phase 1B v2.1.';

COMMENT ON COLUMN user_broker_accounts.broker IS
    '증권사 식별자. Broker enum: KIS, TOSS, ... whitelist. 직접 클라이언트 입력 불가.';

COMMENT ON COLUMN user_broker_accounts.account_type IS
    '계좌 유형. AccountType enum: MOCK (모의), REAL (실전).';

COMMENT ON COLUMN user_broker_accounts.account_alias IS
    '사용자 별명 (예: "메인 계좌", "분리 운용"). NULL 허용 — 미설정 시 ${broker} ${type} ${maskedNumber} 자동 표시.';

COMMENT ON COLUMN user_broker_accounts.app_secret_encrypted IS
    'GCM 암호화된 app_secret. Base64(IV(12B) || ciphertext+tag(16B)). v1 ECB 컬럼 제거됨 (V62 backfill 완료 후).';

COMMENT ON COLUMN user_broker_accounts.deleted_at IS
    'Soft delete 휴지통 마커. NULL=활성, NOT NULL=휴지통 (7일 후 Cloud Scheduler 가 hard delete).';

-- 활성 row unique: (user_id, broker, account_type, account_number) WHERE deleted_at IS NULL.
-- 휴지통 row 는 unique 무관 — 활성과 공존 가능 (스왑 시 동일 4-tuple 가능).
CREATE UNIQUE INDEX uk_user_broker_accounts_active
    ON user_broker_accounts (user_id, broker, account_type, account_number)
    WHERE deleted_at IS NULL;

-- 휴지통 hard delete cron 가속.
CREATE INDEX idx_user_broker_accounts_deleted_at
    ON user_broker_accounts (deleted_at)
    WHERE deleted_at IS NOT NULL;

-- 자주 쓰는 조회 (사용자별 broker 별 계좌 목록).
CREATE INDEX idx_user_broker_accounts_user_broker
    ON user_broker_accounts (user_id, broker)
    WHERE deleted_at IS NULL;
