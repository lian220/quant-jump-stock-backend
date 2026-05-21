-- V69: prediction_results 에 recommendation_label + effective_prediction_date 추가
-- 작성일: 2026-05-21 (PR 2)
--
-- 목적:
--   1. recommendation_label: ScoringPolicy 의 STRONG/RECOMMEND/WATCH/NONE 라벨 SSoT 저장
--      (PR 1 까지는 sync_service 가 composite_grade 만 저장. 라벨은 Slack 표시 시 재계산.
--       Kotlin Core API 가 동일 라벨 표시하려면 컬럼 필요.)
--   2. effective_prediction_date: AI 예측 데이터의 실제 출처 날짜
--      (PR 1 에서 comprehensive_report 7일 fallback 발동 시 analysis_date 와 다른 날짜
--       데이터 사용 가능. 사용자/감사 추적을 위해 명시.)

ALTER TABLE prediction_results
    ADD COLUMN recommendation_label VARCHAR(20),
    ADD COLUMN effective_prediction_date DATE;

COMMENT ON COLUMN prediction_results.recommendation_label IS
    'ScoringPolicy 라벨 SSoT: STRONG/RECOMMEND/WATCH/NONE (NULL = 미설정, sync_service 후처리 누락 케이스)';
COMMENT ON COLUMN prediction_results.effective_prediction_date IS
    'AI 예측 데이터 실제 사용 날짜. analysis_date 와 같으면 정확한 날짜, 다르면 fallback 발동 (최대 7일 lookback).';

-- 기존 row 백필 정책: 안 함 (sync_service 가 다음 실행 시 채움). 라벨은 composite_grade 에서 유추 가능.
