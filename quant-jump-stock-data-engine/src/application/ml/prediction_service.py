"""
Prediction Application Service

ML 패키지 관리 및 Vertex AI Job 실행을 조율하는 서비스.
"""

import logging
from pathlib import Path
from typing import Optional, Dict, Any
from dataclasses import dataclass

from adapter.output.gcp.storage_service import GcsStorageService, UploadResult
from adapter.output.gcp.vertexai_service import VertexAIService, JobResult, JobConfig

logger = logging.getLogger(__name__)


@dataclass
class GcpConfig:
    """GCP 설정"""
    project_id: str
    region: str
    bucket_name: str
    package_base_path: str = "ml-packages"
    credentials_path: Optional[str] = None

    # Vertex AI Job 설정
    job_name: str = "quantiq-stock-prediction"
    machine_type: str = "n1-standard-4"
    accelerator_type: str = "NVIDIA_TESLA_T4"
    accelerator_count: int = 1
    container_uri: str = "us-docker.pkg.dev/vertex-ai/training/pytorch-gpu.1-13:latest"


class PredictionService:
    """
    예측 서비스

    ML 패키지 업로드 및 Vertex AI Job 실행을 관리합니다.
    """

    def __init__(self, config: GcpConfig, ml_script_path: Optional[Path] = None):
        self.config = config
        self.ml_script_path = ml_script_path or self._get_default_script_path()

        # 어댑터 초기화
        self.storage_service = GcsStorageService(
            project_id=config.project_id,
            bucket_name=config.bucket_name,
            package_base_path=config.package_base_path,
            credentials_path=config.credentials_path
        )

        job_config = JobConfig(
            job_name=config.job_name,
            machine_type=config.machine_type,
            accelerator_type=config.accelerator_type,
            accelerator_count=config.accelerator_count,
            container_uri=config.container_uri
        )

        self.vertexai_service = VertexAIService(
            project_id=config.project_id,
            region=config.region,
            staging_bucket=f"gs://{config.bucket_name}",
            job_config=job_config,
            credentials_path=config.credentials_path
        )

    def _get_default_script_path(self) -> Path:
        """기본 ML 스크립트 경로"""
        # src/ml/predict_optimized.py
        return Path(__file__).parent.parent.parent / "ml" / "predict_optimized.py"

    def upload_package(self) -> UploadResult:
        """
        ML 패키지를 GCS에 업로드

        Returns:
            UploadResult
        """
        logger.info("📦 ML 패키지 업로드 시작")

        if not self.ml_script_path.exists():
            return UploadResult(
                success=False,
                message=f"ML 스크립트를 찾을 수 없습니다: {self.ml_script_path}"
            )

        return self.storage_service.upload_package(self.ml_script_path)

    def get_package_status(self) -> Dict[str, Any]:
        """패키지 상태 조회"""
        return self.storage_service.get_package_status()

    def run_prediction(
        self,
        env_vars: Optional[Dict[str, str]] = None,
        thread_ts: Optional[str] = None
    ) -> JobResult:
        """
        Vertex AI 예측 Job 실행

        Args:
            env_vars: 추가 환경 변수
            thread_ts: Slack 스레드 타임스탬프

        Returns:
            JobResult
        """
        logger.info("🚀 Vertex AI 예측 Job 실행 시작")

        try:
            # 최신 패키지 URI 조회
            package_uri = self.storage_service.get_latest_package_uri()

            # 기본 환경 변수 (VERTEX_AI_ prefix 통일)
            import os
            job_env_vars = {
                "GCS_BUCKET": self.config.bucket_name,
                "GCP_PROJECT_ID": self.config.project_id,
                "FINE_TUNE_MODE": "true",
                "FINE_TUNE_EPOCHS": "5",
                "FULL_TRAIN_EPOCHS": "50",
                # DB 연결 정보 (VERTEX_AI_ prefix 통일)
                "VERTEX_AI_DB_HOST": os.getenv("VERTEX_AI_DB_HOST", ""),
                "VERTEX_AI_DB_PORT": os.getenv("VERTEX_AI_DB_PORT", "5432"),
                "VERTEX_AI_DB_NAME": os.getenv("VERTEX_AI_DB_NAME", "quantiq"),
                "VERTEX_AI_DB_USER": os.getenv("VERTEX_AI_DB_USER", ""),
                "VERTEX_AI_DB_PASSWORD": os.getenv("VERTEX_AI_DB_PASSWORD", ""),
                # MongoDB 연결 정보 (VERTEX_AI_ prefix 통일)
                "VERTEX_AI_MONGODB_URI": os.getenv("VERTEX_AI_MONGODB_URI", ""),
                "VERTEX_AI_MONGODB_DATABASE": os.getenv("VERTEX_AI_MONGODB_DATABASE", "stock_trading"),
                # Slack 설정
                "SLACK_BOT_TOKEN": os.getenv("SLACK_BOT_TOKEN", ""),
                "SLACK_CHANNEL": os.getenv("SLACK_CHANNEL", "#trading-alerts"),
            }

            # 추가 환경 변수 병합
            if env_vars:
                job_env_vars.update(env_vars)

            return self.vertexai_service.create_and_run_job(
                package_uri=package_uri,
                env_vars=job_env_vars,
                thread_ts=thread_ts
            )

        except ValueError as e:
            logger.error(f"패키지 URI 조회 실패: {e}")
            return JobResult(
                success=False,
                message=str(e)
            )

    def get_job_status(self, job_name: str) -> Dict[str, Any]:
        """Job 상태 조회"""
        return self.vertexai_service.get_job_state(job_name)

    def cancel_job(self, job_name: str) -> Dict[str, Any]:
        """Job 취소"""
        return self.vertexai_service.cancel_job(job_name)
