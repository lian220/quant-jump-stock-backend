"""
뉴스 도메인 모델

Core API의 NewsItem, CollectorState, NewsSource와 동기화된 도메인 모델.
"""

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Optional, Dict, Any, List


class NewsSource(str, Enum):
    """뉴스 소스 (Core API NewsSource.kt와 동기화)"""
    SAVETICKER = "SAVETICKER"
    FINNHUB = "FINNHUB"
    ALPHA_VANTAGE = "ALPHA_VANTAGE"
    NAVER = "NAVER"
    YOUTUBE = "YOUTUBE"
    TELEGRAM = "TELEGRAM"
    DART = "DART"
    MANUAL = "MANUAL"


@dataclass
class NewsItem:
    """뉴스 아이템 도메인 모델 (Core API NewsItem.kt와 동기화)"""
    external_id: str
    source: NewsSource
    title_ko: str
    tags: List[str] = field(default_factory=list)
    tickers: List[str] = field(default_factory=list)
    importance_score: float = 0.0
    original_source: Optional[str] = None
    title_en: Optional[str] = None
    content_ko: Optional[str] = None
    summary_ko: Optional[str] = None
    is_headline_only: bool = False
    is_hidden: bool = False
    view_count: int = 0
    source_url: Optional[str] = None
    source_created_at: Optional[datetime] = None
    created_at: Optional[datetime] = None
    extra: Dict[str, Any] = field(default_factory=dict)

    def to_mongo_document(self) -> Dict[str, Any]:
        """MongoDB 저장용 문서로 변환 (기존 Core API snake_case 스키마 준수)"""
        doc = {
            "external_id": self.external_id,
            "source": self.source.value,
            "original_source": self.original_source,
            "title_ko": self.title_ko,
            "title_en": self.title_en,
            "content_ko": self.content_ko,
            "summary_ko": self.summary_ko,
            "tags": self.tags,
            "tickers": self.tickers,
            "importance_score": self.importance_score,
            "is_headline_only": self.is_headline_only,
            "is_hidden": self.is_hidden,
            "view_count": self.view_count,
            "source_url": self.source_url,
            "extra": self.extra,
        }
        if self.source_created_at:
            doc["source_created_at"] = self.source_created_at
        if self.created_at:
            doc["created_at"] = self.created_at
        return doc


@dataclass
class CollectorState:
    """수집기 상태 도메인 모델 (Core API CollectorState.kt와 동기화)"""
    source: NewsSource
    last_fetched_at: Optional[datetime] = None
    last_fetched_id: Optional[str] = None
    fetch_count: int = 0
    consecutive_errors: int = 0
    total_errors: int = 0
    last_response_time_ms: int = 0
    last_error: Optional[str] = None
    last_error_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None
