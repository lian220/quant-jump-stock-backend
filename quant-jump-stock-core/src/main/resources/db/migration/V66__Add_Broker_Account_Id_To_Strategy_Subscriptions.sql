-- Phase 1B v2.1 — 전략별 계좌 매핑.
-- strategy_subscriptions (사용자 ↔ 전략 N:M) 에 broker_account_id FK 추가.
-- 같은 전략 구독을 어느 계좌로 실행할지 사용자가 명시 선택.
-- NULL = legacy fallback (사용자의 활성 계좌 중 첫 번째 사용).

ALTER TABLE strategy_subscriptions
    ADD COLUMN broker_account_id BIGINT NULL REFERENCES user_broker_accounts(id) ON DELETE SET NULL;

COMMENT ON COLUMN strategy_subscriptions.broker_account_id IS
    '본 구독 전략 실행 시 사용할 user_broker_accounts.id. NULL=사용자 활성 계좌 자동 선택 (legacy).';

CREATE INDEX idx_strategy_subscriptions_broker_account_id
    ON strategy_subscriptions (broker_account_id)
    WHERE broker_account_id IS NOT NULL;

-- 24h cooldown 저장 위치: users.last_real_activation_at
-- 첫 실전 전환 시각. 24h 안에 다른 전략의 실전 전환 차단 (Service 레벨 검사).
ALTER TABLE users
    ADD COLUMN last_real_activation_at TIMESTAMP NULL;

COMMENT ON COLUMN users.last_real_activation_at IS
    '사용자가 어느 전략을 실전(REAL) 계좌로 전환한 가장 최근 시각. 24h cooldown 정책에 사용.';
