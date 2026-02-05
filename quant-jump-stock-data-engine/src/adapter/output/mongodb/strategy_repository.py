"""
MongoDB Strategy Repository

전략 정의를 MongoDB에 저장/조회하는 어댑터.
PostgreSQL에 저장된 전략은 별도 어댑터로 조회.
"""

import logging
from typing import List, Optional
from datetime import datetime
from pymongo.database import Database

from application.ports.output_ports import StrategyRepositoryPort
from domain.strategy.models import StrategyDefinition

logger = logging.getLogger(__name__)


class MongoStrategyRepository(StrategyRepositoryPort):
    """
    MongoDB 전략 저장소

    AI 생성 전략 등 동적 전략을 MongoDB에 저장.
    시드 전략은 PostgreSQL에서 조회 (별도 어댑터).
    """

    COLLECTION_NAME = "strategies"

    def __init__(self, db: Database):
        self.db = db
        self.collection = db[self.COLLECTION_NAME]

    async def find_by_id(self, strategy_id: str) -> Optional[StrategyDefinition]:
        """전략 ID로 조회"""
        doc = self.collection.find_one({"strategy_id": strategy_id})
        if doc is None:
            return None
        return self._to_domain(doc)

    async def find_all_active(self) -> List[StrategyDefinition]:
        """활성화된 모든 전략 조회"""
        cursor = self.collection.find({"is_active": {"$ne": False}})
        return [self._to_domain(doc) for doc in cursor]

    async def find_by_tags(self, tags: List[str]) -> List[StrategyDefinition]:
        """태그로 전략 검색"""
        cursor = self.collection.find({"tags": {"$in": tags}})
        return [self._to_domain(doc) for doc in cursor]

    async def save(self, strategy: StrategyDefinition) -> None:
        """전략 저장"""
        doc = self._to_document(strategy)
        doc["created_at"] = datetime.utcnow()
        doc["updated_at"] = datetime.utcnow()
        self.collection.insert_one(doc)
        logger.info(f"Strategy saved: {strategy.strategy_id}")

    async def update(self, strategy: StrategyDefinition) -> None:
        """전략 업데이트"""
        doc = self._to_document(strategy)
        doc["updated_at"] = datetime.utcnow()
        self.collection.update_one(
            {"strategy_id": strategy.strategy_id},
            {"$set": doc}
        )
        logger.info(f"Strategy updated: {strategy.strategy_id}")

    def _to_document(self, strategy: StrategyDefinition) -> dict:
        """도메인 모델 → MongoDB 문서"""
        return strategy.model_dump(mode="json")

    def _to_domain(self, doc: dict) -> StrategyDefinition:
        """MongoDB 문서 → 도메인 모델"""
        # _id 필드 제거
        doc.pop("_id", None)
        return StrategyDefinition.model_validate(doc)
