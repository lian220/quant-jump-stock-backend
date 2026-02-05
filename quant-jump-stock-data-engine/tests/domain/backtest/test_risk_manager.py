"""
Risk Manager 테스트

Stop Loss, Take Profit, Trailing Stop 로직 검증
"""

import pytest
from datetime import date
from decimal import Decimal

from src.domain.backtest.models import Portfolio, Position, ExitReason
from src.domain.backtest.risk_manager import RiskManager, RiskExitType


class TestRiskManagerStopLoss:
    """Stop Loss 테스트"""

    def test_stop_loss_triggered(self):
        """손절 조건 충족 시 청산 신호 발생"""
        # Given: 5% 손절 설정
        risk_manager = RiskManager(stop_loss_pct=0.05, take_profit_pct=0.15)

        # 진입가 10000원, 현재가 9400원 (-6%)
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        portfolio.positions["005930"] = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("9400")
        )

        prices = {"005930": Decimal("9400")}

        # When
        exits = risk_manager.check_risk_exits(portfolio, prices, date(2024, 1, 5))

        # Then
        assert len(exits) == 1
        assert exits[0].symbol == "005930"
        assert exits[0].exit_type == RiskExitType.STOP_LOSS
        assert exits[0].reason == ExitReason.STOP_LOSS
        assert exits[0].pnl_pct < Decimal("-5")

    def test_stop_loss_not_triggered(self):
        """손절 조건 미충족 시 청산 신호 없음"""
        # Given: 5% 손절 설정
        risk_manager = RiskManager(stop_loss_pct=0.05, take_profit_pct=0.15)

        # 진입가 10000원, 현재가 9600원 (-4%)
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        portfolio.positions["005930"] = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("9600")
        )

        prices = {"005930": Decimal("9600")}

        # When
        exits = risk_manager.check_risk_exits(portfolio, prices, date(2024, 1, 5))

        # Then
        assert len(exits) == 0

    def test_stop_loss_exact_threshold(self):
        """정확히 손절 비율에 도달 시 청산"""
        # Given: 5% 손절 설정
        risk_manager = RiskManager(stop_loss_pct=0.05, take_profit_pct=0.15)

        # 진입가 10000원, 현재가 9500원 (-5%)
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        portfolio.positions["005930"] = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("9500")
        )

        prices = {"005930": Decimal("9500")}

        # When
        exits = risk_manager.check_risk_exits(portfolio, prices, date(2024, 1, 5))

        # Then
        assert len(exits) == 1
        assert exits[0].exit_type == RiskExitType.STOP_LOSS


class TestRiskManagerTakeProfit:
    """Take Profit 테스트"""

    def test_take_profit_triggered(self):
        """익절 조건 충족 시 청산 신호 발생"""
        # Given: 15% 익절 설정
        risk_manager = RiskManager(stop_loss_pct=0.05, take_profit_pct=0.15)

        # 진입가 10000원, 현재가 11600원 (+16%)
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        portfolio.positions["005930"] = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("11600")
        )

        prices = {"005930": Decimal("11600")}

        # When
        exits = risk_manager.check_risk_exits(portfolio, prices, date(2024, 1, 5))

        # Then
        assert len(exits) == 1
        assert exits[0].symbol == "005930"
        assert exits[0].exit_type == RiskExitType.TAKE_PROFIT
        assert exits[0].reason == ExitReason.TAKE_PROFIT
        assert exits[0].pnl_pct > Decimal("15")

    def test_take_profit_not_triggered(self):
        """익절 조건 미충족 시 청산 신호 없음"""
        # Given: 15% 익절 설정
        risk_manager = RiskManager(stop_loss_pct=0.05, take_profit_pct=0.15)

        # 진입가 10000원, 현재가 11400원 (+14%)
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        portfolio.positions["005930"] = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("11400")
        )

        prices = {"005930": Decimal("11400")}

        # When
        exits = risk_manager.check_risk_exits(portfolio, prices, date(2024, 1, 5))

        # Then
        assert len(exits) == 0


