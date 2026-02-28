"""전문가 피드백 반영 전략 최적화 (Iteration 3-2)

Iteration 3 결과 반영:
- SMA200 필터가 RSI+MACD에 너무 제한적 (6~17 거래) → RSI 완화 필요
- RSI 과매도 v3 (SMA200) = 141.71% (CAGR 18.68%) → 편향 보정 후 현실적 성과
- RSI+MACD는 SMA200 없이 운용하되 SL/데드존 개선
- trailing stop 효과 단독 테스트
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

STRATEGIES = {
    # === A. RSI+MACD 계열 최적화 ===
    # SMA200 없이, 데드존과 SL만 개선
    "rsi_macd_widen_dz": {
        "strategy_id": "rsi_macd_widen_dz", "name": "RSI+MACD (DZ 42→65)", "version": "1.2",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0}
            ], "logic": "and", "weight": 1.0},
            # 매도: RSI 58→65로 확대 (보유 기간 증가)
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },

    # RSI+MACD + SL 10% + TP 높임 (SMA200 없음)
    "rsi_macd_sl10": {
        "strategy_id": "rsi_macd_sl10", "name": "RSI+MACD (SL10 TP25)", "version": "1.3",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 58}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },

    # RSI+MACD + Trailing Stop
    "rsi_macd_trailing": {
        "strategy_id": "rsi_macd_trailing", "name": "RSI+MACD + TS", "version": "1.4",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 62}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {
            "stop_loss_pct": 0.10, "take_profit_pct": 0.30, "max_position_pct": 0.1,
            "trailing_stop": True, "trailing_stop_pct": 0.08
        }
    },

    # RSI+MACD + SMA200 + RSI 완화 (42→45)
    "rsi_macd_sma200_loose": {
        "strategy_id": "rsi_macd_sma200_loose", "name": "RSI+MACD+SMA200 (RSI45)", "version": "1.5",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 45},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 62}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },

    # === B. RSI 과매도 Trailing Stop 단독 테스트 (SMA200 없음) ===
    "rsi_oversold_ts_only": {
        "strategy_id": "rsi_oversold_ts_only", "name": "RSI 과매도 + TS (no SMA200)", "version": "2.2",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 60}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {
            "stop_loss_pct": 0.05, "take_profit_pct": 0.20, "max_position_pct": 0.1,
            "trailing_stop": True, "trailing_stop_pct": 0.07
        }
    },

    # === C. 골든크로스+RSI 매도 개선 ===
    # Porter 피드백: death cross 매도 유지하되 trailing stop 추가
    "golden_cross_rsi_ts": {
        "strategy_id": "golden_cross_rsi_ts", "name": "골든크로스+RSI + TS", "version": "1.2",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 55}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_below", "value": "sma_50"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {
            "stop_loss_pct": 0.10, "take_profit_pct": 0.30, "max_position_pct": 0.1,
            "trailing_stop": True, "trailing_stop_pct": 0.10
        }
    },
}

# 비교 기준
BASELINE = {
    "rsi_macd_v1": {
        "strategy_id": "rsi_macd_v1", "name": "RSI+MACD v1 (기준)", "version": "1.0",
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
    "golden_cross_rsi_v1": {
        "strategy_id": "golden_cross_rsi_v1", "name": "골든크로스+RSI v1 (기준)", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 55}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_below", "value": "sma_50"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },
}

SYMBOLS = ["AAPL", "NVDA", "TSLA", "MSFT", "GOOGL", "AMZN", "META", "JNJ", "AMAT", "INTC"]
NUM_RUNS = 3


def main():
    loader = MongoDataLoader(uri=os.environ["MONGODB_URI"])
    try:
        config = BacktestConfig(
            start_date=date(2021, 1, 1), end_date=date(2026, 2, 25),
            initial_capital=Decimal("100000"), tickers=SYMBOLS,
            commission_rate=Decimal("0.00015"), tax_rate=Decimal("0.0023"),
            slippage_rate=Decimal("0.001"), max_positions=5, position_size_pct=Decimal("0.2"),
            benchmark_ticker="SPY",
        )

        print("=" * 140)
        print("전문가 피드백 최적화 Iteration 3-2")
        print("기간: 2021-01-01 ~ 2026-02-25 | 종목: %d개 | 자본: $100,000 | 반복: %d회" % (len(SYMBOLS), NUM_RUNS))
        print("=" * 140)

        all_results = {}

        def run_strategy(sid, sdict, is_baseline=False):
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
            print("  [%s] %-30s | %9.2f%% | %4d trades | %5.1f%% win | MDD %7.2f%% | Sharpe %6.2f | CAGR %7.2f%% | SL:%d TP:%d | PF:%.2f | Avg %4.1fd" % (
                det, sdict["name"], r["total_return"], r["total_trades"], r["win_rate"], r["mdd"],
                r["sharpe"], r["cagr"], r["stop_loss_count"], r["take_profit_count"], r["profit_factor"], r["avg_holding_days"]))

        print("\n[최적화 전략]")
        print("-" * 140)
        for sid, sdict in STRATEGIES.items():
            run_strategy(sid, sdict)

        print("\n[기준선]")
        print("-" * 140)
        for sid, sdict in BASELINE.items():
            run_strategy(sid, sdict, is_baseline=True)

        # 종합 비교
        print("\n\n" + "=" * 140)
        print("종합 비교 (수익률 순)")
        print("=" * 140)
        sorted_results = sorted(all_results.items(), key=lambda x: x[1]["runs"][0]["total_return"], reverse=True)
        for idx, (sid, data) in enumerate(sorted_results, 1):
            r = data["runs"][0]
            det_ok = all(run["total_return"] == r["total_return"] for run in data["runs"])
            marker = "REF" if data["is_baseline"] else "OPT"
            print("%-3d [%s] %-28s | %9.2f%% | %5d | %5.1f%% | %7.2f%% | %6.2f | %7.2f%% | %5.2f | %s" % (
                idx, marker, data["name"], r["total_return"], r["total_trades"],
                r["win_rate"], r["mdd"], r["sharpe"], r["cagr"], r["profit_factor"],
                "PASS" if det_ok else "FAIL"))

        # v1 → 개선 직접 비교
        print("\n" + "=" * 140)
        print("직접 비교")
        print("=" * 140)
        pairs = [
            ("rsi_macd_v1", "rsi_macd_widen_dz", "RSI+MACD: v1 → 데드존 확대 (58→65)"),
            ("rsi_macd_v1", "rsi_macd_sl10", "RSI+MACD: v1 → SL10%+TP25%"),
            ("rsi_macd_v1", "rsi_macd_trailing", "RSI+MACD: v1 → 데드존+TS"),
            ("rsi_macd_v1", "rsi_macd_sma200_loose", "RSI+MACD: v1 → SMA200+RSI45"),
            ("golden_cross_rsi_v1", "golden_cross_rsi_ts", "골든크로스+RSI: v1 → +TS"),
        ]
        for base, new, label in pairs:
            if base in all_results and new in all_results:
                b = all_results[base]["runs"][0]
                n = all_results[new]["runs"][0]
                diff = n["total_return"] - b["total_return"]
                wr_diff = n["win_rate"] - b["win_rate"]
                print("  %s" % label)
                print("    수익률: %+.2f%% → %+.2f%% (%+.2f%%p)  승률: %.1f%% → %.1f%% (%+.1f%%p)  거래: %d → %d" % (
                    b["total_return"], n["total_return"], diff, b["win_rate"], n["win_rate"], wr_diff, b["total_trades"], n["total_trades"]))

        # JSON 저장
        out_path = Path(__file__).resolve().parent / "expert_improved_v2_results.json"
        output = {
            "test_name": "expert_improved_iteration3_2",
            "results": {sid: data for sid, data in sorted_results},
            "determinism": "ALL_PASS" if all(
                all(run["total_return"] == data["runs"][0]["total_return"] for run in data["runs"])
                for data in all_results.values()
            ) else "SOME_FAIL",
        }
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(output, f, ensure_ascii=False, indent=2, default=str)
        print(f"\n결과 저장: {out_path}")
    finally:
        loader.close()
    print("완료.")


if __name__ == "__main__":
    main()
