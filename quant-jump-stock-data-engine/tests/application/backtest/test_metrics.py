"""
Backtest Metrics Tests

SCRUM-184: metrics.py 모듈의 단위 테스트
"""

import pytest
from datetime import date
from decimal import Decimal

from application.backtest.metrics import (
    calculate_cagr,
    calculate_mdd,
    calculate_volatility,
    calculate_sharpe_ratio,
    calculate_sortino_ratio,
    calculate_daily_returns,
    calculate_performance_metrics,
    calculate_trade_metrics,
    PerformanceMetrics,
    TradeMetrics,
)


class TestCalculateCagr:
    """CAGR 계산 테스트"""

    def test_positive_return_one_year(self):
        """1년간 10% 수익 → CAGR 10%"""
        cagr = calculate_cagr(
            initial_value=Decimal("1000000"),
            final_value=Decimal("1100000"),
            start_date=date(2023, 1, 1),
            end_date=date(2024, 1, 1)
        )
        assert cagr == Decimal("10.00")

    def test_positive_return_two_years(self):
        """2년간 21% 수익 → CAGR ~10%"""
        cagr = calculate_cagr(
            initial_value=Decimal("1000000"),
            final_value=Decimal("1210000"),
            start_date=date(2022, 1, 1),
            end_date=date(2024, 1, 1)
        )
        # (1.21)^(1/2) - 1 = 0.10 = 10%
        assert cagr == Decimal("10.00")

    def test_negative_return(self):
        """손실 시나리오"""
        cagr = calculate_cagr(
            initial_value=Decimal("1000000"),
            final_value=Decimal("900000"),
            start_date=date(2023, 1, 1),
            end_date=date(2024, 1, 1)
        )
        assert cagr == Decimal("-10.00")

    def test_zero_days(self):
        """기간이 0일인 경우"""
        cagr = calculate_cagr(
            initial_value=Decimal("1000000"),
            final_value=Decimal("1100000"),
            start_date=date(2023, 1, 1),
            end_date=date(2023, 1, 1)
        )
        assert cagr == Decimal("0")

    def test_zero_initial_value(self):
        """초기 자본이 0인 경우"""
        cagr = calculate_cagr(
            initial_value=Decimal("0"),
            final_value=Decimal("1000000"),
            start_date=date(2023, 1, 1),
            end_date=date(2024, 1, 1)
        )
        assert cagr == Decimal("0")

    def test_total_loss(self):
        """100% 손실"""
        cagr = calculate_cagr(
            initial_value=Decimal("1000000"),
            final_value=Decimal("0"),
            start_date=date(2023, 1, 1),
            end_date=date(2024, 1, 1)
        )
        assert cagr == Decimal("-100")


class TestCalculateMdd:
    """MDD 계산 테스트"""

    def test_simple_drawdown(self):
        """단순 하락 시나리오"""
        equity_values = [
            Decimal("100"),
            Decimal("120"),  # 고점
            Decimal("90"),   # 최대 낙폭 (120 -> 90 = -25%)
            Decimal("110"),
        ]
        mdd = calculate_mdd(equity_values)
        assert mdd == Decimal("-25.00")

    def test_no_drawdown(self):
        """계속 상승하는 경우"""
        equity_values = [
            Decimal("100"),
            Decimal("110"),
            Decimal("120"),
            Decimal("130"),
        ]
        mdd = calculate_mdd(equity_values)
        assert mdd == Decimal("0")

    def test_multiple_drawdowns(self):
        """여러 번의 하락 중 최대값"""
        equity_values = [
            Decimal("100"),
            Decimal("120"),  # 고점 1
            Decimal("108"),  # -10%
            Decimal("130"),  # 고점 2
            Decimal("91"),   # -30% (최대 낙폭)
            Decimal("120"),
        ]
        mdd = calculate_mdd(equity_values)
        assert mdd == Decimal("-30.00")

    def test_empty_list(self):
        """빈 리스트"""
        mdd = calculate_mdd([])
        assert mdd == Decimal("0")

    def test_single_value(self):
        """단일 값"""
        mdd = calculate_mdd([Decimal("100")])
        assert mdd == Decimal("0")