class TestRiskManagerTrailingStop:
    """Trailing Stop 테스트"""

    def test_trailing_stop_triggered(self):
        """트레일링 스탑 조건 충족 시 청산 신호 발생"""
        # Given: 5% 트레일링 스탑 설정
        risk_manager = RiskManager(
            stop_loss_pct=0.05,
            take_profit_pct=0.15,
            trailing_stop_enabled=True,
            trailing_stop_pct=0.05
        )

        # 진입가 10000원, 고점 12000원, 현재가 11300원 (고점 대비 -5.83%)
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        position = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("11300")
        )
        position.highest_price = Decimal("12000")  # 수동 설정
        portfolio.positions["005930"] = position

        prices = {"005930": Decimal("11300")}

        # When
        exits = risk_manager.check_risk_exits(portfolio, prices, date(2024, 1, 10))

        # Then
        assert len(exits) == 1
        assert exits[0].exit_type == RiskExitType.TRAILING_STOP
        assert exits[0].reason == ExitReason.TRAILING_STOP

    def test_trailing_stop_not_triggered_in_loss(self):
        """손실 구간에서는 트레일링 스탑 미적용"""
        # Given: 5% 트레일링 스탑 설정
        risk_manager = RiskManager(
            stop_loss_pct=0.10,  # 넓은 손절로 설정
            take_profit_pct=0.15,
            trailing_stop_enabled=True,
            trailing_stop_pct=0.05
        )

        # 진입가 10000원, 현재가 9800원 (손실 구간)
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        position = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("9800")
        )
        portfolio.positions["005930"] = position

        prices = {"005930": Decimal("9800")}

        # When
        exits = risk_manager.check_risk_exits(portfolio, prices, date(2024, 1, 5))

        # Then: 손실 구간이므로 트레일링 스탑 미발동 (손절도 미발동)
        assert len(exits) == 0

    def test_trailing_stop_disabled(self):
        """트레일링 스탑 비활성화 시 미적용"""
        # Given: 트레일링 스탑 비활성화
        risk_manager = RiskManager(
            stop_loss_pct=0.05,
            take_profit_pct=0.20,
            trailing_stop_enabled=False,
            trailing_stop_pct=0.05
        )

        # 고점 대비 10% 하락했지만 트레일링 스탑 비활성화
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        position = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("10800")  # 진입 대비 +8%
        )
        position.highest_price = Decimal("12000")  # 고점 대비 -10%
        portfolio.positions["005930"] = position

        prices = {"005930": Decimal("10800")}

        # When
        exits = risk_manager.check_risk_exits(portfolio, prices, date(2024, 1, 10))

        # Then: 트레일링 스탑 비활성화이므로 청산 없음
        assert len(exits) == 0

    def test_trailing_stop_with_trigger_profit_activated(self):
        """트리거 수익률 이상 달성 시 트레일링 스탑 활성화"""
        # Given: 5% 트리거, 3% 트레일링 스탑 설정
        risk_manager = RiskManager(
            stop_loss_pct=0.05,
            take_profit_pct=0.20,
            trailing_stop_enabled=True,
            trailing_stop_pct=0.03,
            trailing_trigger_profit_pct=0.05  # 5% 수익 달성 시 활성화
        )

        # 진입가 10000원, 고점 11000원(+10%), 현재가 10670원(고점 대비 -3%, 진입 대비 +6.7%)
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        position = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("10670")
        )
        position.highest_price = Decimal("11000")
        portfolio.positions["005930"] = position

        prices = {"005930": Decimal("10670")}

        # When
        exits = risk_manager.check_risk_exits(portfolio, prices, date(2024, 1, 10))

        # Then: 수익률 6.7% >= 트리거 5% 이므로 트레일링 스탑 발동
        assert len(exits) == 1
        assert exits[0].exit_type == RiskExitType.TRAILING_STOP
        assert exits[0].reason == ExitReason.TRAILING_STOP

    def test_trailing_stop_with_trigger_profit_not_activated(self):
        """트리거 수익률 미달 시 트레일링 스탑 미활성화"""
        # Given: 10% 트리거, 3% 트레일링 스탑 설정
        risk_manager = RiskManager(
            stop_loss_pct=0.05,
            take_profit_pct=0.20,
            trailing_stop_enabled=True,
            trailing_stop_pct=0.03,
            trailing_trigger_profit_pct=0.10  # 10% 수익 달성 시 활성화
        )

        # 진입가 10000원, 고점 10500원(+5%), 현재가 10185원(고점 대비 -3%, 진입 대비 +1.85%)
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        position = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("10185")
        )
        position.highest_price = Decimal("10500")
        portfolio.positions["005930"] = position

        prices = {"005930": Decimal("10185")}

        # When
        exits = risk_manager.check_risk_exits(portfolio, prices, date(2024, 1, 10))

        # Then: 수익률 1.85% < 트리거 10% 이므로 트레일링 스탑 미발동
        assert len(exits) == 0

    def test_trailing_stop_trigger_profit_exact_threshold(self):
        """정확히 트리거 수익률 도달 시 트레일링 스탑 활성화"""
        # Given: 5% 트리거, 5% 트레일링 스탑 설정
        risk_manager = RiskManager(
            stop_loss_pct=0.05,
            take_profit_pct=0.20,
            trailing_stop_enabled=True,
            trailing_stop_pct=0.05,
            trailing_trigger_profit_pct=0.05  # 5% 수익 달성 시 활성화
        )

        # 진입가 10000원, 고점 11000원(+10%), 현재가 10500원(진입 대비 +5%, 고점 대비 -4.5%)
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        position = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("10500")
        )
        position.highest_price = Decimal("11000")
        portfolio.positions["005930"] = position

        prices = {"005930": Decimal("10500")}

        # When
        exits = risk_manager.check_risk_exits(portfolio, prices, date(2024, 1, 10))

        # Then: 수익률 5% >= 트리거 5%, 고점 대비 -4.5% < -5% 이므로 미발동
        assert len(exits) == 0

    def test_trailing_stop_jira_example(self):
        """Jira 티켓 예시 시나리오 테스트

        예시:
        - 진입가: 10,000원
        - 최고가: 11,000원 (10% 수익) → 트레일링 활성화
        - 현재가: 10,670원 → 최고가 대비 -3% → 자동 매도
        """
        # Given: 5% 트리거, 3% 트레일링 스탑 (Jira 예시)
        risk_manager = RiskManager(
            stop_loss_pct=0.05,
            take_profit_pct=0.20,
            trailing_stop_enabled=True,
            trailing_stop_pct=0.03,  # 최고점 대비 -3%
            trailing_trigger_profit_pct=0.05  # 5% 수익 달성 시 활성화
        )

        # Jira 예시 시나리오
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        position = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),  # 진입가 10,000원
            quantity=10,
            current_price=Decimal("10670")  # 현재가 10,670원 (진입 대비 +6.7%)
        )
        position.highest_price = Decimal("11000")  # 최고가 11,000원 (10% 수익)
        portfolio.positions["005930"] = position

        prices = {"005930": Decimal("10670")}

        # When
        exits = risk_manager.check_risk_exits(portfolio, prices, date(2024, 1, 10))

        # Then: 트레일링 스탑 발동
        # - 수익률 6.7% >= 트리거 5% (활성화됨)
        # - 고점 대비 하락률: (10670/11000 - 1) = -3% <= -3% (발동)
        assert len(exits) == 1
        assert exits[0].exit_type == RiskExitType.TRAILING_STOP
        assert exits[0].symbol == "005930"


