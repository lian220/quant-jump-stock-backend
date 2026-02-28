"""종합 전략 백테스트 (V56+V57+크로스심볼 반영)

모든 테스트 가능한 전략을 5년 백테스트 + 3회 반복 결정론 검증합니다.
V56(데이터 검증), V57(백테스트 검증) 마이그레이션 + 크로스-심볼 지표 구현 반영.

DB 전체: 24개 전략 (V11: 20개 + V57 신규: 4개)
테스트 대상: 20개 전략
- 기존 기술적 전략 11개 (업데이트 반영)
- V57 신규 전략 4개 (rsi_macd_combo, golden_cross_rsi, rsi_macd_sma200, yield_curve_rsi)
- 캘린더 전략 2개 (calendar 지표 구현)
- 크로스-심볼 전략 3개 (high_dividend, low_per_value, quarterly_momentum)
블록: 4개 (멀티에셋/AI 의존)
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

# ==========================================================================
# 전략 정의 (V56 + V57 마이그레이션 반영 최종 상태)
# ==========================================================================
STRATEGIES = {
    # ======================================================================
    # A. MOMENTUM 전략 (6개)
    # ======================================================================

    # 1. 골든크로스 v2 (V57: SL 5%→10%, TP 15%→25%)
    "golden_cross": {
        "strategy_id": "golden_cross", "name": "골든크로스 v2", "version": "2.0",
        "rules": [
            {"name": "golden_cross_buy", "signal_type": "buy", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"}
            ], "logic": "and", "weight": 1.0},
            {"name": "death_cross_sell", "signal_type": "sell", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_below", "value": "sma_50"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.1}
    },

    # 2. RSI 과매도 v2 (V56: RSI 30→42, 70→60)
    "rsi_oversold": {
        "strategy_id": "rsi_oversold", "name": "RSI 과매도 v2", "version": "2.0",
        "rules": [
            {"name": "rsi_oversold_buy", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42}
            ], "logic": "and", "weight": 1.0},
            {"name": "rsi_overbought_sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 60}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.05, "take_profit_pct": 0.15, "max_position_pct": 0.1}
    },

    # 3. MACD 크로스오버 (V57: INACTIVE, 확인용 테스트)
    "macd_crossover": {
        "strategy_id": "macd_crossover", "name": "MACD 크로스오버 [INACTIVE]", "version": "2.0",
        "rules": [
            {"name": "macd_buy", "signal_type": "buy", "conditions": [
                {"indicator": "macd", "params": {}, "operator": "crosses_above", "value": "macd_signal"}
            ], "logic": "and", "weight": 1.0},
            {"name": "macd_sell", "signal_type": "sell", "conditions": [
                {"indicator": "macd", "params": {}, "operator": "crosses_below", "value": "macd_signal"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.04, "take_profit_pct": 0.12, "max_position_pct": 0.1}
    },

    # 4. 볼린저 밴드 스퀴즈 (변경 없음)
    "bollinger_squeeze": {
        "strategy_id": "bollinger_squeeze", "name": "볼린저 밴드 스퀴즈", "version": "1.0",
        "rules": [
            {"name": "bollinger_lower_buy", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
            ], "logic": "and", "weight": 1.0},
            {"name": "bollinger_upper_sell", "signal_type": "sell", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_upper"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.03, "take_profit_pct": 0.08, "max_position_pct": 0.1}
    },

    # 5. 모멘텀 브레이크아웃 v2 (V57: +SMA50 필터, SL 8%, TP 20%)
    "momentum_breakout": {
        "strategy_id": "momentum_breakout", "name": "모멘텀 브레이크아웃 v2", "version": "2.0",
        "rules": [
            {"name": "momentum_buy", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "ema_20"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 50},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"}
            ], "logic": "and", "weight": 0.9},
            {"name": "momentum_sell", "signal_type": "sell", "conditions": [
                {"indicator": "price", "params": {}, "operator": "lt", "value": "ema_20"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 50}
            ], "logic": "and", "weight": 0.9}
        ],
        "risk_management": {"stop_loss_pct": 0.08, "take_profit_pct": 0.20, "max_position_pct": 0.1}
    },

    # 6. 추세 추종 v4 (상승 추세 내 dip 매수 + RSI 매도, trailing 제거)
    # 근본 원인: v3는 trailing 7%가 수익 전부 청산 (SL 58 vs TP 4)
    # 수정: 역추세 진입(RSI<40 dip) + RSI>70 매도 + trailing 제거
    "trend_following": {
        "strategy_id": "trend_following", "name": "추세 추종 v4", "version": "4.0",
        "rules": [
            {"name": "uptrend_dip_entry", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 40},
                {"indicator": "t10y2y", "params": {}, "operator": "gte", "value": 0}
            ], "logic": "and", "weight": 1.0},
            {"name": "overbought_exit", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 70}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.12, "take_profit_pct": 0.30, "max_position_pct": 0.12}
    },

    # ======================================================================
    # B. QUANT_COMPOSITE 전략 (4개)
    # ======================================================================

    # 7. 트리플 스크린 v3 (매도 단순화: RSI>65 단일 조건)
    # 근본 원인: v2 매도 조건(SMA_20<SMA_50 AND MACD<signal) 너무 느림
    # 수정: 매수는 유지 + 매도를 RSI>65로 빠르게 + SL 10%
    "triple_screen": {
        "strategy_id": "triple_screen", "name": "트리플 스크린 v3", "version": "3.0",
        "rules": [
            {"name": "triple_screen_buy", "signal_type": "buy", "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 45},
                {"indicator": "macd", "params": {}, "operator": "gt", "value": "macd_signal"}
            ], "logic": "and", "weight": 1.0},
            {"name": "overbought_exit", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.08}
    },

    # 8. 평균 회귀 v2 (V56: RSI 25→35, 75→65)
    "mean_reversion": {
        "strategy_id": "mean_reversion", "name": "평균 회귀 v2", "version": "2.0",
        "rules": [
            {"name": "oversold_bounce", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 35},
                {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
            ], "logic": "and", "weight": 1.0},
            {"name": "overbought_pullback", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65},
                {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_upper"}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.03, "take_profit_pct": 0.08, "max_position_pct": 0.08}
    },

    # 9. 듀얼 모멘텀 v4 (4조건→2조건, dip 진입 + 빠른 매도)
    # 근본 원인: v3의 4조건 AND가 고점에서만 매수, SMA_200 매도 너무 느림
    # 수정: price>SMA_200(장기 상승) + RSI<45(dip) 매수, RSI>70 매도
    "dual_momentum": {
        "strategy_id": "dual_momentum", "name": "듀얼 모멘텀 v4", "version": "4.0",
        "rules": [
            {"name": "trend_dip_buy", "signal_type": "buy", "conditions": [
                {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_200"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 45}
            ], "logic": "and", "weight": 1.0},
            {"name": "overbought_or_trend_break_sell", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 70}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.12, "take_profit_pct": 0.30, "max_position_pct": 0.15}
    },

    # 10. RSI 스캘핑 (변경 없음)
    "scalping_rsi": {
        "strategy_id": "scalping_rsi", "name": "RSI 스캘핑", "version": "1.0",
        "rules": [
            {"name": "quick_oversold", "signal_type": "buy", "conditions": [
                {"indicator": "rsi", "params": {"period": 7}, "operator": "lt", "value": 20}
            ], "logic": "and", "weight": 1.0},
            {"name": "quick_overbought", "signal_type": "sell", "conditions": [
                {"indicator": "rsi", "params": {"period": 7}, "operator": "gt", "value": 80}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.015, "take_profit_pct": 0.03, "max_position_pct": 0.05}
    },

    # ======================================================================
    # C. ASSET_ALLOCATION 전략 (1개)
    # ======================================================================

    # 11. 금리 연동 v2 (V56: T10Y 구간 재설계)
    "rate_linked_allocation": {
        "strategy_id": "rate_linked_allocation", "name": "금리 연동 v2", "version": "2.0",
        "rules": [
            {"name": "low_rate_equity", "signal_type": "buy", "conditions": [
                {"indicator": "treasury_10y", "params": {"source": "fred"}, "operator": "lt", "value": 4.2}
            ], "logic": "and", "weight": 1.0},
            {"name": "high_rate_bond", "signal_type": "sell", "conditions": [
                {"indicator": "treasury_10y", "params": {"source": "fred"}, "operator": "gte", "value": 4.8}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.12, "take_profit_pct": 0.25, "max_position_pct": 0.8}
    },

    # ======================================================================
    # D. 신규 전략 (V57, 2개)
    # ======================================================================

    # 12. RSI+MACD 복합 (A등급, CAGR 18.31%)
    "rsi_macd_combo": {
        "strategy_id": "rsi_macd_combo", "name": "RSI+MACD 복합", "version": "1.0",
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

    # 13. 골든크로스+RSI (B+등급, CAGR 10.54%)
    "golden_cross_rsi": {
        "strategy_id": "golden_cross_rsi", "name": "골든크로스+RSI", "version": "1.0",
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

    # 14. RSI+MACD+SMA200 편향보정 (B+등급, CAGR 14.22%, PF 2.85)
    "rsi_macd_sma200": {
        "strategy_id": "rsi_macd_sma200", "name": "RSI+MACD+SMA200 편향보정", "version": "1.0",
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

    # 15. Yield Curve+RSI 매크로 전략 (B+등급, CAGR 12.89%, PF 2.62)
    "yield_curve_rsi": {
        "strategy_id": "yield_curve_rsi", "name": "Yield Curve+RSI", "version": "1.0",
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

    # ======================================================================
    # E. SEASONAL 전략 (2개 - calendar 지표 구현)
    # ======================================================================

    # 14. 1월 효과 (12월 20일 매수, 1월 말 매도)
    "january_effect": {
        "strategy_id": "january_effect", "name": "1월 효과", "version": "1.0",
        "rules": [
            {"name": "january_buy", "signal_type": "buy", "conditions": [
                {"indicator": "calendar", "params": {"month": 12, "day_gte": 20}, "operator": "eq", "value": 1.0}
            ], "logic": "and", "weight": 1.0},
            {"name": "january_sell", "signal_type": "sell", "conditions": [
                {"indicator": "calendar", "params": {"month": 1, "day_gte": 25}, "operator": "eq", "value": 1.0}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.08, "take_profit_pct": 0.15, "max_position_pct": 0.15}
    },

    # 15. Sell in May (5-10월 매도, 11-4월 매수)
    "sell_in_may": {
        "strategy_id": "sell_in_may", "name": "Sell in May", "version": "1.0",
        "rules": [
            {"name": "winter_aggressive", "signal_type": "buy", "conditions": [
                {"indicator": "calendar", "params": {"month_in": [11, 12, 1, 2, 3, 4]}, "operator": "eq", "value": 1.0}
            ], "logic": "and", "weight": 1.0},
            {"name": "summer_defensive", "signal_type": "sell", "conditions": [
                {"indicator": "calendar", "params": {"month_in": [5, 6, 7, 8, 9, 10]}, "operator": "eq", "value": 1.0}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.20, "max_position_pct": 0.8}
    },

    # ======================================================================
    # F. CROSS-SYMBOL 전략 (3개 - 크로스-심볼 지표 구현)
    # ======================================================================

    # 16. 고배당 (dividend_yield > 1.5, pbr < 10.0 - 테크 대형주 유니버스 대응)
    "high_dividend": {
        "strategy_id": "high_dividend", "name": "고배당", "version": "1.0",
        "rules": [
            {"name": "high_div_buy", "signal_type": "buy", "conditions": [
                {"indicator": "dividend_yield", "params": {}, "operator": "gt", "value": 1.5},
                {"indicator": "pbr", "params": {}, "operator": "lt", "value": 10.0}
            ], "logic": "and", "weight": 1.0},
            {"name": "high_div_sell", "signal_type": "sell", "conditions": [
                {"indicator": "dividend_yield", "params": {}, "operator": "lt", "value": 0.5}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.15}
    },

    # 17. 저PER 가치 (per < 25, per_sector_ratio < 0.8 - 테크주 PER 높음 반영)
    "low_per_value": {
        "strategy_id": "low_per_value", "name": "저PER 가치", "version": "1.0",
        "rules": [
            {"name": "low_per_buy", "signal_type": "buy", "conditions": [
                {"indicator": "per", "params": {}, "operator": "lt", "value": 25},
                {"indicator": "per_sector_ratio", "params": {}, "operator": "lt", "value": 0.8}
            ], "logic": "and", "weight": 1.0},
            {"name": "high_per_sell", "signal_type": "sell", "conditions": [
                {"indicator": "per_sector_ratio", "params": {}, "operator": "gt", "value": 1.3}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.25, "max_position_pct": 0.15}
    },

    # 18. 분기 모멘텀 (calendar day_in + momentum_3m top 5)
    "quarterly_momentum": {
        "strategy_id": "quarterly_momentum", "name": "분기 모멘텀", "version": "1.0",
        "rules": [
            {"name": "quarter_start_buy", "signal_type": "buy", "conditions": [
                {"indicator": "calendar", "params": {"day_in": ["1-2", "4-1", "7-1", "10-1"], "day_tolerance": 3}, "operator": "eq", "value": 1.0},
                {"indicator": "momentum_3m", "params": {"top_n": 5}, "operator": "eq", "value": 1.0}
            ], "logic": "and", "weight": 1.0},
            {"name": "quarter_end_sell", "signal_type": "sell", "conditions": [
                {"indicator": "calendar", "params": {"day_in": ["3-15", "6-15", "9-15", "12-15"], "day_tolerance": 3}, "operator": "eq", "value": 1.0}
            ], "logic": "and", "weight": 1.0}
        ],
        "risk_management": {"stop_loss_pct": 0.10, "take_profit_pct": 0.20, "max_position_pct": 0.2}
    },
}

# 미테스트/블록된 전략 목록 (참고용)
BLOCKED_STRATEGIES = {
    "balanced_60_40": "portfolio_drift 지표 미구현 (멀티에셋)",
    "all_weather": "portfolio_drift 지표 미구현 (멀티에셋)",
    "vertex_ai_prediction": "ml_prediction 외부 모델 의존",
    "ai_technical_hybrid": "ensemble_score 외부 모델 의존",
}

SYMBOLS = ["AAPL", "NVDA", "TSLA", "MSFT", "GOOGL", "AMZN", "META", "JNJ", "AMAT", "INTC"]
NUM_RUNS = 3


def grade_strategy(r):
    """전략 등급 부여"""
    ret = r["total_return"]
    trades = r["total_trades"]
    win = r["win_rate"]
    pf = r["profit_factor"]

    if trades == 0:
        return "N/A"
    if ret > 50 and win > 55 and pf > 1.5:
        return " A "
    elif ret > 20 and win > 40 and pf > 1.2:
        return "B+ "
    elif ret > 0 and trades > 20:
        return " B "
    elif ret > -20:
        return " C "
    elif ret > -50:
        return " D "
    else:
        return " F "


def _is_deterministic(data):
    """Check determinism for a strategy result.

    Returns False (non-deterministic) when:
    - The entry was produced by the error handler (single synthetic run).
    - Multiple runs produced different total_return values.
    """
    if data.get("error"):
        return False
    runs = data["runs"]
    return all(r["total_return"] == runs[0]["total_return"] for r in runs)


def main():
    loader = MongoDataLoader(uri=os.environ["MONGODB_URI"])
    try:
        config = BacktestConfig(
            start_date=date(2021, 1, 1), end_date=date(2026, 2, 25),
            initial_capital=Decimal("50000000"), tickers=SYMBOLS,
            commission_rate=Decimal("0.00015"), tax_rate=Decimal("0.0023"),
            slippage_rate=Decimal("0.001"), max_positions=5, position_size_pct=Decimal("0.2"),
            benchmark_ticker="SPY",
        )

        print("=" * 150)
        print("종합 전략 백테스트 (V56+V57+크로스심볼 반영)")
        print("기간: 2021-01-01 ~ 2026-02-25 | 종목: %d개 | 자본: ₩50,000,000 | 반복: %d회" % (len(SYMBOLS), NUM_RUNS))
        print("전략: %d개 테스트 | %d개 블록 (미구현 지표)" % (len(STRATEGIES), len(BLOCKED_STRATEGIES)))
        print("=" * 150)

        all_results = {}

        def run_strategy(sid, sdict):
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
            all_results[sid] = {"runs": runs, "name": sdict["name"], "version": sdict.get("version", "1.0")}
            det = "PASS" if all(r["total_return"] == runs[0]["total_return"] for r in runs) else "FAIL"
            r = runs[0]
            grade = grade_strategy(r)
            print("  [%s] %s %-32s | %9.2f%% | %4d trades | %5.1f%% win | MDD %7.2f%% | Sharpe %6.2f | CAGR %7.2f%% | PF %5.2f | SL:%d TP:%d | %4.1fd" % (
                det, grade, sdict["name"], r["total_return"], r["total_trades"], r["win_rate"], r["mdd"],
                r["sharpe"], r["cagr"], r["profit_factor"], r["stop_loss_count"], r["take_profit_count"],
                r["avg_holding_days"]))

        # 카테고리별 실행
        categories = {
            "MOMENTUM": ["golden_cross", "rsi_oversold", "macd_crossover", "bollinger_squeeze", "momentum_breakout", "trend_following"],
            "QUANT_COMPOSITE": ["triple_screen", "mean_reversion", "dual_momentum", "scalping_rsi"],
            "ASSET_ALLOCATION": ["rate_linked_allocation"],
            "V57_NEW": ["rsi_macd_combo", "golden_cross_rsi", "rsi_macd_sma200", "yield_curve_rsi"],
            "SEASONAL": ["january_effect", "sell_in_may"],
            "CROSS_SYMBOL": ["high_dividend", "low_per_value", "quarterly_momentum"],
        }

        for cat_name, strategy_ids in categories.items():
            print(f"\n[{cat_name}]")
            print("-" * 150)
            for sid in strategy_ids:
                if sid in STRATEGIES:
                    try:
                        run_strategy(sid, STRATEGIES[sid])
                    except Exception as e:
                        print(f"  [ERR] {STRATEGIES[sid]['name']}: {e}")
                        all_results[sid] = {"runs": [{"total_return": 0, "total_trades": 0, "win_rate": 0, "mdd": 0,
                                                      "sharpe": 0, "cagr": 0, "avg_holding_days": 0, "profit_factor": 0,
                                                      "stop_loss_count": 0, "take_profit_count": 0}],
                                            "name": STRATEGIES[sid]["name"], "version": STRATEGIES[sid].get("version", "1.0"),
                                            "error": str(e)}

        # 종합 순위표
        print("\n\n" + "=" * 150)
        print("종합 순위 (수익률 순)")
        print("=" * 150)
        print("%-3s %-4s %-4s %-32s | %10s | %5s | %6s | %8s | %7s | %8s | %6s | %s" % (
            "#", "등급", "DET", "전략명", "수익률", "거래", "승률", "MDD", "Sharpe", "CAGR", "PF", "SL/TP"))
        print("-" * 150)

        sorted_results = sorted(all_results.items(), key=lambda x: x[1]["runs"][0]["total_return"], reverse=True)
        for idx, (sid, data) in enumerate(sorted_results, 1):
            r = data["runs"][0]
            grade = grade_strategy(r)
            det = "PASS" if _is_deterministic(data) else "FAIL"
            print("%-3d %s %s %-32s | %9.2f%% | %5d | %5.1f%% | %7.2f%% | %6.2f | %7.2f%% | %5.2f | %d/%d" % (
                idx, grade, det, data["name"], r["total_return"], r["total_trades"],
                r["win_rate"], r["mdd"], r["sharpe"], r["cagr"], r["profit_factor"],
                r["stop_loss_count"], r["take_profit_count"]))

        # 블록된 전략 표시
        print("\n" + "=" * 150)
        print("블록된 전략 (%d개 - 미구현 지표)" % len(BLOCKED_STRATEGIES))
        print("=" * 150)
        for sid, reason in BLOCKED_STRATEGIES.items():
            print(f"  [BLOCKED] {sid}: {reason}")

        # 전략 분류 요약
        print("\n" + "=" * 150)
        print("전략 분류 요약")
        print("=" * 150)
        active_good = []
        active_ok = []
        inactive = []
        no_trades = []

        for sid, data in sorted_results:
            r = data["runs"][0]
            grade = grade_strategy(r)
            if r["total_trades"] == 0:
                no_trades.append((sid, data["name"]))
            elif "A" in grade or "B+" in grade:
                active_good.append((sid, data["name"], r["total_return"], r["cagr"]))
            elif "B" in grade or "C" in grade:
                active_ok.append((sid, data["name"], r["total_return"], r["cagr"]))
            else:
                inactive.append((sid, data["name"], r["total_return"]))

        print(f"\n  ★ 추천 (A/B+ 등급, {len(active_good)}개):")
        for sid, name, ret, cagr in active_good:
            print(f"    - {name}: +{ret:.2f}% (CAGR {cagr:.2f}%)")

        print(f"\n  ○ 조건부 활성 (B/C 등급, {len(active_ok)}개):")
        for sid, name, ret, cagr in active_ok:
            print(f"    - {name}: {ret:+.2f}% (CAGR {cagr:.2f}%)")

        print(f"\n  ✗ 비활성화 권장 (D/F 등급, {len(inactive)}개):")
        for sid, name, ret in inactive:
            print(f"    - {name}: {ret:.2f}%")

        print(f"\n  ? 거래 없음 ({len(no_trades)}개):")
        for sid, name in no_trades:
            print(f"    - {name}")

        # JSON 결과 저장
        out_path = Path(__file__).resolve().parent / "comprehensive_test_results.json"
        output = {
            "test_name": "comprehensive_v56_v57_cross_symbol_test",
            "test_date": str(date.today()),
            "config": {
                "period": "2021-01-01 ~ 2026-02-25",
                "symbols": SYMBOLS,
                "initial_capital": 50000000,
                "runs": NUM_RUNS,
            },
            "results": {sid: data for sid, data in sorted_results},
            "blocked_strategies": BLOCKED_STRATEGIES,
            "determinism": "ALL_PASS" if all(
                _is_deterministic(data) for data in all_results.values()
            ) else "SOME_FAIL",
            "summary": {
                "total_tested": len(all_results),
                "total_blocked": len(BLOCKED_STRATEGIES),
                "a_grade": len(active_good),
                "b_grade": len(active_ok),
                "inactive": len(inactive),
                "no_trades": len(no_trades),
            }
        }
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(output, f, ensure_ascii=False, indent=2, default=str)
        print(f"\n결과 저장: {out_path}")

        print("완료.")
    finally:
        loader.close()


if __name__ == "__main__":
    main()
