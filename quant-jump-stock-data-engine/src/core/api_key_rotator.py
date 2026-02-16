"""
Alpha Vantage API Key Rotation Manager

여러 개의 API 키를 라운드 로빈 방식으로 순환하며 사용.
Rate Limit 감지 시 자동으로 다음 키로 전환.
"""
import os
import logging
import time
from typing import List, Optional
from dataclasses import dataclass, field
from datetime import datetime, timedelta

logger = logging.getLogger(__name__)


@dataclass
class ApiKeyStats:
    """API 키 사용 통계"""
    key: str
    total_calls: int = 0
    successful_calls: int = 0
    failed_calls: int = 0
    rate_limited_count: int = 0
    last_used_at: Optional[datetime] = None
    last_rate_limit_at: Optional[datetime] = None

    @property
    def masked_key(self) -> str:
        """마스킹된 키 (보안)"""
        if len(self.key) > 8:
            return f"{self.key[:4]}...{self.key[-4:]}"
        return "****"


class AlphaVantageKeyRotator:
    """
    Alpha Vantage API 키 로테이션 관리자

    특징:
    - 라운드 로빈 방식 순환
    - Rate Limit 자동 감지 및 전환
    - 모든 키 limit 시 백오프 전략
    - 사용 통계 추적
    """

    # Alpha Vantage 무료 제한
    RATE_LIMIT_DELAY = 12  # 5 calls/min → 12초 대기
    BACKOFF_DELAY = 60     # 모든 키 limit 시 1분 대기

    def __init__(self):
        self.keys: List[str] = self._load_keys()
        self.stats: List[ApiKeyStats] = [ApiKeyStats(key=k) for k in self.keys]
        self.current_index: int = 0

        if not self.keys:
            logger.warning("⚠️ Alpha Vantage API 키가 설정되지 않았습니다. 뉴스 감정 분석을 건너뜁니다.")
        else:
            logger.info(f"🔑 API Key Rotator 초기화: {len(self.keys)}개 키 로드됨")

    def _load_keys(self) -> List[str]:
        """환경변수에서 API 키 로드"""
        keys = []

        # ALPHA_VANTAGE_API_KEY_1, _2, _3... 순서로 로드
        for i in range(1, 10):  # 최대 9개까지 지원
            key = os.getenv(f"ALPHA_VANTAGE_API_KEY_{i}")
            if key and key.strip():
                keys.append(key.strip())

        # 레거시 호환: ALPHA_VANTAGE_API_KEY도 체크
        legacy_key = os.getenv("ALPHA_VANTAGE_API_KEY")
        if legacy_key and legacy_key.strip() and legacy_key not in keys:
            keys.insert(0, legacy_key.strip())

        return keys

    def get_next_key(self) -> str:
        """다음 API 키 반환 (라운드 로빈)"""
        if not self.keys:
            raise RuntimeError("No API keys available")

        key = self.keys[self.current_index]
        stats = self.stats[self.current_index]

        # 통계 업데이트
        stats.total_calls += 1
        stats.last_used_at = datetime.utcnow()

        # 다음 키로 순환
        self.current_index = (self.current_index + 1) % len(self.keys)

        logger.debug(f"Using API key: {stats.masked_key} (total calls: {stats.total_calls})")
        return key

    def mark_success(self, key: str):
        """성공한 API 호출 기록"""
        stats = self._get_stats(key)
        if stats:
            stats.successful_calls += 1

    def mark_failure(self, key: str):
        """실패한 API 호출 기록"""
        stats = self._get_stats(key)
        if stats:
            stats.failed_calls += 1

    def mark_rate_limited(self, key: str):
        """Rate Limit 감지 기록"""
        stats = self._get_stats(key)
        if stats:
            stats.rate_limited_count += 1
            stats.last_rate_limit_at = datetime.utcnow()
            logger.warning(
                f"⚠️ Rate Limit 감지: {stats.masked_key} "
                f"(total: {stats.rate_limited_count}회)"
            )

    def should_backoff(self) -> bool:
        """모든 키가 최근 1분 내 rate limit 걸렸는지 확인"""
        now = datetime.utcnow()
        one_minute_ago = now - timedelta(seconds=60)

        all_rate_limited = all(
            stats.last_rate_limit_at and stats.last_rate_limit_at > one_minute_ago
            for stats in self.stats
        )

        return all_rate_limited

    def wait_if_needed(self, key: str, is_rate_limited: bool = False):
        """Rate Limit 대기 (필요시)"""
        if is_rate_limited:
            self.mark_rate_limited(key)

            if self.should_backoff():
                logger.warning(
                    f"🛑 모든 키가 Rate Limit 상태. "
                    f"{self.BACKOFF_DELAY}초 대기..."
                )
                time.sleep(self.BACKOFF_DELAY)
            else:
                logger.debug(f"다음 키로 전환 (대기 없음)")
        else:
            # 정상 호출 간 기본 딜레이 (5 calls/min 준수)
            time.sleep(self.RATE_LIMIT_DELAY)

    def get_statistics(self) -> dict:
        """전체 통계 반환"""
        total_calls = sum(s.total_calls for s in self.stats)
        successful = sum(s.successful_calls for s in self.stats)
        failed = sum(s.failed_calls for s in self.stats)
        rate_limited = sum(s.rate_limited_count for s in self.stats)

        return {
            "total_keys": len(self.keys),
            "total_calls": total_calls,
            "successful_calls": successful,
            "failed_calls": failed,
            "rate_limited_count": rate_limited,
            "success_rate": f"{(successful / total_calls * 100):.2f}%" if total_calls > 0 else "0%",
            "keys": [
                {
                    "masked_key": s.masked_key,
                    "total_calls": s.total_calls,
                    "successful_calls": s.successful_calls,
                    "rate_limited_count": s.rate_limited_count,
                    "last_used_at": s.last_used_at.isoformat() if s.last_used_at else None
                }
                for s in self.stats
            ]
        }

    def _get_stats(self, key: str) -> Optional[ApiKeyStats]:
        """키에 해당하는 통계 객체 반환"""
        for stats in self.stats:
            if stats.key == key:
                return stats
        return None
