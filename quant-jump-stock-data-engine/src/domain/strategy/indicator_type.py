"""
IndicatorType Enum

백테스트에서 사용 가능한 지표 타입 정의.
기술적 지표 + 감성 분석 + 추천 지표 통합.
"""
from enum import Enum


class IndicatorType(str, Enum):
    """백테스트 지표 타입"""

    # === 가격 기반 지표 ===
    OPEN = "open"
    HIGH = "high"
    LOW = "low"
    CLOSE = "close"
    VOLUME = "volume"

    # === 이동평균 지표 ===
    SMA = "sma"  # Simple Moving Average
    EMA = "ema"  # Exponential Moving Average

    # === 모멘텀 지표 ===
    RSI = "rsi"  # Relative Strength Index
    MACD = "macd"  # Moving Average Convergence Divergence
    MACD_SIGNAL = "macd_signal"
    MACD_HIST = "macd_histogram"
    STOCH_K = "stochastic_k"  # Stochastic %K
    STOCH_D = "stochastic_d"  # Stochastic %D

    # === 변동성 지표 ===
    BB_UPPER = "bollinger_upper"  # Bollinger Bands Upper
    BB_MIDDLE = "bollinger_middle"  # Bollinger Bands Middle
    BB_LOWER = "bollinger_lower"  # Bollinger Bands Lower
    ATR = "atr"  # Average True Range

    # === 거래량 지표 ===
    OBV = "obv"  # On-Balance Volume

    # === 감성 분석 지표 (SCRUM-326 추가) ===
    SENTIMENT_SCORE = "sentiment_score"  # 감성 분석 점수 (-1.0 ~ 1.0)
    SENTIMENT_COUNT = "sentiment_count"  # 감성 분석 기사 수

    # === 기술적 추천 지표 (SCRUM-326 추가) ===
    IS_RECOMMENDED = "is_recommended"  # 기술적 분석 추천 여부 (bool)
    REC_RSI = "rec_rsi"  # 추천 시점의 RSI
    REC_SCORE = "rec_score"  # 추천 점수

    # === ML 예측 지표 (stock_predictions) ===
    PREDICTED_PRICE = "predicted_price"  # ML 예측 가격
    FORECAST_HORIZON = "forecast_horizon"  # 예측 기간 (일)
    PREDICTION_ERROR = "prediction_error"  # |actual - predicted| (실시간 계산)

    @classmethod
    def is_sentiment_indicator(cls, indicator: str) -> bool:
        """감성 분석 지표 여부"""
        return indicator in {
            cls.SENTIMENT_SCORE.value,
            cls.SENTIMENT_COUNT.value
        }

    @classmethod
    def is_recommendation_indicator(cls, indicator: str) -> bool:
        """추천 지표 여부"""
        return indicator in {
            cls.IS_RECOMMENDED.value,
            cls.REC_RSI.value,
            cls.REC_SCORE.value
        }

    @classmethod
    def requires_mongo_join(cls, indicator: str) -> bool:
        """MongoDB Left Join이 필요한 지표"""
        return (
            cls.is_sentiment_indicator(indicator) or
            cls.is_recommendation_indicator(indicator)
        )
