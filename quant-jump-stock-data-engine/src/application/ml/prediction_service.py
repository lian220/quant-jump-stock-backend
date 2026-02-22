"""
Prediction Application Service

ML 패키지 관리 및 Vertex AI Job 실행을 조율하는 서비스.
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Optional, Dict, Any
from dataclasses import dataclass

from adapter.output.gcp.storage_service import GcsStorageService, UploadResult
from adapter.output.gcp.vertexai_service import JobResult
# Lazy import: vertexai_service는 실제 사용 시점에만 import (Cold Start 최적화)
# google-cloud-aiplatform 패키지가 ~8초 import 소요

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
        from adapter.output.gcp.vertexai_service import VertexAIService, JobConfig

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

        Cloud Scheduler 직접 호출을 위해 DB 크레덴셜 + Slack 설정 + Callback URL을
        스크립트에 환경변수로 주입합니다.

        Returns:
            UploadResult
        """
        logger.info("📦 ML 패키지 업로드 시작")

        if not self.ml_script_path.exists():
            return UploadResult(
                success=False,
                message=f"ML 스크립트를 찾을 수 없습니다: {self.ml_script_path}"
            )

        import os
        from config.vertex_ai_job_env import VertexAIJobEnvLoader

        # .env.db.prod에서 DB 크레덴셜 로드
        db_env = VertexAIJobEnvLoader().load()

        # 주입할 환경변수 구성
        inject_env = {
            # DB 연결 정보
            **db_env,
            # Slack 설정
            "SLACK_BOT_TOKEN": os.getenv("SLACK_BOT_TOKEN", ""),
            "SLACK_CHANNEL": os.getenv("SLACK_CHANNEL", "#trading-alerts"),
        }

        # 빈 값 제거
        inject_env = {k: v for k, v in inject_env.items() if v}

        logger.info(f"📋 환경변수 {len(inject_env)}개 주입 예정: {list(inject_env.keys())}")

        return self.storage_service.upload_package(self.ml_script_path, inject_env=inject_env)

    def get_package_status(self) -> Dict[str, Any]:
        """패키지 상태 조회"""
        return self.storage_service.get_package_status()

    def run_prediction(
        self,
        target_date: Optional[str] = None,
        env_vars: Optional[Dict[str, str]] = None,
        thread_ts: Optional[str] = None
    ) -> JobResult:
        """
        Vertex AI 예측 Job 실행

        Args:
            target_date: 분석 기준 날짜 (YYYY-MM-DD, None이면 현재 날짜)
            env_vars: 추가 환경 변수
            thread_ts: Slack 스레드 타임스탬프

        Returns:
            JobResult
        """
        logger.info(f"🚀 Vertex AI 예측 Job 실행 시작 (target_date={target_date or '현재'})")

        try:
            # 최신 패키지 URI 조회
            package_uri = self.storage_service.get_latest_package_uri()

            # DB/Slack/Callback 등은 패키지 업로드 시 스크립트에 주입됨 (os.environ.setdefault)
            # 여기서는 런타임에 동적으로 바뀌는 값만 전달
            job_env_vars: Dict[str, str] = {}

            # Admin에서 강제 지정한 학습 모드 (없으면 스크립트가 요일로 자체 판단)
            if env_vars and "FINE_TUNE_MODE" in env_vars:
                job_env_vars["FINE_TUNE_MODE"] = env_vars["FINE_TUNE_MODE"]

            # 분석 기준 날짜 (없으면 스크립트가 오늘 날짜로 자체 판단)
            if target_date:
                job_env_vars["TARGET_DATE"] = target_date

            mode = job_env_vars.get("FINE_TUNE_MODE", "auto")
            logger.info(f"📋 학습 모드: {mode} (auto=스크립트 자체 판단)")

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
