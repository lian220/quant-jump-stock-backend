-- ============================================
-- V8: Stock 중복 데이터 제거
-- 생성일: 2026-02-01
-- 목적: ticker 기준 중복 제거 (id가 작은 것 삭제)
-- ============================================

-- 중복된 stock 데이터 제거 (같은 ticker가 여러 개 있으면 최신 것만 유지)
DELETE FROM stocks a
USING stocks b
WHERE a.id < b.id
  AND a.ticker = b.ticker;

-- 삭제된 행 수 확인 (로그용)
-- 이 쿼리는 마이그레이션 후 수동으로 확인 가능
-- SELECT ticker, COUNT(*) as count FROM stocks GROUP BY ticker HAVING COUNT(*) > 1;

-- 인덱스 재구성 (성능 향상)
REINDEX INDEX uq_stocks_ticker;
REINDEX INDEX idx_stocks_ticker;

-- 완료 로그
DO $$
BEGIN
    RAISE NOTICE '✅ Stock 중복 제거 완료';
END $$;
