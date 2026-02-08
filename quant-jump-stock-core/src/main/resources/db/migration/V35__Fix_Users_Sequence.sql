-- ============================================
-- V35: Fix users_id_seq sequence
-- ============================================
-- 문제: V11에서 명시적으로 ID=1,2를 INSERT했지만 sequence를 업데이트하지 않아
--       네이버 로그인 시 중복 키 에러 발생
-- 해결: sequence를 현재 최대 ID로 재설정

-- users 테이블의 sequence를 최대 ID + 1로 재설정
SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 0) + 1 FROM users), false);
