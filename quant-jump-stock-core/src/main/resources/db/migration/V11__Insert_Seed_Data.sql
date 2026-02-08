-- ============================================
-- V11: 시드 데이터 통합 삽입
-- 기존 V4, V8, V13, V15, V18, V26, V33 시드 데이터 통합
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
-- 2. Stocks market/exchange 데이터 설정 (V33)
-- ============================================

-- 모든 초기 종목의 market을 'US'로 설정
UPDATE stocks SET market = 'US'
WHERE ticker IN (
    'AAPL','AMAT','AMD','AMZN','APP','AVGO','BE','CLS',
    'CRDO','CRM','CRWD','GOOGL','HOOD','INTC','JNJ','LLY',
    'META','MSFT','MU','NBIS','NVDA','OKLO','ORCL','PANW',
    'PLTR','QQQ','SNOW','SOXX','SPY','TSLA','TSM','TXN',
    'VRT','VST','WMT'
);

-- NASDAQ 거래소 설정 (exchange IS NULL인 경우만)
UPDATE stocks SET exchange = 'NASDAQ'
WHERE ticker IN (
    'AAPL','AMAT','AMD','AMZN','APP','AVGO','CRDO','CRM',
    'CRWD','GOOGL','HOOD','INTC','LLY','META','MSFT','MU',
    'NBIS','NVDA','OKLO','ORCL','PANW','PLTR','QQQ','SNOW',
    'SOXX','SPY','TSLA','TSM','TXN','VRT','VST'
) AND exchange IS NULL;

-- NYSE 거래소 설정 (exchange IS NULL인 경우만)
UPDATE stocks SET exchange = 'NYSE'
WHERE ticker IN ('BE','CLS','JNJ','WMT')
AND exchange IS NULL;

-- ============================================
-- 3. FRED Indicators 초기 데이터
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
-- 4. Yahoo Finance Indicators 초기 데이터
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

-- ============================================
-- 5. RBAC - Roles 생성
-- ============================================
INSERT INTO roles (name, description, is_system) VALUES
    ('SUPER_ADMIN', '슈퍼 관리자 - 모든 권한', TRUE),
    ('ADMIN', '관리자 - 시스템 관리 권한', TRUE),
    ('MODERATOR', '운영자 - 콘텐츠 관리 권한', TRUE),
    ('USER', '일반 사용자', TRUE)
ON CONFLICT (name) DO NOTHING;

-- ============================================
-- 6. RBAC - Permissions 생성
-- ============================================
INSERT INTO permissions (code, name, description, category) VALUES
    -- User Management
    ('USER_VIEW', '사용자 조회', '사용자 정보 조회', 'USER_MANAGEMENT'),
    ('USER_CREATE', '사용자 생성', '새 사용자 생성', 'USER_MANAGEMENT'),
    ('USER_UPDATE', '사용자 수정', '사용자 정보 수정', 'USER_MANAGEMENT'),
    ('USER_DELETE', '사용자 삭제', '사용자 삭제', 'USER_MANAGEMENT'),
    ('USER_ROLE_ASSIGN', '역할 할당', '사용자에게 역할 할당', 'USER_MANAGEMENT'),

    -- Strategy Management
    ('STRATEGY_VIEW', '전략 조회', '거래 전략 조회', 'STRATEGY'),
    ('STRATEGY_CREATE', '전략 생성', '거래 전략 생성', 'STRATEGY'),
    ('STRATEGY_UPDATE', '전략 수정', '거래 전략 수정', 'STRATEGY'),
    ('STRATEGY_DELETE', '전략 삭제', '거래 전략 삭제', 'STRATEGY'),
    ('STRATEGY_APPROVE', '전략 승인', '거래 전략 승인', 'STRATEGY'),
    ('STRATEGY_PUBLISH', '전략 게시', '거래 전략 게시', 'STRATEGY'),

    -- Backtest
    ('BACKTEST_RUN', '백테스트 실행', '백테스트 실행', 'BACKTEST'),
    ('BACKTEST_VIEW_ALL', '모든 백테스트 조회', '모든 사용자 백테스트 조회', 'BACKTEST'),
    ('BACKTEST_UNLIMITED', '무제한 백테스트', '백테스트 횟수 제한 없음', 'BACKTEST'),

    -- Analytics
    ('ANALYTICS_VIEW', '분석 조회', '거래 분석 데이터 조회', 'ANALYTICS'),
    ('ANALYTICS_EXPORT', '분석 내보내기', '분석 데이터 내보내기', 'ANALYTICS'),

    -- System
    ('SYSTEM_SETTINGS', '시스템 설정', '시스템 설정 관리', 'SYSTEM'),
    ('SYSTEM_LOGS', '시스템 로그', '시스템 로그 조회', 'SYSTEM'),
    ('SYSTEM_MAINTENANCE', '시스템 유지보수', '시스템 유지보수 작업', 'SYSTEM'),

    -- Subscription & Payment
    ('SUBSCRIPTION_VIEW', '구독 조회', '구독 정보 조회', 'SUBSCRIPTION'),
    ('SUBSCRIPTION_MANAGE', '구독 관리', '구독 관리', 'SUBSCRIPTION'),
    ('PAYMENT_VIEW', '결제 조회', '결제 정보 조회', 'PAYMENT'),
    ('PAYMENT_REFUND', '결제 환불', '결제 환불 처리', 'PAYMENT'),

    -- Trading
    ('TRADING_VIEW', '거래 조회', '거래 내역 조회', 'TRADING'),
    ('TRADING_EXECUTE', '거래 실행', '거래 주문 실행', 'TRADING')
