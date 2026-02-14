"""
Vertex AI Job 환경변수 로더

Vertex AI Job 컨테이너에 전달할 DB 환경변수를 .env.db.prod에서 읽어옴.
dotenv_values()로 파일만 읽고, 프로세스 환경변수를 오염시키지 않음.

왜 필요한가:
  - 로컬: Data Engine은 DB_HOST=postgresql (Docker)을 사용
  - Vertex AI Job: GCP에서 실행되므로 DB_HOST=supabase (외부 DB) 필요
  - 같은 변수명이지만 값이 달라야 하므로, 파일에서 직접 읽어 Job에만 전달
"""

import logging
from pathlib import Path
from typing import Dict, Optional

from dotenv import dotenv_values

logger = logging.getLogger(__name__)


class VertexAIJobEnvLoader:
    """Vertex AI Job에 전달할 환경변수를 .env.db.prod에서 읽는 로더"""

    def __init__(self, env_db_path: Optional[str] = None):
        if env_db_path:
            self._path = Path(env_db_path)
        else:
            self._path = Path(__file__).parent.parent.parent / ".env.db.prod"

    def load(self) -> Dict[str, str]:
        """
        .env.db.prod에서 환경변수를 읽어 딕셔너리로 반환.
        프로세스 환경에 영향 없음.
        """
        if not self._path.exists():
            logger.warning(f"Vertex AI Job DB 설정 파일 없음: {self._path}")
            return {}

        values = dotenv_values(self._path)
        result = {k: v for k, v in values.items() if v}

        logger.info(f"Vertex AI Job DB 환경변수 로드: {self._path} ({len(result)}개)")
        return result
