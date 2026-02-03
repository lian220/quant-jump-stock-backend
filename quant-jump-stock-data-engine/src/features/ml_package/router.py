"""
ML 패키지 관리 라우터
"""
import logging
import os
import subprocess
import sys
from pathlib import Path
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from datetime import datetime
from pytz import timezone

KST = timezone('Asia/Seoul')

# ML 스크립트 경로 (환경변수 또는 기본값)
# Docker: /scripts/ml/
# Local: backend/scripts/ml/ (상대경로로 계산)
ML_SCRIPTS_DIR = os.getenv("ML_SCRIPTS_DIR", "")

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/ml", tags=["ML Package"])


class PackageUploadResponse(BaseModel):
    success: bool
    message: str
    gcs_uri: str | None = None
    version: int | None = None
    timestamp: str


@router.post("/upload-package", response_model=PackageUploadResponse)
async def upload_package():
    """
    predict_optimized.py를 GCS에 업로드합니다.

    upload_to_gcs.py 스크립트를 실행하여 패키지를 빌드하고 GCS에 업로드합니다.
    """
    logger.info("=" * 80)
    logger.info("📦 GCS 패키지 업로드 요청 수신")
    logger.info("=" * 80)

    try:
        # upload_to_gcs.py 스크립트 경로
        # Docker: /scripts/ml/ (볼륨 마운트)
        # Local: backend/scripts/ml/ (상대경로)
        if ML_SCRIPTS_DIR:
            script_dir = Path(ML_SCRIPTS_DIR)
        else:
            # 로컬 개발환경: router.py 기준 상대경로 계산
            script_dir = Path(__file__).parent.parent.parent.parent.parent / "scripts" / "ml"

        upload_script = script_dir / "upload_to_gcs.py"

        if not upload_script.exists():
            raise FileNotFoundError(f"업로드 스크립트를 찾을 수 없습니다: {upload_script}")

        # predict_optimized.py 경로
        predict_script = script_dir / "predict_optimized.py"
        if not predict_script.exists():
            raise FileNotFoundError(f"예측 스크립트를 찾을 수 없습니다: {predict_script}")

        logger.info(f"업로드 스크립트: {upload_script}")
        logger.info(f"예측 스크립트: {predict_script}")

        # 스크립트 실행
        logger.info("스크립트 실행 중...")
        result = subprocess.run(
            [sys.executable, str(upload_script), "--file", str(predict_script)],
            capture_output=True,
            text=True,
            timeout=300  # 5분 타임아웃
        )

        # 로그 출력
        if result.stdout:
            logger.info("=== 스크립트 출력 ===")
            for line in result.stdout.split('\n'):
                if line.strip():
                    logger.info(line)

        if result.stderr:
            logger.warning("=== 스크립트 경고/오류 ===")
            for line in result.stderr.split('\n'):
                if line.strip():
                    logger.warning(line)

        # 실행 결과 확인
        if result.returncode != 0:
            error_msg = f"스크립트 실행 실패 (exit code: {result.returncode})"
            if result.stderr:
                error_msg += f"\n{result.stderr}"
            logger.error(error_msg)
            raise RuntimeError(error_msg)

        # 성공 응답에서 GCS URI와 버전 추출 (로그 파싱)
        # stdout과 stderr 모두 확인 (로깅 출력이 stderr로 갈 수 있음)
        gcs_uri = None
        version = None

        combined_output = result.stdout + "\n" + result.stderr

        for line in combined_output.split('\n'):
            if "GCS URI:" in line:
                gcs_uri = line.split("GCS URI:")[-1].strip()
            if "패키지 버전:" in line or "새 버전:" in line:
                try:
                    # "v1", "v2" 형식에서 숫자만 추출
                    version_str = line.split(":")[-1].strip().replace("v", "")
                    version = int(version_str)
                except (ValueError, IndexError):
                    pass

        logger.info("=" * 80)
        logger.info("✅ GCS 패키지 업로드 완료")
        logger.info(f"GCS URI: {gcs_uri}")
        logger.info(f"버전: v{version}")
        logger.info("=" * 80)

        return PackageUploadResponse(
            success=True,
            message="패키지 업로드 완료",
            gcs_uri=gcs_uri,
            version=version,
            timestamp=datetime.now(KST).isoformat()
        )

    except subprocess.TimeoutExpired:
        logger.error("❌ 스크립트 실행 타임아웃 (5분 초과)")
        raise HTTPException(status_code=504, detail="스크립트 실행 타임아웃")
    except FileNotFoundError as e:
        logger.error(f"❌ 파일을 찾을 수 없습니다: {e}")
        raise HTTPException(status_code=404, detail=str(e))
    except Exception as e:
        logger.error(f"❌ 패키지 업로드 실패: {e}")
        raise HTTPException(status_code=500, detail=f"패키지 업로드 실패: {str(e)}")


@router.get("/package-status")
async def get_package_status():
    """
    현재 GCS에 업로드된 패키지 상태 조회
    """
    # TODO: GCS에서 최신 버전 정보 조회
    return {
        "service": "ml-package-manager",
        "status": "running",
        "message": "패키지 상태 조회 (구현 예정)",
        "timestamp": datetime.now(KST).isoformat()
    }
