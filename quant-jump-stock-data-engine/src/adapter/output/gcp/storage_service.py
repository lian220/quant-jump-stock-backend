"""
GCS Storage Service

ML 패키지를 GCS에 업로드하고 관리하는 어댑터.
"""
from __future__ import annotations

import io
import tarfile
import logging
from pathlib import Path
from datetime import datetime
from typing import Optional, Dict
from dataclasses import dataclass

from google.cloud import storage

logger = logging.getLogger(__name__)


@dataclass
class UploadResult:
    """업로드 결과"""
    success: bool
    message: str
    gcs_uri: Optional[str] = None
    version: Optional[int] = None
    timestamp: str = ""

    def __post_init__(self):
        if not self.timestamp:
            self.timestamp = datetime.now().isoformat()


class GcsStorageService:
    """GCS 스토리지 서비스"""

    PACKAGE_PREFIX = "quantiq-ml-package"
    TRAINER_DIR = "aiplatform_custom_trainer_script"

    def __init__(
        self,
        project_id: str,
        bucket_name: str,
        package_base_path: str = "ml-packages",
        credentials_path: Optional[str] = None
    ):
        self.project_id = project_id
        self.bucket_name = bucket_name
        self.package_base_path = package_base_path

        if credentials_path:
            self.client = storage.Client.from_service_account_json(credentials_path)
        else:
            self.client = storage.Client(project=project_id)

        self.bucket = self.client.bucket(bucket_name)

    def upload_package(self, script_path: Path, inject_env: Optional[Dict[str, str]] = None) -> UploadResult:
        """
        ML 패키지를 GCS에 업로드

        Args:
            script_path: predict_optimized.py 경로
            inject_env: 스크립트 상단에 주입할 환경변수 딕셔너리

        Returns:
            UploadResult
        """
        logger.debug(f"GCS 업로드 시작: 버킷={self.bucket_name}, 스크립트={script_path}")

        try:
            current_version = self.get_current_version()
            new_version = current_version + 1

            logger.debug(f"버전: v{current_version} → v{new_version}")

            # 스크립트 로드
            python_script = script_path.read_text(encoding="utf-8")

            # 환경변수 주입 (Cloud Scheduler 직접 호출 시 credentials 내장)
            if inject_env:
                env_lines = ["# === 자동 주입된 환경변수 (업로드 시점) ==="]
                for key, value in inject_env.items():
                    # 값에 따옴표가 포함될 수 있으므로 repr 사용
                    env_lines.append(f'os.environ.setdefault({repr(key)}, {repr(value)})')
                env_lines.append("# === 환경변수 주입 끝 ===\n")
                env_block = "\n".join(env_lines) + "\n"

                # 'import os' 다음 줄에 주입
                import_os_marker = "import os\n"
                if import_os_marker in python_script:
                    python_script = python_script.replace(
                        import_os_marker,
                        import_os_marker + env_block,
                        1  # 첫 번째만
                    )
                else:
                    # import os가 없으면 최상단에 추가
                    python_script = f"import os\n{env_block}" + python_script

                logger.info(f"환경변수 {len(inject_env)}개 주입 완료")

            setup_script = self._generate_setup_py()

            # tar.gz 패키지 생성
            package_bytes = self._create_tar_gz_package(python_script, setup_script)

            # 1) 버전별 경로 업로드 (롤백용)
            blob_path = f"{self.package_base_path}/{self.PACKAGE_PREFIX}-v{new_version}.tar.gz"
            blob = self.bucket.blob(blob_path)
            blob.upload_from_string(package_bytes, content_type="application/gzip")

            # 2) latest 고정 경로 업로드 (Cloud Scheduler용)
            latest_blob_path = f"{self.package_base_path}/predict_optimized-latest.tar.gz"
            latest_blob = self.bucket.blob(latest_blob_path)
            latest_blob.upload_from_string(package_bytes, content_type="application/gzip")

            gcs_uri = f"gs://{self.bucket_name}/{blob_path}"
            latest_gcs_uri = f"gs://{self.bucket_name}/{latest_blob_path}"

            logger.info(f"GCS 패키지 업로드 완료: v{new_version}, {len(package_bytes)} bytes")
            logger.info(f"  버전별: {gcs_uri}")
            logger.info(f"  latest: {latest_gcs_uri}")

            return UploadResult(
                success=True,
                message=f"패키지 업로드 완료 (v{new_version} + latest)",
                gcs_uri=gcs_uri,
                version=new_version
            )

        except Exception as e:
            logger.exception("❌ GCS 패키지 업로드 실패")
            return UploadResult(
                success=False,
                message=f"패키지 업로드 실패: {str(e)}"
            )

    def get_current_version(self) -> int:
        """현재 GCS에 업로드된 최신 버전 조회"""
        try:
            blobs = self.client.list_blobs(
                self.bucket_name,
                prefix=self.package_base_path
            )

            max_version = 0
            for blob in blobs:
                import re
                match = re.search(r"v(\d+)", blob.name)
                if match:
                    version = int(match.group(1))
                    if version > max_version:
                        max_version = version

            logger.info(f"현재 GCS 최신 버전: v{max_version}")
            return max_version

        except Exception as e:
            logger.warning(f"버전 조회 실패, 0으로 시작: {e}")
            return 0

    def get_latest_package_uri(self) -> str:
        """최신 패키지 URI 조회"""
        current_version = self.get_current_version()
        if current_version == 0:
            raise ValueError("GCS에 업로드된 패키지가 없습니다. 먼저 업로드하세요.")

        package_path = f"{self.package_base_path}/{self.PACKAGE_PREFIX}-v{current_version}.tar.gz"
        gcs_uri = f"gs://{self.bucket_name}/{package_path}"

        logger.debug(f"최신 패키지: v{current_version}, {gcs_uri}")

        return gcs_uri

    def get_package_status(self) -> dict:
        """패키지 상태 조회"""
        try:
            current_version = self.get_current_version()
            latest_blob = None

            if current_version > 0:
                blob_path = f"{self.package_base_path}/{self.PACKAGE_PREFIX}-v{current_version}.tar.gz"
                latest_blob = self.bucket.get_blob(blob_path)

            return {
                "bucket": self.bucket_name,
                "base_path": self.package_base_path,
                "current_version": current_version,
                "latest_package": {
                    "gcs_uri": f"gs://{self.bucket_name}/{latest_blob.name}" if latest_blob else None,
                    "size": latest_blob.size if latest_blob else None,
                    "updated": latest_blob.updated.isoformat() if latest_blob and latest_blob.updated else None
                } if latest_blob else None,
                "timestamp": datetime.now().isoformat()
            }

        except Exception as e:
            logger.exception("패키지 상태 조회 실패")
            return {
                "error": str(e),
                "timestamp": datetime.now().isoformat()
            }

    def _create_tar_gz_package(self, python_script: str, setup_script: str) -> bytes:
        """tar.gz 패키지 생성"""
        buffer = io.BytesIO()

        with tarfile.open(fileobj=buffer, mode="w:gz") as tar:
            # 디렉토리 엔트리
            dir_info = tarfile.TarInfo(name=f"{self.TRAINER_DIR}/")
            dir_info.type = tarfile.DIRTYPE
            dir_info.mode = 0o755
            tar.addfile(dir_info)

            # __init__.py
            self._add_file_to_tar(tar, f"{self.TRAINER_DIR}/__init__.py", "")

            # task.py (메인 스크립트)
            self._add_file_to_tar(tar, f"{self.TRAINER_DIR}/task.py", python_script)

            # setup.py
            self._add_file_to_tar(tar, "setup.py", setup_script)

        return buffer.getvalue()

    def _add_file_to_tar(self, tar: tarfile.TarFile, name: str, content: str):
        """tar에 파일 추가"""
        data = content.encode("utf-8")
        info = tarfile.TarInfo(name=name)
        info.size = len(data)
        info.mode = 0o644
        tar.addfile(info, io.BytesIO(data))

    def _generate_setup_py(self) -> str:
        """setup.py 생성"""
        return '''from setuptools import setup, find_packages

setup(
    name="aiplatform-custom-trainer-script",
    version="1.0.0",
    packages=find_packages(),
    install_requires=[
        "numpy>=1.21.0",
        "pandas>=1.3.0",
        "pymongo>=3.12.0",
        "xgboost>=1.5.0",
        "scikit-learn>=1.0.0",
        "google-cloud-storage>=1.44.0",
        "psycopg2-binary>=2.9.0",
        "requests>=2.28.0",
    ],
    python_requires=">=3.8",
)
'''
