"""전략 개선 v2: 엔진 수정 후 전체 전략 재테스트
핵심 수정:
1. 엔진: 리스크 매니저에도 최소 보유 기간 적용 (stop loss 즉시 발동 방지)
2. MACD: 트렌드 필터 + SL/TP 확대
3. Triple Screen: 매도 조건 개선 + SL 확대
4. Golden Cross: SL 확대
5. Momentum Breakout: SL 확대 + 트렌드 필터
6. Trend Following: SL 확대
7. Dual Momentum: SL 확대
8. Bollinger: 단기 볼린저(10) 사용
9. Mean Reversion: RSI 조건 완화
10. High Dividend: 미국 테크주 특성 반영 조건 완화
"""
import os, sys, json
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
# 전략 정의: v1 (기존) vs v2 (개선)
# =====================================================================
STRATEGIES_V1 = {
    "golden_cross": {
        "strategy_id": "golden_cross", "name": "골든크로스 (v1)", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_below", "value": "sma_50"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.05, "take_profit_pct": 0.15, "max_position_pct": 0.1}
    },
    "macd_crossover": {
        "strategy_id": "macd_crossover", "name": "MACD (v1)", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "macd", "params": {}, "operator": "crosses_above", "value": "macd_signal"}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "macd", "params": {}, "operator": "crosses_below", "value": "macd_signal"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.04, "take_profit_pct": 0.12, "max_position_pct": 0.1}
    },
    "triple_screen": {
        "strategy_id": "triple_screen", "name": "트리플 스크린 (v1)", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 70},
                {"indicator": "macd", "params": {}, "operator": "gt", "value": "macd_signal"}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "lt", "value": "sma_50"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 30},
                {"indicator": "macd", "params": {}, "operator": "lt", "value": "macd_signal"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.05, "take_profit_pct": 0.2, "max_position_pct": 0.08}
    },
    "momentum_breakout": {
        "strategy_id": "momentum_breakout", "name": "모멘텀 브레이크아웃 (v1)", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "ema_20"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 50}
            ], "logic": "and", "weight": 0.9},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "price", "params": {}, "operator": "lt", "value": "ema_20"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 50}
            ], "logic": "and", "weight": 0.9}
        ],
        "risk_management": {"stop_loss_pct": 0.04, "take_profit_pct": 0.12, "max_position_pct": 0.1}
    },
    "bollinger_squeeze": {
        "strategy_id": "bollinger_squeeze", "name": "볼린저 밴드 (v1)", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_upper"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.03, "take_profit_pct": 0.08, "max_position_pct": 0.1}
    },
    "mean_reversion": {
        "strategy_id": "mean_reversion", "name": "평균 회귀 (v1)", "version": "1.0",
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
    "high_dividend": {
        "strategy_id": "high_dividend", "name": "고배당 가치주 (v1)", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "dividend_yield", "params": {"type": "fundamental"}, "operator": "gt", "value": 4.0},
                {"indicator": "pbr", "params": {"type": "fundamental"}, "operator": "lt", "value": 1.0}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "dividend_yield", "params": {"type": "fundamental"}, "operator": "lt", "value": 2.0}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.1, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },
}

