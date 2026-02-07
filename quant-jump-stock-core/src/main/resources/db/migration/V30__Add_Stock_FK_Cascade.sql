-- V30: Stock FK에 ON DELETE CASCADE 추가
-- stock 삭제 시 연관된 지정이력, 전략 기본종목도 자동 삭제

-- stock_designation_history.stock_id FK → CASCADE
ALTER TABLE stock_designation_history
  DROP CONSTRAINT IF EXISTS stock_designation_history_stock_id_fkey;
ALTER TABLE stock_designation_history
  ADD CONSTRAINT stock_designation_history_stock_id_fkey
  FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE;

-- strategy_default_stocks.stock_id FK → CASCADE
ALTER TABLE strategy_default_stocks
  DROP CONSTRAINT IF EXISTS strategy_default_stocks_stock_id_fkey;
ALTER TABLE strategy_default_stocks
  ADD CONSTRAINT strategy_default_stocks_stock_id_fkey
  FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE;
