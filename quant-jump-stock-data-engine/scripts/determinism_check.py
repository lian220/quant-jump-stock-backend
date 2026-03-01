"""결정론 검증: 동일 설정 동일 결과 확인"""
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

strategies = {
    "rsi_oversold_original": {
        "strategy_id": "rsi_oversold_original", "name": "[원본] RSI 과매도", "version": "1.0",
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
        "strategy_id": "rsi_oversold_v2", "name": "[검증v2] RSI 과매도", "version": "2.0",
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
    "trend_following_original": {
        "strategy_id": "trend_following_original", "name": "[원본] 추세 추종", "version": "1.0",
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
    "trend_following_fred_v2": {
        "strategy_id": "trend_following_fred_v2", "name": "[검증v2] 추세+FRED", "version": "2.0",
        "rules": [
            {"name": "buy", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"},
                {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"},
                {"indicator": "treasury_10y", "params": {}, "operator": "lt", "value": 5.0}
            ], "logic": "and", "weight": 0.85},
            {"name": "sell", "signal_type": "sell", "conditions": [
                {"indicator": "price", "params": {}, "operator": "lt", "value": "sma_50"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.07, "take_profit_pct": 0.25, "max_position_pct": 0.12,
                            "trailing_stop": True, "trailing_stop_pct": 0.05}
    },
    "triple_screen_original": {
        "strategy_id": "triple_screen_original", "name": "[원본] 트리플 스크린", "version": "1.0",
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
}

SYMBOLS = ["AAPL", "NVDA", "TSLA", "MSFT", "GOOGL", "AMZN", "META", "JNJ", "AMAT", "INTC"]

# 이전 실행 결과값
prev_results = {
    "rsi_oversold_original": 3.16,
    "rsi_oversold_v2": 38.32,
    "trend_following_original": 29.28,
    "trend_following_fred_v2": 29.28,
    "triple_screen_original": 0.52,
}

def main():
    loader = MongoDataLoader(uri=os.environ["MONGODB_URI"])
    try:
        print("=" * 70)
        print("결정론 검증: 이전 실행 결과와 재실행 결과 비교")
        print("=" * 70)
        print("%-35s %10s %10s %6s" % ("전략", "이전", "재실행", "판정"))
        print("-" * 70)

        all_match = True
        for sid, sdict in strategies.items():
            strategy = StrategyDefinition(**sdict)
            config = BacktestConfig(
                start_date=date(2025, 9, 1), end_date=date(2026, 2, 25),
                initial_capital=Decimal("100000"), tickers=SYMBOLS,
                commission_rate=Decimal("0.00015"), tax_rate=Decimal("0.0023"),
                slippage_rate=Decimal("0.001"), max_positions=5, position_size_pct=Decimal("0.2"),
                benchmark_ticker="SPY",
            )
            engine = BacktestEngine(data_loader=loader, config=config)
            r = engine.run(strategy)
            ret = round(float(r.total_return), 2)
            p = prev_results.get(sid)
            if p is not None and ret == p:
                match = "PASS"
            elif p is not None:
                match = "FAIL"
                all_match = False
            else:
                match = "N/A"
            print("%-35s %10s %10.2f %6s" % (sdict["name"], str(p), ret, match))

        print("-" * 70)
        if all_match:
            print("결과: PASS - 모든 전략 이전 실행과 동일 결과 (결정론적)")
        else:
            print("결과: FAIL - 불일치 발견!")
    finally:
        loader.close()


if __name__ == "__main__":
    main()
