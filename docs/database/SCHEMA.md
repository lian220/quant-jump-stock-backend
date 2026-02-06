# Database Schema Documentation

> **최종 업데이트**: 2026-02-06
> **Flyway 버전**: V20

## 개요

QuantiQ 시스템은 **PostgreSQL**과 **MongoDB** 두 가지 데이터베이스를 사용하는 **폴리글롯 퍼시스턴스** 아키텍처를 채택하고 있습니다.

- **PostgreSQL (JPA)**: 트랜잭션 데이터, 사용자 정보, 거래 내역, 전략, 백테스트
- **MongoDB**: 분석 데이터, 시계열 데이터, 예측 결과

---

## PostgreSQL (JPA Entities)

### Entity 목록 (21개)

| 카테고리 | Entity | 테이블명 | 설명 |
|----------|--------|----------|------|
| **사용자** | UserEntity | users | 사용자 정보 |
| | UserTierEntity | user_tiers | 사용자 티어 (FREE/PREMIUM) |
| | UserKisAccountEntity | user_kis_accounts | KIS 계정 정보 |
| | KisTokenEntity | kis_tokens | KIS API 토큰 |
| | TradingConfigEntity | trading_configs | 거래 설정 |
| | AccountBalanceEntity | account_balances | 계좌 잔액 |
| **RBAC** | RoleEntity | roles | 역할 정의 |
| | PermissionEntity | permissions | 권한 정의 |
| | RolePermissionEntity | role_permissions | 역할-권한 매핑 |
| | UserRoleEntity | user_roles | 사용자-역할 매핑 |
| **전략** | StrategyEntity | strategies | 전략 정의 |
| | StrategyCategoryEntity | strategy_categories | 전략 카테고리 |
| | StrategySubscriptionEntity | strategy_subscriptions | 전략 구독 |
| | StrategySignalEntity | strategy_signals | 전략 신호 |
| **백테스트** | BacktestResultEntity | backtest_results | 백테스트 결과 |
| | BacktestTradeEntity | backtest_trades | 백테스트 거래 내역 |
| **거래** | TradeEntity | trades | 실제 거래 내역 |
| | TradeSignalExecutedEntity | trade_signals_executed | 거래 신호 실행 기록 |
| **마스터** | StockEntity | stocks | 종목 정보 |
| | FredIndicatorEntity | fred_indicators | FRED 경제 지표 |
| | YfinanceIndicatorEntity | yfinance_indicators | yfinance 지표 |

---

## 사용자 관련 테이블

### 1. users (사용자)

**엔티티**: `UserEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 사용자 ID |
| user_id | VARCHAR(50) | UNIQUE, NOT NULL | 사용자 고유 ID |
| name | VARCHAR(100) | NULL | 사용자 이름 |
| email | VARCHAR(100) | UNIQUE | 이메일 |
| password_hash | VARCHAR(255) | NULL | 비밀번호 해시 |
| oauth_provider | VARCHAR(20) | NULL | OAuth 제공자 (GOOGLE, NAVER) |
| oauth_provider_id | VARCHAR(255) | NULL | OAuth 제공자 ID |
| profile_image_url | VARCHAR(500) | NULL | 프로필 이미지 URL |
| status | VARCHAR(20) | NOT NULL | 상태 (ACTIVE, INACTIVE, SUSPENDED) |
| role | VARCHAR(20) | NOT NULL | 역할 (ADMIN, USER, MODERATOR) |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

**연관 관계**:
- `1:1` → trading_configs, account_balances, user_kis_accounts, user_tiers
- `1:N` → trades, kis_tokens, user_roles, strategies (owner)

---

### 2. user_tiers (사용자 티어)

**엔티티**: `UserTierEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 티어 ID |
| user_id | BIGINT | FK (users), UNIQUE, NOT NULL | 사용자 ID |
| tier | VARCHAR(20) | NOT NULL | 티어 (FREE, PREMIUM, PREMIUM_YEARLY) |
| started_at | TIMESTAMP | NULL | 프리미엄 시작일 |
| expires_at | TIMESTAMP | NULL | 프리미엄 만료일 |
| backtest_count_today | INT | NOT NULL | 오늘 백테스트 횟수 |
| backtest_count_reset_at | DATE | NOT NULL | 백테스트 카운트 리셋 날짜 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

