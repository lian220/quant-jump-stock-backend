"""V56 전략 업데이트 검증: 변경 전/후 백테스트 비교"""
import os, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "src"))
from dotenv import load_dotenv
for f in [".env.common", ".env.local", ".env.db.local", ".env.db.prod"]:
    p = Path(__file__).resolve().parent.parent.parent / f
    if p.exists(): load_dotenv(p, override=True)

from datetime import date
from decimal import Decimal
from application.backtest.data_loader_mongo import MongoDataLoader
from application.backtest.engine import BacktestEngine, BacktestConfig
from domain.strategy.models import StrategyDefinition

# =====================================================================
# 전략 정의: 변경 전 (v1) / 변경 후 (v2)
# =====================================================================
strategies = {
    # --- 1. RSI 과매도 ---
    "rsi_oversold_v1": {
        "strategy_id": "rsi_oversold_v1", "name": "[v1] RSI 과매도", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 30}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 70}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.03, "take_profit_pct": 0.1, "max_position_pct": 0.1}
    },
    "rsi_oversold_v2": {
        "strategy_id": "rsi_oversold_v2", "name": "[v2] RSI 과매도", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 60}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.05, "take_profit_pct": 0.15, "max_position_pct": 0.1}
    },

    # --- 2. 평균 회귀 ---
    "mean_reversion_v1": {
        "strategy_id": "mean_reversion_v1", "name": "[v1] 평균 회귀", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 25},
                {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 75},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_upper"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.02, "take_profit_pct": 0.06, "max_position_pct": 0.08}
    },
    "mean_reversion_v2": {
        "strategy_id": "mean_reversion_v2", "name": "[v2] 평균 회귀", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 35},
                {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_upper"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.03, "take_profit_pct": 0.08, "max_position_pct": 0.08}
    },

    # --- 3. RSI 스캘핑 ---
    "scalping_rsi_v1": {
        "strategy_id": "scalping_rsi_v1", "name": "[v1] RSI 스캘핑", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 7}, "operator": "lt", "value": 20}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 7}, "operator": "gt", "value": 80}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.015, "take_profit_pct": 0.03, "max_position_pct": 0.05}
    },
    "scalping_rsi_v2": {
        "strategy_id": "scalping_rsi_v2", "name": "[v2] RSI 스캘핑", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 7}, "operator": "lt", "value": 25}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 7}, "operator": "gt", "value": 75}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.015, "take_profit_pct": 0.03, "max_position_pct": 0.05}
    },

    # --- 4. 금리 연동 배분 ---
    "rate_linked_v1": {
        "strategy_id": "rate_linked_v1", "name": "[v1] 금리 연동", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "treasury_10y", "params": {}, "operator": "lt", "value": 2}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "treasury_10y", "params": {}, "operator": "gte", "value": 4}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.12, "take_profit_pct": 0.25, "max_position_pct": 0.8}
    },
    "rate_linked_v2": {
        "strategy_id": "rate_linked_v2", "name": "[v2] 금리 연동", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "treasury_10y", "params": {}, "operator": "lt", "value": 4.2}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "treasury_10y", "params": {}, "operator": "gte", "value": 4.8}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.12, "take_profit_pct": 0.25, "max_position_pct": 0.8}
    },

    # --- 5. 추세 추종 ---
    "trend_following_v1": {
        "strategy_id": "trend_following_v1", "name": "[v1] 추세 추종", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"},
                {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"}
            ], "logic": "and", "weight": 0.85},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "price", "params": {}, "operator": "lt", "value": "sma_50"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.07, "take_profit_pct": 0.25, "max_position_pct": 0.12,
                            "trailing_stop": True, "trailing_stop_pct": 0.05}
    },
    "trend_following_v2": {
        "strategy_id": "trend_following_v2", "name": "[v2] 추세 추종+매크로", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"},
                {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"},
                {"indicator": "t10y2y", "params": {}, "operator": "gte", "value": 0}
            ], "logic": "and", "weight": 0.85},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "price", "params": {}, "operator": "lt", "value": "sma_50"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.07, "take_profit_pct": 0.25, "max_position_pct": 0.12,
                            "trailing_stop": True, "trailing_stop_pct": 0.05}
    },

    # --- 6. 듀얼 모멘텀 ---
    "dual_momentum_v1": {
        "strategy_id": "dual_momentum_v1", "name": "[v1] 듀얼 모멘텀", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 50},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "price", "params": {}, "operator": "lt", "value": "sma_200"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.08, "take_profit_pct": 0.3, "max_position_pct": 0.15}
    },
    "dual_momentum_v2": {
        "strategy_id": "dual_momentum_v2", "name": "[v2] 듀얼 모멘텀+매크로", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 50},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0},
                {"indicator": "t10y2y", "params": {}, "operator": "gte", "value": 0}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "price", "params": {}, "operator": "lt", "value": "sma_200"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.08, "take_profit_pct": 0.3, "max_position_pct": 0.15}
    },
}

