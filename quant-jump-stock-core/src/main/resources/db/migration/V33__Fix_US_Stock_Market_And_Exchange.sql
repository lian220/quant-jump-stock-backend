-- ============================================
-- V33: US 종목 market/exchange 데이터 수정
-- V28에서 exchange IS NULL인 종목들이 market='KR'로 남아있는 문제 해결
-- ============================================

-- 1. 모든 초기 종목의 market을 'US'로 설정
UPDATE stocks SET market = 'US'
WHERE ticker IN (
    'AAPL','AMAT','AMD','AMZN','APP','AVGO','BE','CLS',
    'CRDO','CRM','CRWD','GOOGL','HOOD','INTC','JNJ','LLY',
    'META','MSFT','MU','NBIS','NVDA','OKLO','ORCL','PANW',
    'PLTR','QQQ','SNOW','SOXX','SPY','TSLA','TSM','TXN',
    'VRT','VST','WMT'
);

-- 2. NASDAQ 거래소 설정 (exchange IS NULL인 경우만)
UPDATE stocks SET exchange = 'NASDAQ'
WHERE ticker IN (
    'AAPL','AMAT','AMD','AMZN','APP','AVGO','CRDO','CRM',
    'CRWD','GOOGL','HOOD','INTC','LLY','META','MSFT','MU',
    'NBIS','NVDA','OKLO','ORCL','PANW','PLTR','QQQ','SNOW',
    'SOXX','SPY','TSLA','TSM','TXN','VRT','VST'
) AND exchange IS NULL;

-- 3. NYSE 거래소 설정 (exchange IS NULL인 경우만)
UPDATE stocks SET exchange = 'NYSE'
WHERE ticker IN ('BE','CLS','JNJ','WMT')
AND exchange IS NULL;
