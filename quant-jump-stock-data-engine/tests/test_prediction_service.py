"""
Prediction Service Tests

ML 패키지 관리 및 Vertex AI Job 실행 서비스 테스트.
"""

import pytest
from unittest.mock import MagicMock, patch, PropertyMock
from pathlib import Path

from application.ml.prediction_service import PredictionService, GcpConfig
from adapter.output.gcp.storage_service import UploadResult
from adapter.output.gcp.vertexai_service import JobResult


class TestGcpConfig:
    """GcpConfig 테스트"""

    def test_create_config_with_required_fields(self):
        """필수 필드로 설정 생성"""
        config = GcpConfig(
            project_id="test-project",
            region="us-central1",
            bucket_name="test-bucket"
        )

        assert config.project_id == "test-project"
        assert config.region == "us-central1"
        assert config.bucket_name == "test-bucket"

    def test_config_default_values(self):
        """기본값 확인"""
        config = GcpConfig(
            project_id="test-project",
            region="us-central1",
            bucket_name="test-bucket"
        )

        assert config.package_base_path == "ml-packages"
        assert config.job_name == "quantiq-stock-prediction"
        assert config.machine_type == "n1-standard-4"
        assert config.accelerator_type == "NVIDIA_TESLA_T4"
        assert config.accelerator_count == 1

    def test_config_custom_values(self):
        """커스텀 값 설정"""
        config = GcpConfig(
            project_id="test-project",
            region="asia-northeast3",
            bucket_name="custom-bucket",
            machine_type="n1-standard-8",
            accelerator_count=2
        )

        assert config.region == "asia-northeast3"
        assert config.machine_type == "n1-standard-8"
        assert config.accelerator_count == 2