class TestCalculateVolatility:
    """변동성 계산 테스트"""

    def test_zero_volatility(self):
        """변동이 없는 경우"""
        daily_returns = [0.0, 0.0, 0.0, 0.0]
        volatility = calculate_volatility(daily_returns)
        assert volatility == Decimal("0.00")

    def test_positive_volatility(self):
        """정상적인 변동성"""
        # 일간 수익률 1%, -1%, 1%, -1% 반복
        daily_returns = [0.01, -0.01, 0.01, -0.01] * 10
        volatility = calculate_volatility(daily_returns)
        assert volatility is not None
        assert volatility > Decimal("0")

    def test_empty_returns(self):
        """빈 수익률 리스트"""
        volatility = calculate_volatility([])
        assert volatility is None

    def test_single_return(self):
        """단일 수익률"""
        volatility = calculate_volatility([0.01])
        assert volatility is None


class TestCalculateSharpeRatio:
    """Sharpe Ratio 계산 테스트"""

    def test_positive_sharpe(self):
        """양의 Sharpe Ratio"""
        # CAGR 15%, 변동성 20%, 무위험 3%
        # Sharpe = (0.15 - 0.03) / 0.20 = 0.60
        sharpe = calculate_sharpe_ratio(
            cagr=Decimal("15"),
            volatility=Decimal("20"),
            risk_free_rate=Decimal("0.03")
        )
        assert sharpe == Decimal("0.60")

    def test_negative_sharpe(self):
        """음의 Sharpe Ratio (수익률 < 무위험)"""
        sharpe = calculate_sharpe_ratio(
            cagr=Decimal("2"),
            volatility=Decimal("20"),
            risk_free_rate=Decimal("0.03")
        )
        assert sharpe is not None
        assert sharpe < Decimal("0")

    def test_zero_volatility(self):
        """변동성이 0인 경우"""
        sharpe = calculate_sharpe_ratio(
            cagr=Decimal("15"),
            volatility=Decimal("0")
        )
        assert sharpe is None

    def test_none_volatility(self):
        """변동성이 None인 경우"""
        sharpe = calculate_sharpe_ratio(
            cagr=Decimal("15"),
            volatility=None
        )
        assert sharpe is None


class TestCalculateSortinoRatio:
    """Sortino Ratio 계산 테스트"""

    def test_with_negative_returns(self):
        """음의 수익률이 있는 경우"""
        daily_returns = [0.01, -0.02, 0.015, -0.01, 0.02, -0.005]
        sortino = calculate_sortino_ratio(
            cagr=Decimal("10"),
            daily_returns=daily_returns
        )
        assert sortino is not None

    def test_no_negative_returns(self):
        """음의 수익률이 없는 경우"""
        daily_returns = [0.01, 0.02, 0.015, 0.01, 0.02, 0.005]
        sortino = calculate_sortino_ratio(
            cagr=Decimal("10"),
            daily_returns=daily_returns
        )
        assert sortino is None

    def test_empty_returns(self):
        """빈 수익률 리스트"""
        sortino = calculate_sortino_ratio(
            cagr=Decimal("10"),
            daily_returns=[]
        )
        assert sortino is None


class TestCalculateDailyReturns:
    """일간 수익률 계산 테스트"""

    def test_simple_returns(self):
        """단순 수익률 계산"""
        equity_values = [
            Decimal("100"),
            Decimal("110"),  # +10%
            Decimal("99"),   # -10%
        ]
        returns = calculate_daily_returns(equity_values)
        assert len(returns) == 2
        assert abs(returns[0] - 0.10) < 0.001
        assert abs(returns[1] - (-0.10)) < 0.001

    def test_empty_list(self):
        """빈 리스트"""
        returns = calculate_daily_returns([])
        assert returns == []

    def test_single_value(self):
        """단일 값"""
        returns = calculate_daily_returns([Decimal("100")])
        assert returns == []


