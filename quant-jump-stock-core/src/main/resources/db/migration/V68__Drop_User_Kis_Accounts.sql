-- Phase 1D — deprecated user_kis_accounts 테이블 폐기.
--
-- 배경:
--   V64 에서 user_kis_accounts 데이터를 user_broker_accounts (broker='KIS') 로 마이그 완료.
--   2026-05-19: 외부에서 user_kis_accounts 호출처 없음 확인.
--   BE 의존 코드 (BalanceService / KisApiAdapter / CloudSchedulerController / AppSecretReencryptionRunner)
--   를 모두 user_broker_accounts 로 마이그 + UserKisAccount* 파일 일괄 삭제.
--
-- 안전성:
--   1) PRECHECK: user_kis_accounts row 수와 user_broker_accounts (broker='KIS') 의
--      활성 row 수가 일치해야 함 (deleted_at 무관). V64 의 POSTCHECK 가 이미 보장.
--   2) 본 마이그는 단순 DROP — rollback 시 V64 로직 재실행 필요 (스크립트 별도 보관).

-- ── 1. PRECHECK: row 수 동치 (감사 로그).
DO $$
DECLARE
    v_src INT;
    v_dst INT;
BEGIN
    SELECT COUNT(*) INTO v_src FROM user_kis_accounts;
    SELECT COUNT(*) INTO v_dst FROM user_broker_accounts WHERE broker = 'KIS';

    RAISE NOTICE 'V68 PRECHECK: user_kis_accounts(%) vs user_broker_accounts.KIS(%)', v_src, v_dst;

    -- src 가 0 이면 V64 이후 user_kis_accounts 로 신규 등록 안 됨 (의도된 상태). 통과.
    -- src > 0 이면 dst 도 그 이상 이어야 함 (V64 가 1:1 복사 + 휴지통 row 포함 복사).
    IF v_src > 0 AND v_dst < v_src THEN
        RAISE EXCEPTION 'V68 차단: user_kis_accounts(%) > user_broker_accounts.KIS(%). V64 데이터 마이그 미완료 가능성. 수동 점검 필요.', v_src, v_dst;
    END IF;
END $$;

-- ── 2. DROP — 외래키 의존성 없음 (V63 부터 user_broker_accounts 가 별도 테이블).
DROP TABLE IF EXISTS user_kis_accounts;
