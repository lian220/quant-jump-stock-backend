-- V45: 뉴스 수집기 상태 MongoDB → PostgreSQL 이관 + 뉴스 카테고리 정규화

-- ============================================================
-- 1. 뉴스 수집기 상태 (MongoDB news_collector_state 컬렉션 대체)
-- ============================================================
CREATE TABLE news_collector_state (
    id                   BIGSERIAL PRIMARY KEY,
    source               VARCHAR(50) NOT NULL UNIQUE,
    last_fetched_at      TIMESTAMP,
    last_fetched_id      VARCHAR(255),
    fetch_count          BIGINT NOT NULL DEFAULT 0,
    consecutive_errors   INT NOT NULL DEFAULT 0,
    total_errors         BIGINT NOT NULL DEFAULT 0,
    avg_response_time_ms BIGINT NOT NULL DEFAULT 0,
    last_error           TEXT,
    last_error_at        TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE news_collector_state IS '뉴스 수집기 상태 (소스별 마지막 수집 시각, 에러 카운트 등)';
COMMENT ON COLUMN news_collector_state.source IS 'NewsSource enum (SAVETICKER, FINNHUB, TELEGRAM 등)';
COMMENT ON COLUMN news_collector_state.last_fetched_at IS '마지막 수집 시각';
COMMENT ON COLUMN news_collector_state.last_fetched_id IS '마지막 수집 항목 ID';
COMMENT ON COLUMN news_collector_state.fetch_count IS '누적 수집 횟수';
COMMENT ON COLUMN news_collector_state.consecutive_errors IS '연속 에러 횟수 (성공 시 0으로 리셋)';
COMMENT ON COLUMN news_collector_state.avg_response_time_ms IS '평균 API 응답 시간 (ms)';

-- ============================================================
-- 2. 뉴스 카테고리 마스터 (투자자 관점 재설계)
--    - category_group: UI 그룹핑 (시장, 기업, 자산, 매크로)
--    - icon: 프론트엔드 아이콘 식별자
--    - SaveTicker 기존 태그와 1:N 매핑 (source_tag_mappings)
-- ============================================================
CREATE TABLE news_categories (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    name_en         VARCHAR(100) NOT NULL,
    category_group  VARCHAR(30) NOT NULL,
    description     VARCHAR(300),
    icon            VARCHAR(50),
    weight          DOUBLE PRECISION NOT NULL DEFAULT 0.10,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE news_categories IS '뉴스 카테고리 마스터 (투자자 관점 분류 + 가중치)';
COMMENT ON COLUMN news_categories.category_group IS 'UI 그룹: MARKET(시장), COMPANY(기업), ASSET(자산), MACRO(매크로), INFO(정보)';
COMMENT ON COLUMN news_categories.weight IS '중요도 가중치 (0.0~1.0, NewsScorer에서 참조)';
COMMENT ON COLUMN news_categories.icon IS '프론트엔드 아이콘 식별자 (Lucide 등)';

CREATE INDEX idx_news_categories_group ON news_categories(category_group);

-- 카테고리 시딩: 투자자가 실제로 찾는 분류 기준
-- MARKET: 시장 전체에 영향을 주는 뉴스
INSERT INTO news_categories (name, name_en, category_group, description, icon, weight, sort_order) VALUES
    ('속보',       'Breaking News',        'MARKET',  '시장에 즉각 영향을 주는 긴급 뉴스',         'zap',          0.40, 1),
    ('경제지표',   'Economic Indicators',   'MACRO',   'CPI, GDP, 고용 등 주요 경제 데이터',       'bar-chart-2',  0.35, 2),
    ('중앙은행',   'Central Banks',         'MACRO',   '연준, ECB, BOJ 등 통화정책 및 금리 결정',  'landmark',     0.35, 3),
    ('실적발표',   'Earnings',              'COMPANY', '분기 실적, 어닝 서프라이즈/미스',           'trending-up',  0.40, 4),
    ('기업분석',   'Company Analysis',      'COMPANY', '목표가, 투자의견, 밸류에이션 분석',         'search',       0.30, 5),
    ('규제/정책',  'Regulation & Policy',   'MACRO',   '정부 정책, SEC 규제, 반독점, 세제 변화',   'shield',       0.30, 6),
    ('IPO/M&A',    'IPO & M&A',             'COMPANY', '신규 상장, 인수합병, 스핀오프',             'git-merge',    0.25, 7),
    ('섹터동향',   'Sector Trends',         'MARKET',  'IT, 헬스케어, 금융 등 섹터별 흐름',        'layers',       0.25, 8),
    ('배당/주주환원', 'Dividends & Buybacks', 'COMPANY', '배당 발표, 자사주 매입, 주주환원 정책',   'gift',         0.20, 9),
    ('원자재/에너지', 'Commodities & Energy', 'ASSET',  '유가, 금, 구리, 천연가스 등 원자재',       'flame',        0.20, 10),
    ('암호화폐',   'Crypto',                'ASSET',   '비트코인, 이더리움, 디파이, 규제',          'bitcoin',      0.15, 11),
    ('ETF/인덱스', 'ETF & Index',           'ASSET',   'ETF 신규 상장, 인덱스 리밸런싱',           'pie-chart',    0.15, 12),
    ('지정학',     'Geopolitics',           'MACRO',   '국제 정세, 무역 분쟁, 제재, 전쟁',         'globe',        0.25, 13),
    ('투자전략',   'Investment Strategy',   'INFO',    '포트폴리오 전략, 자산배분, 리스크 관리',    'target',       0.20, 14),
    ('일정/이벤트', 'Calendar & Events',    'INFO',    'FOMC, 옵션 만기, IPO 일정, 경제 캘린더',   'calendar',     0.10, 15);

-- ============================================================
-- 3. 소스 태그 → 카테고리 매핑
--    SaveTicker raw 태그를 정규화 카테고리에 연결
--    새 소스(Telegram, Finnhub 등) 추가 시 매핑만 추가하면 됨
-- ============================================================
CREATE TABLE news_source_tag_mappings (
    id              BIGSERIAL PRIMARY KEY,
    source          VARCHAR(50) NOT NULL,
    source_tag      VARCHAR(200) NOT NULL,
    category_id     BIGINT NOT NULL REFERENCES news_categories(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(source, source_tag)
);

COMMENT ON TABLE news_source_tag_mappings IS '소스별 raw 태그 → 정규화 카테고리 매핑';
COMMENT ON COLUMN news_source_tag_mappings.source IS 'NewsSource enum (SAVETICKER, TELEGRAM 등)';
COMMENT ON COLUMN news_source_tag_mappings.source_tag IS '소스 API에서 오는 원본 태그';

CREATE INDEX idx_source_tag_mappings_lookup ON news_source_tag_mappings(source, source_tag);

-- SaveTicker 기존 태그 매핑
INSERT INTO news_source_tag_mappings (source, source_tag, category_id) VALUES
    ('SAVETICKER', '속보',      (SELECT id FROM news_categories WHERE name = '속보')),
    ('SAVETICKER', '경제지표',  (SELECT id FROM news_categories WHERE name = '경제지표')),
    ('SAVETICKER', '연준',      (SELECT id FROM news_categories WHERE name = '중앙은행')),
    ('SAVETICKER', '투자 의견', (SELECT id FROM news_categories WHERE name = '기업분석')),
    ('SAVETICKER', '분석',      (SELECT id FROM news_categories WHERE name = '기업분석')),
    ('SAVETICKER', '에너지',    (SELECT id FROM news_categories WHERE name = '원자재/에너지')),
    ('SAVETICKER', '종합',      (SELECT id FROM news_categories WHERE name = '섹터동향')),
    ('SAVETICKER', '정보',      (SELECT id FROM news_categories WHERE name = '투자전략')),
    ('SAVETICKER', '암호화폐',  (SELECT id FROM news_categories WHERE name = '암호화폐')),
    ('SAVETICKER', '일정',      (SELECT id FROM news_categories WHERE name = '일정/이벤트'));

-- ============================================================
-- 4. 사용자 뉴스 구독 (개인화 알림)
--    - notify_channel: 알림 채널 (IN_APP, PUSH, TELEGRAM, SLACK)
--    - display_name: 구독 항목 표시명 (UI용)
-- ============================================================
CREATE TABLE user_news_subscriptions (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_type  VARCHAR(20) NOT NULL,
    subscription_value VARCHAR(100) NOT NULL,
    display_name       VARCHAR(200),
    notify_channel     VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, subscription_type, subscription_value, notify_channel)
);

COMMENT ON TABLE user_news_subscriptions IS '사용자 뉴스 구독 (카테고리/티커/소스별 개인화 알림)';
COMMENT ON COLUMN user_news_subscriptions.subscription_type IS 'CATEGORY, TICKER, SOURCE';
COMMENT ON COLUMN user_news_subscriptions.subscription_value IS '구독 대상 값 (카테고리명, 티커명, 소스명)';
COMMENT ON COLUMN user_news_subscriptions.display_name IS 'UI 표시명 (예: "실적발표 알림", "$AAPL 뉴스")';
COMMENT ON COLUMN user_news_subscriptions.notify_channel IS 'IN_APP, PUSH, TELEGRAM, SLACK, EMAIL';

CREATE INDEX idx_user_news_subs_user ON user_news_subscriptions(user_id);
CREATE INDEX idx_user_news_subs_type_value ON user_news_subscriptions(subscription_type, subscription_value);
CREATE INDEX idx_user_news_subs_channel ON user_news_subscriptions(notify_channel, is_active);