class TestCalculatePerformanceMetrics:
    """종합 성과 지표 계산 테스트"""

    def test_full_metrics(self):
        """전체 성과 지표 계산"""
        equity_values = [
            Decimal("1000000"),
            Decimal("1050000"),
            Decimal("1020000"),
            Decimal("1100000"),
            Decimal("1080000"),
            Decimal("1150000"),
        ]

        metrics = calculate_performance_metrics(
            initial_capital=Decimal("1000000"),
            final_value=Decimal("1150000"),
            start_date=date(2023, 1, 1),
            end_date=date(2023, 12, 31),
            equity_values=equity_values
        )

        assert isinstance(metrics, PerformanceMetrics)
        assert metrics.total_return == Decimal("150000")
        assert metrics.total_return_pct == Decimal("15.00")
        assert metrics.cagr > Decimal("0")
        assert metrics.mdd < Decimal("0")


class TestCalculateTradeMetrics:
    """거래 통계 지표 계산 테스트"""

    def test_winning_trades(self):
        """수익 거래만 있는 경우"""
        trades = [
            {"symbol": "A", "trade_type": "buy", "trade_date": date(2023, 1, 1),
             "price": 100, "quantity": 10, "commission": 0},
            {"symbol": "A", "trade_type": "sell", "trade_date": date(2023, 1, 10),
             "price": 110, "quantity": 10, "commission": 0},
        ]

        metrics = calculate_trade_metrics(trades)

        assert metrics.total_trades == 1
        assert metrics.winning_trades == 1
        assert metrics.losing_trades == 0
        assert metrics.win_rate == Decimal("100.00")
        assert metrics.avg_win == Decimal("100.00")  # (110-100)*10

    def test_losing_trades(self):
        """손실 거래만 있는 경우"""
        trades = [
            {"symbol": "A", "trade_type": "buy", "trade_date": date(2023, 1, 1),
             "price": 100, "quantity": 10, "commission": 0},
            {"symbol": "A", "trade_type": "sell", "trade_date": date(2023, 1, 10),
             "price": 90, "quantity": 10, "commission": 0},
        ]

        metrics = calculate_trade_metrics(trades)

        assert metrics.total_trades == 1
        assert metrics.winning_trades == 0
        assert metrics.losing_trades == 1
        assert metrics.win_rate == Decimal("0.00")

    def test_mixed_trades(self):
        """혼합 거래"""
        trades = [
            # 수익 거래
            {"symbol": "A", "trade_type": "buy", "trade_date": date(2023, 1, 1),
             "price": 100, "quantity": 10, "commission": 0},
            {"symbol": "A", "trade_type": "sell", "trade_date": date(2023, 1, 10),
             "price": 120, "quantity": 10, "commission": 0},
            # 손실 거래
            {"symbol": "B", "trade_type": "buy", "trade_date": date(2023, 2, 1),
             "price": 100, "quantity": 10, "commission": 0},
            {"symbol": "B", "trade_type": "sell", "trade_date": date(2023, 2, 10),
             "price": 90, "quantity": 10, "commission": 0},
        ]

        metrics = calculate_trade_metrics(trades)

        assert metrics.total_trades == 2
        assert metrics.winning_trades == 1
        assert metrics.losing_trades == 1
        assert metrics.win_rate == Decimal("50.00")
        assert metrics.profit_factor == Decimal("2.00")  # 200 / 100

    def test_holding_days(self):
        """보유 기간 계산"""
        trades = [
            {"symbol": "A", "trade_type": "buy", "trade_date": date(2023, 1, 1),
             "price": 100, "quantity": 10, "commission": 0},
            {"symbol": "A", "trade_type": "sell", "trade_date": date(2023, 1, 11),
             "price": 110, "quantity": 10, "commission": 0},
        ]

        metrics = calculate_trade_metrics(trades)
        assert metrics.avg_holding_days == Decimal("10.0")

    def test_empty_trades(self):
        """거래가 없는 경우"""
        metrics = calculate_trade_metrics([])

        assert isinstance(metrics, TradeMetrics)
        assert metrics.total_trades == 0
        assert metrics.win_rate is None

    def test_with_commission(self):
        """수수료 포함"""
        trades = [
            {"symbol": "A", "trade_type": "buy", "trade_date": date(2023, 1, 1),
             "price": 100, "quantity": 10, "commission": 10},
            {"symbol": "A", "trade_type": "sell", "trade_date": date(2023, 1, 10),
             "price": 110, "quantity": 10, "commission": 10},
        ]

        metrics = calculate_trade_metrics(trades)

        # PnL = (110-100)*10 - 10 - 10 = 80
        assert metrics.avg_win == Decimal("80.00")
