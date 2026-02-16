"""
뉴스 수집 오케스트레이션 서비스

SaveTicker에서 뉴스를 수집하고 MongoDB에 저장한 뒤,
collector_state를 업데이트하는 전체 워크플로를 조율합니다.
importance_score=0.0으로 원본 저장 → Core가 후속 스코어링.
"""

import logging
import time
from datetime import datetime, timedelta, timezone
from typing import List

from domain.news.models import NewsSource

KST = timezone(timedelta(hours=9))
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

    def collect(self, source: str = NewsSource.SAVETICKER.value) -> dict:
        """뉴스 수집 실행

        Returns:
            dict with keys: collected_count, article_ids, source
        """
        try:
            news_source = NewsSource(source)
        except ValueError:
            raise ValueError(
                f"지원하지 않는 뉴스 소스: {source}. "
                f"유효한 소스: {[s.value for s in NewsSource]}"
            )

        start_time = time.time()

        try:
            # collector_state에서 마지막 수집 정보 가져오기 (단일 쿼리)
            state = self._state_repo.get_state(news_source)
            last_fetched_id = state.last_fetched_id if state else None
            last_fetched_at = state.last_fetched_at if state else None
            if last_fetched_at is None:
                last_fetched_at = datetime.now(KST).replace(tzinfo=None) - timedelta(hours=1)

            # 1) 목록 조회 (가벼운 메타데이터만)
            raw_items = self._client.fetch_list(
                last_fetched_id=last_fetched_id,
                last_fetched_at=last_fetched_at,
            )

            if not raw_items:
                elapsed_ms = int((time.time() - start_time) * 1000)
                self._state_repo.record_success(news_source, elapsed_ms)
                return {"collected_count": 0, "article_ids": [], "source": source}

            # 2) MongoDB 중복 체크 — 이미 저장된 기사 제외
            candidate_ids = [r.get("id", "") for r in raw_items]
            existing_ids = set(self._news_repo.get_saved_ids(source, candidate_ids))
            new_items = [r for r in raw_items if r.get("id", "") not in existing_ids]

            if existing_ids:
                logger.info(f"{source}: {len(existing_ids)}건 중복 스킵, {len(new_items)}건 신규")

            if not new_items:
                elapsed_ms = int((time.time() - start_time) * 1000)
                self._state_repo.record_success(news_source, elapsed_ms)
                return {"collected_count": 0, "article_ids": [], "source": source}

            # 3) 신규 기사만 상세 조회 + 번역 (병렬)
            items = self._client.fetch_details_and_translate(new_items)

            elapsed_ms = int((time.time() - start_time) * 1000)

            if not items:
                self._state_repo.record_success(news_source, elapsed_ms)
                return {"collected_count": 0, "article_ids": [], "source": source}

            # MongoDB에 원본 저장 (importance_score=0.0)
            saved_count = self._news_repo.save_all(items)

            # collector_state 업데이트 (created_at 기준 — source_created_at은 None일 수 있음)
            latest = max(items, key=lambda x: x.created_at or datetime.min)
            self._state_repo.update_state(
                source=news_source,
                last_fetched_at=latest.created_at or datetime.now(KST).replace(tzinfo=None),
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
            try:
                self._state_repo.record_error(news_source, str(e))
            except Exception as state_err:
                logger.error(f"에러 상태 기록 실패: {state_err}")
            logger.exception(f"{source} 수집 실패: {e}")
            raise
