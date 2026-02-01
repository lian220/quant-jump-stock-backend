"""
Event Schema Definitions for Quantiq Data Engine

모든 이벤트의 공통 구조와 도메인별 Payload를 정의합니다.
"""

from dataclasses import dataclass, field, asdict
from typing import Any, Dict, List, Optional
from datetime import datetime
from pytz import timezone
import uuid

KST = timezone('Asia/Seoul')


# ============================================================================
# Base Event Schema
# ============================================================================

@dataclass
class BaseEvent:
    """모든 이벤트의 기본 구조"""
    eventId: str = field(default_factory=lambda: str(uuid.uuid4()))
    eventType: str = ""
    version: str = "1.0"
    timestamp: str = field(default_factory=lambda: datetime.now(KST).isoformat())
    source: str = "quantiq-data-engine"
    payload: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict:
        """딕셔너리로 변환"""
        return asdict(self)


# ============================================================================
# Stock Events
# ============================================================================

@dataclass
class StockPriceUpdatedPayload:
    symbol: str
    price: float
    change: float
    changePercent: float
    volume: int
    marketCap: Optional[int] = None


@dataclass
class StockDataSyncRequestedPayload:
    requestId: str
    symbols: List[str]
    syncType: str  # full, incremental
    priority: str = "normal"  # high, normal, low


@dataclass
class StockDataRefreshedPayload:
    requestId: str
    symbols: List[str]
    recordsUpdated: int
    duration: float
    status: str


# ============================================================================
# Trading Events
# ============================================================================

@dataclass
class TradingOrderCreatedPayload:
    orderId: str
    userId: str
    symbol: str
    orderType: str  # market, limit
    side: str  # buy, sell
    quantity: int
    price: Optional[float]
    status: str


@dataclass
class TradingOrderExecutedPayload:
    orderId: str
    executedPrice: float
    executedQuantity: int
    commission: float
    totalAmount: float


@dataclass
class TradingSignalDetectedPayload:
    symbol: str
    signalType: str  # buy, sell
    confidence: float
    indicators: Dict[str, Any]
    recommendedAction: str
    recommendedQuantity: int


@dataclass
class TradingBalanceUpdatedPayload:
    userId: str
    currency: str
    balance: float
    availableBalance: float
    holdBalance: float


# ============================================================================
# Analysis Events
# ============================================================================

@dataclass
class AnalysisRequestPayload:
    requestId: str
    analysisType: str  # technical, fundamental, sentiment
    symbols: List[str]
    parameters: Dict[str, Any] = field(default_factory=dict)


@dataclass
class AnalysisCompletedPayload:
    requestId: str
    analysisType: str
    symbols: List[str]
    recordsProcessed: int
    duration: float
    status: str


@dataclass
class AnalysisRecommendationGeneratedPayload:
    symbol: str
    recommendation: str  # buy, hold, sell
    targetPrice: float
    stopLoss: float
    confidence: float
    timeframe: str  # short, medium, long
    reasoning: str


@dataclass
class AnalysisPredictionCompletedPayload:
    symbol: str
    predictedPrice: float
    confidence: float
    timeframe: str
    model: str


# ============================================================================
# Economic Events
# ============================================================================

@dataclass
class EconomicDataSyncRequestedPayload:
    requestId: str
    dataTypes: List[str]
    source: str  # scheduled, manual
    priority: str = "normal"
    threadTs: Optional[str] = None  # Slack 스레드 타임스탬프 (Kotlin에서 전달)


@dataclass
class EconomicDataUpdatedPayload:
    requestId: str
    dataTypes: List[str]
    recordsUpdated: int
    duration: float
    status: str


@dataclass
class EconomicDataSyncFailedPayload:
    requestId: str
    errorCode: str
    errorMessage: str
    retryable: bool
    retryAfter: Optional[int] = None


# ============================================================================
# Event Topics (Constants)
# ============================================================================

class EventTopics:
    """이벤트 토픽 상수"""

    # Stock
    STOCK_PRICE_UPDATED = "quantiq.stock.price.updated"
    STOCK_DATA_SYNC_REQUESTED = "quantiq.stock.data.sync.requested"
    STOCK_DATA_REFRESHED = "quantiq.stock.data.refreshed"

    # Trading
    TRADING_ORDER_CREATED = "quantiq.trading.order.created"
    TRADING_ORDER_EXECUTED = "quantiq.trading.order.executed"
    TRADING_ORDER_CANCELLED = "quantiq.trading.order.cancelled"
    TRADING_SIGNAL_DETECTED = "quantiq.trading.signal.detected"
    TRADING_BALANCE_UPDATED = "quantiq.trading.balance.updated"

    # Analysis
    ANALYSIS_REQUEST = "quantiq.analysis.request"
    ANALYSIS_COMPLETED = "quantiq.analysis.completed"
    ANALYSIS_RECOMMENDATION_GENERATED = "quantiq.analysis.recommendation.generated"
    ANALYSIS_PREDICTION_COMPLETED = "quantiq.analysis.prediction.completed"

    # Economic
    ECONOMIC_DATA_SYNC_REQUESTED = "quantiq.economic.data.sync.requested"
    ECONOMIC_DATA_UPDATED = "quantiq.economic.data.updated"
    ECONOMIC_DATA_SYNC_FAILED = "quantiq.economic.data.sync.failed"

    # Legacy (backward compatibility)
    LEGACY_ANALYSIS_REQUEST = "quantiq.analysis.request"
    LEGACY_ANALYSIS_COMPLETED = "quantiq.analysis.completed"
    LEGACY_ECONOMIC_DATA_UPDATE_REQUEST = "economic.data.update.request"


# ============================================================================
# Event Helper Functions
# ============================================================================

def create_event(event_type: str, payload: Any) -> BaseEvent:
    """
    이벤트 생성 헬퍼 함수

    Args:
        event_type: 이벤트 타입 (EventTopics 상수 사용)
        payload: 이벤트 페이로드 (dataclass 또는 dict)

    Returns:
        BaseEvent 인스턴스
    """
    if hasattr(payload, '__dataclass_fields__'):
        # dataclass인 경우 dict로 변환
        payload_dict = asdict(payload)
    elif isinstance(payload, dict):
        payload_dict = payload
    else:
        raise ValueError(f"Unsupported payload type: {type(payload)}")

    return BaseEvent(
        eventType=event_type,
        payload=payload_dict
    )


def parse_event(event_data: dict) -> BaseEvent:
    """
    딕셔너리를 BaseEvent로 파싱

    Args:
        event_data: 이벤트 딕셔너리

    Returns:
        BaseEvent 인스턴스
    """
    return BaseEvent(
        eventId=event_data.get('eventId', str(uuid.uuid4())),
        eventType=event_data.get('eventType', ''),
        version=event_data.get('version', '1.0'),
        timestamp=event_data.get('timestamp', datetime.now(KST).isoformat()),
        source=event_data.get('source', 'unknown'),
        payload=event_data.get('payload', {})
    )
