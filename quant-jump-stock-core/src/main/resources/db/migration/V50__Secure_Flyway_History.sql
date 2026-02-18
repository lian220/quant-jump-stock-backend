/* V50__Secure_Flyway_History.sql */

-- 1. 사용자 및 보안 관련 테이블 잠금
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_kis_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.kis_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.role_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_tiers ENABLE ROW LEVEL SECURITY;

-- 2. 거래 및 자산 관련 테이블 잠금
ALTER TABLE public.trading_configs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_balances ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stock_holdings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trades ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trade_signals_executed ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_portfolios ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.portfolio_stocks ENABLE ROW LEVEL SECURITY;

-- 3. 전략 및 백테스트 관련 테이블 잠금
ALTER TABLE public.strategies ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.strategy_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.strategy_signals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.strategy_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.strategy_default_stocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.backtest_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.backtest_trades ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.backtest_checkpoints ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.prediction_results ENABLE ROW LEVEL SECURITY;

-- 4. 시장 데이터 및 지표 테이블 잠금
ALTER TABLE public.stocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stock_designation_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fred_indicators ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.yfinance_indicators ENABLE ROW LEVEL SECURITY;

-- 5. 뉴스 및 기타 알림 테이블 잠금
ALTER TABLE public.news_collector_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.news_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.news_source_tag_mappings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_news_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_news_notifications ENABLE ROW LEVEL SECURITY;
