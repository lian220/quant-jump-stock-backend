"""
SaveTicker HTTP 클라이언트

SaveTicker API에서 뉴스를 수집하는 클라이언트.
기존 Kotlin SaveTickerApiAdapter를 Python으로 포팅.
"""

import logging
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone, timedelta
from typing import List, Optional, Dict, Any, Tuple

import requests
from dateutil.parser import parse as parse_datetime

from adapter.output.external.translator import translate_if_english
from domain.news.models import NewsItem, NewsSource

KST = timezone(timedelta(hours=9))

logger = logging.getLogger(__name__)


class SaveTickerClient:
    """SaveTicker API HTTP 클라이언트"""

    DEFAULT_BASE_URL = "https://api.saveticker.com/api/news"
    DEFAULT_TIMEOUT = 10
    DEFAULT_HEADERS = {
        "User-Agent": "Quantiq/1.0 (News Aggregator)",
        "Accept": "application/json",
    }
    DETAIL_FETCH_DELAY = 0.2  # 상세 API 호출 간 딜레이 (초)
    MAX_ITEMS_PER_COLLECTION = 10  # 1회 수집 최대 기사 수 (Cloud Run 타임아웃 방지)
    TRANSLATION_WORKERS = 4  # 번역 병렬 스레드 수

    def __init__(
        self,
        base_url: str = DEFAULT_BASE_URL,
        timeout: int = DEFAULT_TIMEOUT,
        headers: Optional[Dict[str, str]] = None,
    ):
        self._base_url = base_url
        self._timeout = timeout
        self._session = requests.Session()
        self._session.headers.update(headers or self.DEFAULT_HEADERS)

    def collect(
        self,
        last_fetched_id: Optional[str] = None,
        last_fetched_at: Optional[datetime] = None,
    ) -> List[NewsItem]:
        """뉴스 목록을 수집하고 도메인 모델로 변환"""
        try:
            response = self._session.get(
                f"{self._base_url}/list",
                params={
                    "page": 1,
                    "page_size": self.MAX_ITEMS_PER_COLLECTION,
                    "sort": "created_at_desc",
                    "label_group": 1,
                    "label_name": 1,
                },
                timeout=self._timeout,
            )
            response.raise_for_status()
            data = response.json()
        except Exception as e:
            logger.error(f"SaveTicker API 호출 실패: {e}")
            raise

        news_list = data.get("news_list", [])
        if not news_list:
            return []

        # Phase 1: 원본 데이터 수집 (순차 — API rate limit 존중)
        raw_with_content: List[Tuple[Dict[str, Any], Optional[str]]] = []
        detail_fetched = False
        for raw in news_list:
            if len(raw_with_content) >= self.MAX_ITEMS_PER_COLLECTION:
                logger.info(f"최대 수집 제한 도달 ({self.MAX_ITEMS_PER_COLLECTION}건), 수집 중단")
                break

            item_id = raw.get("id", "")
            if last_fetched_id and item_id == last_fetched_id:
                break

            created_at = _safe_parse_datetime(raw.get("created_at", ""))
            if last_fetched_at and created_at and created_at <= last_fetched_at:
                continue

            if detail_fetched:
                time.sleep(self.DETAIL_FETCH_DELAY)
            detail_content = self._fetch_detail_content(item_id)
            content = detail_content if detail_content else raw.get("content")
            detail_fetched = True

            raw_with_content.append((raw, content))

        if not raw_with_content:
            return []

        # Phase 2: 도메인 변환 + 번역 (병렬 — 영어 기사만 Google Translate 호출)
        with ThreadPoolExecutor(max_workers=self.TRANSLATION_WORKERS) as executor:
            items = list(executor.map(lambda args: _to_domain(*args), raw_with_content))

        logger.info(f"SaveTicker: {len(items)}건 신규 뉴스 수집")
        return items

    def _fetch_detail_content(self, external_id: str) -> Optional[str]:
        """상세 API에서 본문 추출"""
        try:
            response = self._session.get(
                f"{self._base_url}/detail/{external_id}",
                timeout=self._timeout,
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

    def close(self):
        """HTTP 세션 종료"""
        self._session.close()


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

    # 제목 번역: 영어 원본 보존 + 한글 번역
    raw_title = raw.get("title", "")
    title_en = en_translation.get("title") if en_translation else None
    title_ko = translate_if_english(raw_title)
    # 원본이 영어였고 title_en이 없으면 원본을 title_en에 보존
    if title_ko != raw_title and not title_en:
        title_en = raw_title

    # 본문 번역
    content_ko = content if content and content.strip() else None
    if content_ko:
        content_ko = translate_if_english(content_ko)

    return NewsItem(
        external_id=raw.get("id", ""),
        source=NewsSource.SAVETICKER,
        original_source=raw.get("source"),
        title_ko=title_ko,
        title_en=title_en,
        content_ko=content_ko,
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
    """안전한 날짜 파싱 (KST 변환 후 timezone-naive 반환)"""
    if not date_str:
        return None
    try:
        dt = parse_datetime(date_str)
        # KST로 변환 후 tz 제거 (PostgreSQL TIMESTAMP without tz 호환)
        if dt.tzinfo is not None:
            dt = dt.astimezone(KST).replace(tzinfo=None)
        return dt
    except (ValueError, TypeError):
        return None
