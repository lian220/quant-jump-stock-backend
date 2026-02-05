"""
Risk Manager - Stop Loss / Take Profit Logic

백테스트 및 실시간 트레이딩에서 리스크 관리 로직을 담당합니다.

주요 기능:
- Stop Loss: 고정 손절, ATR 기반 손절
- Take Profit: 고정 익절, 목표가 도달
- Trailing Stop: 고점 대비 추적 손절

사용 예시:
    risk_manager = RiskManager(
        stop_loss_pct=0.05,
        take_profit_pct=0.15,
        trailing_stop_enabled=True,
        trailing_stop_pct=0.05
    )

    for date in trading_dates:
        exits = risk_manager.check_risk_exits(portfolio, prices, date)
        for exit in exits:
            portfolio.close_position(exit.symbol, date, prices[exit.symbol], exit_reason=exit.reason)
"""

import logging
from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from enum import Enum
from typing import Dict, List, Optional

from .models import Portfolio, Position, ExitReason

logger = logging.getLogger(__name__)


class RiskExitType(str, Enum):
    """리스크 청산 유형"""
    STOP_LOSS = "stop_loss"
    TAKE_PROFIT = "take_profit"
    TRAILING_STOP = "trailing_stop"
    MAX_DRAWDOWN = "max_drawdown"


@dataclass
class RiskExitResult:
    """
    리스크 청산 결과

    Attributes:
        symbol: 종목 코드
        exit_type: 청산 유형
        trigger_price: 트리거 가격 (청산 기준이 된 가격)
        entry_price: 진입 가격
        pnl_pct: 손익률
        reason: 청산 사유 (ExitReason)
        message: 상세 메시지
    """
    symbol: str
    exit_type: RiskExitType
    trigger_price: Decimal
    entry_price: Decimal
    pnl_pct: Decimal
    reason: ExitReason
    message: str


