-- V12: 누락된 테이블/컬럼 코멘트 추가
-- 기존 마이그레이션에서 누락된 COMMENT ON 문 보완
-- Note: Quartz 테이블 코멘트 제외 (job-store-type: memory 사용)

-- =====================================================================
-- 1. strategies 테이블 컬럼 코멘트
-- =====================================================================

COMMENT ON COLUMN strategies.id IS '전략 고유 ID (PK)';
COMMENT ON COLUMN strategies.name IS '전략 이름 (표시용)';
COMMENT ON COLUMN strategies.description IS '전략 상세 설명';
COMMENT ON COLUMN strategies.owner_id IS '전략 소유자 사용자 ID (NULL: 시스템 전략)';
COMMENT ON COLUMN strategies.is_public IS '공개 여부 (true: 마켓플레이스 노출)';
COMMENT ON COLUMN strategies.is_premium IS '프리미엄 전략 여부 (true: 유료)';
COMMENT ON COLUMN strategies.status IS '전략 상태 (DRAFT, ACTIVE, ARCHIVED)';
COMMENT ON COLUMN strategies.conditions IS 'DSL 형식 전략 조건 (JSONB)';
COMMENT ON COLUMN strategies.rebalance_frequency IS '리밸런싱 주기 (DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY, NONE)';
COMMENT ON COLUMN strategies.subscriber_count IS '구독자 수';
COMMENT ON COLUMN strategies.average_rating IS '평균 평점 (0.00~5.00)';
COMMENT ON COLUMN strategies.created_at IS '생성 일시';
COMMENT ON COLUMN strategies.updated_at IS '수정 일시';

-- =====================================================================
-- 2. backtest_results 테이블 컬럼 코멘트
-- =====================================================================

COMMENT ON COLUMN backtest_results.id IS '백테스트 결과 고유 ID (PK)';
COMMENT ON COLUMN backtest_results.strategy_id IS '백테스트 대상 전략 ID (FK)';
COMMENT ON COLUMN backtest_results.user_id IS '백테스트 실행 사용자 ID (FK)';
COMMENT ON COLUMN backtest_results.start_date IS '백테스트 시작일';
COMMENT ON COLUMN backtest_results.end_date IS '백테스트 종료일';
COMMENT ON COLUMN backtest_results.initial_capital IS '초기 투자금';
COMMENT ON COLUMN backtest_results.benchmark IS '벤치마크 지수 (기본: KOSPI)';
COMMENT ON COLUMN backtest_results.final_value IS '최종 포트폴리오 가치';
COMMENT ON COLUMN backtest_results.total_return IS '총 수익률';
COMMENT ON COLUMN backtest_results.cagr IS '연평균 복리 수익률 (CAGR)';
COMMENT ON COLUMN backtest_results.mdd IS '최대 낙폭 (MDD, Maximum Drawdown)';
COMMENT ON COLUMN backtest_results.sharpe_ratio IS '샤프 비율 (위험 대비 수익)';
COMMENT ON COLUMN backtest_results.sortino_ratio IS '소르티노 비율 (하방 위험 대비 수익)';
COMMENT ON COLUMN backtest_results.volatility IS '변동성 (표준편차)';
COMMENT ON COLUMN backtest_results.win_rate IS '승률 (%)';
COMMENT ON COLUMN backtest_results.total_trades IS '총 거래 횟수';
COMMENT ON COLUMN backtest_results.winning_trades IS '수익 거래 횟수';
COMMENT ON COLUMN backtest_results.losing_trades IS '손실 거래 횟수';
COMMENT ON COLUMN backtest_results.avg_win IS '평균 수익률 (수익 거래)';
COMMENT ON COLUMN backtest_results.avg_loss IS '평균 손실률 (손실 거래)';
COMMENT ON COLUMN backtest_results.benchmark_return IS '벤치마크 수익률';
COMMENT ON COLUMN backtest_results.alpha IS '알파 (초과 수익률)';
COMMENT ON COLUMN backtest_results.beta IS '베타 (시장 민감도)';
COMMENT ON COLUMN backtest_results.equity_curve IS '자산 곡선 데이터 (JSONB)';
COMMENT ON COLUMN backtest_results.status IS '백테스트 상태 (RUNNING, COMPLETED, FAILED)';
COMMENT ON COLUMN backtest_results.error_message IS '에러 발생 시 메시지';
COMMENT ON COLUMN backtest_results.created_at IS '백테스트 시작 일시';
COMMENT ON COLUMN backtest_results.completed_at IS '백테스트 완료 일시';

-- =====================================================================
-- 3. backtest_trades 테이블 컬럼 코멘트
-- =====================================================================

