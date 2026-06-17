from enum import Enum

from fastapi import APIRouter

from application.analysis.price_history_service import PriceHistoryService
from adapter.output.mongodb.analysis_repository import MongoPriceRepository
from core.database import MongoDB
from core.timezone import latest_complete_bar_date_str

router = APIRouter(prefix="/api/v1/stocks", tags=["Price History"])


class PeriodEnum(str, Enum):
    one_month = "1m"
    three_month = "3m"
    six_month = "6m"
    one_year = "1y"


def _build_repo() -> MongoPriceRepository:
    return MongoPriceRepository(MongoDB.get_db())


def _today() -> str:
    return latest_complete_bar_date_str()


@router.get("/{ticker}/price-history")
def get_price_history(ticker: str, period: PeriodEnum = PeriodEnum.six_month):
    service = PriceHistoryService(_build_repo())
    return service.build(ticker.upper(), period.value, today=_today())
