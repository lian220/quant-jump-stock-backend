-- SCRUM-348: 구독 시 선택한 Universe 타입 저장
ALTER TABLE strategy_subscriptions
    ADD COLUMN IF NOT EXISTS preferred_universe_type VARCHAR(20) DEFAULT 'MARKET' NOT NULL;
