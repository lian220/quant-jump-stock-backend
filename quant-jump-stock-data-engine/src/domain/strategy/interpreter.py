"""
Strategy Interpreter - DSL 기반 전략 실행기

exec/eval 없이 안전하게 전략을 실행합니다.
사전 정의된 지표 함수와 연산자만 사용합니다.

사용 예시:
    interpreter = StrategyInterpreter()
    result = interpreter.execute(strategy, market_data)
"""
import logging
from typing import Dict, List, Optional, Any, Union
from datetime import datetime
import pandas as pd

from .models import (
    StrategyDefinition,
    Rule,
    Condition,
    ConditionOperator,
    IndicatorType,
)
from .indicators import (
    calculate_sma,
    calculate_ema,
    calculate_rsi,
    calculate_macd,
    calculate_bollinger_bands,
    calculate_atr,
    calculate_stochastic,
    calculate_obv,
    crosses_above,
    crosses_below,
)
from src.domain.common.exceptions import (
    StrategyError,
    IndicatorError,
    InsufficientDataError,
)

logger = logging.getLogger(__name__)


class StrategyInterpreter:
    """
    DSL 기반 전략 해석기

    - exec/eval 사용 안함 (보안)
    - 사전 정의된 지표 함수만 사용
    - 조건 평가는 딕셔너리 기반
    """

    def __init__(self):
        # 비교 연산자 함수 매핑
        self._comparison_operators = {
            ConditionOperator.GREATER_THAN: lambda a, b: a > b,
            ConditionOperator.GREATER_EQUAL: lambda a, b: a >= b,
            ConditionOperator.LESS_THAN: lambda a, b: a < b,
            ConditionOperator.LESS_EQUAL: lambda a, b: a <= b,
            ConditionOperator.EQUALS: lambda a, b: abs(a - b) < 1e-6,
            ConditionOperator.NOT_EQUALS: lambda a, b: abs(a - b) >= 1e-6,
        }

    def execute(
        self,
        strategy: StrategyDefinition,
        market_data: pd.DataFrame,
        target_date: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        전략 실행

        Args:
            strategy: DSL 기반 전략 정의
            market_data: OHLCV 데이터 (columns: open, high, low, close, volume)
            target_date: 분석 기준일 (None이면 마지막 날짜)

        Returns:
            {
                "strategy_id": "...",
                "analysis_date": "2024-01-15",
                "signals": [{"date": "...", "type": "buy", "rule": "...", "confidence": 0.85}],
                "indicators": {"sma_20": [...], "rsi_14": [...]},
                "risk_checks": {"stop_loss_triggered": False, ...}
            }
        """
        if market_data.empty:
            return self._empty_result(strategy.strategy_id)

        try:
            # 1. 모든 필요한 지표 계산
            indicators = self._calculate_all_indicators(strategy.rules, market_data)

            # 2. 분석 기준일 결정
            if target_date:
                try:
                    analysis_idx = pd.to_datetime(target_date)
                    if analysis_idx not in market_data.index:
                        # 가장 가까운 날짜 찾기
                        closest_idx = market_data.index[
                            market_data.index.get_indexer([analysis_idx], method='nearest')[0]
                        ]
                        analysis_idx = closest_idx
                except Exception:
                    analysis_idx = market_data.index[-1]
            else:
                analysis_idx = market_data.index[-1]

            # 3. 각 규칙 평가
            signals = []
            for rule in strategy.rules:
                signal = self._evaluate_rule(rule, indicators, market_data, analysis_idx)
                if signal:
                    signals.append(signal)

            # 4. 리스크 체크
            risk_checks = self._check_risk_management(
                strategy.risk_management,
                market_data,
                signals
            )

            return {
                "strategy_id": strategy.strategy_id,
                "strategy_name": strategy.name,
                "analysis_date": str(analysis_idx.date()) if hasattr(analysis_idx, 'date') else str(analysis_idx),
                "signals": signals,
                "indicators": self._serialize_indicators(indicators),
                "risk_checks": risk_checks,
                "executed_at": datetime.utcnow().isoformat()
            }

        except Exception as e:
            logger.error(f"Strategy execution failed: {strategy.strategy_id} - {e}")
            raise StrategyError(f"Execution failed: {e}", strategy.strategy_id)

    def _calculate_all_indicators(
        self,
        rules: List[Rule],
        data: pd.DataFrame
    ) -> Dict[str, pd.Series]:
        """모든 필요한 지표 계산"""
        indicators = {}
        close = data['close']

        # 필요한 지표 수집
        needed_indicators = set()
        for rule in rules:
            for condition in rule.conditions:
                needed_indicators.add((condition.indicator, tuple(sorted(condition.params.items()))))
                # value가 지표 참조인 경우
                if isinstance(condition.value, str) and "_" in condition.value:
                    parts = condition.value.rsplit("_", 1)
                    if len(parts) == 2:
                        try:
                            indicator_type = IndicatorType(parts[0])
                            period = int(parts[1])
                            needed_indicators.add((indicator_type, (("period", period),)))
                        except (ValueError, KeyError):
                            pass

        # 지표 계산
        for indicator_type, params_tuple in needed_indicators:
            params = dict(params_tuple)
            key = self._get_indicator_key(indicator_type, params)

            if key in indicators:
                continue

            try:
                if indicator_type == IndicatorType.SMA:
                    period = params.get("period", 20)
                    indicators[key] = calculate_sma(close, period)

                elif indicator_type == IndicatorType.EMA:
                    period = params.get("period", 20)
                    indicators[key] = calculate_ema(close, period)

                elif indicator_type == IndicatorType.RSI:
                    period = params.get("period", 14)
                    indicators[key] = calculate_rsi(close, period)

                elif indicator_type in [IndicatorType.MACD, IndicatorType.MACD_SIGNAL, IndicatorType.MACD_HIST]:
                    if "macd" not in indicators:
                        short = params.get("short_period", 12)
                        long = params.get("long_period", 26)
                        signal = params.get("signal_period", 9)
                        macd, signal_line, hist = calculate_macd(close, short, long, signal)
                        indicators["macd"] = macd
                        indicators["macd_signal"] = signal_line
                        indicators["macd_hist"] = hist

                elif indicator_type in [IndicatorType.BOLLINGER_UPPER, IndicatorType.BOLLINGER_MIDDLE, IndicatorType.BOLLINGER_LOWER]:
                    period = params.get("period", 20)
                    std_dev = params.get("std_dev", 2.0)
                    bb_key = f"bollinger_{period}"
                    if f"{bb_key}_upper" not in indicators:
                        upper, middle, lower = calculate_bollinger_bands(close, period, std_dev)
                        indicators[f"{bb_key}_upper"] = upper
                        indicators[f"{bb_key}_middle"] = middle
                        indicators[f"{bb_key}_lower"] = lower

                elif indicator_type == IndicatorType.VOLUME:
                    if 'volume' in data.columns:
                        indicators["volume"] = data['volume']

                elif indicator_type == IndicatorType.PRICE:
                    indicators["price"] = close

                elif indicator_type == IndicatorType.ATR:
                    period = params.get("period", 14)
                    if all(col in data.columns for col in ['high', 'low', 'close']):
                        indicators[key] = calculate_atr(
                            data['high'], data['low'], close, period
                        )

                elif indicator_type in [IndicatorType.STOCHASTIC_K, IndicatorType.STOCHASTIC_D]:
                    k_period = params.get("k_period", 14)
                    d_period = params.get("d_period", 3)
                    stoch_key = f"stochastic_{k_period}"
                    if f"{stoch_key}_k" not in indicators:
                        if all(col in data.columns for col in ['high', 'low', 'close']):
                            k, d = calculate_stochastic(
                                data['high'], data['low'], close, k_period, d_period
                            )
                            indicators[f"{stoch_key}_k"] = k
                            indicators[f"{stoch_key}_d"] = d

                elif indicator_type == IndicatorType.OBV:
                    if 'volume' in data.columns:
                        indicators["obv"] = calculate_obv(close, data['volume'])

            except InsufficientDataError as e:
                logger.warning(f"Insufficient data for {key}: {e}")
                indicators[key] = pd.Series(dtype=float)

        return indicators

    def _get_indicator_key(self, indicator_type: IndicatorType, params: Dict) -> str:
        """지표 키 생성"""
        if indicator_type in [IndicatorType.SMA, IndicatorType.EMA]:
            return f"{indicator_type.value}_{params.get('period', '')}"
        elif indicator_type == IndicatorType.RSI:
            return f"rsi_{params.get('period', 14)}"
        elif indicator_type in [IndicatorType.MACD, IndicatorType.MACD_SIGNAL, IndicatorType.MACD_HIST]:
            return indicator_type.value
        elif indicator_type in [IndicatorType.BOLLINGER_UPPER, IndicatorType.BOLLINGER_MIDDLE, IndicatorType.BOLLINGER_LOWER]:
            period = params.get("period", 20)
            suffix = indicator_type.value.split("_")[-1]
            return f"bollinger_{period}_{suffix}"
        elif indicator_type == IndicatorType.ATR:
            return f"atr_{params.get('period', 14)}"
        elif indicator_type in [IndicatorType.STOCHASTIC_K, IndicatorType.STOCHASTIC_D]:
            k_period = params.get("k_period", 14)
            suffix = "k" if indicator_type == IndicatorType.STOCHASTIC_K else "d"
            return f"stochastic_{k_period}_{suffix}"
        elif indicator_type == IndicatorType.OBV:
            return "obv"
        else:
            return indicator_type.value

    def _evaluate_rule(
        self,
        rule: Rule,
        indicators: Dict[str, pd.Series],
        data: pd.DataFrame,
        analysis_idx: pd.Timestamp
    ) -> Optional[Dict]:
        """단일 규칙 평가"""
        results = []

        for condition in rule.conditions:
            result = self._evaluate_condition(condition, indicators, data, analysis_idx)
            if result is None:
                # 조건 평가 실패 시 해당 규칙 스킵
                return None
            results.append(result)

        if not results:
            return None

        # 논리 연산 적용
        if rule.logic == "and":
            passed = all(results)
        else:  # "or"
            passed = any(results)

        if passed:
            return {
                "date": str(analysis_idx.date()) if hasattr(analysis_idx, 'date') else str(analysis_idx),
                "type": rule.signal_type,
                "rule": rule.name,
                "confidence": rule.weight,
                "conditions_met": sum(results),
                "conditions_total": len(results)
            }

        return None

    def _evaluate_condition(
        self,
        condition: Condition,
        indicators: Dict[str, pd.Series],
        data: pd.DataFrame,
        analysis_idx: pd.Timestamp
    ) -> Optional[bool]:
        """단일 조건 평가"""
        try:
            # 지표 값 가져오기
            indicator_key = self._get_indicator_key(condition.indicator, condition.params)
            indicator_series = indicators.get(indicator_key)

            if indicator_series is None or indicator_series.empty:
                return None

            if analysis_idx not in indicator_series.index:
                return None

            current_value = indicator_series.loc[analysis_idx]
            if pd.isna(current_value):
                return None

            # 비교 대상 값 결정
            if isinstance(condition.value, str):
                # 다른 지표 참조
                compare_series = indicators.get(condition.value)
                if compare_series is None or compare_series.empty:
                    return None
                if analysis_idx not in compare_series.index:
                    return None
                compare_value = compare_series.loc[analysis_idx]
            else:
                compare_value = float(condition.value)

            if pd.isna(compare_value):
                return None

            # 연산자 적용
            if condition.operator == ConditionOperator.CROSSES_ABOVE:
                cross_result = crosses_above(indicator_series,
                    indicators.get(condition.value) if isinstance(condition.value, str)
                    else pd.Series([condition.value] * len(indicator_series), index=indicator_series.index)
                )
                return bool(cross_result.loc[analysis_idx]) if analysis_idx in cross_result.index else None

            elif condition.operator == ConditionOperator.CROSSES_BELOW:
                cross_result = crosses_below(indicator_series,
                    indicators.get(condition.value) if isinstance(condition.value, str)
                    else pd.Series([condition.value] * len(indicator_series), index=indicator_series.index)
                )
                return bool(cross_result.loc[analysis_idx]) if analysis_idx in cross_result.index else None

            else:
                # 단순 비교 연산
                operator_func = self._comparison_operators.get(condition.operator)
                if operator_func:
                    return operator_func(current_value, compare_value)

            return None

        except Exception as e:
            logger.warning(f"Condition evaluation failed: {e}")
            return None

    def _check_risk_management(
        self,
        risk,
        data: pd.DataFrame,
        signals: List[Dict]
    ) -> Dict:
        """리스크 관리 체크"""
        # 기본 리스크 체크 (추후 포지션 추적과 연동 필요)
        return {
            "stop_loss_pct": risk.stop_loss_pct,
            "take_profit_pct": risk.take_profit_pct,
            "max_position_pct": risk.max_position_pct,
            "stop_loss_triggered": False,
            "take_profit_triggered": False,
            "max_drawdown_ok": True
        }

    def _serialize_indicators(self, indicators: Dict[str, pd.Series]) -> Dict[str, List]:
        """지표를 직렬화 가능한 형태로 변환"""
        result = {}
        for key, series in indicators.items():
            if isinstance(series, pd.Series):
                # 마지막 10개 값만 반환 (전체는 너무 큼)
                result[key] = {
                    "last_value": float(series.iloc[-1]) if not series.empty and not pd.isna(series.iloc[-1]) else None,
                    "recent_values": series.tail(10).tolist()
                }
        return result

    def _empty_result(self, strategy_id: str) -> Dict:
        """빈 결과 반환"""
        return {
            "strategy_id": strategy_id,
            "analysis_date": None,
            "signals": [],
            "indicators": {},
            "risk_checks": {},
            "executed_at": datetime.utcnow().isoformat(),
            "error": "No market data available"
        }