class TestPredictionService:
    """PredictionService 테스트"""

    @pytest.fixture
    def mock_storage_service(self):
        """Storage Service Mock"""
        with patch('application.ml.prediction_service.GcsStorageService') as mock:
            instance = MagicMock()
            mock.return_value = instance
            yield instance

    @pytest.fixture
    def mock_vertexai_service(self):
        """VertexAI Service Mock"""
        with patch('adapter.output.gcp.vertexai_service.VertexAIService') as mock:
            instance = MagicMock()
            mock.return_value = instance
            yield instance

    @pytest.fixture
    def gcp_config(self):
        """GCP 설정 픽스처"""
        return GcpConfig(
            project_id="test-project",
            region="us-central1",
            bucket_name="test-bucket"
        )

    @pytest.fixture
    def prediction_service(self, gcp_config, mock_storage_service, mock_vertexai_service):
        """PredictionService 픽스처"""
        with patch.object(Path, 'exists', return_value=True):
            service = PredictionService(
                config=gcp_config,
                ml_script_path=Path("/tmp/test_script.py")
            )
            service.storage_service = mock_storage_service
            service.vertexai_service = mock_vertexai_service
            return service

    def test_upload_package_success(self, prediction_service, mock_storage_service):
        """패키지 업로드 성공"""
        expected_result = UploadResult(
            success=True,
            message="업로드 완료",
            gcs_uri="gs://test-bucket/ml-packages/v1/package.tar.gz",
            version=1
        )
        mock_storage_service.upload_package.return_value = expected_result

        with patch.object(Path, 'exists', return_value=True):
            prediction_service.ml_script_path = Path("/tmp/test.py")
            result = prediction_service.upload_package()

        assert result.success is True
        mock_storage_service.upload_package.assert_called_once()

    def test_upload_package_file_not_found(self, prediction_service):
        """ML 스크립트 파일 없음"""
        prediction_service.ml_script_path = Path("/nonexistent/path.py")

        with patch.object(Path, 'exists', return_value=False):
            result = prediction_service.upload_package()

        assert result.success is False
        assert "찾을 수 없습니다" in result.message

    def test_get_package_status(self, prediction_service, mock_storage_service):
        """패키지 상태 조회"""
        expected_status = {
            "latest_version": "v18",
            "package_count": 18,
            "latest_uri": "gs://test-bucket/ml-packages/v18/package.tar.gz"
        }
        mock_storage_service.get_package_status.return_value = expected_status

        result = prediction_service.get_package_status()

        assert result["latest_version"] == "v18"
        mock_storage_service.get_package_status.assert_called_once()

    def test_run_prediction_success(self, prediction_service, mock_storage_service, mock_vertexai_service):
        """예측 Job 실행 성공"""
        mock_storage_service.get_latest_package_uri.return_value = "gs://bucket/package.tar.gz"
        mock_vertexai_service.create_and_run_job.return_value = JobResult(
            success=True,
            message="Job 제출 완료",
            job_name="projects/test/jobs/job-123",
            job_state="PENDING",
            request_id="req-123"
        )

        result = prediction_service.run_prediction()

        assert result.success is True
        assert result.job_name == "projects/test/jobs/job-123"
        mock_vertexai_service.create_and_run_job.assert_called_once()

    def test_run_prediction_with_env_vars(self, prediction_service, mock_storage_service, mock_vertexai_service):
        """추가 환경 변수와 함께 예측 Job 실행"""
        mock_storage_service.get_latest_package_uri.return_value = "gs://bucket/package.tar.gz"
        mock_vertexai_service.create_and_run_job.return_value = JobResult(
            success=True,
            message="Job 제출 완료"
        )

        custom_env = {"CUSTOM_VAR": "custom_value"}
        result = prediction_service.run_prediction(env_vars=custom_env)

        assert result.success is True
        call_args = mock_vertexai_service.create_and_run_job.call_args
        env_vars = call_args.kwargs.get('env_vars') or call_args[1].get('env_vars')
        assert "CUSTOM_VAR" in env_vars

    def test_run_prediction_with_thread_ts(self, prediction_service, mock_storage_service, mock_vertexai_service):
        """Slack 스레드와 함께 예측 Job 실행"""
        mock_storage_service.get_latest_package_uri.return_value = "gs://bucket/package.tar.gz"
        mock_vertexai_service.create_and_run_job.return_value = JobResult(
            success=True,
            message="Job 제출 완료"
        )

        result = prediction_service.run_prediction(thread_ts="1234567890.123456")

        assert result.success is True
        call_args = mock_vertexai_service.create_and_run_job.call_args
        thread_ts = call_args.kwargs.get('thread_ts') or call_args[1].get('thread_ts')
        assert thread_ts == "1234567890.123456"

    def test_run_prediction_package_not_found(self, prediction_service, mock_storage_service):
        """패키지 URI 조회 실패"""
        mock_storage_service.get_latest_package_uri.side_effect = ValueError("패키지 없음")

        result = prediction_service.run_prediction()

        assert result.success is False
        assert "패키지 없음" in result.message

    def test_get_job_status(self, prediction_service, mock_vertexai_service):
        """Job 상태 조회"""
        mock_vertexai_service.get_job_state.return_value = {
            "success": True,
            "job_name": "projects/test/jobs/job-123",
            "state": "RUNNING",
            "state_description": "실행 중"
        }

        result = prediction_service.get_job_status("projects/test/jobs/job-123")

        assert result["state"] == "RUNNING"
        mock_vertexai_service.get_job_state.assert_called_once_with("projects/test/jobs/job-123")

    def test_cancel_job(self, prediction_service, mock_vertexai_service):
        """Job 취소"""
        mock_vertexai_service.cancel_job.return_value = {
            "success": True,
            "message": "Job 취소 요청 완료",
            "job_name": "projects/test/jobs/job-123"
        }

        result = prediction_service.cancel_job("projects/test/jobs/job-123")

        assert result["success"] is True
        mock_vertexai_service.cancel_job.assert_called_once_with("projects/test/jobs/job-123")


class TestJobResult:
    """JobResult 테스트"""

    def test_job_result_success(self):
        """성공 결과"""
        result = JobResult(
            success=True,
            message="완료",
            job_name="test-job",
            job_state="SUCCEEDED"
        )

        assert result.success is True
        assert result.job_state == "SUCCEEDED"

    def test_job_result_failure(self):
        """실패 결과"""
        result = JobResult(
            success=False,
            message="실패"
        )

        assert result.success is False
        assert result.job_name is None

    def test_job_result_has_timestamp(self):
        """타임스탬프 자동 생성"""
        result = JobResult(success=True, message="test")

        assert result.timestamp is not None
        assert len(result.timestamp) > 0
