"""
Trading Cost Calculator 테스트

Commission, Tax, Slippage 로직 검증
"""

import pytest
from decimal import Decimal

from src.domain.backtest.trading_cost import (
    TradingCostCalculator,
    TradeCostResult,
    PnLResult,
    SlippageConfig,
    TradeDirection,
)


class TestCommission:
    """수수료 계산 테스트"""

    def test_commission_basic(self):
        """기본 수수료 계산"""
        # Given: 0.015% 수수료율
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            slippage_config=SlippageConfig(enabled=False)
        )

        # When: 100만원 거래
        commission = calculator.calculate_commission(Decimal("1000000"))

        # Then: 150원
        assert commission == Decimal("150")

    def test_commission_with_min(self):
        """최소 수수료 적용"""
        # Given: 최소 수수료 1000원
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            min_commission=1000,
            slippage_config=SlippageConfig(enabled=False)
        )

        # When: 1만원 거래 (계산 수수료 1.5원)
        commission = calculator.calculate_commission(Decimal("10000"))

        # Then: 최소 수수료 1000원
        assert commission == Decimal("1000")


class TestTax:
    """증권거래세 테스트"""

    def test_tax_basic(self):
        """기본 세금 계산"""
        # Given: 0.23% 세율
        calculator = TradingCostCalculator(
            tax_rate=0.0023,
            slippage_config=SlippageConfig(enabled=False)
        )

        # When: 100만원 매도
        tax = calculator.calculate_tax(Decimal("1000000"))

        # Then: 2300원
        assert tax == Decimal("2300")


class TestSlippage:
    """슬리피지 테스트"""

    def test_slippage_basic(self):
        """기본 슬리피지 계산"""
        # Given: 0.1% 기본 슬리피지
        config = SlippageConfig(
            base_slippage=0.001,
            volume_impact=0.0,
            volatility_multiplier=1.0
        )

        # When
        slippage = config.calculate_slippage()

        # Then: 0.1%
        assert slippage == Decimal("0.001")

    def test_slippage_with_volatility(self):
        """변동성 기반 슬리피지 계산"""
        # Given: 0.1% 기본 + 0.05% 거래량 영향
        config = SlippageConfig(
            base_slippage=0.001,
            volume_impact=0.0005,
            volatility_multiplier=1.5
        )

        # When
        slippage = config.calculate_slippage()

        # Then: 0.1% + (0.05% * 1.5) = 0.175%
        expected = Decimal("0.001") + (Decimal("0.0005") * Decimal("1.5"))
        assert slippage == expected

    def test_slippage_disabled(self):
        """슬리피지 비활성화"""
        # Given
        config = SlippageConfig(
            base_slippage=0.001,
            enabled=False
        )

        # When
        slippage = config.calculate_slippage()

        # Then: 0%
        assert slippage == Decimal("0")


class TestBuyCost:
    """매수 비용 테스트"""

    def test_buy_without_slippage(self):
        """슬리피지 없는 매수 비용"""
        # Given
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            slippage_config=SlippageConfig(enabled=False)
        )

        # When: 10000원 * 10주
        result = calculator.calculate_buy_cost(
            price=Decimal("10000"),
            quantity=10
        )

        # Then
        assert result.direction == TradeDirection.BUY
        assert result.base_price == Decimal("10000")
        assert result.execution_price == Decimal("10000")  # 슬리피지 없음
        assert result.quantity == 10
        assert result.gross_value == Decimal("100000")
        assert result.commission == Decimal("15")  # 0.015%
        assert result.tax == Decimal("0")  # 매수 시 세금 없음
        assert result.slippage_cost == Decimal("0")
        assert result.total_cost == Decimal("100015")

    def test_buy_with_slippage(self):
        """슬리피지 포함 매수 비용"""
        # Given: 0.1% 슬리피지
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            slippage_config=SlippageConfig(
                base_slippage=0.001,
                volume_impact=0.0,
                enabled=True
            )
        )

        # When: 10000원 * 10주
        result = calculator.calculate_buy_cost(
            price=Decimal("10000"),
            quantity=10
        )

        # Then: 슬리피지로 인해 가격 상승
        assert result.execution_price == Decimal("10010")  # +0.1%
        assert result.gross_value == Decimal("100100")
        assert result.slippage_cost == Decimal("100")  # 슬리피지 비용
        # 총 비용: 100100 + 수수료(15.015)
        assert result.total_cost > Decimal("100100")


