-- V73: prediction_results 에 formula_version 컬럼 추가 (ADR 0006 §5 롤백/시계열 단절 대응)
-- 작성일: 2026-06-07
--
-- 목적: 점수 산식 버전 추적 (score_version).
--   ADR 0006 §5 Consequences — 점수 스케일이 0~7.4 → 0~100 으로 바뀌어 과거 점수와 값이 단절된다
--   (어제 52 → 오늘 70). row 별로 산식 버전을 남겨야 ① 신/구 점수 구분 ② 롤백 대상 식별
--   ③ 재계산 범위 산정 ④ BETA 커뮤니케이션(어떤 버전 점수인지)이 가능하다.
--
--   - formula_version: scoring_spec.yaml.formula_version (예: "2.0.0"). Python Data Engine 이 산출 시 기록.
--     기존 row(NULL) = 구 산식(0~7.4) 시절. sync_service 다음 실행 시 채워짐. 하위호환 유지.

ALTER TABLE prediction_results
    ADD COLUMN formula_version VARCHAR(20);

COMMENT ON COLUMN prediction_results.formula_version IS
    '점수 산식 버전 (scoring_spec.yaml.formula_version, 예: 2.0.0). NULL=구 산식(0~7.4). 롤백/시계열 단절 식별용. SSoT=scoring_spec.yaml.';
