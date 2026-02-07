-- V32: user_portfolios FK에 ON DELETE 동작 추가
-- user_portfolios.strategy_id: 전략 삭제 시 포트폴리오 유지 (strategy_id만 NULL로 설정)
-- user_portfolios.user_id: 사용자 삭제 시 포트폴리오도 삭제

-- strategy_id FK: ON DELETE SET NULL
ALTER TABLE user_portfolios DROP CONSTRAINT IF EXISTS user_portfolios_strategy_id_fkey;
ALTER TABLE user_portfolios ADD CONSTRAINT user_portfolios_strategy_id_fkey
    FOREIGN KEY (strategy_id) REFERENCES strategies(id) ON DELETE SET NULL;

-- user_id FK: ON DELETE CASCADE
ALTER TABLE user_portfolios DROP CONSTRAINT IF EXISTS user_portfolios_user_id_fkey;
ALTER TABLE user_portfolios ADD CONSTRAINT user_portfolios_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
