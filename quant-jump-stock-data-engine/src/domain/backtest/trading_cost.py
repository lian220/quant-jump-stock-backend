"""
Trading Cost Calculator - 거래 비용 계산기

백테스트에서 실제 거래 비용을 시뮬레이션합니다.

주요 기능:
- Commission: 증권사 수수료
- Tax: 증권거래세 (매도 시)
- Slippage: 슬리피지 (시장 충격 비용)

사용 예시:
    cost_calculator = TradingCostCalculator(
        commission_rate=0.00015,  # 0.015%
        tax_rate=0.0023,          # 0.23%
        slippage_config=SlippageConfig(base_slippage=0.001)
    )

    # 매수 비용 계산
    buy_result = cost_calculator.calculate_buy_cost(
        price=Decimal("50000"),
        quantity=10
    )

    # 매도 수익 계산
    sell_result = cost_calculator.calculate_sell_revenue(
        price=Decimal("55000"),
        quantity=10,
        cost_basis=Decimal("500000")
    )
"""

import logging
from dataclasses import dataclass, field
from decimal import Decimal
from enum import Enum
from typing import Optional

logger = logging.getLogger(__name__)


class TradeDirection(str, Enum):
    """거래 방향"""
    BUY = "buy"
    SELL = "sell"


@dataclass
class SlippageConfig:
    """
    슬리피지 설정

    Adaptive Slippage Model:
    slippage = base_slippage + (volume_impact * volatility_multiplier)

    Attributes:
        base_slippage: 기본 슬리피지 비율 (0.001 = 0.1%)
        volume_impact: 거래량 영향 계수
        volatility_multiplier: 변동성 승수
        enabled: 슬리피지 적용 여부
    """
    base_slippage: float = 0.001  # 0.1%
    volume_impact: float = 0.0005
    volatility_multiplier: float = 1.0
    enabled: bool = True

    def calculate_slippage(self, volatility: Optional[float] = None) -> Decimal:
        """
        슬리피지 비율 계산

        Args:
            volatility: 현재 변동성 (제공되면 승수로 사용)

        Returns:
            슬리피지 비율 (Decimal)
        """
        if not self.enabled:
            return Decimal("0")

        vol_mult = Decimal(str(volatility)) if volatility else Decimal(str(self.volatility_multiplier))
        slippage = Decimal(str(self.base_slippage)) + (
            Decimal(str(self.volume_impact)) * vol_mult
        )
        return slippage


@dataclass
class TradeCostResult:
    """
    거래 비용 계산 결과

    Attributes:
        direction: 거래 방향 (매수/매도)
        base_price: 원래 가격
        execution_price: 실행 가격 (슬리피지 적용)
        quantity: 수량
        gross_value: 총 거래금액 (수수료 제외)
        commission: 증권사 수수료
        tax: 증권거래세 (매도 시만)
        slippage_cost: 슬리피지 비용
        total_cost: 총 비용 (매수) 또는 순수익 (매도)
        cost_breakdown: 비용 내역 상세
    """
    direction: TradeDirection
    base_price: Decimal
    execution_price: Decimal
    quantity: int
    gross_value: Decimal
    commission: Decimal
    tax: Decimal
    slippage_cost: Decimal
    total_cost: Decimal
    cost_breakdown: dict = field(default_factory=dict)


@dataclass
class PnLResult:
    """
    손익 계산 결과

    Attributes:
        gross_pnl: 총 손익 (비용 제외)
        net_pnl: 순손익 (비용 포함)
        total_costs: 총 거래 비용
        cost_basis: 원금
        revenue: 매도 수익
        pnl_pct: 손익률 (%)
        net_pnl_pct: 순손익률 (%)
    """
    gross_pnl: Decimal
    net_pnl: Decimal
    total_costs: Decimal
    cost_basis: Decimal
    revenue: Decimal
    pnl_pct: Decimal
    net_pnl_pct: Decimal


