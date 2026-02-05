"""
Domain Model Tests

Strategy DSL 모델에 대한 단위 테스트.
"""

import pytest
from pydantic import ValidationError

from domain.strategy.models import (
    IndicatorType,
    SignalType,
    ConditionOperator,
    Condition,
    Rule,
    RiskManagement,
    StrategyDefinition,
)


class TestCondition:
    """Condition 모델 테스트"""

    def test_create_valid_condition(self):
        """유효한 조건 생성"""
        condition = Condition(
            indicator=IndicatorType.RSI,
            params={"period": 14},
            operator=ConditionOperator.LESS_THAN,
            value=30
        )

        assert condition.indicator == IndicatorType.RSI
        assert condition.params["period"] == 14
        assert condition.operator == ConditionOperator.LESS_THAN
        assert condition.value == 30

    def test_condition_with_string_value(self):
        """지표 참조 값으로 조건 생성"""
        condition = Condition(
            indicator=IndicatorType.SMA,
            params={"period": 20},
            operator=ConditionOperator.CROSSES_ABOVE,
            value="sma_50"
        )

        assert condition.value == "sma_50"

    def test_get_indicator_key_sma(self):
        """SMA 지표 키 생성"""
        condition = Condition(
            indicator=IndicatorType.SMA,
            params={"period": 20},
            operator=ConditionOperator.GREATER_THAN,
            value=100
        )

        assert condition.get_indicator_key() == "sma_20"

    def test_get_indicator_key_rsi(self):
        """RSI 지표 키 생성"""
        condition = Condition(
            indicator=IndicatorType.RSI,
            params={"period": 14},
            operator=ConditionOperator.LESS_THAN,
            value=30
        )

        assert condition.get_indicator_key() == "rsi_14"

    def test_get_indicator_key_macd(self):
        """MACD 지표 키 생성"""
        condition = Condition(
            indicator=IndicatorType.MACD,
            params={},
            operator=ConditionOperator.GREATER_THAN,
            value=0
        )

        assert condition.get_indicator_key() == "macd"

    def test_invalid_params_type(self):
        """잘못된 params 타입 검증"""
        with pytest.raises(ValidationError):
            Condition(
                indicator=IndicatorType.RSI,
                params="invalid",  # should be dict
                operator=ConditionOperator.LESS_THAN,
                value=30
            )


class TestRule:
    """Rule 모델 테스트"""

    def test_create_buy_rule(self):
        """매수 규칙 생성"""
        rule = Rule(
            name="test_buy_rule",
            signal_type="buy",
            conditions=[
                Condition(
                    indicator=IndicatorType.RSI,
                    params={"period": 14},
                    operator=ConditionOperator.LESS_THAN,
                    value=30
                )
            ],
            logic="and",
            weight=1.0
        )

        assert rule.name == "test_buy_rule"
        assert rule.signal_type == "buy"
        assert len(rule.conditions) == 1

    def test_create_sell_rule(self):
        """매도 규칙 생성"""
        rule = Rule(
            name="test_sell_rule",
            signal_type="sell",
            conditions=[
                Condition(
                    indicator=IndicatorType.RSI,
                    params={"period": 14},
                    operator=ConditionOperator.GREATER_THAN,
                    value=70
                )
            ]
        )

        assert rule.signal_type == "sell"

    def test_rule_requires_conditions(self):
        """규칙은 최소 1개 조건 필요"""
        with pytest.raises(ValidationError):
            Rule(
                name="empty_rule",
                signal_type="buy",
                conditions=[]
            )

    def test_rule_default_values(self):
        """규칙 기본값 확인"""
        rule = Rule(
            name="test_rule",
            signal_type="buy",
            conditions=[
                Condition(
                    indicator=IndicatorType.PRICE,
                    params={},
                    operator=ConditionOperator.GREATER_THAN,
                    value=100
                )
            ]
        )

        assert rule.logic == "and"
        assert rule.weight == 1.0
        assert rule.description is None