**비즈니스 로직**:
- FREE 티어: 일 3회 백테스트 제한
- PREMIUM 티어: 무제한 백테스트

---

### 3. user_kis_accounts (KIS 계정 정보)

**엔티티**: `UserKisAccountEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | KIS 계정 ID |
| user_id | BIGINT | FK (users), UNIQUE, NOT NULL | 사용자 ID |
| app_key | VARCHAR(100) | NOT NULL | KIS App Key |
| app_secret_encrypted | VARCHAR(500) | NOT NULL | 암호화된 App Secret |
| account_number | VARCHAR(20) | NOT NULL | 계좌번호 (앞 8자리) |
| account_product_code | VARCHAR(2) | NOT NULL | 계좌 상품 코드 (01: 해외주식) |
| account_type | VARCHAR(10) | NOT NULL | 계정 타입 (REAL, MOCK) |
| enabled | BOOLEAN | NOT NULL | 활성화 여부 |
| last_used_at | TIMESTAMP | NULL | 마지막 사용 시간 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

---

### 4. kis_tokens (KIS API Access Token)

**엔티티**: `KisTokenEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 토큰 ID |
| user_id | BIGINT | FK (users), NOT NULL | 사용자 ID |
| account_type | VARCHAR(10) | NOT NULL | 계정 타입 (REAL, MOCK) |
| access_token | TEXT | NOT NULL | Access Token |
| expiration_time | TIMESTAMP | NOT NULL | 만료 시간 |
| is_active | BOOLEAN | NOT NULL | 활성화 여부 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

**인덱스**:
- UNIQUE: `(user_id, account_type)`

---

### 5. trading_configs (거래 설정)

**엔티티**: `TradingConfigEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 설정 ID |
| user_id | BIGINT | FK (users), UNIQUE, NOT NULL | 사용자 ID |
| enabled | BOOLEAN | NOT NULL | 활성화 여부 |
| auto_trading_enabled | BOOLEAN | NOT NULL | 자동 거래 활성화 |
| min_composite_score | DECIMAL(5,2) | DEFAULT 2.0 | 최소 종합 점수 |
| max_stocks_to_buy | INT | DEFAULT 5 | 최대 매수 종목 수 |
| max_amount_per_stock | DECIMAL(12,2) | DEFAULT 10000.0 | 종목당 최대 투자 금액 |
| stop_loss_percent | DECIMAL(5,2) | DEFAULT -7.0 | 손절 비율 (%) |
| take_profit_percent | DECIMAL(5,2) | DEFAULT 5.0 | 익절 비율 (%) |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

---

### 6. account_balances (계좌 잔액)

**엔티티**: `AccountBalanceEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 잔액 ID |
| user_id | BIGINT | FK (users), UNIQUE, NOT NULL | 사용자 ID |
| cash | DECIMAL(15,2) | NOT NULL | 현금 |
| total_value | DECIMAL(15,2) | NOT NULL | 총 자산 가치 |
| locked_cash | DECIMAL(15,2) | DEFAULT 0.0 | 잠긴 현금 (주문 대기 중) |
| version | BIGINT | NOT NULL | 낙관적 락 버전 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

**동시성 제어**: `@Version` 낙관적 락 사용

---

## RBAC (역할 기반 접근 제어)

### 7. roles (역할)

**엔티티**: `RoleEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 역할 ID |
| name | VARCHAR(50) | UNIQUE, NOT NULL | 역할명 (SUPER_ADMIN, ADMIN, MODERATOR, USER) |
| description | VARCHAR(255) | NULL | 설명 |
| is_system | BOOLEAN | NOT NULL | 시스템 역할 여부 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |

**연관 관계**:
- `1:N` → role_permissions, user_roles

---

### 8. permissions (권한)

**엔티티**: `PermissionEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 권한 ID |
| code | VARCHAR(100) | UNIQUE, NOT NULL | 권한 코드 |
| name | VARCHAR(100) | NOT NULL | 권한명 |
| description | VARCHAR(255) | NULL | 설명 |
| category | VARCHAR(50) | NULL | 카테고리 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |

