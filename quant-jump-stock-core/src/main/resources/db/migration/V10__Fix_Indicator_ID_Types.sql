-- V10: Fix indicator table ID column types from INTEGER to BIGINT
-- This migration alters the ID columns in fred_indicators and yfinance_indicators
-- to match the Entity definitions which expect BIGINT

-- =====================================================================
-- 1. Fix fred_indicators ID column
-- =====================================================================
-- Drop dependent objects if any
ALTER TABLE fred_indicators ALTER COLUMN id TYPE BIGINT;

-- Update sequence to bigint
ALTER SEQUENCE fred_indicators_id_seq AS BIGINT;

-- =====================================================================
-- 2. Fix yfinance_indicators ID column
-- =====================================================================
ALTER TABLE yfinance_indicators ALTER COLUMN id TYPE BIGINT;

-- Update sequence to bigint
ALTER SEQUENCE yfinance_indicators_id_seq AS BIGINT;

-- =====================================================================
-- Comments
-- =====================================================================
COMMENT ON COLUMN fred_indicators.id IS 'Primary key - BIGINT for consistency with other entities';
COMMENT ON COLUMN yfinance_indicators.id IS 'Primary key - BIGINT for consistency with other entities';
