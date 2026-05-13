-- Phase 1A PRE-요구사항 P3: AES/GCM 전환 — 신규 컬럼 추가.
-- ECB로 암호화된 기존 app_secret_encrypted 데이터는 V61 backfill 후 V62에서 drop 예정.

ALTER TABLE user_kis_accounts
    ADD COLUMN IF NOT EXISTS app_secret_encrypted_v2 VARCHAR(1024);

-- 인덱스 영향 없음 (이 컬럼은 검색 키가 아님).
COMMENT ON COLUMN user_kis_accounts.app_secret_encrypted_v2
    IS 'AES/GCM(V2)으로 암호화된 KIS appSecret. Base64(IV||ciphertext) 형식.';
