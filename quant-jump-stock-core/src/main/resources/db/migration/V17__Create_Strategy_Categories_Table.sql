-- V17: 전략 카테고리 테이블 분리
-- 기존 enum 기반 category를 별도 테이블로 관리

-- 1. strategy_categories 테이블 생성
CREATE TABLE strategy_categories (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    icon VARCHAR(50),
    sort_order INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW() NOT NULL
);

COMMENT ON TABLE strategy_categories IS '전략 카테고리 마스터 테이블';
COMMENT ON COLUMN strategy_categories.code IS '카테고리 코드 (영문)';
COMMENT ON COLUMN strategy_categories.name IS '카테고리 이름 (한글)';
COMMENT ON COLUMN strategy_categories.icon IS '카테고리 아이콘 (optional)';
COMMENT ON COLUMN strategy_categories.sort_order IS '정렬 순서';

-- 2. 기존 enum 데이터를 테이블에 삽입
INSERT INTO strategy_categories (code, name, description, icon, sort_order) VALUES
    ('VALUE', '가치투자', '저PER, 저PBR 등 가치 지표 기반 투자 전략', 'chart-bar', 1),
    ('MOMENTUM', '모멘텀', '가격 추세 및 모멘텀 기반 투자 전략', 'trending-up', 2),
    ('ASSET_ALLOCATION', '자산배분', '다양한 자산군에 분산 투자하는 전략', 'pie-chart', 3),
    ('QUANT_COMPOSITE', '퀀트 복합', '여러 팩터를 복합적으로 활용하는 퀀트 전략', 'calculator', 4),
    ('SEASONAL', '시즌널', '계절성 및 시기별 패턴 기반 투자 전략', 'calendar', 5),
    ('CUSTOM', '사용자 정의', '사용자가 직접 정의한 맞춤형 전략', 'settings', 6),
    ('ML_PREDICTION', 'AI 예측', 'AI/ML 기반 주가 예측 전략', 'brain', 7);

-- 3. strategies 테이블에 category_id 컬럼 추가
ALTER TABLE strategies ADD COLUMN category_id BIGINT;

-- 4. 기존 category 값을 category_id로 마이그레이션
UPDATE strategies s
SET category_id = sc.id
FROM strategy_categories sc
WHERE s.category = sc.code;

-- 5. category_id에 NOT NULL 제약 추가 및 FK 설정
ALTER TABLE strategies
    ALTER COLUMN category_id SET NOT NULL,
    ADD CONSTRAINT fk_strategies_category
        FOREIGN KEY (category_id) REFERENCES strategy_categories(id);

-- 6. 기존 category 컬럼 삭제
ALTER TABLE strategies DROP COLUMN category;

-- 7. 기존 category 관련 체크 제약 삭제 (이미 없을 수 있음)
ALTER TABLE strategies DROP CONSTRAINT IF EXISTS check_strategy_category;

-- 8. 인덱스 생성
CREATE INDEX idx_strategies_category_id ON strategies(category_id);
CREATE INDEX idx_strategy_categories_code ON strategy_categories(code);
CREATE INDEX idx_strategy_categories_is_active ON strategy_categories(is_active);
