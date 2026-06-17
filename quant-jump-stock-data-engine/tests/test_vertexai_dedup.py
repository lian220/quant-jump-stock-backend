"""
VertexAIService 멱등성(중복 GPU 잡 제출 방지) 테스트

Pub/Sub 재배달(at-least-once)로 동일 메시지가 여러 번 도착해도
같은 target_date 에 대해 GPU 잡이 1개만 생성되어야 한다.
"""

import pytest
from unittest.mock import MagicMock, patch

from adapter.output.gcp import vertexai_service as vs
from adapter.output.gcp.vertexai_service import (
    VertexAIService,
    JobConfig,
    _sanitize_label_value,
    _LABEL_JOB_KIND,
    _LABEL_TARGET_DATE,
    _JOB_KIND_STOCK_PREDICTION,
)


def _make_service() -> VertexAIService:
    """aiplatform 초기화를 건너뛰고 VertexAIService 인스턴스를 만든다."""
    svc = VertexAIService.__new__(VertexAIService)
    svc.project_id = "test-project"
    svc.region = "us-central1"
    svc.staging_bucket = "gs://test-bucket"
    svc.job_config = JobConfig()
    return svc


class TestSanitizeLabelValue:
    def test_valid_date_unchanged(self):
        assert _sanitize_label_value("2026-06-17") == "2026-06-17"

    def test_uppercase_lowered(self):
        assert _sanitize_label_value("ABC") == "abc"

    def test_invalid_chars_replaced(self):
        assert _sanitize_label_value("a/b c") == "a-b-c"

    def test_truncated_to_63(self):
        assert len(_sanitize_label_value("x" * 100)) == 63


class TestFindActiveDuplicateJob:
    def test_returns_resource_name_when_active_job_exists(self):
        svc = _make_service()
        existing = MagicMock()
        existing.resource_name = "projects/p/locations/us-central1/customJobs/123"
        with patch.object(vs, "CustomJob") as MockCustomJob:
            MockCustomJob.list.return_value = [existing]
            result = svc._find_active_duplicate_job("2026-06-17")
        assert result == "projects/p/locations/us-central1/customJobs/123"

    def test_returns_none_when_no_job(self):
        svc = _make_service()
        with patch.object(vs, "CustomJob") as MockCustomJob:
            MockCustomJob.list.return_value = []
            assert svc._find_active_duplicate_job("2026-06-17") is None

    def test_fail_open_on_list_error(self):
        """조회 실패 시 None 반환(fail-open) — 예측 누락 방지"""
        svc = _make_service()
        with patch.object(vs, "CustomJob") as MockCustomJob:
            MockCustomJob.list.side_effect = RuntimeError("API down")
            assert svc._find_active_duplicate_job("2026-06-17") is None

    def test_filter_includes_labels_and_active_states(self):
        svc = _make_service()
        with patch.object(vs, "CustomJob") as MockCustomJob:
            MockCustomJob.list.return_value = []
            svc._find_active_duplicate_job("2026-06-17")
            used_filter = MockCustomJob.list.call_args.kwargs["filter"]
        assert f'labels.{_LABEL_JOB_KIND}="{_JOB_KIND_STOCK_PREDICTION}"' in used_filter
        assert f'labels.{_LABEL_TARGET_DATE}="2026-06-17"' in used_filter
        assert 'state="JOB_STATE_RUNNING"' in used_filter
        assert 'state="JOB_STATE_PENDING"' in used_filter


class TestCreateAndRunJobIdempotency:
    def test_skips_submit_when_duplicate_active_job_exists(self):
        """중복 활성 잡이 있으면 새 잡을 만들지 않고 success=True 로 흡수(ACK)"""
        svc = _make_service()
        with patch.object(vs, "CustomJob") as MockCustomJob:
            MockCustomJob.list.return_value = [MagicMock(resource_name="dup-job")]
            result = svc.create_and_run_job(
                package_uri="gs://pkg/x.tar.gz",
                env_vars={"TARGET_DATE": "2026-06-17"},
            )
        # 잡 생성자가 호출되지 않아야 함 (submit 안 함)
        MockCustomJob.assert_not_called()
        assert result.success is True
        assert result.job_state == "DUPLICATE_SKIPPED"
        assert result.job_name == "dup-job"

    def test_submits_with_labels_when_no_duplicate(self):
        """중복이 없으면 라벨을 붙여 새 잡을 제출"""
        svc = _make_service()
        job_instance = MagicMock()
        job_instance.resource_name = "new-job"
        with patch.object(vs, "CustomJob") as MockCustomJob:
            MockCustomJob.list.return_value = []
            MockCustomJob.return_value = job_instance
            result = svc.create_and_run_job(
                package_uri="gs://pkg/x.tar.gz",
                env_vars={"TARGET_DATE": "2026-06-17"},
            )
        job_instance.submit.assert_called_once()
        labels = MockCustomJob.call_args.kwargs["labels"]
        assert labels[_LABEL_JOB_KIND] == _JOB_KIND_STOCK_PREDICTION
        assert labels[_LABEL_TARGET_DATE] == "2026-06-17"
        assert result.success is True
        assert result.job_state == "PENDING"
        assert result.job_name == "new-job"

    def test_uses_utc_today_when_target_date_missing(self):
        """TARGET_DATE 미지정 시 UTC 오늘 날짜를 dedup 키로 사용"""
        svc = _make_service()
        with patch.object(vs, "CustomJob") as MockCustomJob:
            MockCustomJob.list.return_value = []
            MockCustomJob.return_value = MagicMock(resource_name="new-job")
            svc.create_and_run_job(package_uri="gs://pkg/x.tar.gz", env_vars={})
            used_filter = MockCustomJob.list.call_args.kwargs["filter"]
        # 날짜 형식 YYYY-MM-DD 가 필터에 들어갔는지만 확인 (정확한 값은 실행일 의존)
        import re
        assert re.search(r'labels\.target_date="\d{4}-\d{2}-\d{2}"', used_filter)
