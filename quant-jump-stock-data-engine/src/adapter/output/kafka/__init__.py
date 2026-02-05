"""
Kafka Output Adapter

Kafka Producer 기반 이벤트 발행 어댑터.
"""

from .producer import KafkaProducerAdapter

__all__ = [
    "KafkaProducerAdapter",
]
