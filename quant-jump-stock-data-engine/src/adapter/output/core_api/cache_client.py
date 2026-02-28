"""Core API 캐시 eviction 클라이언트

Data Engine → Core API HTTP 콜백으로 Caffeine 캐시 무효화.
Best-effort: 실패해도 파이프라인 중단하지 않음 (TTL로 자연 만료됨).
"""
import logging
from typing import List

import requests

logger = logging.getLogger(__name__)

# 종목 추천 완료 후 무효화할 캐시 목록
PREDICTION_CACHE_NAMES = [
    "buySignals",
    "tickerPredictions",
    "recentPredictions",
    "predictionStats",
]


class CoreCacheClient:
    """Core API 캐시 eviction 클라이언트"""

    def __init__(self, base_url: str):
        self._base_url = base_url.rstrip("/")
        self._evict_url = f"{self._base_url}/api/v1/internal/cache/evict"

    def evict_prediction_caches(self) -> bool:
        """종목 추천 관련 캐시 4개 무효화 (best-effort)"""
        return self._evict(PREDICTION_CACHE_NAMES)

    def _evict(self, cache_names: List[str]) -> bool:
        """캐시 eviction 요청. 성공 시 True, 실패 시 False (예외 없음)."""
        try:
            resp = requests.post(
                self._evict_url,
                json={"cacheNames": cache_names},
                timeout=5,
            )
            if resp.status_code == 200:
                data = resp.json()
                logger.info(
                    f"Core 캐시 eviction 성공: {data.get('evicted', [])}"
                )
                return True
            else:
                logger.warning(
                    f"Core 캐시 eviction 실패 (HTTP {resp.status_code}): {resp.text}"
                )
                return False
        except requests.exceptions.ConnectionError:
            logger.warning(
                f"Core API 연결 불가 ({self._evict_url}) — 캐시 eviction 스킵"
            )
            return False
        except Exception as e:
            logger.warning(f"Core 캐시 eviction 오류: {e}")
            return False
