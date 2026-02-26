"""신규 전략 4개 백테스트 + 기존 A등급 전략 비교
성공 원칙 적용:
1. 매수-매도 사각지대(dead zone) 확보
2. SL 최소 8~10%
3. 다중 확인 필터 (AND 조건 2~3개)
4. 결정론 검증 (3회 반복)
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
# 신규 전략 4개
# =====================================================================
NEW_STRATEGIES = {
    # === 1. RSI + MACD 복합 ===
    # 컨셉: RSI 과매도 구간에서 MACD 히스토그램이 양전환할 때 매수 (반등 확인)
    # 사각지대: RSI 42~58 구간 (16pt gap)
    # 매도: RSI 과매수 OR MACD 히스토그램 음전환
    "rsi_macd_combo": {
        "strategy_id": "rsi_macd_combo", "name": "RSI+MACD 복합", "version": "1.0",
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

    # === 2. 볼린저 + RSI 반등 ===
    # 컨셉: 가격이 볼린저 하단 이탈 + RSI 과매도 = 강한 반등 시그널
    # 사각지대: RSI 45~55 구간 (매도는 BB 중단 도달)
    # 매도: 가격이 볼린저 중단 회복 (평균 회귀 완료)
    "bollinger_rsi_bounce": {
        "strategy_id": "bollinger_rsi_bounce", "name": "볼린저+RSI 반등", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 45}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_middle"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.08, "take_profit_pct": 0.15, "max_position_pct": 0.1}
    },

    # === 3. 골든크로스 + RSI 필터 ===
    # 컨셉: SMA 교차 매수 시 RSI 과매수가 아닌 경우만 진입 (고점 추격 방지)
    # 매도: SMA 데드크로스 (기존 골든크로스와 동일)
    # 개선: RSI < 55 필터로 과매수 구간 진입 방지
    "golden_cross_rsi": {
        "strategy_id": "golden_cross_rsi", "name": "골든크로스+RSI", "version": "1.0",
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

    # === 4. MACD 히스토그램 방향 전환 ===
    # 컨셉: MACD hist가 음→양 전환 + 트렌드 확인(가격>SMA50)
    # 사각지대: MACD hist가 양→음 전환 시 매도 (전환 기반 = 매일 발생 X)
    # crosses_above/crosses_below 사용으로 전환 시점에만 시그널
    "macd_hist_reversal": {
        "strategy_id": "macd_hist_reversal", "name": "MACD Hist 반전", "version": "1.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "macd_hist", "params": {}, "operator": "crosses_above",
                 "value": 0},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"}
            ], "logic": "and", "weight": 1.0},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "macd_hist", "params": {}, "operator": "crosses_below",
                 "value": 0}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },
}

# 비교용 기존 A등급 전략
BASELINE_STRATEGIES = {
    "rsi_oversold": {
        "strategy_id": "rsi_oversold", "name": "RSI 과매도 v2 (기준)", "version": "2.0",
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
        "strategy_id": "scalping_rsi", "name": "RSI 스캘핑 (기준)", "version": "1.0",
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
print("신규 전략 4개 백테스트 + 기존 A등급 비교")
print("기간: 2021-01-01 ~ 2026-02-25 | 종목: %d개 | 자본: $100,000 | 반복: %d회" % (len(SYMBOLS), NUM_RUNS))
print("=" * 140)

all_results = {}

# 신규 전략 테스트
print("\n[신규 전략]")
print("-" * 140)
for sid, sdict in NEW_STRATEGIES.items():
    strategy = StrategyDefinition(**sdict)
    runs = []
    for run_idx in range(NUM_RUNS):
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
    all_results[sid] = {"runs": runs, "name": sdict["name"], "is_new": True}
    det = "PASS" if all(r["total_return"] == runs[0]["total_return"] for r in runs) else "FAIL"
    r = runs[0]
    print("  [%s] %-25s | %9.2f%% | %4d trades | %5.1f%% win | MDD %7.2f%% | Sharpe %6.2f | CAGR %7.2f%% | SL:%d TP:%d | PF:%.2f" % (
        det, sdict["name"], r["total_return"], r["total_trades"], r["win_rate"], r["mdd"],
        r["sharpe"], r["cagr"], r["stop_loss_count"], r["take_profit_count"], r["profit_factor"]))

# 기준선 전략 테스트
print("\n[기준선 (A등급 전략)]")
print("-" * 140)
for sid, sdict in BASELINE_STRATEGIES.items():
    strategy = StrategyDefinition(**sdict)
    runs = []
    for run_idx in range(NUM_RUNS):
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
    all_results[sid] = {"runs": runs, "name": sdict["name"], "is_new": False}
    det = "PASS" if all(r["total_return"] == runs[0]["total_return"] for r in runs) else "FAIL"
    r = runs[0]
    print("  [%s] %-25s | %9.2f%% | %4d trades | %5.1f%% win | MDD %7.2f%% | Sharpe %6.2f | CAGR %7.2f%% | SL:%d TP:%d | PF:%.2f" % (
        det, sdict["name"], r["total_return"], r["total_trades"], r["win_rate"], r["mdd"],
        r["sharpe"], r["cagr"], r["stop_loss_count"], r["take_profit_count"], r["profit_factor"]))

# 종합 비교
print("\n\n" + "=" * 140)
print("종합 비교 (수익률 순)")
print("=" * 140)
print("%-4s %-2s %-25s | %10s | %6s | %7s | %8s | %7s | %8s | %6s | %s" % (
    "#", "", "전략명", "수익률", "거래수", "승률", "MDD", "Sharpe", "CAGR", "PF", "결정론"
))
print("-" * 140)

sorted_results = sorted(all_results.items(), key=lambda x: x[1]["runs"][0]["total_return"], reverse=True)
for idx, (sid, data) in enumerate(sorted_results, 1):
    r = data["runs"][0]
    det_ok = all(run["total_return"] == r["total_return"] for run in data["runs"])
    marker = "NEW" if data["is_new"] else "REF"
    print("%-4d [%s] %-23s | %9.2f%% | %6d | %6.1f%% | %7.2f%% | %7.2f | %7.2f%% | %5.2f | %s" % (
        idx, marker, data["name"], r["total_return"], r["total_trades"],
        r["win_rate"], r["mdd"], r["sharpe"], r["cagr"], r["profit_factor"],
        "PASS" if det_ok else "FAIL"))

# JSON 저장
output = {
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

out_path = Path(__file__).resolve().parent / "new_strategies_results.json"
with open(out_path, "w", encoding="utf-8") as f:
    json.dump(output, f, ensure_ascii=False, indent=2, default=str)
print(f"\n결과 저장: {out_path}")

loader.close()
print("완료.")
