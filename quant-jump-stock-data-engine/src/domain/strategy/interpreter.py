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
    crosses_above,
    crosses_below,
)
from domain.common.exceptions import (
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

    def pre_compute_indicators(
        self,
        strategy: StrategyDefinition,
        data: pd.DataFrame
    ) -> Dict[str, pd.Series]:
        """
        전체 기간 지표 사전 계산 (백테스트 최적화용)

        전체 데이터에 대해 지표를 한 번만 계산하여 일별 루프에서
        중복 계산을 방지합니다. O(n²) → O(n) 최적화.

        Args:
            strategy: 전략 정의
            data: 전체 기간 OHLCV 데이터

        Returns:
            {indicator_key: pd.Series} 딕셔너리
        """
        return self._calculate_all_indicators(strategy.rules, data)

    def execute_with_precomputed(
        self,
        strategy: StrategyDefinition,
        precomputed_indicators: Dict[str, pd.Series],
        market_data: pd.DataFrame,
        target_date: str
    ) -> Dict[str, Any]:
        """
        사전 계산된 지표를 사용하여 전략 실행

        _calculate_all_indicators() 호출을 건너뛰고 사전 계산된 지표를
        직접 사용하여 규칙을 평가합니다.

        Args:
            strategy: 전략 정의
            precomputed_indicators: pre_compute_indicators()의 결과
            market_data: OHLCV 데이터 (analysis_idx 결정용)
            target_date: 분석 기준일

        Returns:
            실행 결과 딕셔너리
        """
        if market_data.empty:
            return self._empty_result(strategy.strategy_id)

        try:
            # 분석 기준일 결정
            analysis_idx = pd.to_datetime(target_date)
            if analysis_idx not in market_data.index:
                closest_idx = market_data.index[
                    market_data.index.get_indexer([analysis_idx], method='nearest')[0]
                ]
                analysis_idx = closest_idx

            # 각 규칙 평가 (사전 계산된 지표 사용)
            signals = []
            for rule in strategy.rules:
                signal = self._evaluate_rule(rule, precomputed_indicators, market_data, analysis_idx)
                if signal:
                    signals.append(signal)

            # 리스크 체크
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
                "indicators": {},
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
                hashable_params = tuple(sorted(
                    (k, tuple(v) if isinstance(v, list) else v)
                    for k, v in condition.params.items()
                ))
                needed_indicators.add((condition.indicator, hashable_params))
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

                elif indicator_type == IndicatorType.PER:
                    if 'per' in data.columns:
                        indicators["per"] = data['per']
                    else:
                        logger.warning("PER data not available in market_data")
                        indicators["per"] = pd.Series(dtype=float)

                elif indicator_type == IndicatorType.PBR:
                    if 'pbr' in data.columns:
                        indicators["pbr"] = data['pbr']
                    else:
                        logger.warning("PBR data not available in market_data")
                        indicators["pbr"] = pd.Series(dtype=float)

                elif indicator_type == IndicatorType.DIVIDEND_YIELD:
                    if 'dividend_yield' in data.columns:
                        indicators["dividend_yield"] = data['dividend_yield']
                    else:
                        logger.warning("Dividend yield data not available in market_data")
                        indicators["dividend_yield"] = pd.Series(dtype=float)

                # === 펀더멘탈 지표 (신규) ===
                elif indicator_type == IndicatorType.ROE:
                    if 'roe' in data.columns:
                        indicators["roe"] = data['roe']
                    else:
                        logger.warning("ROE data not available in market_data")
                        indicators["roe"] = pd.Series(dtype=float)

                elif indicator_type == IndicatorType.EARNINGS_GROWTH:
                    if 'earnings_growth' in data.columns:
                        indicators["earnings_growth"] = data['earnings_growth']
                    else:
                        logger.warning("Earnings growth data not available in market_data")
                        indicators["earnings_growth"] = pd.Series(dtype=float)

                elif indicator_type == IndicatorType.DEBT_TO_EQUITY:
                    if 'debt_to_equity' in data.columns:
                        indicators["debt_to_equity"] = data['debt_to_equity']
                    else:
                        logger.warning("Debt to equity data not available in market_data")
                        indicators["debt_to_equity"] = pd.Series(dtype=float)

                elif indicator_type == IndicatorType.FORWARD_PE:
                    if 'forward_pe' in data.columns:
                        indicators["forward_pe"] = data['forward_pe']
                    else:
                        logger.warning("Forward PE data not available in market_data")
                        indicators["forward_pe"] = pd.Series(dtype=float)

                # === 감성 분석 지표 (SCRUM-324) ===
                elif indicator_type == IndicatorType.SENTIMENT_SCORE:
                    if 'sentiment_score' in data.columns:
                        indicators["sentiment_score"] = data['sentiment_score']
                    else:
                        logger.warning("Sentiment score data not available in market_data")
                        indicators["sentiment_score"] = pd.Series(0.0, index=data.index)

                elif indicator_type == IndicatorType.SENTIMENT_COUNT:
                    if 'sentiment_count' in data.columns:
                        indicators["sentiment_count"] = data['sentiment_count']
                    else:
                        logger.warning("Sentiment count data not available in market_data")
                        indicators["sentiment_count"] = pd.Series(0, index=data.index)

                # === 기술적 추천 지표 (SCRUM-324) ===
                elif indicator_type == IndicatorType.IS_RECOMMENDED:
                    if 'is_recommended' in data.columns:
                        indicators["is_recommended"] = data['is_recommended'].astype(int)
                    else:
                        logger.warning("Recommendation data not available in market_data")
                        indicators["is_recommended"] = pd.Series(0, index=data.index)

                elif indicator_type == IndicatorType.REC_RSI:
                    if 'rec_rsi' in data.columns:
                        indicators["rec_rsi"] = data['rec_rsi']
                    else:
                        logger.warning("Recommendation RSI data not available in market_data")
                        indicators["rec_rsi"] = pd.Series(dtype=float)

                elif indicator_type == IndicatorType.REC_SCORE:
                    if 'rec_score' in data.columns:
                        indicators["rec_score"] = data['rec_score']
                    else:
                        logger.warning("Recommendation score data not available in market_data")
                        indicators["rec_score"] = pd.Series(dtype=float)

                # === 캘린더 지표 (계절성 전략) ===
                elif indicator_type == IndicatorType.CALENDAR:
                    idx = data.index
                    mask = pd.Series(True, index=idx)

                    cal_month = params.get("month")
                    cal_day_gte = params.get("day_gte")
                    cal_month_in = params.get("month_in")
                    cal_day_in = params.get("day_in")

                    if cal_month is not None:
                        mask = mask & (idx.month == int(cal_month))
                    if cal_day_gte is not None:
                        mask = mask & (idx.day >= int(cal_day_gte))
                    if cal_month_in is not None:
                        month_list = list(cal_month_in) if not isinstance(cal_month_in, list) else cal_month_in
                        mask = mask & idx.month.isin([int(m) for m in month_list])
                    if cal_day_in is not None:
                        day_list = list(cal_day_in) if not isinstance(cal_day_in, list) else cal_day_in
                        day_mask = pd.Series(False, index=idx)
                        for d in day_list:
                            parts = str(d).split("-")
                            d_month, d_day = int(parts[0]), int(parts[1])
                            day_mask = day_mask | ((idx.month == d_month) & (idx.day >= d_day))
                        mask = mask & day_mask

                    indicators[key] = mask.astype(float)

                # === 모멘텀 지표 ===
                elif indicator_type == IndicatorType.MOMENTUM_3M:
                    period = 63  # ~3개월 거래일
                    indicators["momentum_3m"] = (close / close.shift(period) - 1) * 100

                # === FRED 경제지표 (데이터 검증 기반 추가) ===
                elif indicator_type == IndicatorType.TREASURY_10Y:
                    if 'treasury_10y' in data.columns:
                        indicators["treasury_10y"] = data['treasury_10y']
                    else:
                        logger.warning("Treasury 10Y data not available in market_data")
                        indicators["treasury_10y"] = pd.Series(dtype=float)

                elif indicator_type == IndicatorType.T10Y2Y:
                    if 't10y2y' in data.columns:
                        indicators["t10y2y"] = data['t10y2y']
                    else:
                        logger.warning("T10Y2Y (yield spread) data not available in market_data")
                        indicators["t10y2y"] = pd.Series(dtype=float)

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
        elif indicator_type == IndicatorType.CALENDAR:
            # 캘린더 파라미터별 고유 키 생성
            param_parts = []
            for k, v in sorted(params.items()):
                if isinstance(v, (list, tuple)):
                    param_parts.append(f"{k}={'_'.join(str(x) for x in v)}")
                else:
                    param_parts.append(f"{k}={v}")
            return f"calendar_{'_'.join(param_parts)}" if param_parts else "calendar"
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
