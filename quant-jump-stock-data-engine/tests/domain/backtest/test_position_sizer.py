"""
Position Sizer 테스트

Fixed, Volatility Adjusted, Kelly Criterion 포지션 사이징 검증
"""

import pytest
from decimal import Decimal

from src.domain.backtest.position_sizer import (
    PositionSizer,
    PositionSizingMethod,
    PositionSizeResult,
)


class TestPositionSizerFixed:
    """Fixed Percentage 테스트"""

    def test_fixed_basic(self):
        """기본 고정 비율 포지션 사이징"""
        # Given: 20% 최대 포지션
        sizer = PositionSizer(
            method=PositionSizingMethod.FIXED,
            max_position_pct=0.2,
            max_positions=10,
            max_total_exposure=0.8
        )

        # When: 100만원 자산, 주당 5만원
        result = sizer.calculate_position_size(
            equity=Decimal("1000000"),
            price=Decimal("50000")
        )

        # Then: 20만원 투자, 4주 매수
        assert result.quantity == 4
        assert result.position_value == Decimal("200000")
        assert result.method == PositionSizingMethod.FIXED

    def test_fixed_respects_available_exposure(self):
        """남은 투자 비중 제한 적용"""
        # Given: 20% 최대 포지션, 이미 70% 투자됨
        sizer = PositionSizer(
            method=PositionSizingMethod.FIXED,
            max_position_pct=0.2,
            max_total_exposure=0.8
        )

        # When: 남은 비중 10% (80% - 70%)
        result = sizer.calculate_position_size(
            equity=Decimal("1000000"),
            price=Decimal("50000"),
            current_exposure=Decimal("0.7")
        )

        # Then: 10만원만 투자 (남은 10%)
        assert result.quantity == 2
        assert result.position_value == Decimal("100000")

    def test_fixed_max_positions_reached(self):
        """최대 포지션 수 도달 시 매수 불가"""
        # Given: 최대 5개 포지션
        sizer = PositionSizer(
            method=PositionSizingMethod.FIXED,
            max_positions=5
        )

        # When: 이미 5개 보유
        result = sizer.calculate_position_size(
            equity=Decimal("1000000"),
            price=Decimal("50000"),
            current_positions=5
        )

        # Then: 매수 불가
        assert result.quantity == 0
        assert "최대 포지션 수" in result.message

    def test_fixed_max_exposure_reached(self):
        """최대 투자 비중 도달 시 매수 불가"""
        # Given: 80% 최대 투자 비중
        sizer = PositionSizer(
            method=PositionSizingMethod.FIXED,
            max_total_exposure=0.8
        )

        # When: 이미 80% 투자됨
        result = sizer.calculate_position_size(
            equity=Decimal("1000000"),
            price=Decimal("50000"),
            current_exposure=Decimal("0.8")
        )

        # Then: 매수 불가
        assert result.quantity == 0
        assert "최대 투자 비중" in result.message


