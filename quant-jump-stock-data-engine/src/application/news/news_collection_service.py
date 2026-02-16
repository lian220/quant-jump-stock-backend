"""
뉴스 수집 오케스트레이션 서비스

SaveTicker에서 뉴스를 수집하고 MongoDB에 저장한 뒤,
collector_state를 업데이트하는 전체 워크플로를 조율합니다.
importance_score=0.0으로 원본 저장 → Core가 후속 스코어링.
"""

import logging
import time
from datetime import datetime, timedelta
from typing import List

from domain.news.models import NewsSource
from adapter.output.external.saveticker_client import SaveTickerClient
from adapter.output.mongodb.news_repository import MongoNewsRepository
from adapter.output.postgresql.collector_state_repository import PostgresCollectorStateRepository

logger = logging.getLogger(__name__)


class NewsCollectionService:
    """뉴스 수집 서비스"""

    def __init__(
        self,
        saveticker_client: SaveTickerClient,
        news_repository: MongoNewsRepository,
        collector_state_repository: PostgresCollectorStateRepository,
    ):
        self._client = saveticker_client
        self._news_repo = news_repository
        self._state_repo = collector_state_repository

    def collect(self, source: str = "SAVETICKER") -> dict:
        """뉴스 수집 실행

        Returns:
            dict with keys: collected_count, article_ids, source
        """
        news_source = NewsSource(source)
        start_time = time.time()

        # collector_state에서 마지막 수집 정보 가져오기
        last_fetched_id = self._state_repo.get_last_fetched_id(news_source)
        last_fetched_at = self._state_repo.get_last_fetched_at(news_source)
        if last_fetched_at is None:
            last_fetched_at = datetime.now() - timedelta(hours=1)

        try:
            # SaveTicker API 호출
            items = self._client.collect(
                last_fetched_id=last_fetched_id,
                last_fetched_at=last_fetched_at,
            )

            elapsed_ms = int((time.time() - start_time) * 1000)

            if not items:
                self._state_repo.record_success(news_source, elapsed_ms)
                return {"collected_count": 0, "article_ids": [], "source": source}

            # MongoDB에 원본 저장 (importance_score=0.0)
            saved_count = self._news_repo.save_all(items)

            # collector_state 업데이트
            latest = max(items, key=lambda x: x.source_created_at or datetime.min)
            self._state_repo.update_state(
                source=news_source,
                last_fetched_at=latest.source_created_at or datetime.now(),
                last_fetched_id=latest.external_id,
            )
            self._state_repo.record_success(news_source, elapsed_ms)

            article_ids = [item.external_id for item in items]
            logger.info(
                f"{source}: {saved_count}건 저장 완료 (응답시간: {elapsed_ms}ms)"
            )

            return {
                "collected_count": saved_count,
                "article_ids": article_ids,
                "source": source,
            }

        except Exception as e:
            self._state_repo.record_error(news_source, str(e))
            logger.error(f"{source} 수집 실패: {e}")
            raise
