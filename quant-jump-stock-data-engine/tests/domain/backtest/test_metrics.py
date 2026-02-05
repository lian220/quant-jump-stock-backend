"""
Performance Metrics Calculator 테스트

CAGR, MDD, Sharpe Ratio, Profit Factor, Expectancy, Kelly Percentage 검증
"""

import pytest
from datetime import date, timedelta
from decimal import Decimal

from src.domain.backtest.metrics import (
    MetricsCalculator,
    PerformanceMetrics,
    Trade,
    TradeAnalysis,
)


# Test Fixtures
@pytest.fixture
def sample_winning_trades():
    """수익 거래 샘플"""
    return [
        Trade(
            symbol="005930",
            entry_date=date(2024, 1, 2),
            exit_date=date(2024, 1, 10),
            entry_price=Decimal("10000"),
            exit_price=Decimal("11000"),
            quantity=10,
            pnl=Decimal("10000"),
            pnl_pct=Decimal("10"),
            exit_reason="take_profit"
        ),
        Trade(
            symbol="000660",
            entry_date=date(2024, 1, 15),
            exit_date=date(2024, 1, 25),
            entry_price=Decimal("5000"),
            exit_price=Decimal("5500"),
            quantity=20,
            pnl=Decimal("10000"),
            pnl_pct=Decimal("10"),
            exit_reason="signal"
        ),
    ]


@pytest.fixture
def sample_losing_trades():
    """손실 거래 샘플"""
    return [
        Trade(
            symbol="035720",
            entry_date=date(2024, 2, 1),
            exit_date=date(2024, 2, 5),
            entry_price=Decimal("8000"),
            exit_price=Decimal("7600"),
            quantity=15,
            pnl=Decimal("-6000"),
            pnl_pct=Decimal("-5"),
            exit_reason="stop_loss"
        ),
    ]


@pytest.fixture
def sample_mixed_trades(sample_winning_trades, sample_losing_trades):
    """혼합 거래 샘플 (2승 1패)"""
    return sample_winning_trades + sample_losing_trades


@pytest.fixture
def sample_equity_curve():
    """자산 곡선 샘플"""
    return [
        Decimal("1000000"),  # 시작
        Decimal("1010000"),
        Decimal("1005000"),
        Decimal("1020000"),
        Decimal("1015000"),
        Decimal("1030000"),
        Decimal("1025000"),
        Decimal("1040000"),
        Decimal("1050000"),
        Decimal("1100000"),  # 종료: +10%
    ]


class TestTrade:
    """Trade 모델 테스트"""

    def test_holding_days(self):
        """보유 기간 계산"""
        trade = Trade(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            exit_date=date(2024, 1, 11),
            entry_price=Decimal("10000"),
            exit_price=Decimal("11000"),
            quantity=10,
            pnl=Decimal("10000"),
            pnl_pct=Decimal("10")
        )

        assert trade.holding_days == 10

    def test_is_winner(self):
        """수익 거래 판별"""
        winner = Trade(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            exit_date=date(2024, 1, 10),
            entry_price=Decimal("10000"),
            exit_price=Decimal("11000"),
            quantity=10,
            pnl=Decimal("10000"),
            pnl_pct=Decimal("10")
        )

        loser = Trade(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            exit_date=date(2024, 1, 10),
            entry_price=Decimal("10000"),
            exit_price=Decimal("9000"),
            quantity=10,
            pnl=Decimal("-10000"),
            pnl_pct=Decimal("-10")
        )

        assert winner.is_winner is True
        assert winner.is_loser is False
        assert loser.is_winner is False
        assert loser.is_loser is True


class TestTradeAnalysis:
    """거래 분석 테스트"""

    def test_analyze_mixed_trades(self, sample_mixed_trades):
        """혼합 거래 분석"""
        calculator = MetricsCalculator()
        analysis = calculator._analyze_trades(sample_mixed_trades)

        assert analysis.total_trades == 3
        assert analysis.winning_trades == 2
        assert analysis.losing_trades == 1
        assert analysis.stop_loss_count == 1
        assert analysis.take_profit_count == 1
        assert analysis.signal_exit_count == 1

    def test_analyze_empty_trades(self):
        """빈 거래 분석"""
        calculator = MetricsCalculator()
        analysis = calculator._analyze_trades([])

        assert analysis.total_trades == 0
        assert analysis.winning_trades == 0
        assert analysis.losing_trades == 0