class TestRiskManagerMultiplePositions:
    """다중 포지션 테스트"""

    def test_multiple_positions_partial_exit(self):
        """여러 포지션 중 일부만 청산 조건 충족"""
        # Given
        risk_manager = RiskManager(stop_loss_pct=0.05, take_profit_pct=0.15)

        portfolio = Portfolio(initial_capital=Decimal("1000000"))

        # 손절 조건 충족
        portfolio.positions["005930"] = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("9400")  # -6%
        )

        # 익절 조건 충족
        portfolio.positions["000660"] = Position(
            symbol="000660",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("5000"),
            quantity=20,
            current_price=Decimal("5800")  # +16%
        )

        # 조건 미충족
        portfolio.positions["035720"] = Position(
            symbol="035720",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("8000"),
            quantity=15,
            current_price=Decimal("8400")  # +5%
        )

        prices = {
            "005930": Decimal("9400"),
            "000660": Decimal("5800"),
            "035720": Decimal("8400")
        }

        # When
        exits = risk_manager.check_risk_exits(portfolio, prices, date(2024, 1, 5))

        # Then
        assert len(exits) == 2

        symbols = {e.symbol for e in exits}
        assert "005930" in symbols  # 손절
        assert "000660" in symbols  # 익절
        assert "035720" not in symbols  # 보유 유지


class TestRiskManagerHelpers:
    """헬퍼 메서드 테스트"""

    def test_get_stop_loss_price(self):
        """손절가 계산"""
        risk_manager = RiskManager(stop_loss_pct=0.05, take_profit_pct=0.15)

        stop_price = risk_manager.get_stop_loss_price(Decimal("10000"))

        assert stop_price == Decimal("9500")

    def test_get_take_profit_price(self):
        """익절가 계산"""
        risk_manager = RiskManager(stop_loss_pct=0.05, take_profit_pct=0.15)

        take_profit_price = risk_manager.get_take_profit_price(Decimal("10000"))

        assert take_profit_price == Decimal("11500")

    def test_get_trailing_stop_price(self):
        """트레일링 스탑 가격 계산"""
        risk_manager = RiskManager(
            stop_loss_pct=0.05,
            take_profit_pct=0.15,
            trailing_stop_pct=0.05
        )

        trailing_price = risk_manager.get_trailing_stop_price(Decimal("12000"))

        assert trailing_price == Decimal("11400")


