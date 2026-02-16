"""
SaveTicker HTTP 클라이언트

SaveTicker API에서 뉴스를 수집하는 클라이언트.
기존 Kotlin SaveTickerApiAdapter를 Python으로 포팅.
"""

import logging
from datetime import datetime
from typing import List, Optional, Dict, Any

import requests
from dateutil.parser import parse as parse_datetime

from domain.news.models import NewsItem, NewsSource

logger = logging.getLogger(__name__)

API_BASE_URL = "https://api.saveticker.com/api/news"
API_TIMEOUT = 10
HEADERS = {
    "User-Agent": "AlphaFoundry/1.0 (News Aggregator)",
    "Accept": "application/json",
}


class SaveTickerClient:
    """SaveTicker API HTTP 클라이언트"""

    def collect(
        self,
        last_fetched_id: Optional[str] = None,
        last_fetched_at: Optional[datetime] = None,
    ) -> List[NewsItem]:
        """뉴스 목록을 수집하고 도메인 모델로 변환"""
        try:
            response = requests.get(
                f"{API_BASE_URL}/list",
                params={
                    "page": 1,
                    "page_size": 10,
                    "sort": "created_at_desc",
                    "label_group": 1,
                    "label_name": 1,
                },
                headers=HEADERS,
                timeout=API_TIMEOUT,
            )
            response.raise_for_status()
            data = response.json()
        except Exception as e:
            logger.error(f"SaveTicker API 호출 실패: {e}")
            raise

        news_list = data.get("news_list", [])
        if not news_list:
            return []

        items: List[NewsItem] = []
        for raw in news_list:
            item_id = raw.get("id", "")
            # 이전 마지막 ID 이후 필터
            if last_fetched_id and item_id == last_fetched_id:
                continue

            created_at = _safe_parse_datetime(raw.get("created_at", ""))
            if last_fetched_at and created_at and created_at <= last_fetched_at:
                continue

            # 헤드라인만 있는 기사는 상세 본문 가져오기
            is_headline_only = raw.get("is_headline_only", False)
            content = raw.get("content")
            if not is_headline_only:
                detail_content = self._fetch_detail_content(item_id)
                if detail_content:
                    content = detail_content

            items.append(_to_domain(raw, content))

        if items:
            logger.info(f"SaveTicker: {len(items)}건 신규 뉴스 수집")

        return items

    def _fetch_detail_content(self, external_id: str) -> Optional[str]:
        """상세 API에서 본문 추출"""
        try:
            response = requests.get(
                f"{API_BASE_URL}/detail/{external_id}",
                headers=HEADERS,
                timeout=API_TIMEOUT,
            )
            response.raise_for_status()
            data = response.json()

            news = data.get("news", {})
            translations = news.get("translations", {})
            translated = translations.get("translated", {})

            ko_content = translated.get("ko_KR", {}).get("content")
            raw_content = news.get("content")

            blocks = ko_content if ko_content else raw_content
            if not blocks:
                return None

            parts = [
                b.get("content", "")
                for b in blocks
                if b.get("content") and b["content"].strip() and b["content"] != "\n"
            ]
            result = "\n\n".join(parts)
            return result if result.strip() else None
        except Exception as e:
            logger.debug(f"SaveTicker 상세 API 호출 실패 (id={external_id}): {e}")
            return None


def _to_domain(raw: Dict[str, Any], content: Optional[str]) -> NewsItem:
    """API 응답을 도메인 모델로 변환"""
    translations = raw.get("translations", {})
    translated = translations.get("translated", {}) if translations else {}
    ko_translation = translated.get("ko_KR", {})
    en_translation = translated.get("en_US", {})

    tag_names = raw.get("tag_names", [])
    tickers = [t for t in tag_names if t.startswith("$")]
    tags = [t for t in tag_names if not t.startswith("$")]

    summary_blocks = ko_translation.get("summary", []) if ko_translation else []
    summary_ko = None
    if summary_blocks:
        first_content = summary_blocks[0].get("content") if summary_blocks else None
        if first_content and first_content.strip():
            summary_ko = first_content

    extra = raw.get("extra", {}) or {}

    return NewsItem(
        external_id=raw.get("id", ""),
        source=NewsSource.SAVETICKER,
        original_source=raw.get("source"),
        title_ko=raw.get("title", ""),
        title_en=en_translation.get("title") if en_translation else None,
        content_ko=content if content and content.strip() else None,
        summary_ko=summary_ko,
        tags=tags,
        tickers=tickers,
        importance_score=0.0,
        is_headline_only=raw.get("is_headline_only", False),
        view_count=raw.get("view_count", 0),
        source_url=extra.get("source_url"),
        source_created_at=_safe_parse_datetime(extra.get("source_created_at")),
        created_at=_safe_parse_datetime(raw.get("created_at", "")),
        extra=extra,
    )


def _safe_parse_datetime(date_str: Optional[str]) -> Optional[datetime]:
    """안전한 날짜 파싱 (항상 timezone-naive 반환)"""
    if not date_str:
        return None
    try:
        dt = parse_datetime(date_str)
        # PostgreSQL TIMESTAMP(without tz)와 비교 호환을 위해 tz 제거
        if dt.tzinfo is not None:
            dt = dt.replace(tzinfo=None)
        return dt
    except (ValueError, TypeError):
        return None