ON CONFLICT (code) DO NOTHING;

-- ============================================
-- 7. RBAC - Role-Permission 매핑
-- ============================================

-- SUPER_ADMIN: 모든 권한
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ADMIN: 시스템 관리 권한 (USER, STRATEGY, BACKTEST, ANALYTICS, SYSTEM, SUBSCRIPTION)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
AND p.category IN ('USER_MANAGEMENT', 'STRATEGY', 'BACKTEST', 'ANALYTICS', 'SYSTEM', 'SUBSCRIPTION')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- MODERATOR: 콘텐츠 관리 권한 (STRATEGY, ANALYTICS)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'MODERATOR'
AND p.category IN ('STRATEGY', 'ANALYTICS')
AND p.code NOT IN ('STRATEGY_DELETE', 'STRATEGY_APPROVE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- USER: 기본 사용자 권한 (조회 및 자신의 데이터 관리)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'USER'
AND p.code IN ('STRATEGY_VIEW', 'BACKTEST_RUN', 'ANALYTICS_VIEW', 'TRADING_VIEW', 'TRADING_EXECUTE', 'SUBSCRIPTION_VIEW')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================
-- 8. Admin 사용자 생성
-- ============================================

-- pgcrypto extension 활성화 (BCrypt 지원)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Admin 사용자 생성 (BCrypt로 비밀번호 해싱)
INSERT INTO users (user_id, name, email, password_hash, status)
VALUES (
    'lian',
    'Admin User',
    'admin@quantiq.com',
    crypt('sfn0008', gen_salt('bf', 10)),
    'ACTIVE'
)
ON CONFLICT (user_id) DO NOTHING;

-- Admin 사용자에게 ADMIN 역할 할당
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.user_id = 'lian'
AND r.name = 'ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ============================================
-- 9. Trading Config 초기화
-- ============================================
INSERT INTO trading_configs (user_id, enabled, auto_trading_enabled)
SELECT id, FALSE, FALSE
FROM users
WHERE user_id = 'lian'
ON CONFLICT (user_id) DO NOTHING;

-- ============================================
-- 10. Account Balance 초기화
-- ============================================
INSERT INTO account_balances (user_id, cash, total_value)
SELECT id, 0.00, 0.00
FROM users
WHERE user_id = 'lian'
ON CONFLICT (user_id) DO NOTHING;

-- ============================================
-- 11. User Tiers 초기화 (기존 사용자 FREE 티어)
-- ============================================
INSERT INTO user_tiers (user_id, tier)
SELECT id, 'FREE'
FROM users
WHERE id NOT IN (SELECT user_id FROM user_tiers)
ON CONFLICT (user_id) DO NOTHING;

-- ============================================
-- 12. DSL 형식 시드 전략 (V18 기반, 총 20개)
-- ============================================

-- =====================================================================
-- MOMENTUM 전략 (6개)
-- =====================================================================

-- 12.1 골든크로스
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'golden_cross',
    '골든크로스',
    '단기(20일) 이동평균선이 장기(50일) 이동평균선을 상향 돌파할 때 매수하는 모멘텀 전략입니다. 추세 전환의 강력한 신호로 활용됩니다.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "golden_cross_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "단기 이평선이 장기 이평선 상향 돌파"
            },
            {
                "name": "death_cross_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_below", "value": "sma_50"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "단기 이평선이 장기 이평선 하향 돌파"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.15,
            "max_position_pct": 0.1
        },
        "tags": ["trend-following", "momentum", "classic"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.2 RSI 과매도 반등
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'rsi_oversold',
    'RSI 과매도 반등',
    'RSI(상대강도지수)가 30 이하 과매도 구간에서 매수하고, 70 이상 과매수 구간에서 매도하는 역추세 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "rsi_oversold_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 30}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "RSI 과매도 구간 진입"
            },
            {
                "name": "rsi_overbought_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 70}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "RSI 과매수 구간 진입"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.03,
            "take_profit_pct": 0.1,
            "max_position_pct": 0.1
        },
        "tags": ["mean-reversion", "oscillator"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.3 MACD 크로스오버
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'macd_crossover',
    'MACD 크로스오버',
    'MACD선이 시그널선을 상향 돌파할 때 매수하는 추세추종 전략입니다. 중장기 추세 파악에 효과적입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "macd_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "macd", "params": {}, "operator": "crosses_above", "value": "macd_signal"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "MACD 상향 돌파"
            },
            {
                "name": "macd_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "macd", "params": {}, "operator": "crosses_below", "value": "macd_signal"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "MACD 하향 돌파"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.04,
            "take_profit_pct": 0.12,
            "max_position_pct": 0.1
        },
        "tags": ["trend-following", "momentum"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.4 볼린저 밴드 스퀴즈
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'bollinger_squeeze',
    '볼린저 밴드 스퀴즈',
    '가격이 볼린저 하단 밴드 아래면 매수, 상단 밴드 위면 매도하는 평균회귀 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "bollinger_lower_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "가격이 볼린저 하단 밴드 하회"
            },
            {
                "name": "bollinger_upper_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_upper"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "가격이 볼린저 상단 밴드 상회"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.03,
            "take_profit_pct": 0.08,
            "max_position_pct": 0.1
        },
        "tags": ["mean-reversion", "volatility"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.5 모멘텀 브레이크아웃
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'momentum_breakout',
    '모멘텀 브레이크아웃',
    '가격이 20일 EMA 위에 있고 RSI가 50 이상이면 상승 모멘텀으로 판단하는 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "momentum_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "ema_20"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 50}
                ],
                "logic": "and",
                "weight": 0.9,
                "description": "상승 모멘텀 확인"
            },
            {
                "name": "momentum_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "ema_20"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 50}
                ],
                "logic": "and",
                "weight": 0.9,
                "description": "하락 모멘텀 확인"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.04,
            "take_profit_pct": 0.12,
            "max_position_pct": 0.1
        },
        "tags": ["momentum", "trend-following"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.6 추세 추종
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'trend_following',
    '추세 추종',
    '장기 추세를 따라 매매하는 전략입니다. 50일선 위에서 매수, 아래서 매도합니다.',
    (SELECT id FROM strategy_categories WHERE code = 'MOMENTUM'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "uptrend_entry",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"},
                    {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"}
                ],
                "logic": "and",
                "weight": 0.85,
                "description": "상승 추세 진입"
            },
            {
                "name": "downtrend_exit",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "sma_50"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "하락 추세 이탈"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.07,
            "take_profit_pct": 0.25,
            "max_position_pct": 0.12,
            "trailing_stop": true,
            "trailing_stop_pct": 0.05
        },
        "tags": ["trend-following", "long-term"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- =====================================================================
-- QUANT_COMPOSITE 전략 (4개)
-- =====================================================================

-- 12.7 트리플 스크린
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'triple_screen',
    '트리플 스크린',
    '골든크로스 + RSI 비과매수 + MACD 상승 3가지 조건을 동시에 충족할 때만 매수하는 보수적인 복합 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'QUANT_COMPOSITE'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "triple_screen_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 70},
                    {"indicator": "macd", "params": {}, "operator": "gt", "value": "macd_signal"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "3중 필터 조건 충족"
            },
            {
                "name": "triple_screen_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "lt", "value": "sma_50"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 30},
                    {"indicator": "macd", "params": {}, "operator": "lt", "value": "macd_signal"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "3중 매도 조건 충족"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.2,
            "max_position_pct": 0.08
        },
        "tags": ["multi-indicator", "trend-following"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.8 평균 회귀
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'mean_reversion',
    '평균 회귀',
    '가격이 SMA에서 크게 벗어나면 평균으로 회귀할 것을 기대하는 역추세 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'QUANT_COMPOSITE'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "oversold_bounce",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 25},
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "극단적 과매도 반등 기대"
            },
            {
                "name": "overbought_pullback",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 75},
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_upper"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "극단적 과매수 조정 기대"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.02,
            "take_profit_pct": 0.06,
            "max_position_pct": 0.08
        },
        "tags": ["mean-reversion", "counter-trend"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.9 듀얼 모멘텀
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'dual_momentum',
    '듀얼 모멘텀',
    '절대 모멘텀과 상대 모멘텀을 결합한 전략입니다. 200일선 위에서 RSI와 MACD 확인 후 매수합니다.',
    (SELECT id FROM strategy_categories WHERE code = 'QUANT_COMPOSITE'),
    true,
    true,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "dual_momentum_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 50},
                    {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "절대+상대 모멘텀 강세"
            },
            {
                "name": "dual_momentum_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "sma_200"}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "장기 추세 이탈"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.08,
            "take_profit_pct": 0.3,
            "max_position_pct": 0.15
        },
        "tags": ["momentum", "long-term"]
    }'::jsonb,
    'MONTHLY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.10 RSI 스캘핑
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'scalping_rsi',
    'RSI 스캘핑',
    '단기 RSI(7일) 극단값에서 빠른 진입/청산하는 단타 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'QUANT_COMPOSITE'),
    true,
    true,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "quick_oversold",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 7}, "operator": "lt", "value": 20}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "단기 RSI 극과매도"
            },
            {
                "name": "quick_overbought",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 7}, "operator": "gt", "value": 80}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "단기 RSI 극과매수"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.015,
            "take_profit_pct": 0.03,
            "max_position_pct": 0.05
        },
        "tags": ["scalping", "short-term", "mean-reversion"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- =====================================================================
-- VALUE 전략 (2개)
-- =====================================================================

-- 12.11 저PER 가치주
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'low_per_value',
    '저PER 가치주',
    'PER이 10 미만이고 업종 평균 대비 30% 이상 저평가된 종목을 발굴하는 전통적인 가치투자 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'VALUE'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "low_per_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "per", "params": {"type": "fundamental"}, "operator": "lt", "value": 10},
                    {"indicator": "per_sector_ratio", "params": {}, "operator": "lt", "value": 0.7}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "저PER + 업종 대비 저평가"
            },
            {
                "name": "high_per_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "per", "params": {"type": "fundamental"}, "operator": "gt", "value": 20}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "PER 고평가 구간"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.1,
            "take_profit_pct": 0.3,
            "max_position_pct": 0.1
        },
        "tags": ["value", "fundamental", "long-term"]
    }'::jsonb,
    'MONTHLY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.12 고배당 가치주
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'high_dividend',
    '고배당 가치주',
    '배당수익률 4% 이상, PBR 1 미만의 안정적인 배당주에 투자하는 인컴형 가치투자 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'VALUE'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "high_dividend_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "dividend_yield", "params": {"type": "fundamental"}, "operator": "gt", "value": 4.0},
                    {"indicator": "pbr", "params": {"type": "fundamental"}, "operator": "lt", "value": 1.0}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "고배당 + 저PBR"
            },
            {
                "name": "low_dividend_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "dividend_yield", "params": {"type": "fundamental"}, "operator": "lt", "value": 2.0}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "배당수익률 하락"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.1,
            "take_profit_pct": 0.25,
            "max_position_pct": 0.1
        },
        "tags": ["value", "dividend", "income"]
    }'::jsonb,
    'QUARTERLY',
    '1.0',
    NOW(),
    NOW()
);

