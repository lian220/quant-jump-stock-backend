"""
비즈니스 임계값 설정

하드코딩된 매직 넘버를 중앙 집중화합니다.
기존 코드에서 발견된 하드코딩 값들:
- RSI < 50 (technical_analysis.py:132)
- lookback_days = 180 (technical_analysis.py:11)
- 가중치 0.7/0.3 (recommendation_service.py)
"""
from pydantic import BaseModel, Field


class RSIThresholds(BaseModel):
    """RSI 지표 임계값"""
    oversold: float = Field(default=30.0, description="과매도 기준")
    overbought: float = Field(default=70.0, description="과매수 기준")
    neutral_buy: float = Field(default=50.0, description="중립 매수 기준 (현재 사용)")
    period: int = Field(default=14, description="RSI 계산 기간")


class SMAThresholds(BaseModel):
    """이동평균 임계값"""
    short_period: int = Field(default=20, description="단기 이동평균 기간")
    long_period: int = Field(default=50, description="장기 이동평균 기간")


class MACDThresholds(BaseModel):
    """MACD 지표 임계값"""
    short_period: int = Field(default=12, description="단기 EMA 기간")
    long_period: int = Field(default=26, description="장기 EMA 기간")
    signal_period: int = Field(default=9, description="시그널 기간")


class BollingerThresholds(BaseModel):
    """볼린저 밴드 임계값"""
    period: int = Field(default=20, description="이동평균 기간")
    std_dev: float = Field(default=2.0, description="표준편차 배수")


class AnalysisThresholds(BaseModel):
    """분석 관련 임계값"""
    lookback_days: int = Field(default=180, description="분석 데이터 조회 기간 (일)")
    min_data_points: int = Field(default=50, description="최소 데이터 포인트 수")

    # 가중치
    technical_weight: float = Field(default=0.7, description="기술적 분석 가중치")
    sentiment_weight: float = Field(default=0.3, description="감성 분석 가중치")


class RiskManagementThresholds(BaseModel):
    """리스크 관리 임계값"""
    default_stop_loss_pct: float = Field(default=0.05, description="기본 손절 비율 (5%)")
    default_take_profit_pct: float = Field(default=0.15, description="기본 익절 비율 (15%)")
    max_position_pct: float = Field(default=0.1, description="최대 포지션 비율 (10%)")
    max_drawdown_pct: float = Field(default=0.2, description="최대 낙폭 (20%)")


class Thresholds(BaseModel):
    """통합 임계값 설정"""
    rsi: RSIThresholds = Field(default_factory=RSIThresholds)
    sma: SMAThresholds = Field(default_factory=SMAThresholds)
    macd: MACDThresholds = Field(default_factory=MACDThresholds)
    bollinger: BollingerThresholds = Field(default_factory=BollingerThresholds)
    analysis: AnalysisThresholds = Field(default_factory=AnalysisThresholds)
    risk: RiskManagementThresholds = Field(default_factory=RiskManagementThresholds)


# 싱글톤 인스턴스
thresholds = Thresholds()
