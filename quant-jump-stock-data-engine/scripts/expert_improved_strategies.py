"""전문가 패널 피드백 반영 전략 개선 테스트 (Iteration 3)

전문가 권장사항 적용:
1. SMA200 레짐 필터 (모든 전략에 적용) - Meadows/Taleb
2. 데드존 확대 (RSI 38~65) - Taleb
3. SL 10%로 상향 - Taleb
4. Trailing Stop 활용 - Collins
5. RSI+MACD 복합 개선 (v2)
6. MACD Hist 반전 개선 (trailing stop 적용)
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
# 개선 전략 (전문가 피드백 반영)
# =====================================================================
IMPROVED_STRATEGIES = {
    # === 1. RSI+MACD 복합 v2 ===
    # 변경: 데드존 확대 (42~58 → 38~65 = 27pt), SL 8%→10%, SMA200 필터 추가
    # Taleb: "데드존 확대 + SMA200 레짐 필터 필수"
    "rsi_macd_combo_v2": {
        "strategy_id": "rsi_macd_combo_v2", "name": "RSI+MACD 복합 v2", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 38},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },

    # === 2. RSI+MACD 복합 (원본 + SMA200만 추가) ===
    # 컨트롤 그룹: SMA200 필터만 추가하고 나머지는 동일
    "rsi_macd_combo_sma200": {
        "strategy_id": "rsi_macd_combo_sma200", "name": "RSI+MACD+SMA200", "version": "1.1",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 58}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.08, "take_profit_pct": 0.20, "max_position_pct": 0.1}
    },

    # === 3. RSI 과매도 v3 (SMA200 레짐 필터 추가) ===
    # Taleb/Meadows: "모든 전략에 레짐 필터 필수"
    # 변경: 기존 v2 + SMA200 상승장 필터
    "rsi_oversold_v3": {
        "strategy_id": "rsi_oversold_v3", "name": "RSI 과매도 v3 (SMA200)", "version": "3.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 60}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.05, "take_profit_pct": 0.15, "max_position_pct": 0.1}
    },

    # === 4. MACD Hist 반전 v2 (Trailing Stop + SMA200) ===
    # 전문가: "매도를 trailing stop으로 변경, SMA200 필터 추가"
    # 변경: 매도 조건 제거 → trailing stop 15%로 수익 보호, SMA200 필터
    "macd_hist_reversal_v2": {
        "strategy_id": "macd_hist_reversal_v2", "name": "MACD Hist 반전 v2", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "macd_hist", "params": {}, "operator": "crosses_above", "value": 0},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
            ], "logic": "and", "weight": 1.0},
            # 매도: RSI > 75로 변경 (MACD cross_below 대신 과매수 구간 청산)
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 75}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {
            "stop_loss_pct": 0.12, "take_profit_pct": 0.30, "max_position_pct": 0.1,
            "trailing_stop": True, "trailing_stop_pct": 0.12
        }
    },

    # === 5. RSI 과매도 + Trailing Stop ===
    # Collins: "최고의 전략에 trailing stop 적용"
    "rsi_oversold_trailing": {
        "strategy_id": "rsi_oversold_trailing", "name": "RSI 과매도 + TS", "version": "2.1",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 60}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {
            "stop_loss_pct": 0.08, "take_profit_pct": 0.25, "max_position_pct": 0.1,
            "trailing_stop": True, "trailing_stop_pct": 0.07
        }
    },

    # === 6. 골든크로스+RSI v2 (RSI 매도 + SMA200) ===
    # Porter: "death cross 매도를 RSI>70으로 변경"
    "golden_cross_rsi_v2": {
        "strategy_id": "golden_cross_rsi_v2", "name": "골든크로스+RSI v2", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 55},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
            ], "logic": "and", "weight": 1.0},
            # 매도: death cross → RSI > 70 (빠른 청산)
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 70}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {
            "stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1,
            "trailing_stop": True, "trailing_stop_pct": 0.10
        }
    },
}

# 비교 기준
BASELINE = {
    "rsi_macd_combo_v1": {
        "strategy_id": "rsi_macd_combo_v1", "name": "RSI+MACD 복합 v1 (기준)", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 58}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.08, "take_profit_pct": 0.20, "max_position_pct": 0.1}
    },
    "rsi_oversold_v2": {
        "strategy_id": "rsi_oversold_v2", "name": "RSI 과매도 v2 (기준)", "version": "2.0",
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

print("=" * 140)
print("전문가 피드백 반영 전략 개선 테스트 (Iteration 3)")
print("적용: SMA200 레짐 필터, 데드존 확대, Trailing Stop, SL 확대")
print("기간: 2021-01-01 ~ 2026-02-25 | 종목: %d개 | 자본: $100,000 | 반복: %d회" % (len(SYMBOLS), NUM_RUNS))
print("=" * 140)

all_results = {}

def run_strategy(sid, sdict, group_name, is_baseline=False):
    strategy = StrategyDefinition(**sdict)
    runs = []
    for _ in range(NUM_RUNS):
        engine = BacktestEngine(data_loader=loader, config=config)
        r = engine.run(strategy)
        run_data = {
            "total_return": round(float(r.total_return), 2),
            "total_trades": r.total_trades,
            "win_rate": round(float(r.win_rate), 1) if r.win_rate else 0.0,
            "mdd": round(float(r.mdd), 2) if r.mdd else 0.0,
            "sharpe": round(float(r.sharpe_ratio), 2) if r.sharpe_ratio else 0.0,
            "cagr": round(float(r.cagr), 2) if r.cagr else 0.0,
            "avg_holding_days": round(float(r.avg_holding_days), 1) if r.avg_holding_days else 0.0,
            "profit_factor": round(float(r.profit_factor), 2) if r.profit_factor else 0.0,
            "stop_loss_count": r.stop_loss_count or 0,
            "take_profit_count": r.take_profit_count or 0,
        }
        runs.append(run_data)

    all_results[sid] = {"runs": runs, "name": sdict["name"], "is_baseline": is_baseline}
    det = "PASS" if all(r["total_return"] == runs[0]["total_return"] for r in runs) else "FAIL"
    r = runs[0]
    print("  [%s] %-28s | %9.2f%% | %4d trades | %5.1f%% win | MDD %7.2f%% | Sharpe %6.2f | CAGR %7.2f%% | SL:%d TP:%d | PF:%.2f" % (
        det, sdict["name"], r["total_return"], r["total_trades"], r["win_rate"], r["mdd"],
        r["sharpe"], r["cagr"], r["stop_loss_count"], r["take_profit_count"], r["profit_factor"]))

# 개선 전략 테스트
print("\n[개선 전략 (전문가 피드백 반영)]")
print("-" * 140)
for sid, sdict in IMPROVED_STRATEGIES.items():
    run_strategy(sid, sdict, "improved")

# 기준선 테스트
print("\n[기준선]")
print("-" * 140)
for sid, sdict in BASELINE.items():
    run_strategy(sid, sdict, "baseline", is_baseline=True)

# 종합 비교
print("\n\n" + "=" * 140)
print("종합 비교 (수익률 순)")
print("=" * 140)
print("%-4s %-2s %-28s | %10s | %6s | %7s | %8s | %7s | %8s | %6s | %s" % (
    "#", "", "전략명", "수익률", "거래수", "승률", "MDD", "Sharpe", "CAGR", "PF", "결정론"
))
print("-" * 140)

sorted_results = sorted(all_results.items(), key=lambda x: x[1]["runs"][0]["total_return"], reverse=True)
for idx, (sid, data) in enumerate(sorted_results, 1):
    r = data["runs"][0]
    det_ok = all(run["total_return"] == r["total_return"] for run in data["runs"])
    marker = "REF" if data["is_baseline"] else "IMP"
    print("%-4d [%s] %-25s | %9.2f%% | %6d | %6.1f%% | %7.2f%% | %7.2f | %7.2f%% | %5.2f | %s" % (
        idx, marker, data["name"], r["total_return"], r["total_trades"],
        r["win_rate"], r["mdd"], r["sharpe"], r["cagr"], r["profit_factor"],
        "PASS" if det_ok else "FAIL"))

# 전략 비교: 같은 계열 v1 vs v2 직접 비교
print("\n\n" + "=" * 140)
print("전략 계열별 v1 → v2 비교")
print("=" * 140)

comparisons = [
    ("rsi_macd_combo_v1", "rsi_macd_combo_sma200", "RSI+MACD: 원본 → +SMA200"),
    ("rsi_macd_combo_v1", "rsi_macd_combo_v2", "RSI+MACD: 원본 → v2 (전문가)"),
    ("rsi_oversold_v2", "rsi_oversold_v3", "RSI 과매도: v2 → v3 (+SMA200)"),
    ("rsi_oversold_v2", "rsi_oversold_trailing", "RSI 과매도: v2 → +TS (+SMA200+trailing)"),
]

for base_sid, new_sid, label in comparisons:
    if base_sid in all_results and new_sid in all_results:
        base_r = all_results[base_sid]["runs"][0]
        new_r = all_results[new_sid]["runs"][0]
        diff = new_r["total_return"] - base_r["total_return"]
        arrow = "↑" if diff > 0 else "↓" if diff < 0 else "="
        print("  %s: %+.2f%% → %+.2f%% (%s%+.2f%%p)" % (
            label, base_r["total_return"], new_r["total_return"], arrow, diff))

# JSON 저장
output = {
    "test_name": "expert_improved_iteration3",
    "expert_recommendations_applied": [
        "SMA200 regime filter (Meadows/Taleb)",
        "Wider dead zone RSI 38-65 (Taleb)",
        "SL 10% minimum (Taleb)",
        "Trailing stop on key strategies (Collins)",
        "RSI-based exit instead of death cross (Porter)",
    ],
    "test_config": {
        "period": "2021-01-01 ~ 2026-02-25",
        "symbols": SYMBOLS,
        "initial_capital": 100000,
        "num_runs": NUM_RUNS,
    },
    "results": {sid: data for sid, data in sorted_results},
    "determinism": "ALL_PASS" if all(
        all(run["total_return"] == data["runs"][0]["total_return"] for run in data["runs"])
        for data in all_results.values()
    ) else "SOME_FAIL",
}

out_path = Path(__file__).resolve().parent / "expert_improved_results.json"
with open(out_path, "w", encoding="utf-8") as f:
    json.dump(output, f, ensure_ascii=False, indent=2, default=str)
print(f"\n결과 저장: {out_path}")

loader.close()
print("완료.")
