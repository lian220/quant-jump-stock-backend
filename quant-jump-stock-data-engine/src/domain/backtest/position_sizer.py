"""
Position Sizer - 포지션 사이징 계산기

다양한 포지션 사이징 전략을 지원합니다.

사용 예시:
    sizer = PositionSizer(method="fixed_percentage", max_position_pct=Decimal("0.1"))
    position_value = sizer.calculate(portfolio_value=Decimal("10000000"), price=Decimal("50000"))
"""

import logging
import math
from decimal import Decimal
from typing import Optional, List

logger = logging.getLogger(__name__)


class PositionSizer:
    """
    포지션 사이징 계산기

    지원 방식:
    - fixed_percentage: 포트폴리오의 고정 비율
    - equal_weight: 최대 포지션 수로 균등 분배
    - kelly: 켈리 기준 (승률, 평균수익/손실 기반)
    - volatility_target: 목표 변동성 기반
    - risk_parity: 리스크 패리티 (변동성 역비례)
    """

    def __init__(
        self,
        method: str = "fixed_percentage",
        max_position_pct: Decimal = Decimal("0.1"),
        max_positions: int = 10,
        # kelly 파라미터
        win_rate: Optional[Decimal] = None,
        avg_win: Optional[Decimal] = None,
        avg_loss: Optional[Decimal] = None,
        kelly_fraction: Decimal = Decimal("0.5"),  # half-kelly (보수적)
        # volatility_target 파라미터
        target_volatility: Decimal = Decimal("0.15"),  # 연 15%
        trading_days: int = 252,
    ):
        self.method = method
        self.max_position_pct = max_position_pct
        self.max_positions = max_positions
        self.win_rate = win_rate
        self.avg_win = avg_win
        self.avg_loss = avg_loss
        self.kelly_fraction = kelly_fraction
        self.target_volatility = target_volatility
        self.trading_days = trading_days

    def calculate(
        self,
        portfolio_value: Decimal,
        price: Decimal,
        symbol_volatility: Optional[Decimal] = None,
        recent_returns: Optional[List[Decimal]] = None,
    ) -> Decimal:
        """
        포지션 가치 계산

        Args:
            portfolio_value: 포트폴리오 총 가치
            price: 현재 주가
            symbol_volatility: 종목 연간 변동성 (volatility_target, risk_parity용)
            recent_returns: 최근 일간 수익률 리스트 (변동성 직접 계산용)

        Returns:
            포지션 가치 (금액)
        """
        if self.method == "fixed_percentage":
            return self._fixed_percentage(portfolio_value)
        elif self.method == "equal_weight":
            return self._equal_weight(portfolio_value)
        elif self.method == "kelly":
            return self._kelly_criterion(portfolio_value)
        elif self.method == "volatility_target":
            vol = symbol_volatility or self._estimate_volatility(recent_returns)
            return self._volatility_target(portfolio_value, vol)
        elif self.method == "risk_parity":
            vol = symbol_volatility or self._estimate_volatility(recent_returns)
            return self._risk_parity(portfolio_value, vol)
        else:
            logger.warning(f"Unknown position sizing method: {self.method}, falling back to fixed_percentage")
            return self._fixed_percentage(portfolio_value)

    def _fixed_percentage(self, portfolio_value: Decimal) -> Decimal:
        """고정 비율: 포트폴리오의 일정 비율"""
        return portfolio_value * self.max_position_pct

    def _equal_weight(self, portfolio_value: Decimal) -> Decimal:
        """균등 분배: 최대 포지션 수로 균등"""
        if self.max_positions <= 0:
            return Decimal("0")
        pct = Decimal("1") / Decimal(str(self.max_positions))
        return portfolio_value * min(pct, self.max_position_pct)

    def _kelly_criterion(self, portfolio_value: Decimal) -> Decimal:
        """켈리 기준: (승률 * 평균수익 - 패률 * 평균손실) / 평균수익"""
        if not self.win_rate or not self.avg_win or not self.avg_loss:
            return self._fixed_percentage(portfolio_value)

        win_rate_ratio = self.win_rate / Decimal("100")
        lose_rate_ratio = Decimal("1") - win_rate_ratio

        if self.avg_win <= 0:
            return self._fixed_percentage(portfolio_value)

        kelly = (win_rate_ratio * self.avg_win - lose_rate_ratio * self.avg_loss) / self.avg_win

        # half-kelly 적용 (보수적)
        kelly = kelly * self.kelly_fraction

        # 음수면 투자하지 않음, 최대 max_position_pct로 제한
        kelly = max(Decimal("0"), min(kelly, self.max_position_pct))

        return portfolio_value * kelly

    def _volatility_target(
        self, portfolio_value: Decimal, symbol_volatility: Optional[Decimal]
    ) -> Decimal:
        """목표 변동성: target_vol / symbol_vol 비율로 포지션 크기 결정"""
        if not symbol_volatility or symbol_volatility <= 0:
            return self._fixed_percentage(portfolio_value)

        # 포지션 비중 = target_volatility / symbol_volatility
        weight = self.target_volatility / symbol_volatility
        weight = min(weight, self.max_position_pct)  # 상한

        return portfolio_value * weight

    def _risk_parity(
        self, portfolio_value: Decimal, symbol_volatility: Optional[Decimal]
    ) -> Decimal:
        """리스크 패리티: 변동성에 반비례하여 배분"""
        if not symbol_volatility or symbol_volatility <= 0:
            return self._fixed_percentage(portfolio_value)

        # 단일 종목 기준: 1/vol 비례 + max_position_pct로 cap
        # 실제 리스크 패리티는 전체 유니버스 변동성이 필요하나, 단일 종목 근사치로 계산
        inverse_vol = Decimal("1") / symbol_volatility
        # 기준 변동성(20%) 대비 역수를 정규화
        base_inverse_vol = Decimal("1") / Decimal("0.2")
        weight = (inverse_vol / base_inverse_vol) * self.max_position_pct
        weight = min(weight, self.max_position_pct)

        return portfolio_value * weight

    def _estimate_volatility(self, recent_returns: Optional[List[Decimal]]) -> Optional[Decimal]:
        """일간 수익률에서 연간 변동성 추정"""
        if not recent_returns or len(recent_returns) < 5:
            return None

        returns_float = [float(r) for r in recent_returns]
        mean = sum(returns_float) / len(returns_float)
        variance = sum((r - mean) ** 2 for r in returns_float) / len(returns_float)
        daily_vol = math.sqrt(variance) if variance > 0 else 0

        annual_vol = daily_vol * math.sqrt(self.trading_days)
        return Decimal(str(round(annual_vol, 6)))

    def update_stats(
        self, win_rate: Decimal, avg_win: Decimal, avg_loss: Decimal
    ) -> None:
        """거래 통계 업데이트 (kelly 방식에서 동적으로 사용)"""
        self.win_rate = win_rate
        self.avg_win = avg_win
        self.avg_loss = avg_loss
