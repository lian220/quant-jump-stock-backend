"""전문가 패널 제안 전략 백테스트

전문가 패널(Taleb, Porter, Meadows, Christensen) 분석 기반:
1. RSI + SMA200 레짐 + MACD 필터 (Taleb+Meadows)
2. Yield Curve 레짐 + RSI (Christensen+Meadows)
3. 초광폭 데드존 RSI (Porter+Collins)
4. RSI+MACD+SMA200 RSI45 (이전 세션 검증)
5. RSI+MACD+SMA200 RSI35 (데드존 35→70 변형)
+ 기준 전략 4개 (상관관계 비교용)
+ 거래비용 스트레스 테스트 (0.3% round-trip)
+ 전략 간 상관관계 분석
"""
import os, sys, json, math
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

# ==========================================================================
# 전략 정의
# ==========================================================================
STRATEGIES = {
    # === 신규 전략 (전문가 제안) ===

    "rsi_sma200_macd": {
        "strategy_id": "rsi_sma200_macd", "name": "RSI+SMA200+MACD (Taleb)", "version": "1.0",
        "rules": [
            {"name": "regime_rsi_macd_buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 35},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"}
            ], "logic": "and", "weight": 1.0},
            {"name": "rsi_overbought_sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 70}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },

    "yield_curve_rsi": {
        "strategy_id": "yield_curve_rsi", "name": "Yield Curve+RSI (Christensen)", "version": "1.0",
        "rules": [
            {"name": "yield_rsi_buy", "signal_type": "buy", "conditions": [
                {"indicator": "t10y2y", "params": {}, "operator": "gt", "value": 0},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 40},
                {"indicator": "sma", "params": {"period": 50}, "operator": "gt", "value": "sma_200"}
            ], "logic": "and", "weight": 1.0},
            {"name": "rsi_overbought_sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65}
            ], "logic": "and", "weight": 1.0},
            {"name": "yield_curve_inversion_sell", "signal_type": "sell", "conditions": [
                {"indicator": "t10y2y", "params": {}, "operator": "lt", "value": -0.5}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.12, "take_profit_pct": 0.30, "max_position_pct": 0.1}
    },

    "ultra_wide_deadzone": {
        "strategy_id": "ultra_wide_deadzone", "name": "초광폭 데드존 RSI (Porter)", "version": "1.0",
        "rules": [
            {"name": "ultra_strict_buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 30},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"}
            ], "logic": "and", "weight": 1.0},
            {"name": "rsi_high_sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 75}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.15, "take_profit_pct": 0.35, "max_position_pct": 0.1}
    },

    "rsi_macd_sma200_45": {
        "strategy_id": "rsi_macd_sma200_45", "name": "RSI+MACD+SMA200 RSI45", "version": "1.0",
        "rules": [
            {"name": "rsi_macd_sma200_buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 45},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"}
            ], "logic": "and", "weight": 1.0},
            {"name": "rsi_overbought_sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },

    "rsi_macd_sma200_35": {
        "strategy_id": "rsi_macd_sma200_35", "name": "RSI+MACD+SMA200 RSI35", "version": "1.0",
        "rules": [
            {"name": "rsi_macd_sma200_buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 35},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"}
            ], "logic": "and", "weight": 1.0},
            {"name": "rsi_overbought_sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 70}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },

    # === 기준 전략 (상관관계 비교용) ===

    "rsi_oversold_v2": {
        "strategy_id": "rsi_oversold", "name": "RSI 과매도 v2 (기준)", "version": "2.0",
        "rules": [
            {"name": "rsi_oversold_buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42}
            ], "logic": "and", "weight": 1.0},
            {"name": "rsi_overbought_sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 60}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },

    "rsi_macd_combo": {
        "strategy_id": "rsi_macd_combo", "name": "RSI+MACD 복합 (기준)", "version": "1.0",
        "rules": [
            {"name": "rsi_macd_buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42},
                {"indicator": "macd_hist", "params": {}, "operator": "gt", "value": 0}
            ], "logic": "and", "weight": 1.0},
            {"name": "rsi_overbought_sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },

    "golden_cross_rsi": {
        "strategy_id": "golden_cross_rsi", "name": "골든크로스+RSI (기준)", "version": "1.0",
        "rules": [
            {"name": "golden_cross_rsi_buy", "signal_type": "buy", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 55}
            ], "logic": "and", "weight": 1.0},
            {"name": "death_cross_sell", "signal_type": "sell", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_below", "value": "sma_50"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },

    "rate_linked_v2": {
        "strategy_id": "rate_linked_allocation", "name": "금리 연동 v2 (기준)", "version": "2.0",
        "rules": [
            {"name": "rate_low_buy", "signal_type": "buy", "conditions": [
                {"indicator": "treasury_10y", "params": {}, "operator": "lt", "value": 4.5},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 50}
            ], "logic": "and", "weight": 0.8},
            {"name": "rate_drop_buy", "signal_type": "buy", "conditions": [
                {"indicator": "treasury_10y", "params": {}, "operator": "lt", "value": 4.0},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 60}
            ], "logic": "and", "weight": 1.0},
            {"name": "rate_high_sell", "signal_type": "sell", "conditions": [
                {"indicator": "treasury_10y", "params": {}, "operator": "gt", "value": 5.0},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65}
            ], "logic": "and", "weight": 0.8},
            {"name": "rsi_high_sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 70}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.30, "max_position_pct": 0.1}
    },
}

SYMBOLS = ["AAPL", "NVDA", "TSLA", "MSFT", "GOOGL", "AMZN", "META", "JNJ", "AMAT", "INTC"]
START = date(2021, 1, 1)
END = date(2026, 2, 25)
INITIAL_CAPITAL = Decimal("100000")
NUM_RUNS = 3
NEW_KEYS = ["rsi_sma200_macd", "yield_curve_rsi", "ultra_wide_deadzone", "rsi_macd_sma200_45", "rsi_macd_sma200_35"]
REF_KEYS = ["rsi_oversold_v2", "rsi_macd_combo", "golden_cross_rsi", "rate_linked_v2"]


def make_config(commission=Decimal("0.001"), tax=Decimal("0.001")):
    return BacktestConfig(
        start_date=START, end_date=END,
        initial_capital=INITIAL_CAPITAL, tickers=SYMBOLS,
        commission_rate=commission, tax_rate=tax,
        slippage_rate=Decimal("0.001"), max_positions=5,
        position_size_pct=Decimal("0.2"),
    )


def grade(cagr, mdd, pf, win, trades):
    if trades == 0: return "N/A"
    if cagr >= 15 and mdd > -25 and pf >= 1.5 and win >= 50: return "A"
    if cagr >= 8 and pf >= 1.3 and win >= 35: return "B+"
    if cagr >= 0: return "B"
    if cagr >= -5: return "C"
    if cagr >= -15: return "D"
    return "F"


def safe_float(v, default=0.0):
    try:
        return round(float(v), 2) if v is not None else default
    except:
        return default


def pearson_corr(x, y):
    n = min(len(x), len(y))
    if n < 30: return float('nan')
    x, y = x[:n], y[:n]
    mx, my = sum(x)/n, sum(y)/n
    sx = math.sqrt(sum((xi-mx)**2 for xi in x)/n)
    sy = math.sqrt(sum((yi-my)**2 for yi in y)/n)
    if sx == 0 or sy == 0: return float('nan')
    return sum((xi-mx)*(yi-my) for xi, yi in zip(x, y)) / (n * sx * sy)


def extract_daily_returns(result):
    """equity_curve에서 일별 수익률 추출"""
    ec = result.equity_curve
    if not ec or len(ec) < 2:
        return []
    returns = []
    for i in range(1, len(ec)):
        prev_val = float(ec[i-1].equity)
        curr_val = float(ec[i].equity)
        if prev_val > 0:
            returns.append((curr_val - prev_val) / prev_val)
        else:
            returns.append(0.0)
    return returns


def main():
    loader = MongoDataLoader(uri=os.environ["MONGODB_URI"])
    try:
        config = make_config()

        print("=" * 130)
        print("전문가 패널 제안 전략 백테스트")
        print(f"기간: {START} ~ {END} | 종목: {len(SYMBOLS)}개 | 자본: ${INITIAL_CAPITAL:,} | 반복: {NUM_RUNS}회")
        print("=" * 130)

        # =================================================================
        # Phase 1: 전략 백테스트 (결정론 3회 반복)
        # =================================================================
        print(f"\n{'='*130}")
        print(f"Phase 1: 전략 백테스트 (신규 {len(NEW_KEYS)} + 기준 {len(REF_KEYS)} = {len(NEW_KEYS)+len(REF_KEYS)}개)")
        print(f"{'='*130}")

        all_results = {}
        all_daily_returns = {}

        for key in NEW_KEYS + REF_KEYS:
            sdict = STRATEGIES[key]
            tag = "NEW" if key in NEW_KEYS else "REF"
            sd = StrategyDefinition(**sdict)

            runs = []
            for run_idx in range(NUM_RUNS):
                try:
                    engine = BacktestEngine(data_loader=loader, config=config)
                    r = engine.run(sd)
                    rd = {
                        "return_pct": safe_float(r.total_return),
                        "cagr": safe_float(r.cagr),
                        "mdd": safe_float(r.mdd),
                        "sharpe": safe_float(r.sharpe_ratio),
                        "pf": safe_float(r.profit_factor),
                        "win_rate": safe_float(r.win_rate),
                        "trades": r.total_trades,
                        "avg_holding": safe_float(r.avg_holding_days),
                        "sl_count": r.stop_loss_count or 0,
                        "tp_count": r.take_profit_count or 0,
                    }
                    runs.append(rd)
                    if run_idx == 0:
                        all_daily_returns[key] = extract_daily_returns(r)
                except Exception as e:
                    print(f"  [{tag}] ERROR {sdict['name']}: {e}")
                    runs.append(None)

            valid = [r for r in runs if r]
            if len(valid) >= 2:
                det_pass = all(abs(valid[0]["return_pct"] - r["return_pct"]) < 0.01 for r in valid[1:])
            else:
                det_pass = len(valid) == 1

            if valid:
                m = valid[0]
                g = grade(m["cagr"], m["mdd"], m["pf"], m["win_rate"], m["trades"])
                det_str = "PASS" if det_pass else "FAIL"
                all_results[key] = {"metrics": m, "grade": g, "det": det_str, "name": sdict["name"], "tag": tag}

                print(f"  [{det_str}] {g:>3s} [{tag}] {sdict['name']:<35s}"
                      f" | {m['return_pct']:>10.2f}% | {m['trades']:>3d} trades | {m['win_rate']:>5.1f}% win"
                      f" | MDD {m['mdd']:>8.2f}% | Sharpe {m['sharpe']:>6.2f}"
                      f" | CAGR {m['cagr']:>8.2f}% | PF {m['pf']:>5.2f}"
                      f" | SL:{m['sl_count']} TP:{m['tp_count']} | {m['avg_holding']:.1f}d")
            else:
                print(f"  [FAIL] --- [{tag}] {sdict['name']}: 모든 실행 실패")

        # =================================================================
        # Phase 2: 거래비용 스트레스 테스트 (0.3% round-trip)
        # =================================================================
        print(f"\n{'='*130}")
        print("Phase 2: 거래비용 스트레스 테스트 (0.15% commission + 0.15% tax = 0.3% round-trip)")
        print(f"{'='*130}")

        stress_config = make_config(commission=Decimal("0.0015"), tax=Decimal("0.0015"))
        stress_keys = ["rsi_sma200_macd", "rsi_macd_sma200_45", "rsi_macd_combo", "rsi_oversold_v2"]

        for key in stress_keys:
            sdict = STRATEGIES[key]
            sd = StrategyDefinition(**sdict)
            try:
                engine = BacktestEngine(data_loader=loader, config=stress_config)
                r = engine.run(sd)
                stress_ret = safe_float(r.total_return)
                stress_cagr = safe_float(r.cagr)
                stress_pf = safe_float(r.profit_factor)
                orig_ret = all_results.get(key, {}).get("metrics", {}).get("return_pct", 0)
                diff = stress_ret - orig_ret
                print(f"  {sdict['name']:<35s}"
                      f" | 기본: {orig_ret:>10.2f}% → 스트레스: {stress_ret:>10.2f}% (차이: {diff:>+8.2f}%)"
                      f" | CAGR {stress_cagr:>8.2f}% | PF {stress_pf:>5.2f}")
            except Exception as e:
                print(f"  {sdict['name']:<35s} | ERROR: {e}")

        # =================================================================
        # Phase 3: 전략 간 상관관계 분석
        # =================================================================
        print(f"\n{'='*130}")
        print("Phase 3: 전략 간 일별 수익률 상관관계 (피어슨)")
        print(f"{'='*130}")

        corr_keys = [k for k in NEW_KEYS + REF_KEYS if k in all_daily_returns and len(all_daily_returns[k]) > 30]

        if len(corr_keys) >= 2:
            # 헤더
            print(f"\n{'':>37s}", end="")
            for k in corr_keys:
                short = STRATEGIES[k]["name"][:8]
                print(f" {short:>10s}", end="")
            print()

            corr_matrix = {}
            for k1 in corr_keys:
                name = STRATEGIES[k1]["name"][:35]
                print(f"  {name:<35s}", end="")
                for k2 in corr_keys:
                    corr = pearson_corr(all_daily_returns[k1], all_daily_returns[k2])
                    corr_matrix[(k1, k2)] = corr
                    if math.isnan(corr):
                        print(f" {'N/A':>10s}", end="")
                    else:
                        print(f" {corr:>10.4f}", end="")
                print()

            # 높은 상관관계
            print(f"\n  ⚠️  높은 상관관계 (|r| > 0.7):")
            warned = set()
            for i, k1 in enumerate(corr_keys):
                for j, k2 in enumerate(corr_keys):
                    if i >= j: continue
                    corr = corr_matrix.get((k1, k2), float('nan'))
                    if not math.isnan(corr) and abs(corr) > 0.7:
                        pair = tuple(sorted([k1, k2]))
                        if pair not in warned:
                            warned.add(pair)
                            print(f"    {STRATEGIES[k1]['name']} ↔ {STRATEGIES[k2]['name']}: r={corr:.4f}")
            if not warned:
                print("    없음 (모든 전략 쌍 |r| < 0.7)")

            # 낮은 상관관계
            print(f"\n  ✅  낮은 상관관계 (|r| < 0.3) - 분산 효과:")
            good = set()
            for i, k1 in enumerate(corr_keys):
                for j, k2 in enumerate(corr_keys):
                    if i >= j: continue
                    corr = corr_matrix.get((k1, k2), float('nan'))
                    if not math.isnan(corr) and abs(corr) < 0.3:
                        pair = tuple(sorted([k1, k2]))
                        if pair not in good:
                            good.add(pair)
                            print(f"    {STRATEGIES[k1]['name']} ↔ {STRATEGIES[k2]['name']}: r={corr:.4f}")
            if not good:
                print("    없음")
        else:
            print("  데이터 부족")

        # =================================================================
        # Phase 4: 종합 순위
        # =================================================================
        print(f"\n{'='*130}")
        print("Phase 4: 종합 순위 (수익률 순)")
        print(f"{'='*130}")

        sorted_res = sorted(all_results.items(), key=lambda x: x[1]["metrics"]["return_pct"], reverse=True)
        print(f"{'#':>2s} {'등급':>4s} {'DET':>4s} {'TAG':>4s} {'전략명':<35s} | {'수익률':>10s} | {'거래':>5s} | {'승률':>6s} | {'MDD':>8s} | {'Sharpe':>7s} | {'CAGR':>8s} | {'PF':>6s} | {'보유':>6s}")
        print("-" * 130)

        for rank, (key, data) in enumerate(sorted_res, 1):
            m = data["metrics"]
            print(f"{rank:>2d} {data['grade']:>4s} {data['det']:>4s} {data['tag']:>4s} {data['name']:<35s}"
                  f" | {m['return_pct']:>10.2f}% | {m['trades']:>5d} | {m['win_rate']:>5.1f}% | {m['mdd']:>7.2f}%"
                  f" | {m['sharpe']:>7.2f} | {m['cagr']:>7.2f}% | {m['pf']:>5.2f} | {m['avg_holding']:>5.1f}d")

        # =================================================================
        # Phase 5: 데드존 폭 vs 성과
        # =================================================================
        print(f"\n{'='*130}")
        print("Phase 5: 데드존 폭 vs 성과 분석")
        print(f"{'='*130}")

        deadzone_data = [
            ("RSI 과매도 v2 (42→60)", 18, "rsi_oversold_v2"),
            ("RSI+MACD+SMA200 RSI45 (45→65)", 20, "rsi_macd_sma200_45"),
            ("RSI+MACD (42→65)", 23, "rsi_macd_combo"),
            ("RSI+SMA200+MACD (35→70)", 35, "rsi_sma200_macd"),
            ("RSI+MACD+SMA200 RSI35 (35→70)", 35, "rsi_macd_sma200_35"),
            ("초광폭 데드존 (30→75)", 45, "ultra_wide_deadzone"),
        ]

        print(f"  {'전략':<35s} | {'데드존':>6s} | {'CAGR':>8s} | {'PF':>6s} | {'승률':>6s} | {'거래':>5s} | {'MDD':>8s}")
        print(f"  {'-'*95}")
        for name, dz, key in sorted(deadzone_data, key=lambda x: x[1]):
            if key in all_results:
                m = all_results[key]["metrics"]
                print(f"  {name:<35s} | {dz:>4d}pt | {m['cagr']:>7.2f}% | {m['pf']:>5.2f} | {m['win_rate']:>5.1f}% | {m['trades']:>5d} | {m['mdd']:>7.2f}%")
            else:
                print(f"  {name:<35s} | {dz:>4d}pt | (데이터 없음)")

        # JSON 저장
        save_data = {
            "test_config": {"period": f"{START} ~ {END}", "stocks": SYMBOLS, "initial_capital": float(INITIAL_CAPITAL), "runs": NUM_RUNS},
            "results": {k: {"name": v["name"], "grade": v["grade"], "det": v["det"], "tag": v["tag"], **v["metrics"]} for k, v in all_results.items()},
        }
        out_path = Path(__file__).parent / "expert_panel_results.json"
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(save_data, f, ensure_ascii=False, indent=2)
        print(f"\n결과 저장: {out_path}")
    finally:
        loader.close()
    print("완료.")


if __name__ == "__main__":
    main()
