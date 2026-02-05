"""
Vertex AI Service

Vertex AI Custom Job을 관리하는 어댑터.
"""

import logging
from typing import Optional, Dict, Any
from dataclasses import dataclass
from datetime import datetime
import uuid

from google.cloud import aiplatform
from google.cloud.aiplatform import CustomJob
from google.cloud.aiplatform_v1.types import JobState

logger = logging.getLogger(__name__)


@dataclass
class JobConfig:
    """Vertex AI Job 설정"""
    job_name: str = "quantiq-stock-prediction"
    machine_type: str = "n1-standard-4"
    accelerator_type: str = "NVIDIA_TESLA_T4"
    accelerator_count: int = 1
    container_uri: str = "us-docker.pkg.dev/vertex-ai/training/pytorch-gpu.1-13:latest"


@dataclass
class JobResult:
    """Job 실행 결과"""
    success: bool
    message: str
    job_name: Optional[str] = None
    job_state: Optional[str] = None
    request_id: Optional[str] = None
    timestamp: str = ""

    def __post_init__(self):
        if not self.timestamp:
            self.timestamp = datetime.now().isoformat()


class VertexAIService:
    """Vertex AI Custom Job 서비스"""

    def __init__(
        self,
        project_id: str,
        region: str,
        staging_bucket: str,
        job_config: Optional[JobConfig] = None,
        credentials_path: Optional[str] = None
    ):
        self.project_id = project_id
        self.region = region
        self.staging_bucket = staging_bucket
        self.job_config = job_config or JobConfig()

        # Vertex AI 초기화 (staging_bucket 포함)
        aiplatform.init(
            project=project_id,
            location=region,
            staging_bucket=staging_bucket,
            credentials=self._load_credentials(credentials_path) if credentials_path else None
        )

    def _load_credentials(self, credentials_path: str):
        """서비스 계정 인증 정보 로드"""
        from google.oauth2 import service_account
        return service_account.Credentials.from_service_account_file(credentials_path)

    def create_and_run_job(
        self,
        package_uri: str,
        env_vars: Dict[str, str],
        thread_ts: Optional[str] = None
    ) -> JobResult:
        """
        Vertex AI Custom Job 생성 및 실행

        Args:
            package_uri: GCS 패키지 URI
            env_vars: 환경 변수
            thread_ts: Slack 스레드 타임스탬프

        Returns:
            JobResult
        """
        request_id = str(uuid.uuid4())

        logger.info("=" * 60)
        logger.info("🚀 Vertex AI 주가 예측 작업 시작")
        logger.info(f"Request ID: {request_id}")
        logger.info(f"Project: {self.project_id}")
        logger.info(f"Region: {self.region}")
        logger.info(f"Package URI: {package_uri}")
        logger.info("=" * 60)

        try:
            # 환경 변수에 request_id 추가
            job_env_vars = {
                **env_vars,
                "REQUEST_ID": request_id,
            }
            if thread_ts:
                job_env_vars["SLACK_THREAD_TS"] = thread_ts

            # 민감 정보 마스킹 로깅
            self._log_env_vars_safely(job_env_vars)

            # CustomJob 생성
            job = CustomJob(
                display_name=self.job_config.job_name,
                worker_pool_specs=[{
                    "machine_spec": {
                        "machine_type": self.job_config.machine_type,
                        "accelerator_type": self.job_config.accelerator_type,
                        "accelerator_count": self.job_config.accelerator_count,
                    },
                    "replica_count": 1,
                    "python_package_spec": {
                        "executor_image_uri": self.job_config.container_uri,
                        "package_uris": [package_uri],
                        "python_module": "aiplatform_custom_trainer_script.task",
                        "env": [{"name": k, "value": v} for k, v in job_env_vars.items() if v],
                    },
                }],
            )

            # Job 실행 (비동기)
            job.submit()

            logger.info("=" * 60)
            logger.info("✅ Vertex AI CustomJob 제출 완료")
            logger.info(f"Job Name: {job.resource_name}")
            logger.info(f"Request ID: {request_id}")
            logger.info("=" * 60)

            return JobResult(
                success=True,
                message="Vertex AI Job 제출 완료",
                job_name=job.resource_name,
                job_state="PENDING",
                request_id=request_id
            )

        except Exception as e:
            logger.exception("❌ Vertex AI CustomJob 실행 실패")
            return JobResult(
                success=False,
                message=f"Vertex AI Job 실행 실패: {str(e)}",
                request_id=request_id
            )

    def get_job_state(self, job_name: str) -> Dict[str, Any]:
        """Job 상태 조회"""
        try:
            job = CustomJob(job_name=job_name)
            state = job.state

            return {
                "success": True,
                "job_name": job_name,
                "state": state.name if state else "UNKNOWN",
                "state_description": self._get_state_description(state),
                "timestamp": datetime.now().isoformat()
            }

        except Exception as e:
            logger.exception(f"Job 상태 조회 실패: {job_name}")
            return {
                "success": False,
                "job_name": job_name,
                "error": str(e),
                "timestamp": datetime.now().isoformat()
            }

    def cancel_job(self, job_name: str) -> Dict[str, Any]:
        """Job 취소"""
        try:
            job = CustomJob(job_name=job_name)
            job.cancel()

            logger.info(f"Job 취소 요청 완료: {job_name}")

            return {
                "success": True,
                "message": "Job 취소 요청 완료",
                "job_name": job_name,
                "timestamp": datetime.now().isoformat()
            }

        except Exception as e:
            logger.exception(f"Job 취소 실패: {job_name}")
            return {
                "success": False,
                "error": str(e),
                "job_name": job_name,
                "timestamp": datetime.now().isoformat()
            }

    def _get_state_description(self, state) -> str:
        """Job 상태 설명"""
        descriptions = {
            JobState.JOB_STATE_QUEUED: "대기 중",
            JobState.JOB_STATE_PENDING: "준비 중",
            JobState.JOB_STATE_RUNNING: "실행 중",
            JobState.JOB_STATE_SUCCEEDED: "완료",
            JobState.JOB_STATE_FAILED: "실패",
            JobState.JOB_STATE_CANCELLING: "취소 중",
            JobState.JOB_STATE_CANCELLED: "취소됨",
            JobState.JOB_STATE_PAUSED: "일시정지",
            JobState.JOB_STATE_EXPIRED: "만료됨",
        }
        return descriptions.get(state, "알 수 없음")

    def _log_env_vars_safely(self, env_vars: Dict[str, str]):
        """민감 정보 마스킹하여 로깅"""
        sensitive_keys = {"PASSWORD", "SECRET", "TOKEN", "KEY"}
        logger.info(f"📋 환경 변수 {len(env_vars)}개 설정:")
        for key, value in env_vars.items():
            if any(s in key.upper() for s in sensitive_keys):
                logger.info(f"  {key}: ***")
            else:
                logger.info(f"  {key}: {value}")
