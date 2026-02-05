"""
Position Sizer - 포지션 사이징 알고리즘

백테스트 및 실시간 트레이딩에서 포지션 크기를 결정합니다.

주요 기능:
- Fixed Percentage: 고정 비율 포지션 사이징
- Volatility Adjusted: 변동성 기반 조정
- Kelly Criterion: 켈리 기준 최적 비율

사용 예시:
    position_sizer = PositionSizer(
        method=PositionSizingMethod.VOLATILITY_ADJUSTED,
        max_position_pct=0.2,
        max_positions=10,
        max_total_exposure=0.8
    )

    size = position_sizer.calculate_position_size(
        equity=1000000,
        price=50000,
        volatility=0.25
    )
"""

import logging
import math
from dataclasses import dataclass
from decimal import Decimal
from enum import Enum
from typing import Optional

logger = logging.getLogger(__name__)


class PositionSizingMethod(str, Enum):
    """포지션 사이징 방법"""
    FIXED = "fixed"                       # 고정 비율
    VOLATILITY_ADJUSTED = "volatility"    # 변동성 조정
    KELLY = "kelly"                       # 켈리 기준


@dataclass
class PositionSizeResult:
    """
    포지션 사이징 결과

    Attributes:
        position_value: 투자 금액
        quantity: 매수 수량
        weight: 포트폴리오 비중 (%)
        method: 사용된 사이징 방법
        adjustment_factor: 조정 계수 (변동성, 켈리 등)
        message: 상세 메시지
    """
    position_value: Decimal
    quantity: int
    weight: Decimal
    method: PositionSizingMethod
    adjustment_factor: Decimal
    message: str


