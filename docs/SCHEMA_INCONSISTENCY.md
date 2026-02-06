# 스키마 불일치 목록 (TODO: 나중에 수정)

이 문서는 백테스트 통합 테스트 중 발견된 DB 스키마와 코드 간 불일치를 기록합니다.

## 1. backtest_trades 테이블

### 현재 DB 스키마 (V9 마이그레이션)
```sql
CREATE TABLE backtest_trades (
    id BIGSERIAL PRIMARY KEY,
    backtest_id BIGINT NOT NULL,      -- FK to backtest_results
    trade_date DATE NOT NULL,
    ticker VARCHAR(20) NOT NULL,       -- "ticker" 사용
    side VARCHAR(10) NOT NULL,         -- "side" 사용 (BUY/SELL)
    quantity INTEGER NOT NULL,
    price NUMERIC(15, 4) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL,
    commission NUMERIC(10, 2),
    pnl NUMERIC(15, 2),                -- "pnl" 사용
    pnl_percent NUMERIC(10, 4),
    holding_days INTEGER,
    signal_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL
);
```

### 코드에서 사용하던 필드명 (수정 전)
| DB 컬럼 | 코드에서 사용 | 비고 |
|---------|--------------|------|
| `backtest_id` | `backtest_result_id` | 불일치 |
| `ticker` | `symbol` | 불일치 |
| `side` | `trade_type` | 불일치 |
| `pnl` | `realized_pnl` | 불일치 |
| `signal_reason` | `exit_reason` | 불일치 |

### 권장 통일안
DB 스키마 기준으로 통일 (다른 테이블들도 `ticker` 사용):
- `ticker` 유지 (7개 테이블에서 사용 중)
- `side` 유지 (BUY/SELL)
- `pnl` 유지

---

## 2. backtest_results 테이블

### 현재 DB 스키마
```sql
CREATE TABLE backtest_results (
    id BIGSERIAL PRIMARY KEY,
    strategy_id BIGINT NOT NULL,
    user_id BIGINT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    initial_capital NUMERIC(15,2) NOT NULL,
    benchmark VARCHAR(20),
    final_value NUMERIC(15,2) NOT NULL,
    total_return NUMERIC(10,4) NOT NULL,   -- 수익률 (소수점)
    cagr NUMERIC(10,4) NOT NULL,
    mdd NUMERIC(10,4) NOT NULL,
    sharpe_ratio NUMERIC(10,4),
    sortino_ratio NUMERIC(10,4),
    volatility NUMERIC(10,4),
    win_rate NUMERIC(5,2),
    total_trades INTEGER,
    winning_trades INTEGER,
    losing_trades INTEGER,
    avg_win NUMERIC(10,4),
    avg_loss NUMERIC(10,4),
    benchmark_return NUMERIC(10,4),
    alpha NUMERIC(10,4),
    beta NUMERIC(10,4),
    equity_curve JSONB,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);
```

### 코드에서 사용하던 필드 (수정 전)
| 코드 필드 | DB 컬럼 | 상태 |
|-----------|---------|------|
| `total_return_pct` | 없음 | DB에 컬럼 없음 |
| `largest_win` | 없음 | DB에 컬럼 없음 |
| `largest_loss` | 없음 | DB에 컬럼 없음 |
| `profit_factor` | 없음 | DB에 컬럼 없음 |
| `avg_holding_period` | 없음 | DB에 컬럼 없음 |
| `exit_reason_counts` | 없음 | DB에 컬럼 없음 |
| `execution_time_seconds` | 없음 | DB에 컬럼 없음 |

### 권장 조치
1. **DB에 컬럼 추가** (V20 마이그레이션):
   - `profit_factor` - Profit Factor (총수익/총손실)
   - `avg_holding_days` - 평균 보유 기간
   - `largest_win` - 최대 수익 거래
   - `largest_loss` - 최대 손실 거래
   - `exit_reason_counts` - JSON (청산 사유별 카운트)
   - `execution_time_seconds` - 실행 시간

2. **또는 코드에서 제거** (현재 임시 조치)

---

## 3. 필드명 일관성 문제

### symbols vs tickers
| 위치 | 사용 필드 | 상태 |
|------|-----------|------|
| DB 테이블들 | `ticker` | 7개 테이블에서 사용 |
| Core API DTO | `tickers` | 수정 완료 |
| Data Engine Handler | `tickers` | 수정 완료 |
| BacktestConfig | `tickers` | 수정 완료 |
| BacktestEngine | `tickers` | 수정 완료 |

**결론**: 전체 코드베이스에서 `tickers` (리스트) / `ticker` (단일) 사용으로 통일

---

## 4. V19 마이그레이션 삭제됨

### 원인
- V19__Add_Column_Comments.sql이 실제 DB 스키마와 불일치
- 존재하지 않는 컬럼에 코멘트 추가 시도

### 조치
- V19 파일 삭제 (임시)
- 나중에 실제 DB 스키마에 맞는 코멘트 마이그레이션 작성 필요

---

## 5. EquityCurvePoint 속성

### 클래스 정의
```python
@dataclass
class EquityCurvePoint:
    date: date
    equity: Decimal      # "equity" 사용 (not "value")
    cash: Decimal
    positions_value: Decimal
    drawdown_pct: Decimal
```

### 수정 완료
- Repository에서 `p.value` → `p.equity`로 수정

---

## 작업 기록

| 날짜 | 작업 내용 |
|------|----------|
| 2026-02-05 | 백테스트 통합 테스트 중 스키마 불일치 발견 |
| 2026-02-05 | Repository INSERT 문을 DB 스키마에 맞게 수정 |
| 2026-02-05 | symbols → tickers로 전체 코드베이스 수정 |
| 2026-02-05 | V19 마이그레이션 삭제 (나중에 재작성 필요) |