-- =====================================================================
-- ASSET_ALLOCATION 전략 (3개)
-- =====================================================================

-- 12.13 주식-채권 밸런스 60/40
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'balanced_60_40',
    '주식-채권 밸런스 60/40',
    '전통적인 60% 주식 + 40% 채권 포트폴리오입니다. 목표 비중에서 5% 이상 벗어나면 리밸런싱합니다.',
    (SELECT id FROM strategy_categories WHERE code = 'ASSET_ALLOCATION'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "rebalance_check",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "portfolio_drift", "params": {}, "operator": "gt", "value": 0.05}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "목표 비중에서 5% 이상 이탈 시 리밸런싱"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.15,
            "take_profit_pct": 0.3,
            "max_position_pct": 0.6
        },
        "allocation": {
            "equity": {"weight": 0.6, "ticker": "KODEX200", "name": "코덱스200"},
            "bond": {"weight": 0.4, "ticker": "KOSEF국고채10년", "name": "코세프국고채10년"}
        },
        "tags": ["asset-allocation", "balanced", "classic"]
    }'::jsonb,
    'QUARTERLY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.14 금리 연동 동적 배분
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'rate_linked_allocation',
    '금리 연동 동적 배분',
    'FRED 10년물 국채금리에 따라 주식/채권 비중을 자동 조절하는 동적 자산배분 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'ASSET_ALLOCATION'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "low_rate_equity",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "treasury_10y", "params": {"source": "fred"}, "operator": "lt", "value": 2}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "저금리: 주식 80%"
            },
            {
                "name": "high_rate_bond",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "treasury_10y", "params": {"source": "fred"}, "operator": "gte", "value": 4}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "고금리: 주식 40%"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.12,
            "take_profit_pct": 0.25,
            "max_position_pct": 0.8
        },
        "allocation_rules": [
            {"condition": "treasury_10y < 2", "equity": 0.8, "bond": 0.2},
            {"condition": "treasury_10y >= 2 AND treasury_10y < 4", "equity": 0.6, "bond": 0.4},
            {"condition": "treasury_10y >= 4", "equity": 0.4, "bond": 0.6}
        ],
        "tags": ["asset-allocation", "dynamic", "macro"]
    }'::jsonb,
    'MONTHLY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.15 올웨더 포트폴리오
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'all_weather',
    '올웨더 포트폴리오',
    '레이 달리오의 사계절 포트폴리오를 한국 ETF로 구현한 전략입니다. 어떤 경제 환경에서도 안정적인 수익을 추구합니다.',
    (SELECT id FROM strategy_categories WHERE code = 'ASSET_ALLOCATION'),
    true,
    true,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "rebalance_trigger",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "portfolio_drift", "params": {}, "operator": "gt", "value": 0.05}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "목표 비중에서 5% 이상 이탈 시 리밸런싱"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.1,
            "take_profit_pct": 0.2,
            "max_position_pct": 0.4
        },
        "allocation": {
            "stocks": {"weight": 0.30, "ticker": "KODEX200", "name": "주식"},
            "long_term_bonds": {"weight": 0.40, "ticker": "KOSEF국고채10년", "name": "장기채권"},
            "mid_term_bonds": {"weight": 0.15, "ticker": "KBSTAR중기우량회사채", "name": "중기채권"},
            "gold": {"weight": 0.075, "ticker": "KODEX골드선물(H)", "name": "금"},
            "commodities": {"weight": 0.075, "ticker": "KODEX WTI원유선물(H)", "name": "원자재"}
        },
        "tags": ["asset-allocation", "all-weather", "ray-dalio"]
    }'::jsonb,
    'YEARLY',
    '1.0',
    NOW(),
    NOW()
);