COMMENT ON COLUMN backtest_trades.id IS '백테스트 거래 고유 ID (PK)';
COMMENT ON COLUMN backtest_trades.backtest_id IS '백테스트 결과 ID (FK)';
COMMENT ON COLUMN backtest_trades.trade_date IS '거래 일자';
COMMENT ON COLUMN backtest_trades.ticker IS '종목 티커';
COMMENT ON COLUMN backtest_trades.side IS '매매 방향 (BUY, SELL)';
COMMENT ON COLUMN backtest_trades.quantity IS '거래 수량';
COMMENT ON COLUMN backtest_trades.price IS '거래 가격';
COMMENT ON COLUMN backtest_trades.amount IS '거래 금액';
COMMENT ON COLUMN backtest_trades.commission IS '수수료';
COMMENT ON COLUMN backtest_trades.pnl IS '손익 금액 (SELL 시)';
COMMENT ON COLUMN backtest_trades.pnl_percent IS '손익률 (SELL 시)';
COMMENT ON COLUMN backtest_trades.holding_days IS '보유 기간 (일, SELL 시)';
COMMENT ON COLUMN backtest_trades.signal_reason IS '매매 신호 사유';
COMMENT ON COLUMN backtest_trades.created_at IS '레코드 생성 일시';

-- =====================================================================
-- 4. strategy_signals 테이블 컬럼 코멘트
-- =====================================================================

COMMENT ON COLUMN strategy_signals.id IS '전략 신호 고유 ID (PK)';
COMMENT ON COLUMN strategy_signals.strategy_id IS '전략 ID (FK)';
COMMENT ON COLUMN strategy_signals.signal_date IS '신호 발생일';
COMMENT ON COLUMN strategy_signals.signal_type IS '신호 유형 (BUY, SELL, REBALANCE, HOLD)';
COMMENT ON COLUMN strategy_signals.ticker IS '대상 종목 티커';
COMMENT ON COLUMN strategy_signals.target_weight IS '목표 비중 (%)';
COMMENT ON COLUMN strategy_signals.reason IS '신호 발생 사유';
COMMENT ON COLUMN strategy_signals.conditions_snapshot IS '신호 발생 시점 조건 스냅샷 (JSONB)';
COMMENT ON COLUMN strategy_signals.created_at IS '신호 생성 일시';

-- =====================================================================
-- 5. strategy_subscriptions 테이블 컬럼 코멘트
-- =====================================================================

COMMENT ON COLUMN strategy_subscriptions.id IS '구독 고유 ID (PK)';
COMMENT ON COLUMN strategy_subscriptions.user_id IS '구독자 사용자 ID (FK)';
COMMENT ON COLUMN strategy_subscriptions.strategy_id IS '구독 전략 ID (FK)';
COMMENT ON COLUMN strategy_subscriptions.status IS '구독 상태 (ACTIVE, PAUSED, CANCELLED)';
COMMENT ON COLUMN strategy_subscriptions.notify_signals IS '매매 신호 알림 수신 여부';
COMMENT ON COLUMN strategy_subscriptions.notify_rebalance IS '리밸런싱 알림 수신 여부';
COMMENT ON COLUMN strategy_subscriptions.subscribed_at IS '구독 시작 일시';
COMMENT ON COLUMN strategy_subscriptions.cancelled_at IS '구독 취소 일시';
COMMENT ON COLUMN strategy_subscriptions.created_at IS '레코드 생성 일시';

-- =====================================================================
-- 6. 기타 누락된 컬럼 코멘트 보완
-- =====================================================================

-- users 테이블 추가 컬럼
COMMENT ON COLUMN users.id IS '사용자 고유 ID (PK, 내부 식별자)';
COMMENT ON COLUMN users.name IS '사용자 이름';
COMMENT ON COLUMN users.email IS '이메일 주소 (로그인 ID)';
COMMENT ON COLUMN users.password_hash IS '비밀번호 해시 (BCrypt)';
COMMENT ON COLUMN users.created_at IS '계정 생성 일시';
COMMENT ON COLUMN users.updated_at IS '계정 정보 수정 일시';

