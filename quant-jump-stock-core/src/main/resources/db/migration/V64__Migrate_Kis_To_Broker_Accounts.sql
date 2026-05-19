-- Phase 1B v2.1 — user_kis_accounts → user_broker_accounts 데이터 마이그레이션.
-- V63 (스키마) 이후 적용.
--
-- 안전성:
--   1. PRECHECK 으로 app_secret_encrypted_v2 NULL row 가 있으면 실패. AppSecretReencryptionRunner 가
--      fail-soft 라서 부팅 시점에 실패한 row 가 남아있을 수 있으므로 마이그 전 명시 검증.
--   2. user_kis_accounts 는 보존. 코드 마이그 + 4주 운영 검증 후 V70 에서 drop.
--   3. INSERT 는 단일 트랜잭션 — 도중 실패 시 user_broker_accounts 는 빈 상태로 롤백.

-- PRECHECK: v2 (GCM) backfill 완료 검증.
-- 단 v1 만 있는 row 가 있으면 INSERT 시 app_secret 정보가 손실되므로 차단.
DO $$
DECLARE
    v_null_count INT;
BEGIN
    SELECT COUNT(*) INTO v_null_count
    FROM user_kis_accounts
    WHERE app_secret_encrypted_v2 IS NULL;

    IF v_null_count > 0 THEN
        RAISE EXCEPTION 'V64 차단: user_kis_accounts 에 app_secret_encrypted_v2 NULL row 가 %개 남아 있습니다. AppSecretReencryptionRunner 가 backfill 완료할 때까지 V64 실행 금지.', v_null_count;
    END IF;
END $$;

-- 데이터 마이그: 모든 row 를 user_broker_accounts 로 복사. broker='KIS' 부여.
-- account_type 은 enum 문자열 그대로 (PostgreSQL 의 VARCHAR(10) 타입에 'MOCK' / 'REAL' 저장).
-- account_alias 는 마이그 시점엔 NULL. 사용자가 mypage 에서 추후 설정.
INSERT INTO user_broker_accounts (
    user_id,
    broker,
    account_type,
    account_number,
    account_product_code,
    account_alias,
    app_key,
    app_secret_encrypted,
    enabled,
    last_used_at,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    user_id,
    'KIS',
    account_type,                    -- VARCHAR(10) → VARCHAR(10) 동치 (enum.name())
    account_number,
    account_product_code,
    NULL,                            -- account_alias 미설정
    app_key,
    app_secret_encrypted_v2,         -- PRECHECK 로 NOT NULL 보장
    enabled,
    last_used_at,
    created_at,
    updated_at,
    deleted_at
FROM user_kis_accounts;

-- 마이그 결과 row 수 동치 검증 (안전망).
DO $$
DECLARE
    v_src INT;
    v_dst INT;
BEGIN
    SELECT COUNT(*) INTO v_src FROM user_kis_accounts;
    SELECT COUNT(*) INTO v_dst FROM user_broker_accounts WHERE broker = 'KIS';

    IF v_src <> v_dst THEN
        RAISE EXCEPTION 'V64 row 수 불일치: user_kis_accounts(%)와 user_broker_accounts.KIS(%)가 다릅니다.', v_src, v_dst;
    END IF;
END $$;
