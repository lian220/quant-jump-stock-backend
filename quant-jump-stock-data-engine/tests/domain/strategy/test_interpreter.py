"""
Strategy Interpreter Tests

SCRUM-185: 기술 분석 지표와 백테스트 엔진 통합 테스트
ATR, Stochastic, OBV 지표 지원 추가 검증
"""

import pytest
import pandas as pd
import numpy as np
from datetime import datetime, timedelta

from domain.strategy.interpreter import StrategyInterpreter
from domain.strategy.models import (
    StrategyDefinition,
    Rule,
    Condition,
    ConditionOperator,
    IndicatorType,
    RiskManagement,
)


@pytest.fixture
def interpreter():
    """StrategyInterpreter 인스턴스"""
    return StrategyInterpreter()


@pytest.fixture
def sample_ohlcv_data():
    """테스트용 OHLCV 데이터 (60일)"""
    dates = pd.date_range(start="2024-01-01", periods=60, freq="D")
    np.random.seed(42)

    # 현실적인 주가 시뮬레이션
    close_prices = [100]
    for i in range(59):
        change = np.random.normal(0, 2)
        close_prices.append(max(close_prices[-1] + change, 50))

    close = np.array(close_prices)
    high = close * (1 + np.random.uniform(0.01, 0.03, 60))
    low = close * (1 - np.random.uniform(0.01, 0.03, 60))
    open_prices = (close + low) / 2 + np.random.uniform(-1, 1, 60)
    volume = np.random.randint(500000, 2000000, 60)

    return pd.DataFrame({
        "open": open_prices,
        "high": high,
        "low": low,
        "close": close,
        "volume": volume
    }, index=dates)


class TestGetIndicatorKey:
    """_get_indicator_key 메서드 테스트"""

    def test_atr_key_default_period(self, interpreter):
        """ATR 키 생성 (기본 기간)"""
        key = interpreter._get_indicator_key(
            IndicatorType.ATR,
            {}
        )
        assert key == "atr_14"

    def test_atr_key_custom_period(self, interpreter):
        """ATR 키 생성 (사용자 정의 기간)"""
        key = interpreter._get_indicator_key(
            IndicatorType.ATR,
            {"period": 20}
        )
        assert key == "atr_20"

    def test_stochastic_k_key_default(self, interpreter):
        """Stochastic %K 키 생성 (기본)"""
        key = interpreter._get_indicator_key(
            IndicatorType.STOCHASTIC_K,
            {}
        )
        assert key == "stochastic_14_k"

    def test_stochastic_d_key_default(self, interpreter):
        """Stochastic %D 키 생성 (기본)"""
        key = interpreter._get_indicator_key(
            IndicatorType.STOCHASTIC_D,
            {}
        )
        assert key == "stochastic_14_d"

    def test_stochastic_key_custom_period(self, interpreter):
        """Stochastic 키 생성 (사용자 정의 기간)"""
        key_k = interpreter._get_indicator_key(
            IndicatorType.STOCHASTIC_K,
            {"k_period": 21}
        )
        key_d = interpreter._get_indicator_key(
            IndicatorType.STOCHASTIC_D,
            {"k_period": 21}
        )
        assert key_k == "stochastic_21_k"
        assert key_d == "stochastic_21_d"

    def test_obv_key(self, interpreter):
        """OBV 키 생성"""
        key = interpreter._get_indicator_key(
            IndicatorType.OBV,
            {}
        )
        assert key == "obv"