-- trading_configs 테이블 추가 컬럼
COMMENT ON COLUMN trading_configs.id IS '거래 설정 고유 ID (PK)';
COMMENT ON COLUMN trading_configs.user_id IS '사용자 ID (FK, UNIQUE)';
COMMENT ON COLUMN trading_configs.enabled IS '거래 기능 활성화 여부';
COMMENT ON COLUMN trading_configs.auto_trading_enabled IS '자동 매매 활성화 여부';
COMMENT ON COLUMN trading_configs.max_stocks_to_buy IS '최대 매수 종목 수';
COMMENT ON COLUMN trading_configs.max_amount_per_stock IS '종목당 최대 투자 금액';
COMMENT ON COLUMN trading_configs.stop_loss_percent IS '손절 기준 (%, 음수)';
COMMENT ON COLUMN trading_configs.take_profit_percent IS '익절 기준 (%)';
COMMENT ON COLUMN trading_configs.created_at IS '설정 생성 일시';
COMMENT ON COLUMN trading_configs.updated_at IS '설정 수정 일시';

-- account_balances 테이블 추가 컬럼
COMMENT ON COLUMN account_balances.id IS '계좌 잔액 고유 ID (PK)';
COMMENT ON COLUMN account_balances.user_id IS '사용자 ID (FK, UNIQUE)';
COMMENT ON COLUMN account_balances.cash IS '현금 잔액';
COMMENT ON COLUMN account_balances.total_value IS '총 자산 가치 (현금 + 주식)';
COMMENT ON COLUMN account_balances.created_at IS '레코드 생성 일시';
COMMENT ON COLUMN account_balances.updated_at IS '잔액 갱신 일시';

-- stock_holdings 테이블 추가 컬럼
COMMENT ON COLUMN stock_holdings.id IS '보유 주식 고유 ID (PK)';
COMMENT ON COLUMN stock_holdings.user_id IS '사용자 ID (FK)';
COMMENT ON COLUMN stock_holdings.ticker IS '종목 티커';
COMMENT ON COLUMN stock_holdings.quantity IS '보유 수량';
COMMENT ON COLUMN stock_holdings.average_price IS '평균 매수 단가';
COMMENT ON COLUMN stock_holdings.total_cost IS '총 매수 금액';
COMMENT ON COLUMN stock_holdings.current_value IS '현재 평가 금액';
COMMENT ON COLUMN stock_holdings.created_at IS '최초 매수 일시';
COMMENT ON COLUMN stock_holdings.updated_at IS '보유 정보 갱신 일시';

-- trades 테이블 추가 컬럼
COMMENT ON COLUMN trades.id IS '거래 고유 ID (PK)';
COMMENT ON COLUMN trades.user_id IS '사용자 ID (FK)';
COMMENT ON COLUMN trades.ticker IS '종목 티커';
COMMENT ON COLUMN trades.side IS '매매 방향 (BUY, SELL)';
COMMENT ON COLUMN trades.quantity IS '거래 수량';
COMMENT ON COLUMN trades.price IS '거래 단가';
COMMENT ON COLUMN trades.total_amount IS '총 거래 금액';
COMMENT ON COLUMN trades.commission IS '수수료';
COMMENT ON COLUMN trades.status IS '거래 상태 (PENDING, EXECUTED, FAILED, CANCELLED)';
COMMENT ON COLUMN trades.executed_at IS '체결 일시';
COMMENT ON COLUMN trades.created_at IS '주문 생성 일시';
COMMENT ON COLUMN trades.updated_at IS '주문 상태 갱신 일시';

-- trade_signals_executed 테이블 추가 컬럼
COMMENT ON COLUMN trade_signals_executed.id IS '신호 실행 로그 고유 ID (PK)';
COMMENT ON COLUMN trade_signals_executed.user_id IS '사용자 ID (FK)';
COMMENT ON COLUMN trade_signals_executed.ticker IS '종목 티커';
COMMENT ON COLUMN trade_signals_executed.signal IS '신호 유형 (BUY, SELL, HOLD)';
COMMENT ON COLUMN trade_signals_executed.confidence IS '신호 신뢰도 (0.00~1.00)';
COMMENT ON COLUMN trade_signals_executed.skip_reason IS '미실행 시 사유';
COMMENT ON COLUMN trade_signals_executed.executed_trade_id IS '실행된 거래 ID (FK)';
COMMENT ON COLUMN trade_signals_executed.created_at IS '신호 처리 일시';

-- stocks 테이블 추가 컬럼
COMMENT ON COLUMN stocks.id IS '주식 고유 ID (PK)';
COMMENT ON COLUMN stocks.exchange IS '거래소 (NYSE, NASDAQ 등)';
COMMENT ON COLUMN stocks.sector IS '섹터';
COMMENT ON COLUMN stocks.industry IS '산업';
COMMENT ON COLUMN stocks.is_active IS '활성 상태 (true: 거래 가능)';
COMMENT ON COLUMN stocks.created_at IS '레코드 생성 일시';
COMMENT ON COLUMN stocks.updated_at IS '정보 갱신 일시';

