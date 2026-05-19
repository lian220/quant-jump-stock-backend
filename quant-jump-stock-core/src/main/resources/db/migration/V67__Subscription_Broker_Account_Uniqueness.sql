-- Phase 1C — strategy_subscriptions.broker_account_id 1:1 제약.
--
-- 배경:
--   Phase 1B v2.1 까지는 한 broker_account 가 여러 active 구독에 매핑 가능 (다대일).
--   동일 계좌로 여러 전략 신호가 동시에 들어가 잔고/포지션 충돌 위험.
--   1:1 제약으로 (계좌 ↔ 활성 전략 1개) 모델 무결성 확보.
--
-- 24h cooldown 컬럼은 사용 안 하기로 결정 → 동일 마이그에서 DROP.
--
-- 안전성:
--   1) PRECHECK: 이미 1:1 위반된 row 가 있으면 CLEAN 단계에서 최신 1건만 유지하고
--      나머지는 broker_account_id = NULL 로 변경.
--   2) POSTCHECK: CLEAN 후 위반 0건 확인 후에만 INDEX 생성.
--   3) INDEX 생성 실패 시 트랜잭션 롤백.

-- ── 1. PRECHECK: 위반 row 수 확인 (정보 로그용).
DO $$
DECLARE
    v_dup_count INT;
BEGIN
    SELECT COUNT(*) INTO v_dup_count
    FROM (
        SELECT broker_account_id
        FROM strategy_subscriptions
        WHERE status = 'ACTIVE' AND broker_account_id IS NOT NULL
        GROUP BY broker_account_id
        HAVING COUNT(*) > 1
    ) t;

    IF v_dup_count > 0 THEN
        RAISE NOTICE 'V67 PRECHECK: 1:1 제약 위반 계좌 % 개 발견. CLEAN 단계에서 최신 구독만 유지하고 나머지는 NULL 처리.', v_dup_count;
    END IF;
END $$;

-- ── 2. CLEAN: 한 계좌에 여러 active 구독이 있으면 가장 최근 (subscribed_at 최대) 구독만 유지.
--    나머지 구독의 broker_account_id 를 NULL 로 변경. 구독 자체는 보존.
WITH ranked AS (
    SELECT
        id,
        broker_account_id,
        ROW_NUMBER() OVER (
            PARTITION BY broker_account_id
            ORDER BY subscribed_at DESC, id DESC
        ) AS rn
    FROM strategy_subscriptions
    WHERE status = 'ACTIVE' AND broker_account_id IS NOT NULL
)
UPDATE strategy_subscriptions s
SET broker_account_id = NULL
FROM ranked r
WHERE s.id = r.id AND r.rn > 1;

-- ── 3. POSTCHECK: 위반 0건 검증.
DO $$
DECLARE
    v_remaining INT;
BEGIN
    SELECT COUNT(*) INTO v_remaining
    FROM (
        SELECT broker_account_id
        FROM strategy_subscriptions
        WHERE status = 'ACTIVE' AND broker_account_id IS NOT NULL
        GROUP BY broker_account_id
        HAVING COUNT(*) > 1
    ) t;

    IF v_remaining > 0 THEN
        RAISE EXCEPTION 'V67 차단: CLEAN 이후에도 1:1 제약 위반 % 건 잔존. 수동 점검 필요.', v_remaining;
    END IF;
END $$;

-- ── 4. UNIQUE INDEX: 활성 구독 + 매핑된 계좌 기준 1:1.
--    NULL 매핑은 무제한 허용 (legacy fallback / 사용자 미설정).
--    CANCELLED/PAUSED 상태 구독은 제약 대상 아님.
CREATE UNIQUE INDEX uk_subscription_broker_account_active
    ON strategy_subscriptions (broker_account_id)
    WHERE status = 'ACTIVE' AND broker_account_id IS NOT NULL;

COMMENT ON INDEX uk_subscription_broker_account_active IS
    '계좌-구독 1:1 제약. 한 broker_account 는 동시에 하나의 ACTIVE 구독에만 매핑 가능.';

-- ── 5. users.last_real_activation_at 컬럼 DROP.
--    24h cooldown 정책 폐기. 1:1 제약으로 대체.
--    감사 로그가 필요하면 별도 audit 테이블에서 처리.
ALTER TABLE users DROP COLUMN IF EXISTS last_real_activation_at;