-- =====================================================================
-- SEASONAL 전략 (3개)
-- =====================================================================

-- 12.16 1월 효과
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'january_effect',
    '1월 효과',
    '연말 세금 매도 후 1월에 소형주가 반등하는 계절적 패턴을 활용합니다. 12월 20일 매수, 1월 31일 매도.',
    (SELECT id FROM strategy_categories WHERE code = 'SEASONAL'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "january_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "calendar", "params": {"month": 12, "day_gte": 20}, "operator": "eq", "value": true}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "12월 20일 이후 매수"
            },
            {
                "name": "january_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "calendar", "params": {"month": 1, "day_gte": 31}, "operator": "eq", "value": true}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "1월 31일 매도"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.08,
            "take_profit_pct": 0.15,
            "max_position_pct": 0.15
        },
        "schedule": {
            "buy_date": {"month": 12, "day": 20},
            "sell_date": {"month": 1, "day": 31}
        },
        "target": {"ticker": "KODEX코스닥150", "name": "코스닥150 ETF"},
        "tags": ["seasonal", "january-effect", "small-cap"]
    }'::jsonb,
    'YEARLY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.17 Sell in May
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'sell_in_may',
    'Sell in May',
    '"5월에 팔고 떠나라" 격언을 활용한 전략입니다. 5-10월 채권 비중 증가, 11-4월 주식 비중 증가.',
    (SELECT id FROM strategy_categories WHERE code = 'SEASONAL'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "summer_defensive",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "calendar", "params": {"month_in": [5,6,7,8,9,10]}, "operator": "eq", "value": true}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "5-10월 방어적 포지션"
            },
            {
                "name": "winter_aggressive",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "calendar", "params": {"month_in": [11,12,1,2,3,4]}, "operator": "eq", "value": true}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "11-4월 공격적 포지션"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.1,
            "take_profit_pct": 0.2,
            "max_position_pct": 0.8
        },
        "schedule": {
            "summer": {"months": [5,6,7,8,9,10], "allocation": {"equity": 0.2, "bond": 0.8}},
            "winter": {"months": [11,12,1,2,3,4], "allocation": {"equity": 0.8, "bond": 0.2}}
        },
        "tags": ["seasonal", "halloween-effect"]
    }'::jsonb,
    'MONTHLY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.18 분기말 모멘텀 리밸런싱
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'quarterly_momentum',
    '분기말 모멘텀 리밸런싱',
    '분기 마지막 주에 최근 3개월 수익률 상위 10종목으로 포트폴리오를 재구성하는 모멘텀 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'SEASONAL'),
    true,
    false,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "quarterly_rebalance",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "calendar", "params": {"day_in": ["03-25","06-25","09-25","12-25"]}, "operator": "eq", "value": true},
                    {"indicator": "momentum_3m", "params": {"rank": "top", "n": 10}, "operator": "eq", "value": true}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "분기말 상위 모멘텀 종목 매수"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.1,
            "take_profit_pct": 0.25,
            "max_position_pct": 0.1
        },
        "schedule": {
            "rebalance_dates": ["03-25", "06-25", "09-25", "12-25"]
        },
        "selection": {
            "criteria": "momentum_3m",
            "direction": "desc",
            "top_n": 10,
            "universe": "KOSPI200"
        },
        "tags": ["seasonal", "momentum", "quarterly"]
    }'::jsonb,
    'QUARTERLY',
    '1.0',
    NOW(),
    NOW()
);

