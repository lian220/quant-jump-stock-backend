"""
ML REST API Router

ML 패키지 관리 및 Vertex AI Job 실행 API.
"""

import logging
from typing import Optional, Dict, Any
from datetime import datetime

from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/ml", tags=["ML Package"])


class JobStatusRequest(BaseModel):
    """Job 상태 조회 요청"""
    job_name: str


# 전역 서비스 인스턴스 (main.py에서 설정)
_prediction_service = None


def get_prediction_service():
    """PredictionService 의존성"""
    if _prediction_service is None:
        raise HTTPException(
            status_code=503,
            detail="GCP가 비활성화 상태입니다. GCP_ENABLED=true로 설정해주세요."
        )
    return _prediction_service


def set_prediction_service(service):
    """PredictionService 설정 (main.py에서 호출)"""
    global _prediction_service
    _prediction_service = service


@router.post("/upload")
async def upload_package(service=Depends(get_prediction_service)):
    """
    ML 패키지를 GCS에 업로드

    predict_optimized.py를 tar.gz로 패키징하여 GCS에 업로드합니다.
    """
    logger.info("📦 ML 패키지 업로드 요청")

    result = service.upload_package()

    if result.success:
        return {
            "success": True,
            "message": result.message,
            "gcs_uri": result.gcs_uri,
            "version": result.version,
            "timestamp": result.timestamp
        }
    else:
        raise HTTPException(status_code=500, detail=result.message)


@router.get("/status")
async def get_package_status(service=Depends(get_prediction_service)):
    """
    ML 패키지 상태 조회

    GCS에 업로드된 패키지 정보를 조회합니다.
    """
    logger.info("📋 패키지 상태 조회 요청")
    return service.get_package_status()


# NOTE: /predict 엔드포인트는 제공하지 않음
# Vertex AI Job 실행은 Core API → Kafka → Data Engine 흐름으로만 가능
# Core API: POST /api/v1/vertex-ai/run


@router.get("/job/status")
async def get_job_status(
    job_name: str,
    service=Depends(get_prediction_service)
):
    """
    Vertex AI Job 상태 조회
    """
    logger.info(f"📋 Job 상태 조회: {job_name}")
    return service.get_job_status(job_name)


@router.post("/job/cancel")
async def cancel_job(
    request: JobStatusRequest,
    service=Depends(get_prediction_service)
):
    """
    Vertex AI Job 취소
    """
    logger.info(f"🛑 Job 취소 요청: {request.job_name}")
    return service.cancel_job(request.job_name)


@router.post("/callback")
async def job_callback(
    request_id: str,
    status: str,
    message: Optional[str] = None,
    duration: Optional[str] = None,
    error_detail: Optional[str] = None,
    thread_ts: Optional[str] = None
):
    """
    Vertex AI Job 완료 콜백

    predict_optimized.py에서 Job 완료 시 호출합니다.
    """
    logger.info("=" * 60)
    logger.info("📬 Vertex AI Job 콜백 수신")
    logger.info(f"Request ID: {request_id}")
    logger.info(f"Status: {status}")
    logger.info(f"Thread TS: {thread_ts}")
    logger.info("=" * 60)

    # TODO: Slack 알림 전송
    # TODO: 결과 저장

    return {
        "success": True,
        "message": "콜백 처리 완료",
        "request_id": request_id,
        "status": status,
        "timestamp": datetime.now().isoformat()
    }