**권한 카테고리**:
- User Management: USER_VIEW, USER_CREATE, USER_UPDATE, USER_DELETE, USER_ROLE_ASSIGN
- Strategy Management: STRATEGY_VIEW, STRATEGY_CREATE, STRATEGY_UPDATE, STRATEGY_DELETE, STRATEGY_APPROVE, STRATEGY_PUBLISH
- Backtest: BACKTEST_RUN, BACKTEST_VIEW_ALL, BACKTEST_UNLIMITED
- Analytics: ANALYTICS_VIEW, ANALYTICS_EXPORT
- System: SYSTEM_SETTINGS, SYSTEM_LOGS, SYSTEM_MAINTENANCE
- Subscription: SUBSCRIPTION_VIEW, SUBSCRIPTION_MANAGE, PAYMENT_VIEW, PAYMENT_REFUND

---

### 9. role_permissions (역할-권한 매핑)

**엔티티**: `RolePermissionEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| role_id | BIGINT | FK (roles), NOT NULL | 역할 ID |
| permission_id | BIGINT | FK (permissions), NOT NULL | 권한 ID |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |

**인덱스**: UNIQUE `(role_id, permission_id)`

---

### 10. user_roles (사용자-역할 매핑)

**엔티티**: `UserRoleEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| user_id | BIGINT | FK (users), NOT NULL | 사용자 ID |
| role_id | BIGINT | FK (roles), NOT NULL | 역할 ID |
| assigned_at | TIMESTAMP | NOT NULL | 할당일시 |
| assigned_by | BIGINT | FK (users), NULL | 할당자 ID |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |

**인덱스**: UNIQUE `(user_id, role_id)`

---

## 전략 관련 테이블

### 11. strategy_categories (전략 카테고리)

**엔티티**: `StrategyCategoryEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 카테고리 ID |
| code | VARCHAR(50) | UNIQUE, NOT NULL | 카테고리 코드 |
| name | VARCHAR(100) | NOT NULL | 카테고리명 |
| description | TEXT | NULL | 설명 |
| icon | VARCHAR(50) | NULL | 아이콘 |
| sort_order | INT | DEFAULT 0 | 정렬 순서 |
| is_active | BOOLEAN | DEFAULT TRUE | 활성화 여부 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

---

### 12. strategies (전략)

**엔티티**: `StrategyEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 전략 ID |
| name | VARCHAR(100) | NOT NULL | 전략명 |
| description | TEXT | NULL | 설명 |
| category_id | BIGINT | FK (strategy_categories), NOT NULL | 카테고리 ID |
| owner_id | BIGINT | FK (users), NULL | 소유자 ID |
| is_public | BOOLEAN | DEFAULT FALSE | 공개 여부 |
| is_premium | BOOLEAN | DEFAULT FALSE | 프리미엄 전용 여부 |
| status | VARCHAR(20) | NOT NULL | 상태 |
| conditions | JSONB | NOT NULL | 전략 조건 (DSL) |
| rebalance_frequency | VARCHAR(20) | DEFAULT 'MONTHLY' | 리밸런싱 주기 |
| subscriber_count | INT | DEFAULT 0 | 구독자 수 |
| average_rating | DECIMAL(3,2) | DEFAULT 0 | 평균 평점 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

**전략 상태 (StrategyStatus)**:
- `DRAFT`: 초안
- `PENDING_REVIEW`: 검토 대기
- `APPROVED`: 승인됨
- `PUBLISHED`: 발행됨 (마켓플레이스 공개)
- `REJECTED`: 반려됨
- `ACTIVE`: 활성 (레거시)
- `ARCHIVED`: 보관됨

**리밸런싱 주기 (RebalanceFrequency)**:
- DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY, NONE

**연관 관계**:
- `N:1` → strategy_categories, users (owner)
- `1:N` → strategy_subscriptions, backtest_results, strategy_signals

---

