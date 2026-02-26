"""
검증된 데이터 기반 전략 백테스트 실행 스크립트

프로덕션 데이터 검증 결과를 반영하여 수정된 전략 DSL로 백테스트를 실행하고,
기존 전략과 성과를 비교합니다.

실행:
    cd quant-jump-stock-data-engine
    python scripts/run_validated_backtest.py
"""

import os
import sys
import json
import logging
from datetime import date
from decimal import Decimal
from pathlib import Path

# src를 PYTHONPATH에 추가
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "src"))

from dotenv import load_dotenv

# 환경 변수 로드 (.env.db.prod가 최종 오버라이드)
for env_file in [".env.common", ".env.local", ".env.db.local", ".env.db.prod"]:
    env_path = Path(__file__).resolve().parent.parent.parent / env_file
    if env_path.exists():
        load_dotenv(env_path, override=True)
        print(f"[ENV] Loaded: {env_file}")

from application.backtest.data_loader_mongo import MongoDataLoader
from application.backtest.engine import BacktestEngine, BacktestConfig
from domain.strategy.models import StrategyDefinition

logging.basicConfig(level=logging.INFO, format="%(levelname)s | %(message)s")
logger = logging.getLogger(__name__)

# ==============================================================
# 테스트 대상 종목 (검증 데이터에서 유효한 종목 포함)
# ==============================================================
TEST_SYMBOLS = ["AAPL", "NVDA", "TSLA", "MSFT", "GOOGL", "AMZN", "META", "JNJ", "AMAT", "INTC"]

# ==============================================================
# 기존 전략 DSL (원본)
# ==============================================================

ORIGINAL_STRATEGIES = {
    "rsi_oversold": {
        "strategy_id": "rsi_oversold_original",
        "name": "[원본] RSI 과매도 반등",
        "description": "기존 RSI 30/70 전략",
        "version": "1.0",
        "rules": [
            {
                "name": "rsi_oversold_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 30}
                ],
                "logic": "and",
                "weight": 1.0
            },
            {
                "name": "rsi_overbought_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 70}
                ],
                "logic": "and",
                "weight": 1.0
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.03,
            "take_profit_pct": 0.1,
            "max_position_pct": 0.1
        }
    },
    "trend_following": {
        "strategy_id": "trend_following_original",
        "name": "[원본] 추세 추종",
        "description": "기존 SMA50 추세 추종",
        "version": "1.0",
        "rules": [
            {
                "name": "uptrend_entry",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"},
                    {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"}
                ],
                "logic": "and",
                "weight": 0.85
            },
            {
                "name": "downtrend_exit",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "sma_50"}
                ],
                "logic": "and",
                "weight": 1.0
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.07,
            "take_profit_pct": 0.25,
            "max_position_pct": 0.12,
            "trailing_stop": True,
            "trailing_stop_pct": 0.05
        }
    },
    "triple_screen": {
        "strategy_id": "triple_screen_original",
        "name": "[원본] 트리플 스크린",
        "description": "기존 SMA+RSI+MACD 3중 필터",
        "version": "1.0",
        "rules": [
            {
                "name": "triple_screen_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 70},
                    {"indicator": "macd", "params": {}, "operator": "gt", "value": "macd_signal"}
                ],
                "logic": "and",
                "weight": 1.0
            },
            {
                "name": "triple_screen_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "lt", "value": "sma_50"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 30},
                    {"indicator": "macd", "params": {}, "operator": "lt", "value": "macd_signal"}
                ],
                "logic": "and",
                "weight": 1.0
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.2,
            "max_position_pct": 0.08
        }
    },
    "mean_reversion": {
        "strategy_id": "mean_reversion_original",
        "name": "[원본] 평균 회귀",
        "description": "기존 RSI 25 + 볼린저 하단",
        "version": "1.0",
        "rules": [
            {
                "name": "oversold_bounce",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 25},
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
                ],
                "logic": "and",
                "weight": 1.0
            },
            {
                "name": "overbought_pullback",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 75},
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_upper"}
                ],
                "logic": "and",
                "weight": 1.0
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.02,
            "take_profit_pct": 0.06,
            "max_position_pct": 0.08
        }
    },
}

# ==============================================================
# 검증 데이터 반영 수정 전략 DSL
# ==============================================================