class TestPositionSizerVolatility:
    """Volatility Adjusted 테스트"""

    def test_volatility_low_vol_increases_position(self):
        """낮은 변동성 → 포지션 증가"""
        # Given: 목표 변동성 15%, 실제 변동성 7.5% (스칼라 2x)
        sizer = PositionSizer(
            method=PositionSizingMethod.VOLATILITY_ADJUSTED,
            max_position_pct=0.1,  # 10%
            target_volatility=0.15,
            max_volatility_scalar=2.0
        )

        # When: 7.5% 변동성
        result = sizer.calculate_position_size(
            equity=Decimal("1000000"),
            price=Decimal("50000"),
            volatility=0.075
        )

        # Then: 스칼라 2x → 20% 포지션
        assert result.adjustment_factor == Decimal("2")
        assert result.quantity == 4  # 20만원 / 5만원

    def test_volatility_high_vol_decreases_position(self):
        """높은 변동성 → 포지션 감소"""
        # Given: 목표 변동성 15%, 실제 변동성 30% (스칼라 0.5x)
        sizer = PositionSizer(
            method=PositionSizingMethod.VOLATILITY_ADJUSTED,
            max_position_pct=0.2,  # 20%
            target_volatility=0.15
        )

        # When: 30% 변동성
        result = sizer.calculate_position_size(
            equity=Decimal("1000000"),
            price=Decimal("50000"),
            volatility=0.30
        )

        # Then: 스칼라 0.5x → 10% 포지션
        assert result.adjustment_factor == Decimal("0.5")
        assert result.quantity == 2  # 10만원 / 5만원

    def test_volatility_capped_at_max_scalar(self):
        """변동성 스칼라 최대값 제한"""
        # Given: 최대 스칼라 2.0
        sizer = PositionSizer(
            method=PositionSizingMethod.VOLATILITY_ADJUSTED,
            max_position_pct=0.1,
            target_volatility=0.15,
            max_volatility_scalar=2.0
        )

        # When: 아주 낮은 변동성 (스칼라 3.0이 되어야 하지만)
        result = sizer.calculate_position_size(
            equity=Decimal("1000000"),
            price=Decimal("50000"),
            volatility=0.05  # 15% / 5% = 3.0 → 2.0으로 제한
        )

        # Then: 스칼라 2.0으로 제한
        assert result.adjustment_factor == Decimal("2.0")

    def test_volatility_missing_falls_back_to_fixed(self):
        """변동성 정보 없으면 고정 비율로 폴백"""
        sizer = PositionSizer(
            method=PositionSizingMethod.VOLATILITY_ADJUSTED,
            max_position_pct=0.2
        )

        result = sizer.calculate_position_size(
            equity=Decimal("1000000"),
            price=Decimal("50000"),
            volatility=None
        )

        # 고정 비율로 계산됨
        assert result.adjustment_factor == Decimal("1")


class TestPositionSizerKelly:
    """Kelly Criterion 테스트"""

    def test_kelly_basic(self):
        """기본 켈리 기준 포지션 사이징"""
        # Given: 60% 승률, 5% 평균 수익, 3% 평균 손실
        # Kelly = (0.6 * 0.05 - 0.4 * 0.03) / 0.05 = (0.03 - 0.012) / 0.05 = 0.36
        # Half Kelly = 0.18
        sizer = PositionSizer(
            method=PositionSizingMethod.KELLY,
            max_position_pct=0.3,
            kelly_fraction=0.5  # Half Kelly
        )

        result = sizer.calculate_position_size(
            equity=Decimal("1000000"),
            price=Decimal("50000"),
            win_rate=0.6,
            avg_win=0.05,
            avg_loss=0.03
        )

        # Kelly 36% * 0.5 = 18%
        assert result.method == PositionSizingMethod.KELLY
        assert result.quantity == 3  # 약 18만원 / 5만원 = 3주

    def test_kelly_negative_no_trade(self):
        """음수 켈리 → 매매 금지"""
        # Given: 40% 승률, 5% 평균 수익, 10% 평균 손실
        # Kelly = (0.4 * 0.05 - 0.6 * 0.10) / 0.05 = (0.02 - 0.06) / 0.05 = -0.8
        sizer = PositionSizer(
            method=PositionSizingMethod.KELLY,
            kelly_fraction=0.5
        )

        result = sizer.calculate_position_size(
            equity=Decimal("1000000"),
            price=Decimal("50000"),
            win_rate=0.4,
            avg_win=0.05,
            avg_loss=0.10
        )

        # Then: 매매 금지
        assert result.quantity == 0
        assert "매매 금지" in result.message

    def test_kelly_capped_at_max_position(self):
        """켈리 비율이 최대 포지션 비중 초과 시 제한"""
        # Given: 높은 Kelly 비율 (80% 승률, 10% 수익, 2% 손실)
        # Kelly = (0.8 * 0.10 - 0.2 * 0.02) / 0.10 = 0.076 / 0.10 = 0.76
        sizer = PositionSizer(
            method=PositionSizingMethod.KELLY,
            max_position_pct=0.2,  # 20%로 제한
            kelly_fraction=1.0  # Full Kelly
        )

        result = sizer.calculate_position_size(
            equity=Decimal("1000000"),
            price=Decimal("50000"),
            win_rate=0.8,
            avg_win=0.10,
            avg_loss=0.02
        )

        # Then: 20%로 제한됨
        assert result.quantity == 4  # 20만원 / 5만원

    def test_kelly_missing_params_falls_back_to_fixed(self):
        """켈리 파라미터 없으면 고정 비율로 폴백"""
        sizer = PositionSizer(
            method=PositionSizingMethod.KELLY,
            max_position_pct=0.2
        )

        result = sizer.calculate_position_size(
            equity=Decimal("1000000"),
            price=Decimal("50000"),
            win_rate=None,
            avg_win=None,
            avg_loss=None
        )

        # 고정 비율로 계산됨
        assert result.adjustment_factor == Decimal("1")