class TestCalculateAllIndicators:
    """_calculate_all_indicators 메서드 테스트"""

    def test_calculate_atr(self, interpreter, sample_ohlcv_data):
        """ATR 지표 계산"""
        rule = Rule(
            name="atr_test",
            signal_type="buy",
            conditions=[
                Condition(
                    indicator=IndicatorType.ATR,
                    params={"period": 14},
                    operator=ConditionOperator.GREATER_THAN,
                    value=1.0
                )
            ]
        )

        indicators = interpreter._calculate_all_indicators([rule], sample_ohlcv_data)

        assert "atr_14" in indicators
        assert len(indicators["atr_14"]) == len(sample_ohlcv_data)
        # ATR은 항상 양수
        valid_values = indicators["atr_14"].dropna()
        assert all(v >= 0 for v in valid_values)

    def test_calculate_stochastic(self, interpreter, sample_ohlcv_data):
        """Stochastic 지표 계산"""
        rule = Rule(
            name="stochastic_test",
            signal_type="buy",
            conditions=[
                Condition(
                    indicator=IndicatorType.STOCHASTIC_K,
                    params={"k_period": 14, "d_period": 3},
                    operator=ConditionOperator.LESS_THAN,
                    value=20
                )
            ]
        )

        indicators = interpreter._calculate_all_indicators([rule], sample_ohlcv_data)

        assert "stochastic_14_k" in indicators
        assert "stochastic_14_d" in indicators

        # Stochastic은 0-100 범위
        k_valid = indicators["stochastic_14_k"].dropna()
        d_valid = indicators["stochastic_14_d"].dropna()
        assert all(0 <= v <= 100 for v in k_valid)
        assert all(0 <= v <= 100 for v in d_valid)

    def test_calculate_obv(self, interpreter, sample_ohlcv_data):
        """OBV 지표 계산"""
        rule = Rule(
            name="obv_test",
            signal_type="buy",
            conditions=[
                Condition(
                    indicator=IndicatorType.OBV,
                    params={},
                    operator=ConditionOperator.GREATER_THAN,
                    value=0
                )
            ]
        )

        indicators = interpreter._calculate_all_indicators([rule], sample_ohlcv_data)

        assert "obv" in indicators
        assert len(indicators["obv"]) == len(sample_ohlcv_data)

    def test_calculate_multiple_new_indicators(self, interpreter, sample_ohlcv_data):
        """여러 새 지표 동시 계산"""
        rules = [
            Rule(
                name="multi_indicator_test",
                signal_type="buy",
                conditions=[
                    Condition(
                        indicator=IndicatorType.ATR,
                        params={"period": 14},
                        operator=ConditionOperator.GREATER_THAN,
                        value=1.0
                    ),
                    Condition(
                        indicator=IndicatorType.STOCHASTIC_K,
                        params={"k_period": 14},
                        operator=ConditionOperator.LESS_THAN,
                        value=20
                    ),
                    Condition(
                        indicator=IndicatorType.OBV,
                        params={},
                        operator=ConditionOperator.GREATER_THAN,
                        value=0
                    )
                ]
            )
        ]

        indicators = interpreter._calculate_all_indicators(rules, sample_ohlcv_data)

        assert "atr_14" in indicators
        assert "stochastic_14_k" in indicators
        assert "stochastic_14_d" in indicators
        assert "obv" in indicators


class TestStrategyExecution:
    """전략 실행 통합 테스트"""

    def test_execute_strategy_with_atr(self, interpreter, sample_ohlcv_data):
        """ATR 조건이 포함된 전략 실행"""
        strategy = StrategyDefinition(
            strategy_id="atr_test_strategy",
            name="ATR Test Strategy",
            rules=[
                Rule(
                    name="atr_volatility_check",
                    signal_type="buy",
                    conditions=[
                        Condition(
                            indicator=IndicatorType.ATR,
                            params={"period": 14},
                            operator=ConditionOperator.GREATER_THAN,
                            value=0.5
                        )
                    ],
                    weight=0.8
                )
            ],
            risk_management=RiskManagement()
        )

        result = interpreter.execute(strategy, sample_ohlcv_data)

        assert result["strategy_id"] == "atr_test_strategy"
        assert "indicators" in result
        assert "signals" in result
        assert "risk_checks" in result

    def test_execute_strategy_with_stochastic_oversold(self, interpreter, sample_ohlcv_data):
        """Stochastic 과매도 조건 전략 실행"""
        strategy = StrategyDefinition(
            strategy_id="stochastic_oversold",
            name="Stochastic Oversold Strategy",
            rules=[
                Rule(
                    name="stochastic_oversold_buy",
                    signal_type="buy",
                    conditions=[
                        Condition(
                            indicator=IndicatorType.STOCHASTIC_K,
                            params={"k_period": 14, "d_period": 3},
                            operator=ConditionOperator.LESS_THAN,
                            value=20
                        ),
                        Condition(
                            indicator=IndicatorType.STOCHASTIC_D,
                            params={"k_period": 14, "d_period": 3},
                            operator=ConditionOperator.LESS_THAN,
                            value=20
                        )
                    ],
                    logic="and",
                    weight=0.9
                )
            ],
            risk_management=RiskManagement()
        )

        result = interpreter.execute(strategy, sample_ohlcv_data)

        assert result["strategy_id"] == "stochastic_oversold"
        assert "stochastic_14_k" in result["indicators"] or len(result["indicators"]) > 0

    def test_execute_strategy_with_obv_trend(self, interpreter, sample_ohlcv_data):
        """OBV 추세 확인 전략 실행"""
        strategy = StrategyDefinition(
            strategy_id="obv_trend",
            name="OBV Trend Strategy",
            rules=[
                Rule(
                    name="obv_positive",
                    signal_type="buy",
                    conditions=[
                        Condition(
                            indicator=IndicatorType.OBV,
                            params={},
                            operator=ConditionOperator.GREATER_THAN,
                            value=0
                        )
                    ],
                    weight=0.7
                )
            ],
            risk_management=RiskManagement()
        )

        result = interpreter.execute(strategy, sample_ohlcv_data)

        assert result["strategy_id"] == "obv_trend"

    def test_execute_combined_strategy(self, interpreter, sample_ohlcv_data):
        """기존 지표 + 신규 지표 결합 전략 실행"""
        strategy = StrategyDefinition(
            strategy_id="combined_strategy",
            name="Combined Indicators Strategy",
            description="RSI + Stochastic + ATR 결합 전략",
            rules=[
                Rule(
                    name="combined_buy",
                    signal_type="buy",
                    conditions=[
                        Condition(
                            indicator=IndicatorType.RSI,
                            params={"period": 14},
                            operator=ConditionOperator.LESS_THAN,
                            value=30
                        ),
                        Condition(
                            indicator=IndicatorType.STOCHASTIC_K,
                            params={"k_period": 14},
                            operator=ConditionOperator.LESS_THAN,
                            value=20
                        ),
                        Condition(
                            indicator=IndicatorType.ATR,
                            params={"period": 14},
                            operator=ConditionOperator.GREATER_THAN,
                            value=0.1
                        )
                    ],
                    logic="and",
                    weight=1.0
                ),
                Rule(
                    name="combined_sell",
                    signal_type="sell",
                    conditions=[
                        Condition(
                            indicator=IndicatorType.RSI,
                            params={"period": 14},
                            operator=ConditionOperator.GREATER_THAN,
                            value=70
                        ),
                        Condition(
                            indicator=IndicatorType.STOCHASTIC_K,
                            params={"k_period": 14},
                            operator=ConditionOperator.GREATER_THAN,
                            value=80
                        )
                    ],
                    logic="and",
                    weight=1.0
                )
            ],
            risk_management=RiskManagement(
                stop_loss_pct=0.05,
                take_profit_pct=0.15
            )
        )

        result = interpreter.execute(strategy, sample_ohlcv_data)

        assert result["strategy_id"] == "combined_strategy"
        assert "rsi_14" in result["indicators"]
        # 지표들이 계산되었는지 확인
        indicators_keys = list(result["indicators"].keys())
        assert any("stochastic" in k for k in indicators_keys)
        assert any("atr" in k for k in indicators_keys)