class TestSellRevenue:
    """매도 수익 테스트"""

    def test_sell_without_slippage(self):
        """슬리피지 없는 매도 수익"""
        # Given
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            tax_rate=0.0023,
            slippage_config=SlippageConfig(enabled=False)
        )

        # When: 10000원 * 10주
        result = calculator.calculate_sell_revenue(
            price=Decimal("10000"),
            quantity=10
        )

        # Then
        assert result.direction == TradeDirection.SELL
        assert result.base_price == Decimal("10000")
        assert result.execution_price == Decimal("10000")
        assert result.gross_value == Decimal("100000")
        assert result.commission == Decimal("15")  # 0.015%
        assert result.tax == Decimal("230")  # 0.23%
        # 순수익: 100000 - 15 - 230 = 99755
        assert result.total_cost == Decimal("99755")

    def test_sell_with_slippage(self):
        """슬리피지 포함 매도 수익"""
        # Given: 0.1% 슬리피지
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            tax_rate=0.0023,
            slippage_config=SlippageConfig(
                base_slippage=0.001,
                volume_impact=0.0,
                enabled=True
            )
        )

        # When: 10000원 * 10주
        result = calculator.calculate_sell_revenue(
            price=Decimal("10000"),
            quantity=10
        )

        # Then: 슬리피지로 인해 가격 하락
        assert result.execution_price == Decimal("9990")  # -0.1%
        assert result.gross_value == Decimal("99900")
        assert result.slippage_cost == Decimal("100")  # 슬리피지 비용


class TestRoundTripPnL:
    """왕복 거래 손익 테스트"""

    def test_profitable_trade(self):
        """수익 거래"""
        # Given
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            tax_rate=0.0023,
            slippage_config=SlippageConfig(enabled=False)
        )

        # When: 10000원 매수 → 11000원 매도
        result = calculator.calculate_round_trip_pnl(
            buy_price=Decimal("10000"),
            sell_price=Decimal("11000"),
            quantity=10
        )

        # Then
        # 총 손익 (비용 제외): (11000 - 10000) * 10 = 10000
        assert result.gross_pnl == Decimal("10000")
        # 순손익은 비용 때문에 더 낮음
        assert result.net_pnl < result.gross_pnl
        assert result.pnl_pct == Decimal("10")  # 10% 수익

    def test_losing_trade(self):
        """손실 거래"""
        # Given
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            tax_rate=0.0023,
            slippage_config=SlippageConfig(enabled=False)
        )

        # When: 10000원 매수 → 9000원 매도
        result = calculator.calculate_round_trip_pnl(
            buy_price=Decimal("10000"),
            sell_price=Decimal("9000"),
            quantity=10
        )

        # Then
        # 총 손익: (9000 - 10000) * 10 = -10000
        assert result.gross_pnl == Decimal("-10000")
        # 비용까지 더해 순손실은 더 큼
        assert result.net_pnl < result.gross_pnl
        assert result.pnl_pct == Decimal("-10")  # -10% 손실

    def test_breakeven_with_costs(self):
        """비용 고려 시 손익분기점"""
        # Given
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            tax_rate=0.0023,
            slippage_config=SlippageConfig(enabled=False)
        )

        # When: 동일가 매수/매도
        result = calculator.calculate_round_trip_pnl(
            buy_price=Decimal("10000"),
            sell_price=Decimal("10000"),
            quantity=10
        )

        # Then: 수수료 + 세금으로 인해 순손실
        assert result.gross_pnl == Decimal("0")
        assert result.net_pnl < Decimal("0")  # 비용으로 인한 손실


class TestBreakevenPrice:
    """손익분기점 가격 테스트"""

    def test_breakeven_calculation(self):
        """손익분기점 가격 계산"""
        # Given
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            tax_rate=0.0023,
            slippage_config=SlippageConfig(enabled=False)
        )

        # When: 10000원 10주 매수
        breakeven = calculator.estimate_breakeven_price(
            buy_price=Decimal("10000"),
            quantity=10
        )

        # Then: 손익분기점은 매수가보다 높아야 함
        assert breakeven > Decimal("10000")

        # 실제로 손익분기점에서 거래하면 손익이 거의 0
        result = calculator.calculate_round_trip_pnl(
            buy_price=Decimal("10000"),
            sell_price=breakeven,
            quantity=10
        )
        # 근사값이므로 작은 오차 허용
        assert abs(result.net_pnl) < Decimal("10")