SYMBOLS = ["AAPL", "NVDA", "TSLA", "MSFT", "GOOGL", "AMZN", "META", "JNJ", "AMAT", "INTC"]

loader = MongoDataLoader(uri=os.environ["MONGODB_URI"])
config = BacktestConfig(
    start_date=date(2025, 9, 1), end_date=date(2026, 2, 25),
    initial_capital=Decimal("100000"), tickers=SYMBOLS,
    commission_rate=Decimal("0.00015"), tax_rate=Decimal("0.0023"),
    slippage_rate=Decimal("0.001"), max_positions=5, position_size_pct=Decimal("0.2"),
    benchmark_ticker="SPY",
)

# 실행
results = {}
for sid, sdict in strategies.items():
    strategy = StrategyDefinition(**sdict)
    engine = BacktestEngine(data_loader=loader, config=config)
    r = engine.run(strategy)
    results[sid] = {
        "total_return": round(float(r.total_return), 2),
        "total_trades": r.total_trades,
        "win_rate": round(float(r.win_rate), 1) if r.win_rate else 0,
        "mdd": round(float(r.mdd), 2) if r.mdd else 0,
        "sharpe_ratio": round(float(r.sharpe_ratio), 2) if r.sharpe_ratio else 0,
    }

# 출력
pairs = [
    ("rsi_oversold", "RSI 과매도"),
    ("mean_reversion", "평균 회귀"),
    ("scalping_rsi", "RSI 스캘핑"),
    ("rate_linked", "금리 연동"),
    ("trend_following", "추세 추종"),
    ("dual_momentum", "듀얼 모멘텀"),
]

print("=" * 100)
print("V56 전략 업데이트 검증: v1 (변경 전) vs v2 (변경 후)")
print("기간: 2025-09-01 ~ 2026-02-25 | 종목: %d개 | 자본: $100,000" % len(SYMBOLS))
print("=" * 100)
print("%-18s | %10s %10s | %7s %7s | %7s %7s | %7s %7s | %7s %7s | %s" % (
    "전략", "수익v1", "수익v2", "거래v1", "거래v2",
    "승률v1", "승률v2", "MDDv1", "MDDv2", "Sharpv1", "Sharpv2", "변화"
))
print("-" * 120)

for key, name in pairs:
    v1 = results[key + "_v1"]
    v2 = results[key + "_v2"]
    diff = v2["total_return"] - v1["total_return"]
    arrow = "▲" if diff > 0 else "▼" if diff < 0 else "="

    print("%-16s | %9.2f%% %9.2f%% | %7d %7d | %6.1f%% %6.1f%% | %6.2f%% %6.2f%% | %7.2f %7.2f | %s%+.2f%%p" % (
        name,
        v1["total_return"], v2["total_return"],
        v1["total_trades"], v2["total_trades"],
        v1["win_rate"], v2["win_rate"],
        v1["mdd"], v2["mdd"],
        v1["sharpe_ratio"], v2["sharpe_ratio"],
        arrow, diff
    ))

print("=" * 100)

# T10Y2Y 현재값 확인
print("\n[참고] FRED 데이터 현재 상태:")
engine_check = BacktestEngine(data_loader=loader, config=config)
check_strategy = StrategyDefinition(**strategies["trend_following_v2"])
# t10y2y 데이터 직접 확인
import pandas as pd
data = loader.load(
    symbols=["AAPL"], start_date=date(2025, 9, 1), end_date=date(2026, 2, 25),
    include_fred=True,
)
aapl_data = data.get("AAPL")
if aapl_data is not None and "t10y2y" in aapl_data.columns:
    t10y2y_vals = aapl_data["t10y2y"].dropna()
    if not t10y2y_vals.empty:
        print("  T10Y2Y 범위: %.3f ~ %.3f (최신: %.3f)" % (
            t10y2y_vals.min(), t10y2y_vals.max(), t10y2y_vals.iloc[-1]
        ))
        inverted_days = (t10y2y_vals < 0).sum()
        total_days = len(t10y2y_vals)
        print("  역전일(T10Y2Y<0): %d/%d일 (%.1f%%)" % (inverted_days, total_days, inverted_days/total_days*100))
    treasury = aapl_data["treasury_10y"].dropna()
    if not treasury.empty:
        print("  DGS10 범위: %.3f ~ %.3f (최신: %.3f)" % (
            treasury.min(), treasury.max(), treasury.iloc[-1]
        ))
else:
    print("  T10Y2Y 데이터 없음")

loader.close()
print("\n검증 완료.")