VALIDATED_STRATEGIES = {
    # RSI 과매도: RSI 30→42 (검증: +2.18%/5일, N=10,599), 매도 RSI 70→60 (검증: 과매수 -0.52%)
    "rsi_oversold_v2": {
        "strategy_id": "rsi_oversold_v2",
        "name": "[검증v2] RSI 과매도 반등",
        "description": "RSI<42 매수 (검증: +2.18%/5일), RSI>60 매도 (검증: 과매수 -0.52%)",
        "version": "2.0",
        "rules": [
            {
                "name": "rsi_oversold_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42}
                ],
                "logic": "and",
                "weight": 1.0
            },
            {
                "name": "rsi_overbought_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 60}
                ],
                "logic": "and",
                "weight": 1.0
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.15,
            "max_position_pct": 0.1
        }
    },

    # RSI 과매도 + 감성 필터: RSI<42 AND sentiment>0.2
    "rsi_oversold_sentiment_v2": {
        "strategy_id": "rsi_oversold_sentiment_v2",
        "name": "[검증v2] RSI 과매도 + 감성 필터",
        "description": "RSI<42 AND sentiment>0.2 (검증: 상위분위 +0.38%/일)",
        "version": "2.0",
        "rules": [
            {
                "name": "rsi_sentiment_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42},
                    {"indicator": "sentiment_score", "params": {}, "operator": "gt", "value": 0.2}
                ],
                "logic": "and",
                "weight": 1.0
            },
            {
                "name": "rsi_overbought_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 60}
                ],
                "logic": "and",
                "weight": 1.0
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.15,
            "max_position_pct": 0.1
        }
    },

    # 추세 추종 + FRED 레짐 필터: 기존 조건 + T10Y2Y > 0 (정상 금리차 = 경기침체 회피)
    "trend_following_fred_v2": {
        "strategy_id": "trend_following_fred_v2",
        "name": "[검증v2] 추세 추종 + FRED 레짐",
        "description": "기존 추세추종 + T10Y2Y>0 레짐 필터 (검증: 1,318일 데이터)",
        "version": "2.0",
        "rules": [
            {
                "name": "uptrend_entry_with_regime",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "sma_50"},
                    {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"},
                    {"indicator": "treasury_10y", "params": {}, "operator": "lt", "value": 5.0}
                ],
                "logic": "and",
                "weight": 0.85
            },
            {
                "name": "downtrend_exit",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "sma_50"}
                ],
                "logic": "and",
                "weight": 1.0
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.07,
            "take_profit_pct": 0.25,
            "max_position_pct": 0.12,
            "trailing_stop": True,
            "trailing_stop_pct": 0.05
        }
    },

    # 트리플 스크린 + 감성 4th Screen
    "triple_screen_sentiment_v2": {
        "strategy_id": "triple_screen_sentiment_v2",
        "name": "[검증v2] 트리플 스크린 + 감성",
        "description": "SMA+RSI+MACD + sentiment>0.2 4th screen",
        "version": "2.0",
        "rules": [
            {
                "name": "quad_screen_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "gt", "value": "sma_50"},
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 70},
                    {"indicator": "macd", "params": {}, "operator": "gt", "value": "macd_signal"},
                    {"indicator": "sentiment_score", "params": {}, "operator": "gt", "value": 0.2}
                ],
                "logic": "and",
                "weight": 1.0
            },
            {
                "name": "triple_screen_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "sma", "params": {"period": 20}, "operator": "lt", "value": "sma_50"},
                    {"indicator": "macd", "params": {}, "operator": "lt", "value": "macd_signal"}
                ],
                "logic": "and",
                "weight": 1.0
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.2,
            "max_position_pct": 0.08
        }
    },

    # 평균 회귀 v2: RSI 25→35, SL 2%→4% (더 많은 매매 기회 + 리스크 관리 완화)
    "mean_reversion_v2": {
        "strategy_id": "mean_reversion_v2",
        "name": "[검증v2] 평균 회귀",
        "description": "RSI<35 + 볼린저하단 (완화된 진입, SL 4%)",
        "version": "2.0",
        "rules": [
            {
                "name": "oversold_bounce",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 35},
                    {"indicator": "price", "params": {}, "operator": "lt", "value": "bollinger_20_lower"}
                ],
                "logic": "and",
                "weight": 1.0
            },
            {
                "name": "overbought_pullback",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 65},
                    {"indicator": "price", "params": {}, "operator": "gt", "value": "bollinger_20_upper"}
                ],
                "logic": "and",
                "weight": 1.0
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.04,
            "take_profit_pct": 0.10,
            "max_position_pct": 0.08
        }
    },

    # 신규: 데이터 검증 멀티시그널 앙상블
    "data_validated_multi_signal": {
        "strategy_id": "data_validated_multi_signal",
        "name": "[신규] 데이터 검증 멀티시그널",
        "description": "RSI<42 + sentiment>0.2 + T10Y2Y<5 (모든 검증 시그널 조합)",
        "version": "1.0",
        "rules": [
            {
                "name": "multi_signal_buy",
                "signal_type": "buy",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 42},
                    {"indicator": "sentiment_score", "params": {}, "operator": "gt", "value": 0.2},
                    {"indicator": "treasury_10y", "params": {}, "operator": "lt", "value": 5.0}
                ],
                "logic": "and",
                "weight": 1.0
            },
            {
                "name": "multi_signal_sell",
                "signal_type": "sell",
                "conditions": [
                    {"indicator": "rsi", "params": {"period": 14}, "operator": "gt", "value": 60}
                ],
                "logic": "and",
                "weight": 1.0
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.15,
            "max_position_pct": 0.1,
            "trailing_stop": True,
            "trailing_stop_pct": 0.05
        }
    },
}