STRATEGIES_V2 = {
    # === 1. 골든크로스: SL 5%→10%, TP 15%→25% (중기 전략에 넓은 SL) ===
    "golden_cross": {
        "strategy_id": "golden_cross", "name": "골든크로스 (v2)", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_below", "value": "sma_50"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },

    # === 2. MACD: + 트렌드 필터(price > SMA 200), SL 4%→10%, TP 12%→25% ===
    "macd_crossover": {
        "strategy_id": "macd_crossover", "name": "MACD (v2)", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "macd", "params": {}, "operator": "crosses_above", "value": "macd_signal"},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "macd", "params": {}, "operator": "crosses_below", "value": "macd_signal"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },

    # === 3. 트리플 스크린: 매도 RSI>30 제거 (무의미), SL 5%→10%, TP 20%→30% ===
    "triple_screen": {
        "strategy_id": "triple_screen", "name": "트리플 스크린 (v2)", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 70},
                {"indicator": "macd", "params": {}, "operator": "gt", "value": "macd_signal"}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "lt", "value": "sma_50"},
                {"indicator": "macd", "params": {}, "operator": "lt", "value": "macd_signal"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.30, "max_position_pct": 0.08}
    },

    # === 4. 모멘텀 브레이크아웃: + SMA_50 트렌드 필터, SL 4%→8%, TP 12%→20% ===
    "momentum_breakout": {
        "strategy_id": "momentum_breakout", "name": "모멘텀 브레이크아웃 (v2)", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "ema_20"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 50},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"}
            ], "logic": "and", "weight": 0.9},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "price", "params": {}, "operator": "lt", "value": "sma_50"}
            ], "logic": "and", "weight": 0.9}
        ],
        "risk_management": {"stop_loss_pct": 0.08, "take_profit_pct": 0.20, "max_position_pct": 0.1}
    },

    # === 5. 볼린저: 매도를 볼린저 미들로 변경 (상단까지 기다리지 않고 평균 회귀) ===
    "bollinger_squeeze": {
        "strategy_id": "bollinger_squeeze", "name": "볼린저 밴드 (v2)", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_middle"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.05, "take_profit_pct": 0.10, "max_position_pct": 0.1}
    },

    # === 6. 평균 회귀: RSI 35→45, 매도 RSI 65→55 + 볼린저 미들 ===
    "mean_reversion": {
        "strategy_id": "mean_reversion", "name": "평균 회귀 (v2)", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 45},
                {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_middle"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.05, "take_profit_pct": 0.10, "max_position_pct": 0.08}
    },

    # === 7. 고배당: 미국 테크주 특성 반영 (div>1.5, PBR<10) ===
    "high_dividend": {
        "strategy_id": "high_dividend", "name": "고배당 가치주 (v2)", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "dividend_yield", "params": {"type": "fundamental"}, "operator": "gt", "value": 1.5},
                {"indicator": "pbr", "params": {"type": "fundamental"}, "operator": "lt", "value": 10.0}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "dividend_yield", "params": {"type": "fundamental"}, "operator": "lt", "value": 0.5}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },
}

