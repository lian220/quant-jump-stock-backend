"""
Strategy DSL 스키마

JSON으로 전략을 정의하는 DSL(Domain Specific Language)입니다.
exec/eval 없이 안전하게 전략을 실행할 수 있습니다.

예시:
{
    "strategy_id": "golden_cross_v1",
    "name": "Golden Cross Strategy",
    "rules": [
        {
            "name": "golden_cross_buy",
            "signal_type": "buy",
            "conditions": [
                {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"},
                {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 70}
            ],
            "logic": "and"
        }
    ]
}
"""
from enum import Enum
from typing import List, Literal, Optional, Dict, Any, Union
from pydantic import BaseModel, Field, field_validator
from datetime import datetime


class IndicatorType(str, Enum):
    """지원하는 기술적 지표 유형"""
    SMA = "sma"          # 단순이동평균
    EMA = "ema"          # 지수이동평균
    RSI = "rsi"          # 상대강도지수
    MACD = "macd"        # MACD
    MACD_SIGNAL = "macd_signal"  # MACD 시그널
    MACD_HIST = "macd_hist"      # MACD 히스토그램
    BOLLINGER_UPPER = "bollinger_upper"  # 볼린저 상단
    BOLLINGER_MIDDLE = "bollinger_middle"  # 볼린저 중간
    BOLLINGER_LOWER = "bollinger_lower"  # 볼린저 하단
    VOLUME = "volume"    # 거래량
    PRICE = "price"      # 현재가
    PER = "per"                      # 주가수익비율
    PBR = "pbr"                      # 주가순자산비율
    DIVIDEND_YIELD = "dividend_yield"  # 배당수익률


class SignalType(str, Enum):
    """매매 신호 유형"""
    BUY = "buy"
    SELL = "sell"
    HOLD = "hold"


class ConditionOperator(str, Enum):
    """조건 연산자"""
    GREATER_THAN = "gt"           # 초과
    GREATER_EQUAL = "gte"         # 이상
    LESS_THAN = "lt"              # 미만
    LESS_EQUAL = "lte"            # 이하
    EQUALS = "eq"                 # 같음
    NOT_EQUALS = "neq"            # 다름
    CROSSES_ABOVE = "crosses_above"   # 상향 돌파
    CROSSES_BELOW = "crosses_below"   # 하향 돌파


class Condition(BaseModel):
    """
    단일 조건

    예시:
    - RSI가 30 미만: {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 30}
    - 20일선이 50일선 상향 돌파: {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"}
    """
    indicator: IndicatorType = Field(description="지표 유형")
    params: Dict[str, Any] = Field(default_factory=dict, description="지표 파라미터 (예: {period: 20})")
    operator: ConditionOperator = Field(description="비교 연산자")
    value: Union[float, str] = Field(description="비교 값 (숫자 또는 다른 지표 참조)")

    @field_validator("params")
    @classmethod
    def validate_params(cls, v, info):
        """지표에 맞는 파라미터 검증"""
        # 기본 검증만 수행, 상세 검증은 인터프리터에서
        if not isinstance(v, dict):
            raise ValueError("params must be a dictionary")
        return v

    def get_indicator_key(self) -> str:
        """지표 고유 키 생성 (캐싱용)"""
        if self.indicator in [IndicatorType.SMA, IndicatorType.EMA]:
            period = self.params.get("period", "")
            return f"{self.indicator.value}_{period}"
        elif self.indicator == IndicatorType.RSI:
            period = self.params.get("period", 14)
            return f"rsi_{period}"
        elif self.indicator in [IndicatorType.MACD, IndicatorType.MACD_SIGNAL, IndicatorType.MACD_HIST]:
            return f"macd"  # MACD는 한 번에 계산
        elif self.indicator in [IndicatorType.BOLLINGER_UPPER, IndicatorType.BOLLINGER_MIDDLE, IndicatorType.BOLLINGER_LOWER]:
            period = self.params.get("period", 20)
            return f"bollinger_{period}"
        else:
            return self.indicator.value