def run_single_backtest(
    strategy_dict: dict,
    data_loader: MongoDataLoader,
    start_date: date,
    end_date: date,
    symbols: list[str],
) -> dict:
    """단일 전략 백테스트 실행"""
    strategy = StrategyDefinition(**strategy_dict)

    config = BacktestConfig(
        start_date=start_date,
        end_date=end_date,
        initial_capital=Decimal("100000"),  # $100,000
        tickers=symbols,
        commission_rate=Decimal("0.00015"),
        tax_rate=Decimal("0.0023"),
        slippage_rate=Decimal("0.001"),
        max_positions=5,
        position_size_pct=Decimal("0.2"),
        benchmark_ticker="SPY",
    )

    engine = BacktestEngine(data_loader=data_loader, config=config)
    result = engine.run(strategy)

    return {
        "strategy_id": strategy_dict["strategy_id"],
        "strategy_name": strategy_dict["name"],
        "total_return": float(result.total_return),
        "cagr": float(result.cagr) if result.cagr else None,
        "mdd": float(result.mdd) if result.mdd else None,
        "sharpe_ratio": float(result.sharpe_ratio) if result.sharpe_ratio else None,
        "sortino_ratio": float(result.sortino_ratio) if result.sortino_ratio else None,
        "win_rate": float(result.win_rate) if result.win_rate else None,
        "total_trades": result.total_trades,
        "profit_factor": float(result.profit_factor) if result.profit_factor else None,
        "avg_holding_days": float(result.avg_holding_days) if result.avg_holding_days else None,
        "final_value": float(result.final_value),
        "benchmark_return": float(result.benchmark_return) if result.benchmark_return else None,
        "alpha": float(result.alpha) if result.alpha else None,
        "error": result.error_message,
        "stop_loss_count": result.stop_loss_count,
        "take_profit_count": result.take_profit_count,
        "trailing_stop_count": result.trailing_stop_count,
    }


def print_comparison_table(results: list[dict]):
    """결과 비교 테이블 출력"""
    print("\n" + "=" * 130)
    print(f"{'전략':<40} {'수익률':>8} {'CAGR':>8} {'MDD':>8} {'샤프':>7} {'승률':>7} {'거래수':>6} {'PF':>6} {'알파':>8} {'에러'}")
    print("-" * 130)

    for r in results:
        error_mark = "❌" if r.get("error") else ""
        total_ret = f"{r['total_return']:.1f}%" if r['total_return'] is not None else "N/A"
        cagr = f"{r['cagr']:.1f}%" if r['cagr'] else "N/A"
        mdd = f"{r['mdd']:.1f}%" if r['mdd'] else "N/A"
        sharpe = f"{r['sharpe_ratio']:.2f}" if r['sharpe_ratio'] else "N/A"
        win_rate = f"{r['win_rate']:.1f}%" if r['win_rate'] else "N/A"
        trades = f"{r['total_trades']}"
        pf = f"{r['profit_factor']:.2f}" if r['profit_factor'] else "N/A"
        alpha = f"{r['alpha']:.2f}%" if r.get('alpha') else "N/A"

        print(f"{r['strategy_name']:<40} {total_ret:>8} {cagr:>8} {mdd:>8} {sharpe:>7} {win_rate:>7} {trades:>6} {pf:>6} {alpha:>8} {error_mark}")

    print("=" * 130)


