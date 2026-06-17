"""
Vertex AI Service

Vertex AI Custom Job을 관리하는 어댑터.
"""

import logging
import re
from typing import Optional, Dict, Any
from dataclasses import dataclass
from datetime import datetime, timezone
import uuid

# ── 중복 GPU 잡 제출 방지(멱등성) ──────────────────────────────
# 같은 분석 기준일(target_date)에 대해 이미 실행 중인 잡이 있으면 새로 제출하지 않는다.
# Pub/Sub 재배달(at-least-once)로 동일 메시지가 여러 번 도착해도 GPU 잡은 1개만 생성됨.
_LABEL_JOB_KIND = "job_kind"
_LABEL_TARGET_DATE = "target_date"
_JOB_KIND_STOCK_PREDICTION = "stock_prediction"
# Vertex AI 잡 비종료(활성) 상태 — 이 상태의 동일 키 잡이 있으면 중복으로 간주
_ACTIVE_JOB_STATES = (
    "JOB_STATE_QUEUED",
    "JOB_STATE_PENDING",
    "JOB_STATE_RUNNING",
)


def _sanitize_label_value(value: str) -> str:
    """GCP 라벨 값 규칙([a-z0-9_-], 소문자, 최대 63자)에 맞게 정규화"""
    v = re.sub(r"[^a-z0-9_-]", "-", value.lower())
    return v[:63]

# Lazy import: google-cloud-aiplatform (~8초 import 시간)
# 실제 사용 시점에만 import하여 Cold Start 최적화
aiplatform = None
CustomJob = None
JobState = None


def _ensure_aiplatform():
    """google-cloud-aiplatform 패키지를 lazy load"""
    global aiplatform, CustomJob, JobState
    if aiplatform is None:
        from google.cloud import aiplatform as _aiplatform
        from google.cloud.aiplatform import CustomJob as _CustomJob
        from google.cloud.aiplatform_v1.types import JobState as _JobState
        aiplatform = _aiplatform
        CustomJob = _CustomJob
        JobState = _JobState

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
        _ensure_aiplatform()

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

        logger.debug(f"Vertex AI Job 시작: request={request_id}, package={package_uri}")

        # ── 멱등성: target_date 기준 중복 잡 차단 ──
        # TARGET_DATE 미지정(스크립트가 오늘로 자체 판단) 시에도 dedup 키가 필요하므로 UTC 오늘로 대체.
        target_date = env_vars.get("TARGET_DATE") or datetime.now(timezone.utc).strftime("%Y-%m-%d")
        target_date_label = _sanitize_label_value(target_date)

        existing = self._find_active_duplicate_job(target_date_label)
        if existing is not None:
            logger.warning(
                f"중복 제출 차단: target_date={target_date} 활성 잡 이미 존재 "
                f"({existing}) — 새 GPU 잡 제출 스킵 (request={request_id})"
            )
            return JobResult(
                success=True,  # 정상 흡수 → 호출자/Pub/Sub은 ACK 처리
                message=f"이미 실행 중인 잡 존재 — 중복 제출 스킵 (target_date={target_date})",
                job_name=existing,
                job_state="DUPLICATE_SKIPPED",
                request_id=request_id,
            )

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

            # CustomJob 생성 (멱등성 dedup 키를 라벨로 부착)
            job = CustomJob(
                display_name=self.job_config.job_name,
                labels={
                    _LABEL_JOB_KIND: _JOB_KIND_STOCK_PREDICTION,
                    _LABEL_TARGET_DATE: target_date_label,
                },
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

            logger.info(f"Vertex AI Job 제출 완료: {job.resource_name}")

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

    def _find_active_duplicate_job(self, target_date_label: str) -> Optional[str]:
        """동일 target_date의 활성(비종료) 잡 resource_name을 반환, 없으면 None.

        멱등성 1차 방어 — Pub/Sub 재배달로 같은 메시지가 다시 와도 중복 GPU 잡을 막는다.
        주의: Vertex list는 eventual consistency라 거의 동시 제출 시 race로 둘 다 통과할 수 있다.
        이 잔여 위험은 dead-letter(max=5) + 즉시 ACK 로 상한이 묶이며, 조회 자체 실패 시에는
        예측 누락을 피하기 위해 fail-open(제출 진행)한다.
        """
        state_filter = " OR ".join(f'state="{s}"' for s in _ACTIVE_JOB_STATES)
        list_filter = (
            f'labels.{_LABEL_JOB_KIND}="{_JOB_KIND_STOCK_PREDICTION}" '
            f'AND labels.{_LABEL_TARGET_DATE}="{target_date_label}" '
            f'AND ({state_filter})'
        )
        try:
            jobs = CustomJob.list(filter=list_filter)
            for job in jobs:
                return job.resource_name
            return None
        except Exception as e:
            # 조회 실패 → fail-open: 중복 위험보다 예측 누락 방지를 우선
            logger.warning(f"중복 잡 조회 실패(fail-open, 제출 진행): {e}")
            return None

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
        logger.debug(f"환경 변수 {len(env_vars)}개 설정")
        for key, value in env_vars.items():
            if any(s in key.upper() for s in sensitive_keys):
                logger.debug(f"  {key}: ***")
            else:
                logger.debug(f"  {key}: {value}")
