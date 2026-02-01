-- ============================================
-- Stock 초기 데이터
-- 자동 생성됨: 2026-02-01
-- ⚠️ 중복 방지: ON CONFLICT DO NOTHING 사용
-- ============================================

INSERT INTO stocks (ticker, stock_name, stock_name_en, is_etf, leverage_ticker, exchange, sector, industry, is_active)
VALUES
    ('AAPL', '애플', NULL, FALSE, 'AAPU', NULL, NULL, NULL, TRUE),
    ('AMAT', '어플라이드 머티리얼즈', NULL, FALSE, NULL, NULL, NULL, NULL, TRUE),
    ('AMD', 'AMD', NULL, FALSE, 'AMDL', NULL, NULL, NULL, TRUE),
    ('AMZN', '아마존', NULL, FALSE, 'AMZU', NULL, NULL, NULL, TRUE),
    ('APP', '앱플로빈', NULL, FALSE, 'APPX', NULL, NULL, NULL, TRUE),
    ('AVGO', '브로드컴', NULL, FALSE, 'AVGG', NULL, NULL, NULL, TRUE),
    ('BE', '블룸에너지', NULL, FALSE, 'BEX', NULL, NULL, NULL, TRUE),
    ('CLS', '셀레스티카', NULL, FALSE, NULL, NULL, NULL, NULL, TRUE),
    ('CRDO', '크리도 테크놀로지 그룹 홀딩', NULL, FALSE, 'CRDU', NULL, NULL, NULL, TRUE),
    ('CRM', '세일즈포스', NULL, FALSE, 'CRMG', NULL, NULL, NULL, TRUE),
    ('CRWD', '크라우드 스트라이크', NULL, FALSE, 'CRWL', NULL, NULL, NULL, TRUE),
    ('GOOGL', '구글 A', NULL, FALSE, 'GGLL', NULL, NULL, NULL, TRUE),
    ('HOOD', '로빈후드', NULL, FALSE, 'HODU', NULL, NULL, NULL, TRUE),
    ('INTC', '인텔', NULL, FALSE, 'INTW', NULL, NULL, NULL, TRUE),
    ('JNJ', '존슨앤존슨', NULL, FALSE, NULL, NULL, NULL, NULL, TRUE),
    ('LLY', '일라이릴리', NULL, FALSE, 'ELIL', NULL, NULL, NULL, TRUE),
    ('META', '메타', NULL, FALSE, 'FBL', NULL, NULL, NULL, TRUE),
    ('MSFT', '마이크로소프트', NULL, FALSE, 'MSFU', NULL, NULL, NULL, TRUE),
    ('MU', '마이크론', NULL, FALSE, 'MUU', NULL, NULL, NULL, TRUE),
    ('NBIS', '네비우스 그룹', NULL, FALSE, 'NEBX', NULL, NULL, NULL, TRUE),
    ('NVDA', '엔비디아', NULL, FALSE, 'NVDL', NULL, NULL, NULL, TRUE),
    ('OKLO', '오클로', NULL, FALSE, 'OKLL', NULL, NULL, NULL, TRUE),
    ('ORCL', '오라클', NULL, FALSE, 'ORCX', NULL, NULL, NULL, TRUE),
    ('PANW', '팔로알토 네트웍스', NULL, FALSE, 'PALU', NULL, NULL, NULL, TRUE),
    ('PLTR', '팔란티어', NULL, FALSE, 'PTIR', NULL, NULL, NULL, TRUE),
    ('QQQ', 'QQQ ETF', NULL, TRUE, 'TQQQ', NULL, NULL, NULL, TRUE),
    ('SNOW', '스노우플레이크', NULL, FALSE, 'SNOU', NULL, NULL, NULL, TRUE),
    ('SOXX', 'SOXX ETF', NULL, TRUE, 'SOXL', NULL, NULL, NULL, TRUE),
    ('SPY', 'S&P 500 ETF', NULL, TRUE, 'UPRO', NULL, NULL, NULL, TRUE),
    ('TSLA', '테슬라', NULL, FALSE, 'TSLL', NULL, NULL, NULL, TRUE),
    ('TSM', 'TSMC', NULL, FALSE, 'TSMG', NULL, NULL, NULL, TRUE),
    ('TXN', '텍사스 인스트루먼트', NULL, FALSE, NULL, NULL, NULL, NULL, TRUE),
    ('VRT', '버티브 홀딩스', NULL, FALSE, 'VRTL', NULL, NULL, NULL, TRUE),
    ('VST', '비스트라 에너지', NULL, FALSE, 'VSTL', NULL, NULL, NULL, TRUE),
    ('WMT', '월마트', NULL, FALSE, NULL, NULL, NULL, NULL, TRUE)
ON CONFLICT (ticker) DO NOTHING;

-- Total: 35 stocks
-- ✅ 중복 방지: ticker가 이미 존재하면 무시