### 13. strategy_subscriptions (전략 구독)

**엔티티**: `StrategySubscriptionEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 구독 ID |
| user_id | BIGINT | FK (users), NOT NULL | 사용자 ID |
| strategy_id | BIGINT | FK (strategies), NOT NULL | 전략 ID |
| status | VARCHAR(20) | NOT NULL | 상태 (ACTIVE, PAUSED, CANCELLED) |
| notify_signals | BOOLEAN | DEFAULT TRUE | 신호 알림 여부 |
| notify_rebalance | BOOLEAN | DEFAULT TRUE | 리밸런싱 알림 여부 |
| subscribed_at | TIMESTAMP | NOT NULL | 구독일시 |
| cancelled_at | TIMESTAMP | NULL | 취소일시 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |

**인덱스**: UNIQUE `(user_id, strategy_id)`

---

### 14. strategy_signals (전략 신호)

**엔티티**: `StrategySignalEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 신호 ID |
| strategy_id | BIGINT | FK (strategies), NOT NULL | 전략 ID |
| signal_date | DATE | NOT NULL | 신호 날짜 |
| signal_type | VARCHAR(20) | NOT NULL | 신호 타입 (BUY, SELL, REBALANCE, HOLD) |
| ticker | VARCHAR(20) | NULL | 종목 티커 |
| target_weight | DECIMAL(5,2) | NULL | 목표 비중 |
| reason | TEXT | NULL | 신호 사유 |
| conditions_snapshot | JSONB | NULL | 조건 스냅샷 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |

---

## 백테스트 관련 테이블

### 15. backtest_results (백테스트 결과)

**엔티티**: `BacktestResultEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 결과 ID |
| strategy_id | BIGINT | FK (strategies), NOT NULL | 전략 ID |
| user_id | BIGINT | FK (users), NULL | 실행자 ID |
| start_date | DATE | NOT NULL | 시작일 |
| end_date | DATE | NOT NULL | 종료일 |
| initial_capital | DECIMAL(15,2) | NOT NULL | 초기 자본 |
| benchmark | VARCHAR(20) | DEFAULT 'KOSPI' | 벤치마크 |
| final_value | DECIMAL(15,2) | NOT NULL | 최종 가치 |
| total_return | DECIMAL(10,4) | NOT NULL | 총 수익률 |
| cagr | DECIMAL(10,4) | NOT NULL | 연평균 수익률 |
| mdd | DECIMAL(10,4) | NOT NULL | 최대 낙폭 |
| sharpe_ratio | DECIMAL(10,4) | NULL | 샤프 비율 |
| sortino_ratio | DECIMAL(10,4) | NULL | 소르티노 비율 |
| volatility | DECIMAL(10,4) | NULL | 변동성 |
| win_rate | DECIMAL(5,2) | NULL | 승률 |
| total_trades | INT | DEFAULT 0 | 총 거래 수 |
| winning_trades | INT | DEFAULT 0 | 수익 거래 수 |
| losing_trades | INT | DEFAULT 0 | 손실 거래 수 |
| avg_win | DECIMAL(10,4) | NULL | 평균 수익 |
| avg_loss | DECIMAL(10,4) | NULL | 평균 손실 |
| benchmark_return | DECIMAL(10,4) | NULL | 벤치마크 수익률 |
| alpha | DECIMAL(10,4) | NULL | 알파 |
| beta | DECIMAL(10,4) | NULL | 베타 |
| equity_curve | JSONB | NULL | 자산 곡선 |
| status | VARCHAR(20) | NOT NULL | 상태 (RUNNING, COMPLETED, FAILED) |
| error_message | TEXT | NULL | 에러 메시지 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| completed_at | TIMESTAMP | NULL | 완료일시 |

**연관 관계**:
- `N:1` → strategies, users
- `1:N` → backtest_trades

---

### 16. backtest_trades (백테스트 거래 내역)

**엔티티**: `BacktestTradeEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 거래 ID |
| backtest_id | BIGINT | FK (backtest_results), NOT NULL | 백테스트 ID |
| trade_date | DATE | NOT NULL | 거래일 |
| ticker | VARCHAR(20) | NOT NULL | 종목 티커 |
| side | VARCHAR(10) | NOT NULL | 방향 (BUY, SELL) |
| quantity | INT | NOT NULL | 수량 |
| price | DECIMAL(15,4) | NOT NULL | 가격 |
| amount | DECIMAL(15,2) | NOT NULL | 거래 금액 |
| commission | DECIMAL(10,2) | DEFAULT 0 | 수수료 |
| pnl | DECIMAL(15,2) | NULL | 손익 (SELL 시) |
| pnl_percent | DECIMAL(10,4) | NULL | 손익률 |
| holding_days | INT | NULL | 보유일 |
| signal_reason | VARCHAR(255) | NULL | 신호 사유 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |

---

## 거래 관련 테이블

### 17. trades (거래 내역)

**엔티티**: `TradeEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 거래 ID |
| user_id | BIGINT | FK (users), NOT NULL | 사용자 ID |
| ticker | VARCHAR(10) | NOT NULL | 종목 티커 |
| side | VARCHAR(10) | NOT NULL | 거래 방향 (BUY, SELL) |
| quantity | INT | NOT NULL | 수량 |
| price | DECIMAL(10,2) | NOT NULL | 단가 |
| total_amount | DECIMAL(15,2) | NOT NULL | 총 거래 금액 |
| commission | DECIMAL(10,2) | DEFAULT 0.0 | 수수료 |
| status | VARCHAR(20) | DEFAULT 'PENDING' | 상태 (PENDING, EXECUTED, FAILED, CANCELLED) |
| kis_order_id | VARCHAR(100) | NULL | KIS 주문 ID |
| executed_at | TIMESTAMP | NULL | 체결 일시 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

---

### 18. trade_signals_executed (거래 신호 실행 기록)

**엔티티**: `TradeSignalExecutedEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 실행 기록 ID |
| user_id | BIGINT | FK (users), NOT NULL | 사용자 ID |
| recommendation_id | VARCHAR(100) | NOT NULL | 추천 ID (MongoDB 참조) |
| ticker | VARCHAR(10) | NOT NULL | 종목 티커 |
| signal | VARCHAR(20) | NOT NULL | 신호 (BUY, SELL, HOLD) |
| confidence | DECIMAL(3,2) | NOT NULL | 신뢰도 |
| execution_decision | VARCHAR(20) | NOT NULL | 실행 결정 (EXECUTED, SKIPPED, FAILED) |
| skip_reason | VARCHAR(255) | NULL | 스킵 사유 |
| executed_trade_id | BIGINT | FK (trades), NULL | 실행된 거래 ID |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |

---

## 마스터 데이터 테이블

### 19. stocks (종목 정보)

**엔티티**: `StockEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 종목 ID |
| ticker | VARCHAR(20) | UNIQUE, NOT NULL | 종목 티커 (예: AAPL, TSLA) |
| stock_name | VARCHAR(200) | NOT NULL | 한글 종목명 |
| stock_name_en | VARCHAR(200) | NULL | 영문 종목명 |
| is_etf | BOOLEAN | DEFAULT FALSE | ETF 여부 |
| leverage_ticker | VARCHAR(20) | NULL | 레버리지 상품 티커 |
| exchange | VARCHAR(50) | NULL | 거래소 (NASDAQ, NYSE 등) |
| sector | VARCHAR(100) | NULL | 섹터 |
| industry | VARCHAR(100) | NULL | 산업 |
| is_active | BOOLEAN | DEFAULT TRUE | 활성화 여부 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

**인덱스**:
- UNIQUE: `ticker`
- INDEX: `is_active`, `sector`, `industry`, `is_etf`

---

### 20. fred_indicators (FRED 경제 지표)

**엔티티**: `FredIndicatorEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 지표 ID |
| code | VARCHAR(50) | UNIQUE, NOT NULL | 지표 코드 |
| name | VARCHAR(200) | NOT NULL | 지표명 |
| description | TEXT | NULL | 설명 |
| category | VARCHAR(100) | NULL | 카테고리 |
| unit | VARCHAR(50) | NULL | 단위 |
| frequency | VARCHAR(20) | NULL | 빈도 (DAILY, WEEKLY, MONTHLY) |
| is_active | BOOLEAN | DEFAULT TRUE | 활성화 여부 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

