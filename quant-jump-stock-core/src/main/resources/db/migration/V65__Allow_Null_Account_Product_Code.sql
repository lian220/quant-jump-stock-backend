-- Phase 1B S2 후속 — backend-architect (Newman 권고) 반영.
-- account_product_code 는 KIS 고유 개념 ('01'=해외주식). broker-agnostic 테이블에
-- NOT NULL DEFAULT '01' 박는 건 schema lock-in. Toss 등 다른 broker 추가 시 NULL 표현 자연.
-- V63 prod 적용 전이면 비용 0, 적용 후라도 ALTER 한 번.

ALTER TABLE user_broker_accounts
    ALTER COLUMN account_product_code DROP NOT NULL;

ALTER TABLE user_broker_accounts
    ALTER COLUMN account_product_code DROP DEFAULT;

COMMENT ON COLUMN user_broker_accounts.account_product_code IS
    'broker-specific 계좌 상품 코드. KIS=해외주식 ''01'' 같은 KIS 고유 의미. 다른 broker 에선 NULL.';