class TestRiskManagerFromModel:
    """RiskManagement 모델로부터 생성 테스트"""

    def test_from_risk_management(self):
        """RiskManagement 객체로부터 RiskManager 생성"""
        from src.domain.strategy.models import RiskManagement

        # Given
        risk_config = RiskManagement(
            stop_loss_pct=0.08,
            take_profit_pct=0.20,
            trailing_stop=True,
            trailing_stop_pct=0.07,
            trailing_trigger_profit_pct=0.05,
            max_drawdown_pct=0.25
        )

        # When
        risk_manager = RiskManager.from_risk_management(risk_config)

        # Then
        assert risk_manager.stop_loss_pct == Decimal("0.08")
        assert risk_manager.take_profit_pct == Decimal("0.20")
        assert risk_manager.trailing_stop_enabled is True
        assert risk_manager.trailing_stop_pct == Decimal("0.07")
        assert risk_manager.trailing_trigger_profit_pct == Decimal("0.05")
        assert risk_manager.max_drawdown_pct == Decimal("0.25")

    def test_from_risk_management_default_trigger_profit(self):
        """trailing_trigger_profit_pct 미설정 시 기본값 0.0"""
        from src.domain.strategy.models import RiskManagement

        # Given: trailing_trigger_profit_pct 미설정
        risk_config = RiskManagement(
            stop_loss_pct=0.05,
            take_profit_pct=0.15,
            trailing_stop=True,
            trailing_stop_pct=0.05
        )

        # When
        risk_manager = RiskManager.from_risk_management(risk_config)

        # Then: 기본값 0.0
        assert risk_manager.trailing_trigger_profit_pct == Decimal("0.0")


class TestPosition:
    """Position 모델 테스트"""

    def test_position_pnl_calculation(self):
        """손익 계산"""
        position = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("11000")
        )

        assert position.pnl == Decimal("10000")  # (11000-10000) * 10
        assert position.pnl_pct == Decimal("10")  # +10%

    def test_position_drawdown_from_high(self):
        """고점 대비 하락률 계산"""
        position = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10,
            current_price=Decimal("11000")
        )
        position.highest_price = Decimal("12000")

        # 12000 -> 11000 = -8.33%
        expected = ((Decimal("11000") / Decimal("12000")) - 1) * 100
        assert position.drawdown_from_high == expected

    def test_position_update_price(self):
        """가격 업데이트 및 최고/최저가 갱신"""
        position = Position(
            symbol="005930",
            entry_date=date(2024, 1, 1),
            entry_price=Decimal("10000"),
            quantity=10
        )

        # 상승
        position.update_price(Decimal("11000"))
        assert position.highest_price == Decimal("11000")
        assert position.lowest_price == Decimal("10000")

        # 하락
        position.update_price(Decimal("9500"))
        assert position.highest_price == Decimal("11000")
        assert position.lowest_price == Decimal("9500")

        # 신고점
        position.update_price(Decimal("12000"))
        assert position.highest_price == Decimal("12000")


class TestPortfolio:
    """Portfolio 모델 테스트"""

    def test_open_position(self):
        """포지션 오픈"""
        portfolio = Portfolio(initial_capital=Decimal("1000000"))

        trade = portfolio.open_position(
            symbol="005930",
            trade_date=date(2024, 1, 1),
            price=Decimal("10000"),
            quantity=10
        )

        assert trade is not None
        assert "005930" in portfolio.positions
        assert portfolio.cash == Decimal("900000")

    def test_close_position(self):
        """포지션 청산"""
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        portfolio.open_position("005930", date(2024, 1, 1), Decimal("10000"), 10)

        trade = portfolio.close_position(
            symbol="005930",
            trade_date=date(2024, 1, 5),
            price=Decimal("11000"),
            exit_reason=ExitReason.TAKE_PROFIT
        )

        assert trade is not None
        assert trade.realized_pnl == Decimal("10000")  # (11000-10000) * 10
        assert trade.exit_reason == ExitReason.TAKE_PROFIT
        assert "005930" not in portfolio.positions
        assert portfolio.cash == Decimal("1010000")

    def test_total_value(self):
        """총 자산 가치 계산"""
        portfolio = Portfolio(initial_capital=Decimal("1000000"))
        portfolio.open_position("005930", date(2024, 1, 1), Decimal("10000"), 10)

        # 현금 900,000 + 포지션 100,000 = 1,000,000
        assert portfolio.total_value == Decimal("1000000")

        # 가격 상승
        portfolio.positions["005930"].update_price(Decimal("11000"))

        # 현금 900,000 + 포지션 110,000 = 1,010,000
        assert portfolio.total_value == Decimal("1010000")
