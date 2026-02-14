"""
Trading Cost Calculator - 거래 비용 계산기

수수료, 세금, 슬리피지를 포함한 거래 비용을 계산합니다.

사용 예시:
    calculator = TradingCostCalculator(
        commission_rate=Decimal("0.00015"),
        tax_rate=Decimal("0.0023"),
        slippage_model="adaptive"
    )
    cost = calculator.calculate_total_cost(
        price=Decimal("50000"), quantity=100, side="SELL", volume=1000000
    )
"""

import logging
from dataclasses import dataclass
from decimal import Decimal
from typing import Optional

logger = logging.getLogger(__name__)


@dataclass
class TradingCostBreakdown:
    """거래 비용 내역"""
    commission: Decimal
    tax: Decimal
    slippage: Decimal
    total: Decimal
    execution_price: Decimal  # 슬리피지 반영 체결가


class TradingCostCalculator:
    """
    거래 비용 계산기

    슬리피지 모델:
    - none: 슬리피지 없음
    - fixed: 고정 비율 슬리피지
    - adaptive: 거래량 대비 시장 충격 반영

    Args:
        commission_rate: 수수료율 (기본 0.015%)
        tax_rate: 세금율 - 매도 시만 적용 (기본 0.23%, 한국 증권거래세)
        slippage_model: 슬리피지 모델 (none, fixed, adaptive)
        base_slippage: 기본 슬리피지율 (기본 0.1%)
        volume_impact_factor: 거래량 충격 계수 (adaptive 모델용, 기본 0.1)
    """

    def __init__(
        self,
        commission_rate: Decimal = Decimal("0.00015"),
        tax_rate: Decimal = Decimal("0.0023"),
        slippage_model: str = "fixed",
        base_slippage: Decimal = Decimal("0.001"),
        volume_impact_factor: Decimal = Decimal("0.1"),
    ):
        self.commission_rate = commission_rate
        self.tax_rate = tax_rate
        self.slippage_model = slippage_model
        self.base_slippage = base_slippage
        self.volume_impact_factor = volume_impact_factor

    def calculate_total_cost(
        self,
        price: Decimal,
        quantity: int,
        side: str,
        volume: Optional[int] = None,
    ) -> TradingCostBreakdown:
        """
        거래 비용 전체 계산

        Args:
            price: 주문 가격
            quantity: 주문 수량
            side: 매수/매도 ("BUY" or "SELL")
            volume: 당일 거래량 (adaptive 슬리피지용)

        Returns:
            TradingCostBreakdown: 비용 내역
        """
        amount = price * quantity

        # 수수료 (매수/매도 모두)
        commission = amount * self.commission_rate

        # 세금 (매도 시만)
        tax = Decimal("0")
        if side.upper() == "SELL":
            tax = amount * self.tax_rate

        # 슬리피지
        slippage = self._calculate_slippage(price, quantity, volume)
        slippage_amount = slippage * quantity  # 주당 슬리피지 × 수량 = 총 슬리피지 금액

        # 슬리피지 반영 체결가
        if side.upper() == "BUY":
            execution_price = price + slippage  # 매수: 더 비싸게
        else:
            execution_price = price - slippage  # 매도: 더 싸게

        total = commission + tax + slippage_amount

        return TradingCostBreakdown(
            commission=commission,
            tax=tax,
            slippage=slippage_amount,
            total=total,
            execution_price=execution_price,
        )

    def _calculate_slippage(
        self, price: Decimal, quantity: int, volume: Optional[int]
    ) -> Decimal:
        """
        주당 슬리피지 계산

        Returns:
            주당 슬리피지 금액
        """
        if self.slippage_model == "none":
            return Decimal("0")

        elif self.slippage_model == "fixed":
            return price * self.base_slippage

        elif self.slippage_model == "adaptive":
            if not volume or volume <= 0:
                # 거래량 정보 없으면 고정 슬리피지 사용
                return price * self.base_slippage

            # 시장 충격: 주문 수량 / 거래량 비율에 비례
            participation_rate = Decimal(str(quantity)) / Decimal(str(volume))
            market_impact = participation_rate * self.volume_impact_factor
            total_slippage_rate = self.base_slippage + market_impact

            return price * total_slippage_rate

        else:
            logger.warning(f"Unknown slippage model: {self.slippage_model}, using fixed")
            return price * self.base_slippage
