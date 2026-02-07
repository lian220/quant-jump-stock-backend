ALTER TABLE strategies ADD COLUMN IF NOT EXISTS risk_settings JSONB DEFAULT '{}';
COMMENT ON COLUMN strategies.risk_settings IS '리스크 관리 설정 (stop_loss, take_profit, trailing_stop, time_stop)';
