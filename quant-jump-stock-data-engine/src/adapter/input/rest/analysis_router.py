"""
Analysis REST API Router

기술적 분석 직접 실행 API.
Kafka 경유 없이 Data Engine에서 바로 실행.
"""
import logging
import uuid
from typing import Optional
from datetime import datetime

from fastapi import APIRouter, HTTPException, Depends
from pytz import timezone

logger = logging.getLogger(__name__)
KST = timezone('Asia/Seoul')

router = APIRouter(prefix="/api/v1/analysis", tags=["Analysis"])

# 전역 서비스 인스턴스 (main.py에서 설정)
_technical_service = None


def get_technical_service():
    """TechnicalAnalysis 서비스 의존성"""
    if _technical_service is None:
        raise HTTPException(
            status_code=503,
            detail="기술적 분석 서비스가 초기화되지 않았습니다."
        )
    return _technical_service


def set_technical_service(service):
    """서비스 설정 (main.py에서 호출)"""
    global _technical_service
    _technical_service = service


@router.post("/technical")
def run_technical_analysis(
    start_date: Optional[str] = None,
    end_date: Optional[str] = None,
    service=Depends(get_technical_service),
):
    """
    기술적 분석 + Composite Score 실행

    Kafka 경유 없이 직접 실행합니다.
    분석 완료 후 BuyCriteria 필터링 + Slack 알림.

    Query Parameters:
        start_date: 분석 시작 날짜 (YYYY-MM-DD, optional)
        end_date: 분석 기준 날짜 (YYYY-MM-DD, optional, 미지정 시 오늘)
    """
    request_id = str(uuid.uuid4())
    logger.info(f"[{request_id}] REST API 기술적 분석 요청 (start_date={start_date}, end_date={end_date})")

    try:
        result = service.run_technical_analysis(
            request_id=request_id,
            thread_ts=None,
            start_date=start_date,
            end_date=end_date,
        )

        return {
            "success": result.get("status") == "success",
            "request_id": request_id,
            "total_analyzed": result.get("total_analyzed", 0),
            "recommended_count": result.get("recommended_count", 0),
            "timestamp": datetime.now(KST).isoformat(),
        }

    except Exception as e:
        logger.exception(f"[{request_id}] 기술적 분석 실패: {e}")
        raise HTTPException(status_code=500, detail=str(e))
