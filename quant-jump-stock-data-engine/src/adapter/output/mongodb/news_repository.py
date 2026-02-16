"""
MongoDB News Repository

뉴스 기사를 MongoDB news 컬렉션에 저장하는 어댑터.
Core API의 MongoNewsRepository와 동일한 스키마 사용.
"""

import logging
from typing import List

from pymongo.database import Database

from domain.news.models import NewsItem

logger = logging.getLogger(__name__)


class MongoNewsRepository:
    """MongoDB 뉴스 저장소"""

    COLLECTION_NAME = "news"

    def __init__(self, db: Database):
        self._collection = db[self.COLLECTION_NAME]

    def save_all(self, items: List[NewsItem]) -> int:
        """뉴스 기사 일괄 저장 (upsert by source+externalId)

        Returns:
            저장된 기사 수
        """
        if not items:
            return 0

        saved = 0
        failed = 0
        for item in items:
            try:
                doc = item.to_mongo_document()
                # created_at은 최초 삽입 시에만 설정 (Core API와 동일 패턴)
                created_at = doc.pop("created_at", None)
                result = self._collection.update_one(
                    {"source": item.source.value, "external_id": item.external_id},
                    {"$set": doc, "$setOnInsert": {"created_at": created_at}},
                    upsert=True,
                )
                if result.upserted_id or result.modified_count > 0:
                    saved += 1
            except Exception as e:
                failed += 1
                logger.warning(
                    f"MongoDB upsert 실패 ({item.source.value}/{item.external_id}): {e}"
                )

        if saved:
            logger.info(f"MongoDB news: {saved}건 저장")
        if failed:
            logger.warning(f"MongoDB news: {failed}건 저장 실패")
        return saved

    def get_saved_ids(self, source: str, external_ids: List[str]) -> List[str]:
        """이미 저장된 기사의 ID 목록 조회"""
        if not external_ids:
            return []
        cursor = self._collection.find(
            {"source": source, "external_id": {"$in": external_ids}},
            {"external_id": 1},
        )
        return [doc["external_id"] for doc in cursor]
