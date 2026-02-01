"""Economic Data Router - FastAPI 엔드포인트 (Read-Only Status API)"""
import logging
from datetime import datetime
from fastapi import APIRouter
from pytz import timezone

from .schemas import StatusResponse

logger = logging.getLogger(__name__)
KST = timezone('Asia/Seoul')

router = APIRouter(prefix="/api/economic", tags=["economic"])


@router.get("/status", response_model=StatusResponse)
def get_economic_data_status():
    """경제 데이터 수집 상태 조회 (Read-Only)"""
    return StatusResponse(
        service="economic-data-collector",
        status="running",
        timestamp=datetime.now(KST).isoformat(),
        supported_triggers=[
            "Kafka Topic: economic.data.update.request"
        ]
    )
