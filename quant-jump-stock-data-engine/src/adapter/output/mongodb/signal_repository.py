"""
MongoDB Signal Repository

매매 신호를 MongoDB에 저장하는 어댑터.
"""

import logging
from typing import List
from datetime import datetime
from pymongo.database import Database

from application.ports.output_ports import SignalOutputPort, TradingSignal

logger = logging.getLogger(__name__)


class MongoSignalRepository(SignalOutputPort):
    """
    MongoDB 신호 저장소

    생성된 매매 신호를 MongoDB에 저장.
    Kafka 발행은 별도 어댑터에서 처리.
    """

    COLLECTION_NAME = "trading_signals"

    def __init__(self, db: Database):
        self.db = db
        self.collection = db[self.COLLECTION_NAME]

    async def publish(self, signal: TradingSignal) -> None:
        """
        단일 신호 발행

        MongoDB 저장만 담당. Kafka 발행은 별도 어댑터.
        """
        # 이 구현에서는 save_to_history로 위임
        await self.save_to_history(signal)

    async def publish_batch(self, signals: List[TradingSignal]) -> None:
        """다수 신호 일괄 발행"""
        if not signals:
            return

        docs = [self._to_document(s) for s in signals]
        self.collection.insert_many(docs)
        logger.info(f"Batch saved {len(signals)} signals to MongoDB")

    async def save_to_history(self, signal: TradingSignal) -> None:
        """신호 이력 저장"""
        doc = self._to_document(signal)
        self.collection.insert_one(doc)
        logger.debug(
            f"Signal saved: {signal.strategy_id} → {signal.symbol} "
            f"({signal.signal_type.value})"
        )

    def _to_document(self, signal: TradingSignal) -> dict:
        """도메인 모델 → MongoDB 문서"""
        return {
            "strategy_id": signal.strategy_id,
            "symbol": signal.symbol,
            "signal_type": signal.signal_type.value,
            "weight": signal.weight,
            "timestamp": signal.timestamp,
            "rule_name": signal.rule_name,
            "description": signal.description,
            "metadata": signal.metadata,
            "created_at": datetime.utcnow()
        }
