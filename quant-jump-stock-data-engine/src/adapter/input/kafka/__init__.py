"""
Kafka Input Adapter

Kafka Consumer 기반 메시지 처리 어댑터.
"""

from .consumer import KafkaConsumerAdapter
from .handlers import (
    MessageHandler,
    EconomicDataHandler,
    TechnicalAnalysisHandler,
    SentimentAnalysisHandler,
    StrategyExecutionHandler,
)

__all__ = [
    "KafkaConsumerAdapter",
    "MessageHandler",
    "EconomicDataHandler",
    "TechnicalAnalysisHandler",
    "SentimentAnalysisHandler",
    "StrategyExecutionHandler",
]