-- =====================================================================
-- ML_PREDICTION 전략 (2개)
-- =====================================================================

-- 12.19 Vertex AI 예측
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'vertex_ai_prediction',
    'Vertex AI 예측',
    'Google Vertex AI 머신러닝 모델이 예측한 상승 확률이 70% 이상인 종목에 투자하는 AI 기반 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'ML_PREDICTION'),
    true,
    true,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "ai_buy_signal",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "ml_prediction", "params": {"model": "vertex_ai", "endpoint": "stock-prediction-v1"}, "operator": "gte", "value": 0.7}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "AI 상승 확률 70% 이상"
            },
            {
                "name": "ai_sell_signal",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "ml_prediction", "params": {"model": "vertex_ai", "endpoint": "stock-prediction-v1"}, "operator": "lt", "value": 0.3}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "AI 상승 확률 30% 미만"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.15,
            "max_position_pct": 0.1
        },
        "model": {
            "provider": "vertex_ai",
            "endpoint": "stock-prediction-v1",
            "features": ["price_momentum", "volume_trend", "technical_indicators", "market_sentiment"]
        },
        "tags": ["ml", "ai", "vertex-ai"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- 12.20 AI + 기술적 분석 하이브리드
INSERT INTO strategies (
    strategy_id, name, description, category_id, is_public, is_premium, status, conditions, rebalance_frequency, version, created_at, updated_at
) VALUES (
    'ai_technical_hybrid',
    'AI + 기술적 분석 하이브리드',
    'AI 예측(50%)과 기술적 분석(50%)을 앙상블하여 더 안정적인 매매 신호를 생성하는 고급 전략입니다.',
    (SELECT id FROM strategy_categories WHERE code = 'ML_PREDICTION'),
    true,
    true,
    'ACTIVE',
    '{
        "rules": [
            {
                "name": "hybrid_buy_signal",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "ensemble_score", "params": {"ai_weight": 0.5, "technical_weight": 0.5}, "operator": "gte", "value": 0.65}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "앙상블 점수 65% 이상"
            },
            {
                "name": "hybrid_sell_signal",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "ensemble_score", "params": {"ai_weight": 0.5, "technical_weight": 0.5}, "operator": "lt", "value": 0.35}
                ],
                "logic": "and",
                "weight": 1.0,
                "description": "앙상블 점수 35% 미만"
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.15,
            "max_position_pct": 0.1
        },
        "ensemble": {
            "ai_prediction": {"weight": 0.5, "source": "vertex_ai"},
            "technical_analysis": {"weight": 0.5, "indicators": ["golden_cross", "rsi", "macd"]}
        },
        "tags": ["ml", "ai", "hybrid", "ensemble"]
    }'::jsonb,
    'DAILY',
    '1.0',
    NOW(),
    NOW()
);