class RiskManager:
    """
    리스크 관리자

    포지션별 Stop Loss / Take Profit / Trailing Stop 체크

    Args:
        stop_loss_pct: 손절 비율 (0.05 = 5%)
        take_profit_pct: 익절 비율 (0.15 = 15%)
        trailing_stop_enabled: 트레일링 스탑 활성화
        trailing_stop_pct: 트레일링 스탑 비율 (고점 대비)
        max_drawdown_pct: 포트폴리오 최대 낙폭 비율
    """

    def __init__(
        self,
        stop_loss_pct: float = 0.05,
        take_profit_pct: float = 0.15,
        trailing_stop_enabled: bool = False,
        trailing_stop_pct: float = 0.05,
        max_drawdown_pct: float = 0.20
    ):
        self.stop_loss_pct = Decimal(str(stop_loss_pct))
        self.take_profit_pct = Decimal(str(take_profit_pct))
        self.trailing_stop_enabled = trailing_stop_enabled
        self.trailing_stop_pct = Decimal(str(trailing_stop_pct))
        self.max_drawdown_pct = Decimal(str(max_drawdown_pct))

        # 히스토리 추적 (포트폴리오 MDD 계산용)
        self._portfolio_high_watermark: Decimal = Decimal("0")

    @classmethod
    def from_risk_management(cls, risk_management) -> "RiskManager":
        """
        RiskManagement (domain/strategy/models.py) 객체로부터 생성

        Args:
            risk_management: RiskManagement 인스턴스

        Returns:
            RiskManager 인스턴스
        """
        return cls(
            stop_loss_pct=risk_management.stop_loss_pct,
            take_profit_pct=risk_management.take_profit_pct,
            trailing_stop_enabled=risk_management.trailing_stop,
            trailing_stop_pct=risk_management.trailing_stop_pct,
            max_drawdown_pct=risk_management.max_drawdown_pct
        )

    def check_risk_exits(
        self,
        portfolio: Portfolio,
        prices: Dict[str, Decimal],
        current_date: date
    ) -> List[RiskExitResult]:
        """
        모든 포지션에 대해 리스크 청산 조건 체크

        Args:
            portfolio: 현재 포트폴리오 상태
            prices: 현재 가격 딕셔너리 (symbol -> price)
            current_date: 현재 날짜

        Returns:
            청산해야 할 포지션 목록
        """
        exits: List[RiskExitResult] = []

        # 1. 포지션별 체크
        for symbol, position in portfolio.positions.items():
            current_price = prices.get(symbol)
            if current_price is None:
                continue

            # 가격 업데이트
            position.update_price(current_price)

            # Stop Loss 체크
            stop_loss_result = self._check_stop_loss(position, current_price)
            if stop_loss_result:
                exits.append(stop_loss_result)
                continue  # 하나의 사유로만 청산

            # Take Profit 체크
            take_profit_result = self._check_take_profit(position, current_price)
            if take_profit_result:
                exits.append(take_profit_result)
                continue

            # Trailing Stop 체크
            if self.trailing_stop_enabled:
                trailing_result = self._check_trailing_stop(position, current_price)
                if trailing_result:
                    exits.append(trailing_result)

        # 2. 포트폴리오 전체 MDD 체크 (옵션)
        # 포트폴리오 전체가 최대 낙폭을 초과하면 전체 청산
        # 이 기능은 선택적으로 활성화 가능

        return exits

    def _check_stop_loss(
        self,
        position: Position,
        current_price: Decimal
    ) -> Optional[RiskExitResult]:
        """
        Stop Loss 조건 체크

        진입가 대비 손절 비율 도달 시 청산
        """
        pnl_pct = position.pnl_pct / 100  # Decimal ratio

        # 손절 조건: pnl_pct <= -stop_loss_pct
        if pnl_pct <= -self.stop_loss_pct:
            logger.info(
                f"[STOP_LOSS] {position.symbol}: "
                f"진입가 {position.entry_price} → 현재가 {current_price} "
                f"(손익률: {pnl_pct * 100:.2f}%)"
            )
            return RiskExitResult(
                symbol=position.symbol,
                exit_type=RiskExitType.STOP_LOSS,
                trigger_price=current_price,
                entry_price=position.entry_price,
                pnl_pct=pnl_pct * 100,
                reason=ExitReason.STOP_LOSS,
                message=f"손절 트리거: {pnl_pct * 100:.2f}% <= -{self.stop_loss_pct * 100:.1f}%"
            )
        return None

    def _check_take_profit(
        self,
        position: Position,
        current_price: Decimal
    ) -> Optional[RiskExitResult]:
        """
        Take Profit 조건 체크

        진입가 대비 익절 비율 도달 시 청산
        """
        pnl_pct = position.pnl_pct / 100  # Decimal ratio

        # 익절 조건: pnl_pct >= take_profit_pct
        if pnl_pct >= self.take_profit_pct:
            logger.info(
                f"[TAKE_PROFIT] {position.symbol}: "
                f"진입가 {position.entry_price} → 현재가 {current_price} "
                f"(손익률: {pnl_pct * 100:.2f}%)"
            )
            return RiskExitResult(
                symbol=position.symbol,
                exit_type=RiskExitType.TAKE_PROFIT,
                trigger_price=current_price,
                entry_price=position.entry_price,
                pnl_pct=pnl_pct * 100,
                reason=ExitReason.TAKE_PROFIT,
                message=f"익절 트리거: {pnl_pct * 100:.2f}% >= {self.take_profit_pct * 100:.1f}%"
            )
        return None

    def _check_trailing_stop(
        self,
        position: Position,
        current_price: Decimal
    ) -> Optional[RiskExitResult]:
        """
        Trailing Stop 조건 체크

        고점 대비 하락률이 trailing_stop_pct 이상이면 청산
        단, 현재 손익이 양수일 때만 적용 (수익 구간에서만)
        """
        # 수익 구간에서만 트레일링 스탑 적용
        if position.pnl_pct <= 0:
            return None

        # 고점 대비 하락률 계산
        drawdown_from_high = position.drawdown_from_high / 100  # Decimal ratio

        # 트레일링 스탑 조건: 고점 대비 하락률 >= trailing_stop_pct
        if drawdown_from_high <= -self.trailing_stop_pct:
            pnl_pct = position.pnl_pct / 100
            logger.info(
                f"[TRAILING_STOP] {position.symbol}: "
                f"고점 {position.highest_price} → 현재가 {current_price} "
                f"(고점 대비: {drawdown_from_high * 100:.2f}%)"
            )
            return RiskExitResult(
                symbol=position.symbol,
                exit_type=RiskExitType.TRAILING_STOP,
                trigger_price=current_price,
                entry_price=position.entry_price,
                pnl_pct=pnl_pct * 100,
                reason=ExitReason.TRAILING_STOP,
                message=(
                    f"트레일링 스탑 트리거: 고점({position.highest_price}) 대비 "
                    f"{drawdown_from_high * 100:.2f}% 하락"
                )
            )
        return None

    def update_portfolio_high_watermark(self, portfolio: Portfolio) -> None:
        """포트폴리오 고점 업데이트"""
        if portfolio.total_value > self._portfolio_high_watermark:
            self._portfolio_high_watermark = portfolio.total_value

    def check_portfolio_max_drawdown(
        self,
        portfolio: Portfolio
    ) -> Optional[RiskExitResult]:
        """
        포트폴리오 전체 MDD 체크

        포트폴리오가 최대 낙폭을 초과하면 전체 청산 신호 반환
        """
        if self._portfolio_high_watermark == 0:
            return None

        current_drawdown = (
            (portfolio.total_value / self._portfolio_high_watermark) - 1
        )

        if current_drawdown <= -self.max_drawdown_pct:
            return RiskExitResult(
                symbol="__PORTFOLIO__",
                exit_type=RiskExitType.MAX_DRAWDOWN,
                trigger_price=portfolio.total_value,
                entry_price=self._portfolio_high_watermark,
                pnl_pct=current_drawdown * 100,
                reason=ExitReason.FORCED_EXIT,
                message=(
                    f"포트폴리오 MDD 초과: "
                    f"{current_drawdown * 100:.2f}% <= -{self.max_drawdown_pct * 100:.1f}%"
                )
            )
        return None

    def get_stop_loss_price(self, entry_price: Decimal) -> Decimal:
        """손절가 계산"""
        return entry_price * (1 - self.stop_loss_pct)

    def get_take_profit_price(self, entry_price: Decimal) -> Decimal:
        """익절가 계산"""
        return entry_price * (1 + self.take_profit_pct)

    def get_trailing_stop_price(self, highest_price: Decimal) -> Decimal:
        """트레일링 스탑 가격 계산"""
        return highest_price * (1 - self.trailing_stop_pct)

    def reset(self) -> None:
        """상태 초기화"""
        self._portfolio_high_watermark = Decimal("0")

    def __repr__(self) -> str:
        return (
            f"RiskManager("
            f"stop_loss={self.stop_loss_pct * 100:.1f}%, "
            f"take_profit={self.take_profit_pct * 100:.1f}%, "
            f"trailing_stop={'ON' if self.trailing_stop_enabled else 'OFF'}"
            f")"
        )
