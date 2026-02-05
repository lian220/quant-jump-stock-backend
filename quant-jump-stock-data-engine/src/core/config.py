"""
DEPRECATED: 이 모듈은 config.settings로 대체되었습니다.

기존 코드 호환성을 위해 유지되며, 점진적으로 마이그레이션 예정입니다.
새 코드에서는 config.settings 사용을 권장합니다:

    from config.settings import get_settings
    settings = get_settings()
"""

import os
from pathlib import Path
from dotenv import load_dotenv
import warnings

warnings.warn(
    "core.config is deprecated. Use config.settings instead.",
    DeprecationWarning,
    stacklevel=2
)

# Docker 환경 감지 (/.dockerenv 파일 존재 여부 또는 KUBERNETES_SERVICE_HOST 환경 변수)
is_docker = os.path.exists('/.dockerenv') or os.getenv('KUBERNETES_SERVICE_HOST') is not None

if not is_docker:
    # 로컬 개발 환경: .env.local 파일 우선 로드
    env_local_path = Path(__file__).parent.parent.parent / ".env.local"
    if env_local_path.exists():
        load_dotenv(env_local_path)
        print(f"✅ 환경변수 로드: {env_local_path}")
    else:
        # Fallback: .env 파일 로드
        load_dotenv()
        print("⚠️ .env.local 파일 없음, .env 사용")
else:
    # Docker/Kubernetes 환경: 환경 변수는 이미 주입됨
    print("✅ Docker 환경 감지: 주입된 환경 변수 사용")

class Settings:
    # MongoDB
    MONGODB_URI = os.getenv("MONGODB_URI", "mongodb://localhost:27017")
    MONGODB_DB_NAME = os.getenv("MONGODB_DB_NAME", "stock_trading")

    # PostgreSQL (기존 DB_* 환경변수 사용)
    POSTGRES_HOST = os.getenv("DB_HOST", "localhost")
    POSTGRES_PORT = int(os.getenv("DB_PORT", "5432"))
    POSTGRES_DB = os.getenv("DB_NAME", "quantiq")
    POSTGRES_USER = os.getenv("DB_USER", "postgres")
    POSTGRES_PASSWORD = os.getenv("DB_PASSWORD", "")

    # Kafka - Local
    KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    KAFKA_TOPIC_ANALYSIS_REQUEST = "quantiq.analysis.request"
    KAFKA_TOPIC_ANALYSIS_COMPLETED = "quantiq.analysis.completed"
    KAFKA_TOPIC_ECONOMIC_DATA_UPDATE_REQUEST = "economic.data.update.request"
    KAFKA_TOPIC_ECONOMIC_DATA_UPDATED = "economic.data.updated"

    # APIs
    FRED_API_KEY = os.getenv("FRED_API_KEY", "aedfbcd8ba091c740281c0bd8ca93b46")
    ALPHA_VANTAGE_API_KEY = os.getenv("ALPHA_VANTAGE_API_KEY", "")

    # Slack Settings
    SLACK_WEBHOOK_URL = os.getenv("SLACK_WEBHOOK_URL", "")
    SLACK_BOT_TOKEN = os.getenv("SLACK_BOT_TOKEN", "")
    SLACK_CHANNEL = os.getenv("SLACK_CHANNEL", "#trading-alerts")

settings = Settings()