# 이미 v2로 확정된 전략 (V56 적용)
ALREADY_V2 = {
    "rsi_oversold": {
        "strategy_id": "rsi_oversold", "name": "RSI 과매도 (v2)", "version": "2.0",
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
    "scalping_rsi": {
        "strategy_id": "scalping_rsi", "name": "RSI 스캘핑", "version": "1.0",
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
    "trend_following": {
        "strategy_id": "trend_following", "name": "추세 추종 (v2)", "version": "2.0",
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
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.30, "max_position_pct": 0.12,
                            "trailing_stop": True, "trailing_stop_pct": 0.07}
    },
    "dual_momentum": {
        "strategy_id": "dual_momentum", "name": "듀얼 모멘텀 (v2)", "version": "2.0",
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
        "risk_management": {"stop_loss_pct": 0.12, "take_profit_pct": 0.35, "max_position_pct": 0.15}
    },
    "rate_linked_allocation": {
        "strategy_id": "rate_linked_allocation", "name": "금리 연동 배분 (v2)", "version": "2.0",
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
}

SYMBOLS = ["AAPL", "NVDA", "TSLA", "MSFT", "GOOGL", "AMZN", "META", "JNJ", "AMAT", "INTC"]

# =====================================================================
# 실행
# =====================================================================
loader = MongoDataLoader(uri=os.environ["MONGODB_URI"])
config = BacktestConfig(
    start_date=date(2021, 1, 1), end_date=date(2026, 2, 25),
    initial_capital=Decimal("100000"), tickers=SYMBOLS,
    commission_rate=Decimal("0.00015"), tax_rate=Decimal("0.0023"),
    slippage_rate=Decimal("0.001"), max_positions=5, position_size_pct=Decimal("0.2"),
    benchmark_ticker="SPY",
)

print("=" * 140)
print("전략 개선 v2 백테스트 (엔진 수정 포함)")
print("핵심 수정: 리스크 매니저에도 최소 보유 기간 적용 (stop loss 즉시 발동 방지)")
print("기간: 2021-01-01 ~ 2026-02-25 | 종목: %d개 | 자본: $100,000" % len(SYMBOLS))
print("=" * 140)

# 1. v1 vs v2 비교 대상 전략
print("\n[PHASE 1] v1 vs v2 비교 테스트 (7개 전략)")
print("-" * 140)
print("%-4s %-28s | %10s | %6s | %7s | %8s | %7s | %8s" % (
    "#", "전략명", "수익률", "거래수", "승률", "MDD", "Sharpe", "CAGR"
))
print("-" * 140)

comparison_results = {}
for sid in STRATEGIES_V1.keys():
    v1_def = StrategyDefinition(**STRATEGIES_V1[sid])
    v2_def = StrategyDefinition(**STRATEGIES_V2[sid])

    e1 = BacktestEngine(data_loader=loader, config=config)
    r1 = e1.run(v1_def)

    e2 = BacktestEngine(data_loader=loader, config=config)
    r2 = e2.run(v2_def)

    comparison_results[sid] = {
        "v1": {"return": float(r1.total_return), "trades": r1.total_trades,
               "win_rate": float(r1.win_rate) if r1.win_rate else 0,
               "mdd": float(r1.mdd) if r1.mdd else 0,
               "sharpe": float(r1.sharpe_ratio) if r1.sharpe_ratio else 0,
               "cagr": float(r1.cagr) if r1.cagr else 0},
        "v2": {"return": float(r2.total_return), "trades": r2.total_trades,
               "win_rate": float(r2.win_rate) if r2.win_rate else 0,
               "mdd": float(r2.mdd) if r2.mdd else 0,
               "sharpe": float(r2.sharpe_ratio) if r2.sharpe_ratio else 0,
               "cagr": float(r2.cagr) if r2.cagr else 0},
    }

    delta = comparison_results[sid]["v2"]["return"] - comparison_results[sid]["v1"]["return"]
    v1 = comparison_results[sid]["v1"]
    v2 = comparison_results[sid]["v2"]

    print("  v1 %-25s | %9.2f%% | %6d | %6.1f%% | %7.2f%% | %7.2f | %7.2f%%" % (
        STRATEGIES_V1[sid]["name"], v1["return"], v1["trades"], v1["win_rate"], v1["mdd"], v1["sharpe"], v1["cagr"]))
    arrow = "▲" if delta > 0 else "▼" if delta < 0 else "="
    print("  v2 %-25s | %9.2f%% | %6d | %6.1f%% | %7.2f%% | %7.2f | %7.2f%%  %s %+.2f%%p" % (
        STRATEGIES_V2[sid]["name"], v2["return"], v2["trades"], v2["win_rate"], v2["mdd"], v2["sharpe"], v2["cagr"], arrow, delta))
    print()

# 2. 이미 확정된 전략 (기존 v2 + 엔진 수정 효과)
print("\n[PHASE 2] 기존 확정 전략 재테스트 (5개, 엔진 수정 효과 확인)")
print("-" * 140)
print("%-4s %-28s | %10s | %6s | %7s | %8s | %7s | %8s" % (
    "#", "전략명", "수익률", "거래수", "승률", "MDD", "Sharpe", "CAGR"
))
print("-" * 140)

existing_results = {}
for sid, sdict in ALREADY_V2.items():
    strategy = StrategyDefinition(**sdict)
    engine = BacktestEngine(data_loader=loader, config=config)
    r = engine.run(strategy)
    existing_results[sid] = {
        "return": float(r.total_return), "trades": r.total_trades,
        "win_rate": float(r.win_rate) if r.win_rate else 0,
        "mdd": float(r.mdd) if r.mdd else 0,
        "sharpe": float(r.sharpe_ratio) if r.sharpe_ratio else 0,
        "cagr": float(r.cagr) if r.cagr else 0,
    }
    er = existing_results[sid]
    print("  %-28s | %9.2f%% | %6d | %6.1f%% | %7.2f%% | %7.2f | %7.2f%%" % (
        sdict["name"], er["return"], er["trades"], er["win_rate"], er["mdd"], er["sharpe"], er["cagr"]))

# 3. 전체 결과 요약 (v2 기준)
print("\n\n" + "=" * 140)
print("전체 결과 요약 (v2 기준, 수익률 순)")
print("=" * 140)
print("%-4s %-28s | %10s | %6s | %7s | %8s | %7s | %8s | %s" % (
    "#", "전략명", "수익률", "거래수", "승률", "MDD", "Sharpe", "CAGR", "판정"
))
print("-" * 140)

all_v2 = {}
for sid, res in comparison_results.items():
    all_v2[sid] = res["v2"]
    all_v2[sid]["name"] = STRATEGIES_V2[sid]["name"]
for sid, res in existing_results.items():
    all_v2[sid] = res
    all_v2[sid]["name"] = ALREADY_V2[sid]["name"]

sorted_v2 = sorted(all_v2.items(), key=lambda x: x[1]["return"], reverse=True)
for idx, (sid, r) in enumerate(sorted_v2, 1):
    if r["return"] > 20:
        grade = "A"
    elif r["return"] > 0:
        grade = "B"
    elif r["return"] > -20:
        grade = "C"
    elif r["trades"] == 0:
        grade = "N/A"
    else:
        grade = "F"
    print("%-4d %-28s | %9.2f%% | %6d | %6.1f%% | %7.2f%% | %7.2f | %7.2f%% | %s" % (
        idx, r["name"], r["return"], r["trades"], r["win_rate"], r["mdd"], r["sharpe"], r["cagr"], grade))

# 4. 결정론 검증 (v2 전략 3회 반복)
print("\n\n[PHASE 3] 결정론 검증 (v2 전략 2회 추가 실행)")
print("-" * 80)

all_det_pass = True
for sid, sdict in {**STRATEGIES_V2, **ALREADY_V2}.items():
    strategy = StrategyDefinition(**sdict)
    returns = [all_v2[sid]["return"]]
    for _ in range(2):
        engine = BacktestEngine(data_loader=loader, config=config)
        r = engine.run(strategy)
        returns.append(round(float(r.total_return), 2))
    det_ok = len(set([round(r, 2) for r in returns])) == 1
    if not det_ok:
        all_det_pass = False
        print("  FAIL %-25s : %s" % (sdict["name"], returns))

if all_det_pass:
    print("  전체 PASS - 12개 전략 x 3회 = 모두 동일 결과")

# 5. JSON 저장
output = {
    "test_config": {
        "period": "2021-01-01 ~ 2026-02-25",
        "symbols": SYMBOLS,
        "initial_capital": 100000,
        "engine_changes": [
            "리스크 매니저에도 최소 보유 기간 적용 (stop loss 즉시 발동 방지)",
            "쿨다운 5일, 최소 보유 5일 유지",
        ]
    },
    "v1_vs_v2_comparison": comparison_results,
    "existing_v2_results": existing_results,
    "all_v2_ranking": {sid: r for sid, r in sorted_v2},
    "determinism": "ALL_PASS" if all_det_pass else "SOME_FAIL",
}

out_path = Path(__file__).resolve().parent / "strategy_improvement_v2_results.json"
with open(out_path, "w", encoding="utf-8") as f:
    json.dump(output, f, ensure_ascii=False, indent=2, default=str)
print(f"\n결과 저장: {out_path}")

loader.close()
print("완료.")
