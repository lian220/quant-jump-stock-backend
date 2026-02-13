-- KOSPI / KOSDAQ 지수를 yfinance_indicators에 추가
INSERT INTO yfinance_indicators (ticker, name, indicator_type, is_active, created_at, updated_at)
VALUES ('^KS11', 'KOSPI 지수', 'index', true, NOW(), NOW()),
       ('^KQ11', 'KOSDAQ 지수', 'index', true, NOW(), NOW())
ON CONFLICT (ticker) DO NOTHING;
