-- V70: prediction_results 에 veto_reasons + warnings 컬럼 추가 (PR 3a 준비)
-- 작성일: 2026-05-22 (PR 3a)
--
-- 목적:
--   PR 3b 에서 도입될 negative AI veto / disagreement detection 의 감사 추적용 컬럼.
--   본 PR (3a) 는 behavior 변경 없음 — 컬럼만 추가하여 PR 3b 가 머지 가능한 schema 상태로
--   prod 를 미리 정리.
--
--   - veto_reasons: ScoringPolicy.Score.veto_reasons (tuple 의 PG 매핑)
--     예: {"ai_negative"}, {"ai_negative", "ai_source_disagreement"}, NULL/{} = 비veto
--   - warnings: Score.warnings (PR 3c/5 의 VIX gate, soft penalty 등)
--     예: {"ai_source_disagreement_soft"}, {"vix_macro_caution"}
--
--   scoring_regression_prod.py 가 본 컬럼 NULL/empty 인 행에서만 drift 0 기대.

ALTER TABLE prediction_results
    ADD COLUMN veto_reasons TEXT[] DEFAULT NULL,
    ADD COLUMN warnings TEXT[] DEFAULT NULL;

COMMENT ON COLUMN prediction_results.veto_reasons IS
    'ScoringPolicy 정책 차단 사유 (PG ARRAY). 예: {ai_negative, ai_source_disagreement}. NULL/{} = 차단 없음.';
COMMENT ON COLUMN prediction_results.warnings IS
    'ScoringPolicy 비차단 경고 (PG ARRAY). 예: {vix_macro_caution, ai_source_disagreement_soft}.';

-- 기존 row 백필 안 함 — sync_service 다음 실행 시 NULL 유지 (현재 veto/warning 없음).
-- PR 3b 머지 후 첫 sync 부터 채워짐.