class TestWinRate:
    """승률 테스트"""

    def test_win_rate_calculation(self, sample_mixed_trades):
        """승률 계산 (2승 1패 = 66.67%)"""
        calculator = MetricsCalculator()
        win_rate = calculator._calculate_win_rate(sample_mixed_trades)

        # 2/3 = 66.67%
        assert win_rate == Decimal("200") / Decimal("3")

    def test_win_rate_all_winners(self, sample_winning_trades):
        """모두 수익 = 100%"""
        calculator = MetricsCalculator()
        win_rate = calculator._calculate_win_rate(sample_winning_trades)

        assert win_rate == Decimal("100")

    def test_win_rate_all_losers(self, sample_losing_trades):
        """모두 손실 = 0%"""
        calculator = MetricsCalculator()
        win_rate = calculator._calculate_win_rate(sample_losing_trades)

        assert win_rate == Decimal("0")

    def test_win_rate_empty(self):
        """빈 거래 = 0%"""
        calculator = MetricsCalculator()
        win_rate = calculator._calculate_win_rate([])

        assert win_rate == Decimal("0")


class TestProfitFactor:
    """Profit Factor 테스트"""

    def test_profit_factor_positive(self, sample_mixed_trades):
        """수익 > 손실 시 Profit Factor > 1"""
        calculator = MetricsCalculator()
        pf = calculator._calculate_profit_factor(sample_mixed_trades)

        # 총 수익: 20000, 총 손실: 6000
        # PF = 20000 / 6000 = 3.33...
        expected = Decimal("20000") / Decimal("6000")
        assert pf == expected

    def test_profit_factor_no_losses(self, sample_winning_trades):
        """손실 없으면 999 반환"""
        calculator = MetricsCalculator()
        pf = calculator._calculate_profit_factor(sample_winning_trades)

        assert pf == Decimal("999")

    def test_profit_factor_no_wins(self, sample_losing_trades):
        """수익 없으면 0 반환"""
        calculator = MetricsCalculator()
        pf = calculator._calculate_profit_factor(sample_losing_trades)

        assert pf == Decimal("0")


class TestExpectancy:
    """Expectancy (기댓값) 테스트"""

    def test_expectancy_positive(self):
        """양의 기댓값"""
        calculator = MetricsCalculator()

        # 60% 승률, 평균 수익 10%, 평균 손실 5%
        # E = 0.6 * 10 - 0.4 * 5 = 6 - 2 = 4%
        expectancy = calculator._calculate_expectancy(
            win_rate=Decimal("60"),
            avg_win=Decimal("10"),
            avg_loss=Decimal("5")
        )

        assert expectancy == Decimal("4")

    def test_expectancy_negative(self):
        """음의 기댓값 (나쁜 전략)"""
        calculator = MetricsCalculator()

        # 40% 승률, 평균 수익 5%, 평균 손실 10%
        # E = 0.4 * 5 - 0.6 * 10 = 2 - 6 = -4%
        expectancy = calculator._calculate_expectancy(
            win_rate=Decimal("40"),
            avg_win=Decimal("5"),
            avg_loss=Decimal("10")
        )

        assert expectancy == Decimal("-4")