class TestPositionSizerConstraints:
    """제약 조건 테스트"""

    def test_jira_example_constraints(self):
        """Jira 티켓 예시: 20% 최대 포지션, 10개 최대, 80% 총 투자"""
        sizer = PositionSizer(
            method=PositionSizingMethod.FIXED,
            max_position_pct=0.2,
            max_positions=10,
            max_total_exposure=0.8
        )

        equity = Decimal("1000000")
        price = Decimal("50000")

        # 첫 번째 포지션
        result1 = sizer.calculate_position_size(
            equity=equity,
            price=price,
            current_positions=0,
            current_exposure=Decimal("0")
        )
        assert result1.quantity == 4  # 20%

        # 8개 포지션 보유 중 (60% 투자)
        result2 = sizer.calculate_position_size(
            equity=equity,
            price=price,
            current_positions=8,
            current_exposure=Decimal("0.6")
        )
        assert result2.quantity == 4  # 남은 20% 가능

        # 10개 포지션 도달
        result3 = sizer.calculate_position_size(
            equity=equity,
            price=price,
            current_positions=10,
            current_exposure=Decimal("0.6")
        )
        assert result3.quantity == 0  # 최대 포지션 도달

    def test_equal_weight_calculation(self):
        """동일 비중 포지션 금액 계산"""
        sizer = PositionSizer(
            max_positions=10,
            max_total_exposure=0.8
        )

        equal_weight = sizer.get_equal_weight_position_value(Decimal("1000000"))

        # 100만원 * 80% / 10 = 8만원
        assert equal_weight == Decimal("80000")

    def test_remaining_capacity(self):
        """남은 투자 여력 계산"""
        sizer = PositionSizer(
            max_positions=10,
            max_total_exposure=0.8
        )

        capacity = sizer.get_remaining_capacity(
            equity=Decimal("1000000"),
            current_positions=6,
            current_exposure=Decimal("0.5")
        )

        assert capacity["remaining_positions"] == 4
        assert capacity["remaining_exposure"] == Decimal("0.3")
        assert capacity["remaining_value"] == Decimal("300000")
        assert capacity["can_add_position"] is True


class TestPositionSizerFromModel:
    """RiskManagement 모델로부터 생성 테스트"""

    def test_from_risk_management(self):
        """RiskManagement 객체로부터 PositionSizer 생성"""
        from src.domain.strategy.models import RiskManagement

        risk_config = RiskManagement(
            max_position_pct=0.15
        )

        sizer = PositionSizer.from_risk_management(risk_config)

        assert sizer.max_position_pct == Decimal("0.15")
        assert sizer.method == PositionSizingMethod.FIXED


class TestPositionSizerRepr:
    """문자열 표현 테스트"""

    def test_repr(self):
        """__repr__ 출력 확인"""
        sizer = PositionSizer(
            method=PositionSizingMethod.VOLATILITY_ADJUSTED,
            max_position_pct=0.2,
            max_positions=10,
            max_total_exposure=0.8
        )

        repr_str = repr(sizer)

        assert "volatility" in repr_str
        assert "20%" in repr_str
        assert "10" in repr_str
        assert "80%" in repr_str