---

### 21. yfinance_indicators (yfinance 지표)

**엔티티**: `YfinanceIndicatorEntity`

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 지표 ID |
| ticker | VARCHAR(20) | UNIQUE, NOT NULL | 티커 |
| name | VARCHAR(200) | NOT NULL | 지표명 |
| description | TEXT | NULL | 설명 |
| indicator_type | VARCHAR(50) | DEFAULT 'ETF' | 지표 타입 |
| is_active | BOOLEAN | DEFAULT TRUE | 활성화 여부 |
| created_at | TIMESTAMP | NOT NULL | 생성일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정일시 |

---

## MongoDB Collections

### 1. daily_stock_data (일별 주식 데이터)

**컬렉션명**: `daily_stock_data`

```javascript
{
  "_id": ObjectId,
  "date": "YYYY-MM-DD",
  "stocks": {
    "AAPL": { "close_price": 231.42 },
    "NVDA": { "close_price": 124.79 },
    // ... 35개 종목
  },
  "yfinance_indicators": {
    "S&P 500 ETF": 597.20,
    // ...
  },
  "fred_indicators": {
    // ...
  }
}
```

**인덱스**: `date` (UNIQUE)

---

### 2. economic_data (경제 지표 데이터)

**컬렉션명**: `economic_data`

```javascript
{
  "_id": ObjectId,
  "date": ISODate,
  "indicators": {
    "GDP": Double,
    "CPI": Double,
    "UnemploymentRate": Double,
    "InterestRate": Double
  },
  "created_at": ISODate
}
```

---

### 3. stock_recommendations (종목 추천)

**컬렉션명**: `stock_recommendations`

```javascript
{
  "_id": ObjectId,
  "ticker": String,
  "date": String,
  "stock_name": String,
  "current_price": Double,
  "composite_score": Double,
  "technical_indicators": {
    "sma20": Double,
    "sma50": Double,
    "rsi": Double,
    "macd": Double
  },
  "sentiment_score": Double,
  "is_recommended": Boolean,
  "updated_at": ISODate
}
```

**인덱스**: `(ticker, date)` UNIQUE

---

### 4. prediction_results (Vertex AI 예측 결과)

**컬렉션명**: `prediction_results`

```javascript
{
  "_id": ObjectId,
  "symbol": String,
  "date": ISODate,
  "predicted_price": Double,
  "confidence": Double,
  "signal": String,  // BUY, SELL, HOLD
  "predicted_change_percent": Double,
  "model_version": String,
  "vertex_ai_job_id": String,
  "created_at": ISODate
}
```

---

## Flyway 마이그레이션 히스토리

| 버전 | 파일명 | 설명 |
|------|--------|------|
| V1 | V1__Initial_Schema.sql | 초기 스키마 (users, trades 등) |
| V2 | V2__Create_Stocks_And_Indicators.sql | 종목, 지표 테이블 |
| V3 | V3__Create_Quartz_Tables.sql | Quartz 스케줄러 테이블 |
| V4 | V4__Insert_Initial_Data.sql | 초기 데이터 |
| V5 | V5__Create_User_KIS_Accounts.sql | KIS 계정 테이블 |
| V6 | V6__Create_KIS_Tokens_Table.sql | KIS 토큰 테이블 |
| V7 | V7__Create_RBAC_Tables.sql | RBAC 테이블 (roles, permissions) |
| V8 | V8__Insert_Admin_User_And_Permissions.sql | 관리자 계정 및 권한 |
| V9 | V9__Create_Strategy_And_Backtest_Tables.sql | 전략, 백테스트 테이블 |
| V10 | V10__Fix_Indicator_ID_Types.sql | 지표 ID 타입 수정 |
| V11 | V11__Add_User_Roles_Audit_Columns.sql | 사용자 역할 감사 컬럼 |
| V12 | V12__Add_ML_Prediction_Strategy.sql | ML 예측 전략 |
| V13 | V13__Create_User_Tiers_Table.sql | 사용자 티어 테이블 |
| V14 | V14__Add_OAuth_Columns_To_Users.sql | OAuth 컬럼 추가 |
| V15 | V15__Create_RBAC_Tables_And_Initial_Data.sql | RBAC 데이터 |
| V16 | V16__Insert_Seed_Strategies.sql | 시드 전략 |
| V17 | V17__Create_Strategy_Categories_Table.sql | 전략 카테고리 |
| V18 | V18__Migrate_Strategies_To_DSL_Format.sql | DSL 포맷 마이그레이션 |
| V19 | V19__Add_Missing_Table_Column_Comments.sql | 컬럼 코멘트 |
| V20 | V20__Create_Backtest_Checkpoints.sql | 백테스트 체크포인트 |

