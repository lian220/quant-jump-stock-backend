-- ============================================
-- 초기 데이터 삽입
-- ============================================

-- ============================================
-- 1. Stocks 초기 데이터
-- ============================================
INSERT INTO stocks (ticker, stock_name, stock_name_en, is_etf, leverage_ticker) VALUES
    ('AAPL', '애플', 'Apple Inc.', FALSE, 'AAPU'),
    ('AMAT', '어플라이드 머티리얼즈', 'Applied Materials', FALSE, NULL),
    ('AMD', 'AMD', 'Advanced Micro Devices', FALSE, 'AMDL'),
    ('AMZN', '아마존', 'Amazon.com Inc.', FALSE, 'AMZU'),
    ('APP', '앱플로빈', 'AppLovin', FALSE, 'APPX'),
    ('AVGO', '브로드컴', 'Broadcom Inc.', FALSE, 'AVGG'),
    ('BE', '블룸에너지', 'Bloom Energy', FALSE, 'BEX'),
    ('CLS', '셀레스티카', 'Celestica', FALSE, NULL),
    ('CRDO', '크리도 테크놀로지 그룹 홀딩', 'Credo Technology', FALSE, 'CRDU'),
    ('CRM', '세일즈포스', 'Salesforce Inc.', FALSE, 'CRMG'),
    ('CRWD', '크라우드 스트라이크', 'CrowdStrike', FALSE, 'CRWL'),
    ('GOOGL', '구글 A', 'Alphabet Inc. Class A', FALSE, 'GGLL'),
    ('HOOD', '로빈후드', 'Robinhood', FALSE, 'HODU'),
    ('INTC', '인텔', 'Intel Corporation', FALSE, 'INTW'),
    ('JNJ', '존슨앤존슨', 'Johnson & Johnson', FALSE, NULL),
    ('LLY', '일라이릴리', 'Eli Lilly', FALSE, 'ELIL'),
    ('META', '메타', 'Meta Platforms Inc.', FALSE, 'FBL'),
    ('MSFT', '마이크로소프트', 'Microsoft Corporation', FALSE, 'MSFU'),
    ('MU', '마이크론', 'Micron Technology', FALSE, 'MUU'),
    ('NBIS', '네비우스 그룹', 'Nebius Group', FALSE, 'NEBX'),
    ('NVDA', '엔비디아', 'NVIDIA Corporation', FALSE, 'NVDL'),
    ('OKLO', '오클로', 'Oklo Inc.', FALSE, 'OKLL'),
    ('ORCL', '오라클', 'Oracle Corporation', FALSE, 'ORCX'),
    ('PANW', '팔로알토 네트웍스', 'Palo Alto Networks', FALSE, 'PALU'),
    ('PLTR', '팔란티어', 'Palantir Technologies', FALSE, 'PTIR'),
    ('QQQ', 'QQQ ETF', 'Invesco QQQ Trust', TRUE, 'TQQQ'),
    ('SNOW', '스노우플레이크', 'Snowflake Inc.', FALSE, 'SNOU'),
    ('SOXX', 'SOXX ETF', 'iShares Semiconductor ETF', TRUE, 'SOXL'),
    ('SPY', 'S&P 500 ETF', 'SPDR S&P 500 ETF Trust', TRUE, 'UPRO'),
    ('TSLA', '테슬라', 'Tesla Inc.', FALSE, 'TSLL'),
    ('TSM', 'TSMC', 'Taiwan Semiconductor', FALSE, 'TSMG'),
    ('TXN', '텍사스 인스트루먼트', 'Texas Instruments', FALSE, NULL),
    ('VRT', '버티브 홀딩스', 'Vertiv Holdings', FALSE, 'VRTL'),
    ('VST', '비스트라 에너지', 'Vistra Energy', FALSE, 'VSTL'),
    ('WMT', '월마트', 'Walmart Inc.', FALSE, NULL)
ON CONFLICT (ticker) DO NOTHING;

-- ============================================
-- 2. FRED Indicators 초기 데이터
-- ============================================
INSERT INTO fred_indicators (code, name, category) VALUES
    ('T10YIE', '10년 기대 인플레이션율', 'economic'),
    ('T10Y2Y', '장단기 금리차', 'economic'),
    ('FEDFUNDS', '기준금리', 'economic'),
    ('UMCSENT', '미시간대 소비자 심리지수', 'economic'),
    ('UNRATE', '실업률', 'economic'),
    ('DGS2', '2년 만기 미국 국채 수익률', 'economic'),
    ('DGS10', '10년 만기 미국 국채 수익률', 'economic'),
    ('STLFSI4', '금융스트레스지수', 'economic'),
    ('PCE', '개인 소비 지출', 'economic'),
    ('CPIAUCSL', '소비자 물가지수', 'economic'),
    ('MORTGAGE5US', '5년 변동금리 모기지', 'economic'),
    ('DTWEXM', '미국 달러 환율', 'economic'),
    ('M2', '통화 공급량 M2', 'economic'),
    ('TDSP', '가계 부채 비율', 'economic'),
    ('GDPC1', 'GDP 성장률', 'economic'),
    ('NASDAQCOM', '나스닥 종합지수', 'index')
ON CONFLICT (code) DO NOTHING;

-- ============================================
-- 3. Yahoo Finance Indicators 초기 데이터
-- ============================================
INSERT INTO yfinance_indicators (ticker, name, indicator_type) VALUES
    ('^GSPC', 'S&P 500 지수', 'index'),
    ('GC=F', '금 가격', 'commodity'),
    ('DX-Y.NYB', '달러 인덱스', 'index'),
    ('^NDX', '나스닥 100', 'index'),
    ('SPY', 'S&P 500 ETF', 'etf'),
    ('QQQ', 'QQQ ETF', 'etf'),
    ('IWM', '러셀 2000 ETF', 'etf'),
    ('DIA', '다우 존스 ETF', 'etf'),
    ('^VIX', 'VIX 지수', 'index'),
    ('^N225', '닛케이 225', 'index'),
    ('000001.SS', '상해종합', 'index'),
    ('^HSI', '항셍', 'index'),
    ('^FTSE', '영국 FTSE', 'index'),
    ('^GDAXI', '독일 DAX', 'index'),
    ('^FCHI', '프랑스 CAC 40', 'index'),
    ('AGG', '미국 전체 채권시장 ETF', 'etf'),
    ('TIP', 'TIPS ETF', 'etf'),
    ('LQD', '투자등급 회사채 ETF', 'etf'),
    ('JPY=X', '달러/엔', 'currency'),
    ('CNY=X', '달러/위안', 'currency'),
    ('VNQ', '미국 리츠 ETF', 'etf'),
    ('SOXX', 'SOXX ETF', 'etf')
ON CONFLICT (ticker) DO NOTHING;