class TradingCostCalculator:
    """
    거래 비용 계산기

    한국 주식 시장 기준의 거래 비용을 계산합니다.

    Args:
        commission_rate: 증권사 수수료율 (기본: 0.00015 = 0.015%)
        tax_rate: 증권거래세율 (기본: 0.0023 = 0.23%, 매도 시만)
        slippage_config: 슬리피지 설정
        min_commission: 최소 수수료 (기본: 0원)
    """

    # 한국 주식 시장 기본값
    DEFAULT_COMMISSION_RATE = Decimal("0.00015")  # 0.015%
    DEFAULT_TAX_RATE = Decimal("0.0023")  # 0.23%

    def __init__(
        self,
        commission_rate: float = 0.00015,
        tax_rate: float = 0.0023,
        slippage_config: Optional[SlippageConfig] = None,
        min_commission: float = 0
    ):
        self.commission_rate = Decimal(str(commission_rate))
        self.tax_rate = Decimal(str(tax_rate))
        self.slippage_config = slippage_config or SlippageConfig()
        self.min_commission = Decimal(str(min_commission))

    def calculate_commission(self, trade_value: Decimal) -> Decimal:
        """
        수수료 계산

        Args:
            trade_value: 거래 금액

        Returns:
            수수료 금액
        """
        commission = trade_value * self.commission_rate
        return max(commission, self.min_commission)

    def calculate_tax(self, sell_value: Decimal) -> Decimal:
        """
        증권거래세 계산 (매도 시만)

        Args:
            sell_value: 매도 금액

        Returns:
            세금 금액
        """
        return sell_value * self.tax_rate

    def calculate_execution_price(
        self,
        price: Decimal,
        direction: TradeDirection,
        volatility: Optional[float] = None
    ) -> tuple[Decimal, Decimal]:
        """
        실행 가격 계산 (슬리피지 적용)

        매수: 불리하게 (가격 상승)
        매도: 불리하게 (가격 하락)

        Args:
            price: 기준 가격
            direction: 거래 방향
            volatility: 현재 변동성

        Returns:
            (실행 가격, 슬리피지 비율)
        """
        slippage = self.slippage_config.calculate_slippage(volatility)

        if direction == TradeDirection.BUY:
            # 매수: 가격 상승 (불리)
            execution_price = price * (Decimal("1") + slippage)
        else:
            # 매도: 가격 하락 (불리)
            execution_price = price * (Decimal("1") - slippage)

        return execution_price, slippage

    def calculate_buy_cost(
        self,
        price: Decimal,
        quantity: int,
        volatility: Optional[float] = None
    ) -> TradeCostResult:
        """
        매수 비용 계산

        total_cost = shares * execution_price + commission

        Args:
            price: 매수 가격
            quantity: 수량
            volatility: 현재 변동성 (슬리피지 계산용)

        Returns:
            TradeCostResult: 매수 비용 계산 결과
        """
        execution_price, slippage = self.calculate_execution_price(
            price, TradeDirection.BUY, volatility
        )

        # 총 거래금액 (슬리피지 적용)
        gross_value = execution_price * quantity

        # 수수료
        commission = self.calculate_commission(gross_value)

        # 슬리피지 비용 (원래 가격 대비)
        base_value = price * quantity
        slippage_cost = gross_value - base_value

        # 총 비용
        total_cost = gross_value + commission

        logger.debug(
            f"[BUY] price={price}, exec_price={execution_price:.2f}, "
            f"qty={quantity}, commission={commission:.2f}, "
            f"slippage_cost={slippage_cost:.2f}, total={total_cost:.2f}"
        )

        return TradeCostResult(
            direction=TradeDirection.BUY,
            base_price=price,
            execution_price=execution_price,
            quantity=quantity,
            gross_value=gross_value,
            commission=commission,
            tax=Decimal("0"),  # 매수 시 세금 없음
            slippage_cost=slippage_cost,
            total_cost=total_cost,
            cost_breakdown={
                "gross_value": gross_value,
                "commission": commission,
                "slippage_cost": slippage_cost,
                "slippage_rate": slippage,
            }
        )

    def calculate_sell_revenue(
        self,
        price: Decimal,
        quantity: int,
        cost_basis: Optional[Decimal] = None,
        volatility: Optional[float] = None
    ) -> TradeCostResult:
        """
        매도 수익 계산

        revenue = shares * execution_price
        net_revenue = revenue - commission - tax

        Args:
            price: 매도 가격
            quantity: 수량
            cost_basis: 원금 (PnL 계산용, 선택)
            volatility: 현재 변동성 (슬리피지 계산용)

        Returns:
            TradeCostResult: 매도 수익 계산 결과
        """
        execution_price, slippage = self.calculate_execution_price(
            price, TradeDirection.SELL, volatility
        )

        # 총 거래금액 (슬리피지 적용)
        gross_value = execution_price * quantity

        # 수수료 및 세금
        commission = self.calculate_commission(gross_value)
        tax = self.calculate_tax(gross_value)

        # 슬리피지 비용 (원래 가격 대비)
        base_value = price * quantity
        slippage_cost = base_value - gross_value  # 양수면 손실

        # 순수익 (수수료, 세금 차감)
        net_revenue = gross_value - commission - tax

        logger.debug(
            f"[SELL] price={price}, exec_price={execution_price:.2f}, "
            f"qty={quantity}, commission={commission:.2f}, tax={tax:.2f}, "
            f"slippage_cost={slippage_cost:.2f}, net={net_revenue:.2f}"
        )

        return TradeCostResult(
            direction=TradeDirection.SELL,
            base_price=price,
            execution_price=execution_price,
            quantity=quantity,
            gross_value=gross_value,
            commission=commission,
            tax=tax,
            slippage_cost=slippage_cost,
            total_cost=net_revenue,  # 매도 시에는 순수익
            cost_breakdown={
                "gross_value": gross_value,
                "commission": commission,
                "tax": tax,
                "slippage_cost": slippage_cost,
                "slippage_rate": slippage,
                "net_revenue": net_revenue,
            }
        )

    def calculate_round_trip_pnl(
        self,
        buy_price: Decimal,
        sell_price: Decimal,
        quantity: int,
        buy_volatility: Optional[float] = None,
        sell_volatility: Optional[float] = None
    ) -> PnLResult:
        """
        왕복 거래 손익 계산 (매수 + 매도)

        Args:
            buy_price: 매수 가격
            sell_price: 매도 가격
            quantity: 수량
            buy_volatility: 매수 시 변동성
            sell_volatility: 매도 시 변동성

        Returns:
            PnLResult: 손익 계산 결과
        """
        # 매수 비용
        buy_result = self.calculate_buy_cost(buy_price, quantity, buy_volatility)

        # 매도 수익
        sell_result = self.calculate_sell_revenue(
            sell_price, quantity, buy_result.total_cost, sell_volatility
        )

        # 원금 (매수 총 비용)
        cost_basis = buy_result.total_cost

        # 매도 순수익
        revenue = sell_result.total_cost

        # 총 비용 (수수료 + 세금 + 슬리피지)
        total_costs = (
            buy_result.commission +
            sell_result.commission +
            sell_result.tax +
            buy_result.slippage_cost +
            sell_result.slippage_cost
        )

        # 총 손익 (비용 제외)
        gross_pnl = (sell_price - buy_price) * quantity

        # 순손익
        net_pnl = revenue - cost_basis

        # 손익률
        pnl_pct = (gross_pnl / (buy_price * quantity)) * 100 if buy_price > 0 else Decimal("0")
        net_pnl_pct = (net_pnl / cost_basis) * 100 if cost_basis > 0 else Decimal("0")

        logger.debug(
            f"[ROUND_TRIP] buy={buy_price}, sell={sell_price}, qty={quantity}, "
            f"gross_pnl={gross_pnl:.2f}, net_pnl={net_pnl:.2f}, "
            f"total_costs={total_costs:.2f}"
        )

        return PnLResult(
            gross_pnl=gross_pnl,
            net_pnl=net_pnl,
            total_costs=total_costs,
            cost_basis=cost_basis,
            revenue=revenue,
            pnl_pct=pnl_pct,
            net_pnl_pct=net_pnl_pct
        )

    def estimate_breakeven_price(
        self,
        buy_price: Decimal,
        quantity: int,
        volatility: Optional[float] = None
    ) -> Decimal:
        """
        손익분기점 가격 계산

        거래 비용을 고려하여 본전이 되는 매도 가격을 계산합니다.

        Args:
            buy_price: 매수 가격
            quantity: 수량
            volatility: 예상 변동성

        Returns:
            손익분기점 매도 가격
        """
        # 매수 비용
        buy_result = self.calculate_buy_cost(buy_price, quantity, volatility)
        cost_basis = buy_result.total_cost

        # 손익분기점 계산 (반복 근사)
        # 목표: sell_result.total_cost >= cost_basis
        slippage = self.slippage_config.calculate_slippage(volatility)
        slippage_factor = Decimal("1") - slippage
        cost_factor = Decimal("1") - self.commission_rate - self.tax_rate

        # breakeven_price * quantity * slippage_factor * cost_factor = cost_basis
        if quantity > 0 and slippage_factor > 0 and cost_factor > 0:
            breakeven = cost_basis / (quantity * slippage_factor * cost_factor)
        else:
            breakeven = buy_price

        return breakeven

    @classmethod
    def from_config(cls, config: dict) -> "TradingCostCalculator":
        """
        설정 딕셔너리로부터 생성

        Args:
            config: 설정 딕셔너리
                - commission_rate: 수수료율
                - tax_rate: 세금율
                - slippage: 슬리피지 설정 (dict)

        Returns:
            TradingCostCalculator 인스턴스
        """
        slippage_dict = config.get("slippage", {})
        slippage_config = SlippageConfig(
            base_slippage=slippage_dict.get("base_slippage", 0.001),
            volume_impact=slippage_dict.get("volume_impact", 0.0005),
            volatility_multiplier=slippage_dict.get("volatility_multiplier", 1.0),
            enabled=slippage_dict.get("enabled", True)
        )

        return cls(
            commission_rate=config.get("commission_rate", 0.00015),
            tax_rate=config.get("tax_rate", 0.0023),
            slippage_config=slippage_config,
            min_commission=config.get("min_commission", 0)
        )

    def reset(self) -> None:
        """상태 초기화 (필요시)"""
        pass

    def __repr__(self) -> str:
        return (
            f"TradingCostCalculator("
            f"commission={self.commission_rate * 100:.3f}%, "
            f"tax={self.tax_rate * 100:.2f}%, "
            f"slippage_enabled={self.slippage_config.enabled}"
            f")"
        )