class TestFromConfig:
    """설정 로드 테스트"""

    def test_from_config(self):
        """설정 딕셔너리로부터 생성"""
        # Given
        config = {
            "commission_rate": 0.0001,
            "tax_rate": 0.002,
            "slippage": {
                "base_slippage": 0.002,
                "volume_impact": 0.001,
                "volatility_multiplier": 2.0,
                "enabled": True
            },
            "min_commission": 500
        }

        # When
        calculator = TradingCostCalculator.from_config(config)

        # Then
        assert calculator.commission_rate == Decimal("0.0001")
        assert calculator.tax_rate == Decimal("0.002")
        assert calculator.min_commission == Decimal("500")
        assert calculator.slippage_config.base_slippage == 0.002
        assert calculator.slippage_config.volume_impact == 0.001


class TestRepr:
    """문자열 표현 테스트"""

    def test_repr(self):
        """__repr__ 출력 확인"""
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            tax_rate=0.0023
        )

        repr_str = repr(calculator)

        assert "0.015%" in repr_str
        assert "0.23%" in repr_str


class TestJiraExamples:
    """Jira 티켓 예시 검증"""

    def test_jira_commission_example(self):
        """Jira 티켓 수수료 예시: 0.015%"""
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            slippage_config=SlippageConfig(enabled=False)
        )

        # 1000만원 거래 시 수수료
        commission = calculator.calculate_commission(Decimal("10000000"))
        assert commission == Decimal("1500")  # 0.015%

    def test_jira_tax_example(self):
        """Jira 티켓 세금 예시: 0.23%"""
        calculator = TradingCostCalculator(
            tax_rate=0.0023,
            slippage_config=SlippageConfig(enabled=False)
        )

        # 1000만원 매도 시 세금
        tax = calculator.calculate_tax(Decimal("10000000"))
        assert tax == Decimal("23000")  # 0.23%

    def test_jira_slippage_example(self):
        """Jira 티켓 슬리피지 예시: Adaptive Model"""
        # base_slippage = 0.001, volume_impact = 0.0005, volatility = 1.5
        config = SlippageConfig(
            base_slippage=0.001,
            volume_impact=0.0005,
            volatility_multiplier=1.5
        )

        slippage = config.calculate_slippage()

        # 0.1% + (0.05% * 1.5) = 0.175%
        expected = Decimal("0.00175")
        assert slippage == expected

    def test_jira_buy_example(self):
        """Jira 티켓 매수 예시"""
        # Given: 슬리피지 적용
        config = SlippageConfig(
            base_slippage=0.001,
            volume_impact=0.0005,
            volatility_multiplier=1.5
        )
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            slippage_config=config
        )

        # When: 50000원 10주 매수
        result = calculator.calculate_buy_cost(
            price=Decimal("50000"),
            quantity=10
        )

        # Then: execution_price = 50000 * (1 + 0.00175) = 50087.5
        expected_exec_price = Decimal("50000") * (Decimal("1") + Decimal("0.00175"))
        assert result.execution_price == expected_exec_price

        # total_cost = shares * execution_price + commission
        expected_gross = expected_exec_price * 10
        expected_commission = expected_gross * Decimal("0.00015")
        expected_total = expected_gross + expected_commission
        assert result.total_cost == expected_total

    def test_jira_sell_example(self):
        """Jira 티켓 매도 예시"""
        # Given: 슬리피지 적용
        config = SlippageConfig(
            base_slippage=0.001,
            volume_impact=0.0005,
            volatility_multiplier=1.5
        )
        calculator = TradingCostCalculator(
            commission_rate=0.00015,
            tax_rate=0.0023,
            slippage_config=config
        )

        # When: 55000원 10주 매도
        result = calculator.calculate_sell_revenue(
            price=Decimal("55000"),
            quantity=10
        )

        # Then: execution_price = 55000 * (1 - 0.00175) = 54903.75
        expected_exec_price = Decimal("55000") * (Decimal("1") - Decimal("0.00175"))
        assert result.execution_price == expected_exec_price

        # revenue = shares * execution_price
        # net_revenue = revenue - commission - tax
        expected_gross = expected_exec_price * 10
        expected_commission = expected_gross * Decimal("0.00015")
        expected_tax = expected_gross * Decimal("0.0023")
        expected_net = expected_gross - expected_commission - expected_tax
        assert result.total_cost == expected_net