class PositionSizer:
    """
    포지션 사이저

    다양한 방법으로 최적의 포지션 크기를 계산합니다.

    Args:
        method: 포지션 사이징 방법
        max_position_pct: 최대 개별 포지션 비중 (0.2 = 20%)
        max_positions: 최대 동시 보유 종목 수
        max_total_exposure: 최대 총 투자 비중 (0.8 = 80%, 20% 현금 유지)
        target_volatility: 목표 변동성 (변동성 조정 방법용)
        max_volatility_scalar: 변동성 스칼라 최대값
        kelly_fraction: 켈리 비율 조정 (보수적 운용: 0.5 = Half Kelly)
    """

    def __init__(
        self,
        method: PositionSizingMethod = PositionSizingMethod.FIXED,
        max_position_pct: float = 0.2,
        max_positions: int = 10,
        max_total_exposure: float = 0.8,
        target_volatility: float = 0.15,
        max_volatility_scalar: float = 2.0,
        kelly_fraction: float = 0.5
    ):
        self.method = method
        self.max_position_pct = Decimal(str(max_position_pct))
        self.max_positions = max_positions
        self.max_total_exposure = Decimal(str(max_total_exposure))
        self.target_volatility = Decimal(str(target_volatility))
        self.max_volatility_scalar = Decimal(str(max_volatility_scalar))
        self.kelly_fraction = Decimal(str(kelly_fraction))

    def calculate_position_size(
        self,
        equity: Decimal,
        price: Decimal,
        current_positions: int = 0,
        current_exposure: Decimal = Decimal("0"),
        volatility: Optional[float] = None,
        win_rate: Optional[float] = None,
        avg_win: Optional[float] = None,
        avg_loss: Optional[float] = None
    ) -> PositionSizeResult:
        """
        포지션 크기 계산

        Args:
            equity: 현재 자산 총액
            price: 매수 가격
            current_positions: 현재 보유 포지션 수
            current_exposure: 현재 총 투자 비중 (0-1)
            volatility: 종목 변동성 (연간화, 변동성 조정 방법용)
            win_rate: 승률 (켈리 방법용)
            avg_win: 평균 수익률 (켈리 방법용)
            avg_loss: 평균 손실률 (켈리 방법용)

        Returns:
            PositionSizeResult: 계산된 포지션 크기 결과
        """
        # 제약 조건 체크
        if current_positions >= self.max_positions:
            return PositionSizeResult(
                position_value=Decimal("0"),
                quantity=0,
                weight=Decimal("0"),
                method=self.method,
                adjustment_factor=Decimal("0"),
                message=f"최대 포지션 수({self.max_positions}) 도달"
            )

        available_exposure = self.max_total_exposure - current_exposure
        if available_exposure <= 0:
            return PositionSizeResult(
                position_value=Decimal("0"),
                quantity=0,
                weight=Decimal("0"),
                method=self.method,
                adjustment_factor=Decimal("0"),
                message=f"최대 투자 비중({self.max_total_exposure * 100:.0f}%) 도달"
            )

        # 방법별 계산
        if self.method == PositionSizingMethod.FIXED:
            result = self._calculate_fixed(equity, price, available_exposure)
        elif self.method == PositionSizingMethod.VOLATILITY_ADJUSTED:
            result = self._calculate_volatility_adjusted(
                equity, price, available_exposure, volatility
            )
        elif self.method == PositionSizingMethod.KELLY:
            result = self._calculate_kelly(
                equity, price, available_exposure, win_rate, avg_win, avg_loss
            )
        else:
            result = self._calculate_fixed(equity, price, available_exposure)

        return result

    def _calculate_fixed(
        self,
        equity: Decimal,
        price: Decimal,
        available_exposure: Decimal
    ) -> PositionSizeResult:
        """
        Fixed Percentage (고정 비율) 포지션 사이징

        position_value = current_equity * max_position_pct
        """
        # 최대 포지션 비중과 남은 투자 가능 비중 중 작은 값 사용
        effective_pct = min(self.max_position_pct, available_exposure)
        position_value = equity * effective_pct

        quantity = int(position_value / price)
        actual_value = price * quantity
        actual_weight = actual_value / equity * 100 if equity > 0 else Decimal("0")

        logger.debug(
            f"[FIXED] equity={equity}, price={price}, "
            f"pct={effective_pct * 100:.1f}%, quantity={quantity}"
        )

        return PositionSizeResult(
            position_value=actual_value,
            quantity=quantity,
            weight=actual_weight,
            method=PositionSizingMethod.FIXED,
            adjustment_factor=Decimal("1"),
            message=f"고정 비율 {effective_pct * 100:.1f}% 적용"
        )

    def _calculate_volatility_adjusted(
        self,
        equity: Decimal,
        price: Decimal,
        available_exposure: Decimal,
        volatility: Optional[float]
    ) -> PositionSizeResult:
        """
        Volatility Adjusted (변동성 조정) 포지션 사이징

        vol_scalar = min(target_vol / recent_vol, max_scalar)
        position_value = current_equity * max_position_pct * vol_scalar
        """
        if volatility is None or volatility <= 0:
            # 변동성 정보 없으면 고정 비율 사용
            return self._calculate_fixed(equity, price, available_exposure)

        vol = Decimal(str(volatility))

        # 변동성 스칼라 계산
        if vol > 0:
            vol_scalar = min(self.target_volatility / vol, self.max_volatility_scalar)
        else:
            vol_scalar = Decimal("1")

        # 조정된 비율 계산
        adjusted_pct = self.max_position_pct * vol_scalar
        effective_pct = min(adjusted_pct, available_exposure)
        position_value = equity * effective_pct

        quantity = int(position_value / price)
        actual_value = price * quantity
        actual_weight = actual_value / equity * 100 if equity > 0 else Decimal("0")

        logger.debug(
            f"[VOLATILITY] equity={equity}, price={price}, vol={vol:.2%}, "
            f"scalar={vol_scalar:.2f}, quantity={quantity}"
        )

        return PositionSizeResult(
            position_value=actual_value,
            quantity=quantity,
            weight=actual_weight,
            method=PositionSizingMethod.VOLATILITY_ADJUSTED,
            adjustment_factor=vol_scalar,
            message=(
                f"변동성 {vol * 100:.1f}% → 스칼라 {vol_scalar:.2f}x → "
                f"비율 {effective_pct * 100:.1f}%"
            )
        )

    def _calculate_kelly(
        self,
        equity: Decimal,
        price: Decimal,
        available_exposure: Decimal,
        win_rate: Optional[float],
        avg_win: Optional[float],
        avg_loss: Optional[float]
    ) -> PositionSizeResult:
        """
        Kelly Criterion (켈리 기준) 포지션 사이징

        kelly_pct = (win_rate * avg_win - (1 - win_rate) * avg_loss) / avg_win
        position_value = current_equity * kelly_pct * kelly_fraction
        """
        # 파라미터 검증
        if (win_rate is None or avg_win is None or avg_loss is None or
                win_rate <= 0 or avg_win <= 0 or avg_loss <= 0):
            # 켈리 계산 불가 시 고정 비율 사용
            return self._calculate_fixed(equity, price, available_exposure)

        wr = Decimal(str(win_rate))
        aw = Decimal(str(avg_win))
        al = Decimal(str(avg_loss))

        # 켈리 비율 계산
        # Kelly % = (W * R - L) / R
        # W = 승률, R = 평균수익/평균손실 비율, L = 패율
        # 또는: Kelly % = (p * b - q) / b
        # p = 승률, q = 패율, b = 손익비
        lose_rate = Decimal("1") - wr
        kelly_pct = (wr * aw - lose_rate * al) / aw if aw > 0 else Decimal("0")

        # 음수면 매매 금지
        if kelly_pct <= 0:
            return PositionSizeResult(
                position_value=Decimal("0"),
                quantity=0,
                weight=Decimal("0"),
                method=PositionSizingMethod.KELLY,
                adjustment_factor=kelly_pct,
                message=f"켈리 비율 음수({kelly_pct * 100:.1f}%) - 매매 금지"
            )

        # 프랙셔널 켈리 적용 (보수적 운용)
        adjusted_kelly = kelly_pct * self.kelly_fraction

        # 최대 포지션 비중 제한
        effective_pct = min(adjusted_kelly, self.max_position_pct, available_exposure)
        position_value = equity * effective_pct

        quantity = int(position_value / price)
        actual_value = price * quantity
        actual_weight = actual_value / equity * 100 if equity > 0 else Decimal("0")

        logger.debug(
            f"[KELLY] equity={equity}, price={price}, "
            f"win_rate={wr:.1%}, avg_win={aw:.1%}, avg_loss={al:.1%}, "
            f"kelly={kelly_pct:.2%}, adjusted={adjusted_kelly:.2%}, quantity={quantity}"
        )

        return PositionSizeResult(
            position_value=actual_value,
            quantity=quantity,
            weight=actual_weight,
            method=PositionSizingMethod.KELLY,
            adjustment_factor=adjusted_kelly,
            message=(
                f"켈리 {kelly_pct * 100:.1f}% × {self.kelly_fraction:.0%} = "
                f"{adjusted_kelly * 100:.1f}% → 적용 {effective_pct * 100:.1f}%"
            )
        )

    def get_equal_weight_position_value(self, equity: Decimal) -> Decimal:
        """
        동일 비중 포지션 금액 계산

        모든 포지션을 동일 비중으로 투자할 때의 개별 포지션 금액
        """
        if self.max_positions <= 0:
            return Decimal("0")
        return equity * self.max_total_exposure / self.max_positions

    def get_remaining_capacity(
        self,
        equity: Decimal,
        current_positions: int,
        current_exposure: Decimal
    ) -> dict:
        """
        남은 투자 여력 계산

        Returns:
            dict: 남은 포지션 수, 남은 투자 가능 금액, 남은 비중
        """
        remaining_positions = max(0, self.max_positions - current_positions)
        remaining_exposure = max(Decimal("0"), self.max_total_exposure - current_exposure)
        remaining_value = equity * remaining_exposure

        return {
            "remaining_positions": remaining_positions,
            "remaining_exposure": remaining_exposure,
            "remaining_value": remaining_value,
            "can_add_position": remaining_positions > 0 and remaining_exposure > 0
        }

    @classmethod
    def from_risk_management(cls, risk_management) -> "PositionSizer":
        """
        RiskManagement (domain/strategy/models.py) 객체로부터 생성

        Args:
            risk_management: RiskManagement 인스턴스

        Returns:
            PositionSizer 인스턴스
        """
        return cls(
            method=PositionSizingMethod.FIXED,
            max_position_pct=risk_management.max_position_pct,
            max_positions=10,  # 기본값
            max_total_exposure=0.8  # 기본값
        )

    def reset(self) -> None:
        """상태 초기화 (필요시)"""
        pass

    def __repr__(self) -> str:
        return (
            f"PositionSizer("
            f"method={self.method.value}, "
            f"max_position={self.max_position_pct * 100:.0f}%, "
            f"max_positions={self.max_positions}, "
            f"max_exposure={self.max_total_exposure * 100:.0f}%"
            f")"
        )
