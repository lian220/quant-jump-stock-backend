-- V72: prediction_results 에 axis_contributions + score_coverage 컬럼 추가 (ADR 0006 Phase 2)
-- 작성일: 2026-06-07
--
-- 목적: 설명가능성(XAI) 데이터 브릿지.
--   ADR 0006 §2.9 — composite 산출 과정에서 이미 계산되는 축별 기여값을 버리지 않고 영속화하여
--   화면이 "왜 이 점수인지"(tech/ai/sentiment 기여 막대 + 커버리지)를 표시할 수 있게 한다.
--   Python Data Engine(scoring_spec.yaml SSoT)이 산출, Kotlin Core 는 pass-through 만 한다.
--
--   - axis_contributions: composite(0~100)를 구성한 축별 기여 점수 dict (w·normalized·100).
--     예: {"tech": 42.5, "ai": 18.0, "sentiment": 9.5}. {} = 기여값 없음(결측/레거시).
--   - score_coverage: 점수 산출에 사용된 present 축의 원본 weight 합 (0~1, 재정규화 전).
--     결측 재정규화 후에도 추천 신뢰도를 설명하기 위해 보존. 기본 1.0(전축 present).
--
--   기존 row 백필 안 함 — sync_service 다음 실행 시 채워짐. 기본값으로 하위호환 유지.

ALTER TABLE prediction_results
    ADD COLUMN axis_contributions JSONB DEFAULT '{}'::jsonb,
    ADD COLUMN score_coverage DECIMAL(4,3) DEFAULT 1.0;

COMMENT ON COLUMN prediction_results.axis_contributions IS
    'XAI 축별 기여 점수 (JSONB, 0~100). 예: {"tech":42.5,"ai":18.0,"sentiment":9.5}. {} = 미설정. SSoT=scoring_spec.yaml, Python 산출.';
COMMENT ON COLUMN prediction_results.score_coverage IS
    '점수 산출에 사용된 present 축 원본 weight 합 (0~1, 재정규화 전). 결측 재정규화 후 추천 신뢰도 설명용. 기본 1.0.';
