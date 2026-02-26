"""전체 전략 5년 백테스트 + 결정론 검증
기간: 2021-01-01 ~ 2026-02-25 (FRED/SPY 데이터 시작점)
대상: 백테스트 가능한 12개 전략 (미구현 지표 사용 전략 제외)
검증: 각 전략 3회 반복 실행 → 동일 결과 확인
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
# 전략 정의 (V56 적용 후 최종 버전)
# =====================================================================
STRATEGIES = {
    # === MOMENTUM (6개) ===
    "golden_cross": {
        "strategy_id": "golden_cross", "name": "골든크로스", "version": "1.0",
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
    "rsi_oversold": {  # V56 업데이트: RSI 30→42, sell 70→60
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
    "macd_crossover": {
        "strategy_id": "macd_crossover", "name": "MACD 크로스오버", "version": "1.0",
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
    "bollinger_squeeze": {
        "strategy_id": "bollinger_squeeze", "name": "볼린저 밴드 스퀴즈", "version": "1.0",
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
    "momentum_breakout": {
        "strategy_id": "momentum_breakout", "name": "모멘텀 브레이크아웃", "version": "1.0",
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
    "trend_following": {  # V56 업데이트: +T10Y2Y>=0 필터
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
        "risk_management": {"stop_loss_pct": 0.07, "take_profit_pct": 0.25, "max_position_pct": 0.12,
                            "trailing_stop": True, "trailing_stop_pct": 0.05}
    },

    # === QUANT_COMPOSITE (4개) ===
    "triple_screen": {
        "strategy_id": "triple_screen", "name": "트리플 스크린", "version": "1.0",
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
    "mean_reversion": {  # V56 업데이트: RSI 25→35, sell 75→65
        "strategy_id": "mean_reversion", "name": "평균 회귀 (v2)", "version": "2.0",
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
    "dual_momentum": {  # V56 업데이트: +T10Y2Y>=0 필터
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
        "risk_management": {"stop_loss_pct": 0.08, "take_profit_pct": 0.3, "max_position_pct": 0.15}
    },
    "scalping_rsi": {  # V56 변경 취소: 기존 유지
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

    # === ASSET_ALLOCATION (1개 - 백테스트 가능한 것만) ===
    "rate_linked_allocation": {  # V56 업데이트: buy<4.2, sell>=4.8
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

    # === VALUE (1개 - per_sector_ratio 미구현으로 high_dividend만) ===
    "high_dividend": {
        "strategy_id": "high_dividend", "name": "고배당 가치주", "version": "1.0",
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

# 백테스트 불가 전략 (미구현 지표)
SKIPPED = {
    "low_per_value": "per_sector_ratio 미구현",
    "balanced_60_40": "portfolio_drift 미구현",
    "all_weather": "portfolio_drift 미구현",
    "january_effect": "calendar 미구현",
    "sell_in_may": "calendar 미구현",
    "quarterly_momentum": "calendar+momentum_3m 미구현",
    "vertex_ai_prediction": "ml_prediction 미구현",
    "ai_technical_hybrid": "ensemble_score 미구현",
}

SYMBOLS = ["AAPL", "NVDA", "TSLA", "MSFT", "GOOGL", "AMZN", "META", "JNJ", "AMAT", "INTC"]
NUM_RUNS = 3

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

print("=" * 130)
print("전체 전략 5년 백테스트 + 결정론 검증")
print("기간: 2021-01-01 ~ 2026-02-25 | 종목: %d개 | 자본: $100,000 | 반복: %d회" % (len(SYMBOLS), NUM_RUNS))
print("=" * 130)

all_results = {}  # {strategy_id: [run1, run2, run3]}

for sid, sdict in STRATEGIES.items():
    strategy = StrategyDefinition(**sdict)
    runs = []
    for run_idx in range(NUM_RUNS):
        engine = BacktestEngine(data_loader=loader, config=config)
        r = engine.run(strategy)
        runs.append({
            "total_return": round(float(r.total_return), 2),
            "total_trades": r.total_trades,
            "win_rate": round(float(r.win_rate), 1) if r.win_rate else 0.0,
            "mdd": round(float(r.mdd), 2) if r.mdd else 0.0,
            "sharpe": round(float(r.sharpe_ratio), 2) if r.sharpe_ratio else 0.0,
            "cagr": round(float(r.cagr), 2) if r.cagr else 0.0,
        })
    all_results[sid] = runs
    # 진행 표시
    det = "PASS" if all(r["total_return"] == runs[0]["total_return"] for r in runs) else "FAIL"
    print("  [%s] %-25s | 수익률: %8.2f%% | 거래: %4d | 승률: %5.1f%% | MDD: %7.2f%% | Sharpe: %6.2f | CAGR: %7.2f%%" % (
        det, sdict["name"],
        runs[0]["total_return"], runs[0]["total_trades"],
        runs[0]["win_rate"], runs[0]["mdd"],
        runs[0]["sharpe"], runs[0]["cagr"]
    ))

# =====================================================================
# 결과 출력
# =====================================================================
print()
print("=" * 130)
print("%-4s %-25s | %10s | %6s | %7s | %8s | %7s | %8s | %s" % (
    "#", "전략명", "수익률", "거래수", "승률", "MDD", "Sharpe", "CAGR", "결정론"
))
print("-" * 130)

sorted_results = sorted(all_results.items(), key=lambda x: x[1][0]["total_return"], reverse=True)

for idx, (sid, runs) in enumerate(sorted_results, 1):
    r = runs[0]
    det_ok = all(run["total_return"] == r["total_return"] for run in runs)
    det_str = "PASS (%dx동일)" % NUM_RUNS if det_ok else "FAIL"
    name = STRATEGIES[sid]["name"]
    ver = STRATEGIES[sid]["version"]
    v_mark = " *" if ver == "2.0" else ""

    print("%-4d %-23s%s | %9.2f%% | %6d | %6.1f%% | %7.2f%% | %7.2f | %7.2f%% | %s" % (
        idx, name, v_mark, r["total_return"], r["total_trades"],
        r["win_rate"], r["mdd"], r["sharpe"], r["cagr"], det_str
    ))

print("-" * 130)
print("* = V56 업데이트 적용 전략 (데이터 검증 기반)")
print()

# 미실행 전략
print("[ 백테스트 미실행 전략 (미구현 지표) ]")
for sid, reason in SKIPPED.items():
    print("  - %-30s : %s" % (sid, reason))

# =====================================================================
# 결정론 상세
# =====================================================================
print()
print("[ 결정론 검증 상세 ]")
all_deterministic = True
for sid, runs in all_results.items():
    returns = [r["total_return"] for r in runs]
    if len(set(returns)) > 1:
        all_deterministic = False
        print("  FAIL %-25s : %s" % (STRATEGIES[sid]["name"], returns))

if all_deterministic:
    print("  전체 PASS - %d개 전략 x %d회 = 모두 동일 결과" % (len(all_results), NUM_RUNS))

# =====================================================================
# JSON 저장
# =====================================================================
output = {
    "test_config": {
        "period": "2021-01-01 ~ 2026-02-25",
        "symbols": SYMBOLS,
        "initial_capital": 100000,
        "num_runs": NUM_RUNS,
    },
    "results": {},
    "skipped": SKIPPED,
    "determinism": "ALL_PASS" if all_deterministic else "SOME_FAIL",
}
for sid, runs in sorted_results:
    output["results"][sid] = {
        "name": STRATEGIES[sid]["name"],
        "version": STRATEGIES[sid]["version"],
        "runs": runs,
        "deterministic": all(r["total_return"] == runs[0]["total_return"] for r in runs),
    }

out_path = Path(__file__).resolve().parent / "full_backtest_results.json"
with open(out_path, "w", encoding="utf-8") as f:
    json.dump(output, f, ensure_ascii=False, indent=2)
print("\n결과 저장: %s" % out_path)

loader.close()
print("완료.")