def main():
    uri = os.environ.get("MONGODB_URI")
    if not uri:
        print("❌ MONGODB_URI not found in environment")
        sys.exit(1)

    print(f"[MongoDB] Connecting... (URI prefix: {uri[:30]}...)")

    data_loader = MongoDataLoader(uri=uri)

    # 데이터 범위 확인
    min_date, max_date, count = data_loader.get_date_range()
    print(f"[Data] Range: {min_date} ~ {max_date}, {count} days")

    # 백테스트 기간: 최근 6개월 (감성/추천 데이터가 있는 기간)
    start = date(2025, 9, 1)
    end = date(2026, 2, 25)
    print(f"[Backtest] Period: {start} ~ {end}")
    print(f"[Backtest] Symbols: {TEST_SYMBOLS}")

    all_results = []

    # --- 기존 전략 실행 ---
    print("\n>>> 기존(원본) 전략 백테스트 실행 중...")
    for key, strategy_dict in ORIGINAL_STRATEGIES.items():
        try:
            logger.info(f"Running: {strategy_dict['name']}")
            result = run_single_backtest(strategy_dict, data_loader, start, end, TEST_SYMBOLS)
            all_results.append(result)
            print(f"  ✅ {strategy_dict['name']}: {result['total_return']:.1f}% ({result['total_trades']} trades)")
        except Exception as e:
            print(f"  ❌ {strategy_dict['name']}: {e}")
            all_results.append({
                "strategy_id": strategy_dict["strategy_id"],
                "strategy_name": strategy_dict["name"],
                "total_return": 0, "cagr": None, "mdd": None,
                "sharpe_ratio": None, "sortino_ratio": None, "win_rate": None,
                "total_trades": 0, "profit_factor": None, "avg_holding_days": None,
                "final_value": 100000, "benchmark_return": None, "alpha": None,
                "error": str(e), "stop_loss_count": 0, "take_profit_count": 0,
                "trailing_stop_count": 0,
            })

    # --- 검증 반영 전략 실행 ---
    print("\n>>> 검증 데이터 반영 전략 백테스트 실행 중...")
    for key, strategy_dict in VALIDATED_STRATEGIES.items():
        try:
            logger.info(f"Running: {strategy_dict['name']}")
            result = run_single_backtest(strategy_dict, data_loader, start, end, TEST_SYMBOLS)
            all_results.append(result)
            print(f"  ✅ {strategy_dict['name']}: {result['total_return']:.1f}% ({result['total_trades']} trades)")
        except Exception as e:
            print(f"  ❌ {strategy_dict['name']}: {e}")
            all_results.append({
                "strategy_id": strategy_dict["strategy_id"],
                "strategy_name": strategy_dict["name"],
                "total_return": 0, "cagr": None, "mdd": None,
                "sharpe_ratio": None, "sortino_ratio": None, "win_rate": None,
                "total_trades": 0, "profit_factor": None, "avg_holding_days": None,
                "final_value": 100000, "benchmark_return": None, "alpha": None,
                "error": str(e), "stop_loss_count": 0, "take_profit_count": 0,
                "trailing_stop_count": 0,
            })

    # --- 비교 테이블 출력 ---
    print_comparison_table(all_results)

    # --- 원본 vs 검증 쌍별 비교 ---
    print("\n>>> 원본 vs 검증 전략 비교:")
    pairs = [
        ("rsi_oversold_original", "rsi_oversold_v2", "RSI 과매도"),
        ("trend_following_original", "trend_following_fred_v2", "추세 추종"),
        ("triple_screen_original", "triple_screen_sentiment_v2", "트리플 스크린"),
        ("mean_reversion_original", "mean_reversion_v2", "평균 회귀"),
    ]

    results_map = {r["strategy_id"]: r for r in all_results}
    for orig_id, val_id, label in pairs:
        orig = results_map.get(orig_id, {})
        val = results_map.get(val_id, {})
        o_ret = orig.get("total_return", 0)
        v_ret = val.get("total_return", 0)
        diff = v_ret - o_ret
        sign = "+" if diff >= 0 else ""
        o_trades = orig.get("total_trades", 0)
        v_trades = val.get("total_trades", 0)
        print(f"  {label:<15}: 원본 {o_ret:+.1f}% ({o_trades}t) → 검증 {v_ret:+.1f}% ({v_trades}t) | 차이: {sign}{diff:.1f}%p")

    # --- JSON 결과 저장 ---
    output_path = Path(__file__).resolve().parent.parent.parent / "docs" / "analysis" / "백테스트_검증결과.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(all_results, f, ensure_ascii=False, indent=2, default=str)
    print(f"\n[Output] 결과 저장: {output_path}")

    data_loader.close()
    print("\n완료.")


if __name__ == "__main__":
    main()