class TestRiskManagement:
    """RiskManagement 모델 테스트"""

    def test_default_risk_management(self):
        """기본 리스크 관리 설정"""
        rm = RiskManagement()

        assert rm.stop_loss_pct == 0.05
        assert rm.take_profit_pct == 0.15
        assert rm.max_position_pct == 0.1
        assert rm.max_drawdown_pct == 0.2
        assert rm.trailing_stop is False

    def test_custom_risk_management(self):
        """커스텀 리스크 관리 설정"""
        rm = RiskManagement(
            stop_loss_pct=0.03,
            take_profit_pct=0.20,
            trailing_stop=True,
            trailing_stop_pct=0.08
        )

        assert rm.stop_loss_pct == 0.03
        assert rm.take_profit_pct == 0.20
        assert rm.trailing_stop is True

    def test_risk_management_validation(self):
        """리스크 관리 값 검증"""
        with pytest.raises(ValidationError):
            RiskManagement(stop_loss_pct=1.5)  # > 1.0

        with pytest.raises(ValidationError):
            RiskManagement(max_position_pct=-0.1)  # < 0


class TestStrategyDefinition:
    """StrategyDefinition 모델 테스트"""

    def test_create_strategy(self, sample_strategy_definition):
        """전략 정의 생성"""
        strategy = StrategyDefinition(**sample_strategy_definition)

        assert strategy.strategy_id == "test_golden_cross"
        assert strategy.name == "Test Golden Cross Strategy"
        assert len(strategy.rules) == 2

    def test_get_buy_rules(self, sample_strategy_definition):
        """매수 규칙 필터링"""
        strategy = StrategyDefinition(**sample_strategy_definition)
        buy_rules = strategy.get_buy_rules()

        assert len(buy_rules) == 1
        assert buy_rules[0].signal_type == "buy"

    def test_get_sell_rules(self, sample_strategy_definition):
        """매도 규칙 필터링"""
        strategy = StrategyDefinition(**sample_strategy_definition)
        sell_rules = strategy.get_sell_rules()

        assert len(sell_rules) == 1
        assert sell_rules[0].signal_type == "sell"

    def test_get_all_indicators(self, sample_strategy_definition):
        """사용 지표 추출"""
        strategy = StrategyDefinition(**sample_strategy_definition)
        indicators = strategy.get_all_indicators()

        assert "sma_20" in indicators
        assert "sma_50" in indicators
        assert "rsi_14" in indicators

    def test_strategy_requires_rules(self):
        """전략은 최소 1개 규칙 필요"""
        with pytest.raises(ValidationError):
            StrategyDefinition(
                strategy_id="empty",
                name="Empty Strategy",
                rules=[]
            )

    def test_strategy_default_version(self):
        """전략 기본 버전"""
        strategy = StrategyDefinition(
            strategy_id="test",
            name="Test",
            rules=[
                Rule(
                    name="rule1",
                    signal_type="buy",
                    conditions=[
                        Condition(
                            indicator=IndicatorType.PRICE,
                            params={},
                            operator=ConditionOperator.GREATER_THAN,
                            value=100
                        )
                    ]
                )
            ]
        )

        assert strategy.version == "1.0"


class TestIndicatorType:
    """IndicatorType Enum 테스트"""

    def test_indicator_values(self):
        """지표 타입 값 확인"""
        assert IndicatorType.SMA.value == "sma"
        assert IndicatorType.EMA.value == "ema"
        assert IndicatorType.RSI.value == "rsi"
        assert IndicatorType.MACD.value == "macd"


class TestConditionOperator:
    """ConditionOperator Enum 테스트"""

    def test_operator_values(self):
        """연산자 값 확인"""
        assert ConditionOperator.GREATER_THAN.value == "gt"
        assert ConditionOperator.LESS_THAN.value == "lt"
        assert ConditionOperator.CROSSES_ABOVE.value == "crosses_above"
        assert ConditionOperator.CROSSES_BELOW.value == "crosses_below"