class Rule(BaseModel):
    """
    매수/매도 규칙

    여러 조건을 AND 또는 OR로 결합합니다.
    """
    name: str = Field(description="규칙 이름 (식별용)")
    signal_type: Literal["buy", "sell"] = Field(description="시그널 유형")
    conditions: List[Condition] = Field(min_length=1, description="조건 목록")
    logic: Literal["and", "or"] = Field(default="and", description="조건 결합 논리")
    weight: float = Field(default=1.0, ge=0.0, le=1.0, description="시그널 가중치")
    description: Optional[str] = Field(default=None, description="규칙 설명")


class RiskManagement(BaseModel):
    """리스크 관리 설정"""
    stop_loss_pct: float = Field(default=0.05, ge=0.0, le=1.0, description="손절 비율 (5%)")
    take_profit_pct: float = Field(default=0.15, ge=0.0, le=1.0, description="익절 비율 (15%)")
    max_position_pct: float = Field(default=0.1, ge=0.0, le=1.0, description="포트폴리오 대비 최대 포지션 (10%)")
    max_drawdown_pct: float = Field(default=0.2, ge=0.0, le=1.0, description="최대 낙폭 (20%)")
    trailing_stop: bool = Field(default=False, description="트레일링 스탑 활성화")
    trailing_stop_pct: float = Field(default=0.05, ge=0.0, le=1.0, description="트레일링 스탑 비율")


class StrategyDefinition(BaseModel):
    """
    전략 정의 (DSL 최상위)

    JSON 기반으로 전략을 정의하며, exec/eval 없이 안전하게 실행됩니다.
    """
    strategy_id: str = Field(description="전략 고유 ID")
    name: str = Field(description="전략 이름")
    description: str = Field(default="", description="전략 설명")
    version: str = Field(default="1.0", description="전략 버전")

    # 전략 규칙
    rules: List[Rule] = Field(min_length=1, description="매수/매도 규칙 목록")

    # 리스크 관리
    risk_management: RiskManagement = Field(default_factory=RiskManagement)

    # 메타데이터
    is_ai_generated: bool = Field(default=False, description="AI 생성 여부")
    source_prompt: Optional[str] = Field(default=None, description="AI 생성 시 원본 프롬프트")
    created_at: Optional[datetime] = Field(default=None, description="생성 시각")
    updated_at: Optional[datetime] = Field(default=None, description="수정 시각")
    tags: List[str] = Field(default_factory=list, description="태그")

    model_config = {
        "json_schema_extra": {
            "example": {
                "strategy_id": "golden_cross_v1",
                "name": "Golden Cross Strategy",
                "description": "20일선이 50일선을 상향 돌파하고 RSI가 과매수가 아닐 때 매수",
                "version": "1.0",
                "rules": [
                    {
                        "name": "golden_cross_buy",
                        "signal_type": "buy",
                        "conditions": [
                            {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_above", "value": "sma_50"},
                            {"indicator": "rsi", "params": {"period": 14}, "operator": "lt", "value": 70}
                        ],
                        "logic": "and",
                        "weight": 1.0
                    },
                    {
                        "name": "death_cross_sell",
                        "signal_type": "sell",
                        "conditions": [
                            {"indicator": "sma", "params": {"period": 20}, "operator": "crosses_below", "value": "sma_50"}
                        ],
                        "logic": "and",
                        "weight": 1.0
                    }
                ],
                "risk_management": {
                    "stop_loss_pct": 0.05,
                    "take_profit_pct": 0.15,
                    "max_position_pct": 0.1
                },
                "is_ai_generated": False,
                "tags": ["momentum", "trend-following"]
            }
        }
    }

    def get_buy_rules(self) -> List[Rule]:
        """매수 규칙만 반환"""
        return [r for r in self.rules if r.signal_type == "buy"]

    def get_sell_rules(self) -> List[Rule]:
        """매도 규칙만 반환"""
        return [r for r in self.rules if r.signal_type == "sell"]

    def get_all_indicators(self) -> set:
        """전략에서 사용하는 모든 지표 반환"""
        indicators = set()
        for rule in self.rules:
            for condition in rule.conditions:
                indicators.add(condition.get_indicator_key())
                # value가 지표 참조인 경우 추가
                if isinstance(condition.value, str) and "_" in condition.value:
                    indicators.add(condition.value)
        return indicators