class TestKellyPercentage:
    """Kelly Percentage 테스트"""

    def test_kelly_positive(self):
        """양의 Kelly (투자 권장)"""
        calculator = MetricsCalculator()

        # 60% 승률, 평균 수익 10%, 평균 손실 5%
        # Kelly = (0.6 * 10 - 0.4 * 5) / 10 = 4 / 10 = 0.4 = 40%
        kelly = calculator._calculate_kelly_percentage(
            win_rate=Decimal("60"),
            avg_win=Decimal("10"),
            avg_loss=Decimal("5")
        )

        assert kelly == Decimal("40")

    def test_kelly_negative(self):
        """음의 Kelly (투자 비권장)"""
        calculator = MetricsCalculator()

        # 40% 승률, 평균 수익 5%, 평균 손실 10%
        # Kelly = (0.4 * 5 - 0.6 * 10) / 5 = -4 / 5 = -0.8 = -80%
        kelly = calculator._calculate_kelly_percentage(
            win_rate=Decimal("40"),
            avg_win=Decimal("5"),
            avg_loss=Decimal("10")
        )

        assert kelly == Decimal("-80")

    def test_kelly_zero_avg_win(self):
        """평균 수익 0이면 Kelly 0"""
        calculator = MetricsCalculator()

        kelly = calculator._calculate_kelly_percentage(
            win_rate=Decimal("50"),
            avg_win=Decimal("0"),
            avg_loss=Decimal("5")
        )

        assert kelly == Decimal("0")


class TestMDD:
    """MDD (최대 낙폭) 테스트"""

    def test_mdd_calculation(self):
        """MDD 계산"""
        calculator = MetricsCalculator()

        # 고점 1200 → 저점 1000 = -16.67%
        equity_curve = [
            Decimal("1000"),
            Decimal("1100"),
            Decimal("1200"),  # 고점
            Decimal("1100"),
            Decimal("1000"),  # 저점 (고점 대비 -16.67%)
            Decimal("1150"),
        ]

        mdd = calculator._calculate_mdd(equity_curve)

        # MDD = (1200 - 1000) / 1200 = 16.67%
        expected = (Decimal("1200") - Decimal("1000")) / Decimal("1200") * 100
        assert abs(mdd - expected) < Decimal("0.01")

    def test_mdd_no_drawdown(self):
        """낙폭 없음 (계속 상승)"""
        calculator = MetricsCalculator()

        equity_curve = [
            Decimal("1000"),
            Decimal("1100"),
            Decimal("1200"),
            Decimal("1300"),
        ]

        mdd = calculator._calculate_mdd(equity_curve)

        assert mdd == Decimal("0")


class TestCAGR:
    """CAGR (연평균 복리 수익률) 테스트"""

    def test_cagr_one_year(self):
        """1년 10% 수익 → CAGR 10%"""
        calculator = MetricsCalculator()

        equity_curve = [Decimal("1000000"), Decimal("1100000")]

        cagr = calculator._calculate_cagr(
            equity_curve=equity_curve,
            initial_capital=Decimal("1000000"),
            start_date=date(2024, 1, 1),
            end_date=date(2025, 1, 1)
        )

        # 1년간 10% = CAGR 10%
        assert abs(cagr - Decimal("10")) < Decimal("0.5")

    def test_cagr_empty_curve(self):
        """빈 자산 곡선"""
        calculator = MetricsCalculator()

        cagr = calculator._calculate_cagr(
            equity_curve=[],
            initial_capital=Decimal("1000000"),
            start_date=date(2024, 1, 1),
            end_date=date(2025, 1, 1)
        )

        assert cagr == Decimal("0")


class TestSharpeRatio:
    """Sharpe Ratio 테스트"""

    def test_sharpe_positive(self, sample_equity_curve):
        """양의 Sharpe Ratio"""
        calculator = MetricsCalculator(risk_free_rate=0.03)

        sharpe = calculator._calculate_sharpe_ratio(
            equity_curve=sample_equity_curve,
            initial_capital=Decimal("1000000")
        )

        # 수익이 있으므로 양수여야 함
        assert sharpe > Decimal("0")

    def test_sharpe_insufficient_data(self):
        """데이터 부족"""
        calculator = MetricsCalculator()

        sharpe = calculator._calculate_sharpe_ratio(
            equity_curve=[Decimal("1000000")],
            initial_capital=Decimal("1000000")
        )

        assert sharpe == Decimal("0")