class TestEdgeCases:
    """엣지 케이스 테스트"""

    def test_empty_data(self, interpreter):
        """빈 데이터 처리"""
        strategy = StrategyDefinition(
            strategy_id="test",
            name="Test",
            rules=[
                Rule(
                    name="test_rule",
                    signal_type="buy",
                    conditions=[
                        Condition(
                            indicator=IndicatorType.ATR,
                            params={"period": 14},
                            operator=ConditionOperator.GREATER_THAN,
                            value=1.0
                        )
                    ]
                )
            ]
        )

        empty_data = pd.DataFrame()
        result = interpreter.execute(strategy, empty_data)

        assert result["signals"] == []
        assert "error" in result

    def test_insufficient_data_for_atr(self, interpreter):
        """ATR 계산에 필요한 데이터 부족"""
        # 5일 데이터만 (ATR 14일 기본값보다 적음)
        dates = pd.date_range(start="2024-01-01", periods=5, freq="D")
        data = pd.DataFrame({
            "open": [100, 101, 102, 103, 104],
            "high": [105, 106, 107, 108, 109],
            "low": [95, 96, 97, 98, 99],
            "close": [102, 103, 104, 105, 106],
            "volume": [1000000] * 5
        }, index=dates)

        strategy = StrategyDefinition(
            strategy_id="test",
            name="Test",
            rules=[
                Rule(
                    name="test_rule",
                    signal_type="buy",
                    conditions=[
                        Condition(
                            indicator=IndicatorType.ATR,
                            params={"period": 14},
                            operator=ConditionOperator.GREATER_THAN,
                            value=1.0
                        )
                    ]
                )
            ]
        )

        result = interpreter.execute(strategy, data)
        # 데이터 부족해도 에러 없이 처리
        assert "strategy_id" in result

    def test_missing_volume_for_obv(self, interpreter):
        """OBV 계산 시 볼륨 데이터 없음"""
        dates = pd.date_range(start="2024-01-01", periods=30, freq="D")
        data = pd.DataFrame({
            "open": np.random.uniform(100, 110, 30),
            "high": np.random.uniform(110, 120, 30),
            "low": np.random.uniform(90, 100, 30),
            "close": np.random.uniform(100, 110, 30),
            # volume 없음
        }, index=dates)

        rule = Rule(
            name="obv_test",
            signal_type="buy",
            conditions=[
                Condition(
                    indicator=IndicatorType.OBV,
                    params={},
                    operator=ConditionOperator.GREATER_THAN,
                    value=0
                )
            ]
        )

        indicators = interpreter._calculate_all_indicators([rule], data)

        # 볼륨 없으면 OBV 계산 안됨
        assert "obv" not in indicators