---

## ERD (Entity Relationship Diagram)

```
┌─────────────────────────────────────────────────────────────────────┐
│                           users                                      │
│─────────────────────────────────────────────────────────────────────│
│ id (PK) | user_id (UK) | email (UK) | oauth_provider | status       │
└────────────────────────────┬────────────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┬──────────────────┐
         │ 1:1                │ 1:1               │ 1:1              │
         ▼                    ▼                   ▼                  ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ trading_configs │  │ account_balances│  │user_kis_accounts│  │   user_tiers    │
└─────────────────┘  └─────────────────┘  └─────────────────┘  └─────────────────┘
         │
         │ 1:N
         ├───────────────────┬────────────────────┐
         ▼                   ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│     trades      │  │   kis_tokens    │  │   user_roles    │
└─────────────────┘  └─────────────────┘  └────────┬────────┘
                                                   │ N:1
                                                   ▼
                                          ┌─────────────────┐
                                          │     roles       │
                                          └────────┬────────┘
                                                   │ 1:N
                                                   ▼
                                          ┌─────────────────┐
                                          │role_permissions │
                                          └────────┬────────┘
                                                   │ N:1
                                                   ▼
                                          ┌─────────────────┐
                                          │  permissions    │
                                          └─────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                         strategies                                   │
│─────────────────────────────────────────────────────────────────────│
│ id (PK) | name | category_id (FK) | owner_id (FK) | conditions      │
└────────────────────────────┬────────────────────────────────────────┘
         │
         │ 1:N
         ├───────────────────┬────────────────────┐
         ▼                   ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ backtest_results│  │strategy_signals │  │strategy_subscri │
│                 │  │                 │  │    ptions       │
└────────┬────────┘  └─────────────────┘  └─────────────────┘
         │ 1:N
         ▼
┌─────────────────┐
│ backtest_trades │
└─────────────────┘

┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│strategy_categor │  │    stocks       │  │ fred_indicators │
│     ies         │  │                 │  │                 │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

---

## 관련 문서

### 아키텍처
- [시스템 아키텍처](../architecture/시스템_아키텍처.md) - 전체 시스템 구조
- [하이브리드 데이터베이스 전략](../architecture/하이브리드_데이터베이스_전략.md) - PostgreSQL + MongoDB 전략
- [데이터베이스 마이그레이션 현황](../architecture/데이터베이스_마이그레이션_현황.md) - 마이그레이션 상태
- [마이그레이션 히스토리](../architecture/마이그레이션_히스토리.md) - 변경 이력

### 데이터베이스
- [테이블 관계도](./RELATIONSHIPS.md) - ERD 및 관계

### 설정 가이드
- [초기 데이터 설정](../setup/초기_데이터_설정.md) - 초기 데이터 구성
- [환경설정 가이드](../setup/환경설정_가이드.md) - DB 연결 설정
- [RDB 빠른시작](../setup/RDB_빠른시작.md) - PostgreSQL 빠른 시작

### 소스 코드
- [Flyway Migration Scripts](../../quant-jump-stock-core/src/main/resources/db/migration)
- [JPA Entity Package](../../quant-jump-stock-core/src/main/kotlin/com/quantjumpstock/core/adapter/output/persistence/jpa)
