"""
Price History REST API Router

종목 OHLCV + 기술지표(MA/RSI/MACD) 시계열 조회 API.
"""
import logging
from enum import Enum

from fastapi import APIRouter, HTTPException, Depends

from application.analysis.price_history_service import PriceHistoryService
from core.timezone import latest_complete_bar_date_str

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/stocks", tags=["Price History"])


class PeriodEnum(str, Enum):
    one_month = "1m"
    three_month = "3m"
    six_month = "6m"
    one_year = "1y"


# 전역 서비스 인스턴스 (main.py에서 설정)
_price_history_service = None


def get_price_history_service() -> PriceHistoryService:
    """PriceHistoryService 의존성"""
    if _price_history_service is None:
        raise HTTPException(
            status_code=503,
            detail="가격 시계열 서비스가 초기화되지 않았습니다."
        )
    return _price_history_service


def set_price_history_service(service: PriceHistoryService) -> None:
    """서비스 설정 (main.py에서 호출)"""
    global _price_history_service
    _price_history_service = service


# sync def: 내부가 동기 pymongo 호출이라 FastAPI threadpool에서 처리(이벤트루프 미차단)
@router.get("/{ticker}/price-history")
def get_price_history(
    ticker: str,
    period: PeriodEnum = PeriodEnum.six_month,
    service: PriceHistoryService = Depends(get_price_history_service),
):
    """종목 OHLCV + MA/RSI/MACD 시계열 조회. period: 1m|3m|6m|1y (기본 6m)."""
    try:
        return service.build(
            ticker.upper(),
            period.value,
            today=latest_complete_bar_date_str(),
        )
    except Exception as e:
        logger.exception(f"가격 시계열 조회 실패: ticker={ticker}, period={period.value}")
        raise HTTPException(status_code=500, detail="가격 시계열 조회 중 오류가 발생했습니다.") from e