class TestAllMetrics:
    """전체 지표 통합 테스트"""

    def test_calculate_all_metrics(self, sample_mixed_trades, sample_equity_curve):
        """전체 지표 계산"""
        calculator = MetricsCalculator()

        metrics = calculator.calculate_all_metrics(
            trades=sample_mixed_trades,
            equity_curve=sample_equity_curve,
            initial_capital=Decimal("1000000"),
            start_date=date(2024, 1, 1),
            end_date=date(2024, 3, 1)
        )

        # 기본 지표 검증
        assert isinstance(metrics, PerformanceMetrics)
        assert metrics.total_return == Decimal("10")  # 10% 수익
        assert metrics.win_rate > Decimal("60")  # 2승 1패 = 66.67%
        assert metrics.profit_factor > Decimal("1")  # 수익 > 손실

        # 추가 지표 검증
        assert metrics.expectancy > Decimal("0")  # 양의 기댓값
        assert metrics.kelly_percentage > Decimal("0")  # 양의 Kelly

        # 거래 분석 검증
        assert metrics.trade_analysis.total_trades == 3
        assert metrics.trade_analysis.winning_trades == 2
        assert metrics.trade_analysis.losing_trades == 1


class TestFromTradeDicts:
    """딕셔너리 변환 테스트"""

    def test_from_trade_dicts(self):
        """딕셔너리에서 Trade 객체 생성"""
        trade_dicts = [
            {
                "symbol": "005930",
                "entry_date": date(2024, 1, 1),
                "exit_date": date(2024, 1, 10),
                "entry_price": 10000,
                "exit_price": 11000,
                "quantity": 10,
                "pnl": 10000,
                "pnl_pct": 10,
                "exit_reason": "take_profit"
            }
        ]

        trades = MetricsCalculator.from_trade_dicts(trade_dicts)

        assert len(trades) == 1
        assert trades[0].symbol == "005930"
        assert trades[0].pnl == Decimal("10000")
        assert trades[0].exit_reason == "take_profit"


class TestJiraExamples:
    """Jira 티켓 예시 검증"""

    def test_jira_profit_factor_example(self):
        """Jira Profit Factor 예시"""
        calculator = MetricsCalculator()

        # 총 수익 50000, 총 손실 20000
        trades = [
            Trade("A", date(2024, 1, 1), date(2024, 1, 10), Decimal("100"), Decimal("150"),
                  10, Decimal("50000"), Decimal("50")),
            Trade("B", date(2024, 1, 15), date(2024, 1, 20), Decimal("100"), Decimal("80"),
                  10, Decimal("-20000"), Decimal("-20")),
        ]

        pf = calculator._calculate_profit_factor(trades)

        # PF = 50000 / 20000 = 2.5
        assert pf == Decimal("2.5")

    def test_jira_expectancy_example(self):
        """Jira Expectancy 예시"""
        calculator = MetricsCalculator()

        # 승률 60%, 평균 수익 5%, 평균 손실 3%
        # E = (0.6 * 5) - (0.4 * 3) = 3 - 1.2 = 1.8%
        expectancy = calculator._calculate_expectancy(
            win_rate=Decimal("60"),
            avg_win=Decimal("5"),
            avg_loss=Decimal("3")
        )

        expected = Decimal("1.8")
        assert expectancy == expected

    def test_jira_kelly_example(self):
        """Jira Kelly Percentage 예시"""
        calculator = MetricsCalculator()

        # 승률 60%, 평균 수익 5%, 평균 손실 3%
        # Kelly = (0.6 * 5 - 0.4 * 3) / 5 = 1.8 / 5 = 0.36 = 36%
        kelly = calculator._calculate_kelly_percentage(
            win_rate=Decimal("60"),
            avg_win=Decimal("5"),
            avg_loss=Decimal("3")
        )

        expected = Decimal("36")
        assert kelly == expected


class TestRepr:
    """문자열 표현 테스트"""

    def test_repr(self):
        """__repr__ 출력 확인"""
        calculator = MetricsCalculator(
            risk_free_rate=0.03,
            trading_days_per_year=252
        )

        repr_str = repr(calculator)

        assert "3.0%" in repr_str
        assert "252" in repr_str