-- fred_indicators 테이블 추가 컬럼
COMMENT ON COLUMN fred_indicators.name IS '지표 이름 (한글)';
COMMENT ON COLUMN fred_indicators.description IS '지표 상세 설명';
COMMENT ON COLUMN fred_indicators.category IS '지표 카테고리';
COMMENT ON COLUMN fred_indicators.unit IS '단위';
COMMENT ON COLUMN fred_indicators.is_active IS '활성 상태';
COMMENT ON COLUMN fred_indicators.created_at IS '레코드 생성 일시';
COMMENT ON COLUMN fred_indicators.updated_at IS '정보 갱신 일시';

-- yfinance_indicators 테이블 추가 컬럼
COMMENT ON COLUMN yfinance_indicators.name IS '지표 이름';
COMMENT ON COLUMN yfinance_indicators.description IS '지표 상세 설명';
COMMENT ON COLUMN yfinance_indicators.is_active IS '활성 상태';
COMMENT ON COLUMN yfinance_indicators.created_at IS '레코드 생성 일시';
COMMENT ON COLUMN yfinance_indicators.updated_at IS '정보 갱신 일시';

-- user_kis_accounts 테이블 추가 컬럼
COMMENT ON COLUMN user_kis_accounts.id IS 'KIS 계정 고유 ID (PK)';
COMMENT ON COLUMN user_kis_accounts.user_id IS '사용자 ID (FK, UNIQUE)';
COMMENT ON COLUMN user_kis_accounts.enabled IS '계정 사용 가능 여부';
COMMENT ON COLUMN user_kis_accounts.last_used_at IS '마지막 사용 일시';
COMMENT ON COLUMN user_kis_accounts.created_at IS '계정 등록 일시';
COMMENT ON COLUMN user_kis_accounts.updated_at IS '계정 정보 수정 일시';

-- kis_tokens 테이블 추가 컬럼
COMMENT ON COLUMN kis_tokens.id IS '토큰 고유 ID (PK)';
COMMENT ON COLUMN kis_tokens.created_at IS '토큰 발급 일시';
COMMENT ON COLUMN kis_tokens.updated_at IS '토큰 갱신 일시';

-- roles 테이블 추가 컬럼
COMMENT ON COLUMN roles.id IS '역할 고유 ID (PK)';
COMMENT ON COLUMN roles.name IS '역할 이름 (ADMIN, USER 등)';
COMMENT ON COLUMN roles.description IS '역할 설명';
COMMENT ON COLUMN roles.created_at IS '역할 생성 일시';

-- permissions 테이블 추가 컬럼
COMMENT ON COLUMN permissions.id IS '권한 고유 ID (PK)';
COMMENT ON COLUMN permissions.name IS '권한 표시 이름';
COMMENT ON COLUMN permissions.description IS '권한 설명';
COMMENT ON COLUMN permissions.category IS '권한 카테고리';
COMMENT ON COLUMN permissions.created_at IS '권한 생성 일시';

-- user_roles 테이블 추가 컬럼
COMMENT ON COLUMN user_roles.id IS '사용자-역할 매핑 고유 ID (PK)';
COMMENT ON COLUMN user_roles.user_id IS '사용자 ID (FK)';
COMMENT ON COLUMN user_roles.role_id IS '역할 ID (FK)';
COMMENT ON COLUMN user_roles.created_at IS '역할 할당 일시';

-- role_permissions 테이블 추가 컬럼
COMMENT ON COLUMN role_permissions.id IS '역할-권한 매핑 고유 ID (PK)';
COMMENT ON COLUMN role_permissions.role_id IS '역할 ID (FK)';
COMMENT ON COLUMN role_permissions.permission_id IS '권한 ID (FK)';
COMMENT ON COLUMN role_permissions.created_at IS '권한 할당 일시';

-- user_tiers 테이블 추가 컬럼
COMMENT ON COLUMN user_tiers.id IS '사용자 티어 고유 ID (PK)';
COMMENT ON COLUMN user_tiers.user_id IS '사용자 ID (FK, UNIQUE)';
COMMENT ON COLUMN user_tiers.created_at IS '티어 레코드 생성 일시';
COMMENT ON COLUMN user_tiers.updated_at IS '티어 정보 수정 일시';

-- strategy_categories 테이블 추가 컬럼
COMMENT ON COLUMN strategy_categories.id IS '카테고리 고유 ID (PK)';
COMMENT ON COLUMN strategy_categories.is_active IS '활성 상태';
COMMENT ON COLUMN strategy_categories.created_at IS '카테고리 생성 일시';
COMMENT ON COLUMN strategy_categories.updated_at IS '카테고리 수정 일시';