-- ============================================
-- 13. 시드 전략 기본 trading_costs 설정 (V26)
-- ============================================
UPDATE strategies
SET trading_costs = jsonb_build_object(
    'commission', jsonb_build_object('rate', 0.015, 'min_fee', 0),
    'tax', jsonb_build_object('rate', 0.23, 'applies_to', 'sell'),
    'slippage', jsonb_build_object('model', 'adaptive', 'fixed_bps', 5, 'volume_impact', 0.1)
)
WHERE trading_costs = '{}'::jsonb
  AND owner_id IS NULL;

-- ============================================
-- 요약
-- ============================================
-- 시드 데이터 통합 삽입 완료:
--   1. Stocks: 35개 US 종목 (market/exchange 포함)
--   2. FRED Indicators: 16개 경제 지표
--   3. Yahoo Finance Indicators: 22개 시장 지표
--   4. RBAC: 4개 역할, 25개 권한, 역할-권한 매핑
--   5. Admin 사용자 및 설정 초기화
--   6. User Tiers: 기존 사용자 FREE 티어 초기화
--   7. DSL 형식 시드 전략 20개:
--      - MOMENTUM: 6개 (골든크로스, RSI, MACD, 볼린저, 모멘텀 브레이크아웃, 추세 추종)
--      - QUANT_COMPOSITE: 4개 (트리플 스크린, 평균 회귀, 듀얼 모멘텀, RSI 스캘핑)
--      - VALUE: 2개 (저PER, 고배당)
--      - ASSET_ALLOCATION: 3개 (60/40, 금리연동, 올웨더)
--      - SEASONAL: 3개 (1월효과, Sell in May, 분기말)
--      - ML_PREDICTION: 2개 (Vertex AI, AI 하이브리드)
--      - 프리미엄 전략: 5개 / 무료 전략: 15개
--   8. Trading costs: 시스템 전략 기본 거래 비용 설정
