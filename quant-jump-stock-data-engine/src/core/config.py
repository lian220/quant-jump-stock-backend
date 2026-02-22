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

# 환경 감지: Cloud Run (K_SERVICE) 또는 Docker (/.dockerenv)
is_cloud_run = os.getenv('K_SERVICE') is not None
is_docker = os.path.exists('/.dockerenv') or os.getenv('KUBERNETES_SERVICE_HOST') is not None

if not (is_cloud_run or is_docker):
    # 로컬 개발 환경: 역순 로드 (override=False)
    # .env.local → .env.db.local → .env.common 순서로, 이미 설정된 변수는 건드리지 않음
    # start.sh --dev 에서 export한 환경변수(localhost)가 .env.db.local(Docker호스트)보다 우선
    backend_root = Path(__file__).parent.parent.parent.parent
    loaded = False
    for env_name in [".env.local", ".env.db.local", ".env.common"]:
        env_path = backend_root / env_name
        if env_path.exists():
            load_dotenv(env_path, override=False)
            loaded = True
    if not loaded:
        load_dotenv()
        print("⚠️ .env 파일 없음, 기본 dotenv 사용")
else:
    # Cloud Run / Docker: Secret Manager 볼륨 마운트에서 .env 파일 로드
    # 각 시크릿은 별도 서브디렉토리에 마운트됨 (Cloud Run 제약)
    # 역순 로드 (override=False): prod → db-prod → common 순서로 로드하여
    # prod 값이 우선 (settings.py의 pydantic env_file 순서와 일치)
    secret_paths = [
        Path("/secrets/prod/env"),      # qjs-env-prod (최우선)
        Path("/secrets/db-prod/env"),   # qjs-env-db-prod
        Path("/secrets/common/env"),    # qjs-env-common (기본값)
    ]
    loaded_secrets = False
    for path in secret_paths:
        if path.exists():
            load_dotenv(path, override=False)
            loaded_secrets = True
    if loaded_secrets:
        print("✅ Secret Manager 볼륨 마운트에서 환경 변수 로드 완료")
    else:
        print("✅ Docker/Cloud Run 환경 감지: 주입된 환경 변수 사용")

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

    # APIs
    FRED_API_KEY = os.getenv("FRED_API_KEY", "")
    ALPHA_VANTAGE_API_KEY = os.getenv("ALPHA_VANTAGE_API_KEY", "")

    # Slack Settings - config.settings에서 위임 (중복 os.getenv 방지)
    @property
    def SLACK_BOT_TOKEN(self):
        from config.settings import get_settings
        return get_settings().SLACK_BOT_TOKEN

    @property
    def SLACK_ENABLED(self):
        from config.settings import get_settings
        return get_settings().SLACK_ENABLED

    @property
    def SLACK_WEBHOOK_URL(self):
        from config.settings import get_settings
        return get_settings().SLACK_WEBHOOK_URL

    @property
    def SLACK_WEBHOOK_URL_TRADING(self):
        from config.settings import get_settings
        return get_settings().SLACK_WEBHOOK_URL_TRADING

    @property
    def SLACK_WEBHOOK_URL_NEWS(self):
        from config.settings import get_settings
        return get_settings().SLACK_WEBHOOK_URL_NEWS

    @property
    def SLACK_WEBHOOK_URL_SCHEDULER(self):
        from config.settings import get_settings
        return get_settings().SLACK_WEBHOOK_URL_SCHEDULER

    @property
    def SLACK_WEBHOOK_URL_ANALYSIS(self):
        from config.settings import get_settings
        return get_settings().SLACK_WEBHOOK_URL_ANALYSIS

    @property
    def SLACK_WEBHOOK_URL_ERROR(self):
        from config.settings import get_settings
        return get_settings().SLACK_WEBHOOK_URL_ERROR

    @property
    def SLACK_CHANNEL(self):
        from config.settings import get_settings
        return get_settings().slack.channel_scheduler  # 기본 채널 = scheduler 채널

    @property
    def SLACK_CHANNEL_SCHEDULER(self):
        from config.settings import get_settings
        return get_settings().slack.channel_scheduler

    @property
    def SLACK_CHANNEL_ANALYSIS(self):
        from config.settings import get_settings
        return get_settings().slack.channel_analysis

    @property
    def SLACK_CHANNEL_ERROR(self):
        from config.settings import get_settings
        return get_settings().slack.channel_error

    @property
    def SLACK_CHANNEL_TRADING(self):
        from config.settings import get_settings
        return get_settings().slack.channel_trading

settings = Settings()
