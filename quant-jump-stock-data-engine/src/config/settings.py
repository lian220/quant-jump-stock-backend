"""
환경 설정 - Pydantic Settings 기반

기존 core/config.py를 대체합니다.
Pydantic을 사용하여 타입 안전성과 검증을 제공합니다.
"""
import os
from pathlib import Path
from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field


def _detect_env_files():
    """환경에 맞는 .env 파일 경로 반환 (.env.common → .env.local 순서)"""
    is_cloud_run = os.getenv('K_SERVICE') is not None
    is_docker = os.path.exists('/.dockerenv') or os.getenv('KUBERNETES_SERVICE_HOST') is not None

    if is_cloud_run or is_docker:
        # Cloud Run / Docker: Secret Manager 볼륨 마운트에서 .env 파일 로드
        # 각 시크릿은 별도 서브디렉토리에 마운트됨 (Cloud Run 제약)
        secret_paths = [
            Path("/secrets/common/env"),    # qjs-env-common
            Path("/secrets/db-prod/env"),   # qjs-env-db-prod
            Path("/secrets/prod/env"),      # qjs-env-prod
        ]
        files = [str(p) for p in secret_paths if p.exists()]
        if files:
            return tuple(files)
        return None  # 볼륨 마운트 없으면 환경변수 직접 사용 (fallback)

    # 로컬 개발 환경: .env.common → .env.db.local → .env.local (후자가 오버라이드)
    backend_root = Path(__file__).parent.parent.parent.parent
    env_common = backend_root / ".env.common"
    env_db_local = backend_root / ".env.db.local"
    env_local = backend_root / ".env.local"

    files = []
    for f in [env_common, env_db_local, env_local]:
        if f.exists():
            files.append(str(f))

    return tuple(files) if files else None


_env_files = _detect_env_files()


class DatabaseSettings(BaseSettings):
    """데이터베이스 설정"""
    model_config = SettingsConfigDict(env_file=_env_files, env_prefix="", extra="ignore")

    # MongoDB
    mongodb_uri: str = Field(default="mongodb://localhost:27017", alias="MONGODB_URI")
    mongodb_db_name: str = Field(default="stock_trading", alias="MONGODB_DB_NAME")

    # PostgreSQL
    postgres_host: str = Field(default="localhost", alias="DB_HOST")
    postgres_port: int = Field(default=5432, alias="DB_PORT")
    postgres_db: str = Field(default="quantiq", alias="DB_NAME")
    postgres_user: str = Field(default="postgres", alias="DB_USER")
    postgres_password: str = Field(default="", alias="DB_PASSWORD")


class PubSubSettings(BaseSettings):
    """Pub/Sub 설정"""
    model_config = SettingsConfigDict(env_file=_env_files, env_prefix="", extra="ignore")

    project_id: str = Field(default="quantiq-local", alias="PUBSUB_PROJECT_ID")
    emulator_host: str = Field(default="", alias="PUBSUB_EMULATOR_HOST")
    mode: str = Field(default="pull", alias="PUBSUB_MODE")  # pull | push

    # Topics (dot notation - adapter 레이어에서 hyphen으로 변환)
    topic_analysis_request: str = "quantiq.analysis.request"
    topic_analysis_completed: str = "quantiq.analysis.completed"
    topic_economic_data_update_request: str = "economic.data.update.request"
    topic_economic_data_updated: str = "economic.data.updated"
    topic_technical_analysis_request: str = "analysis.technical.request"
    topic_sentiment_analysis_request: str = "analysis.sentiment.request"


class ApiSettings(BaseSettings):
    """외부 API 설정"""
    model_config = SettingsConfigDict(env_file=_env_files, env_prefix="", extra="ignore")

    fred_api_key: str = Field(default="", alias="FRED_API_KEY")
    alpha_vantage_api_key: str = Field(default="", alias="ALPHA_VANTAGE_API_KEY")


class SlackSettings(BaseSettings):
    """Slack 설정 - Bot Token (스레드 답글) + Webhook (fallback)"""
    model_config = SettingsConfigDict(env_file=_env_files, env_prefix="", extra="ignore")

    # Bot Token (스레드 답글 지원)
    bot_token: str = Field(default="", alias="SLACK_BOT_TOKEN")
    channel_scheduler: str = Field(default="", alias="SLACK_CHANNEL_SCHEDULER")
    channel_analysis: str = Field(default="", alias="SLACK_CHANNEL_ANALYSIS")
    channel_error: str = Field(default="", alias="SLACK_CHANNEL_ERROR")
    channel_trading: str = Field(default="", alias="SLACK_CHANNEL_TRADING")

    # Webhook (fallback)
    webhook_url: str = Field(default="", alias="SLACK_WEBHOOK_URL")
    webhook_url_trading: str = Field(default="", alias="SLACK_WEBHOOK_URL_TRADING")
    webhook_url_analysis: str = Field(default="", alias="SLACK_WEBHOOK_URL_ANALYSIS")
    webhook_url_news: str = Field(default="", alias="SLACK_WEBHOOK_URL_NEWS")
    webhook_url_scheduler: str = Field(default="", alias="SLACK_WEBHOOK_URL_SCHEDULER")
    webhook_url_error: str = Field(default="", alias="SLACK_WEBHOOK_URL_ERROR")
    enabled: bool = Field(default=True, alias="SLACK_ENABLED")


class GcpSettings(BaseSettings):
    """Vertex AI / GCP 설정"""
    model_config = SettingsConfigDict(env_file=_env_files, env_prefix="", extra="ignore")

    enabled: bool = Field(default=False, alias="VERTEX_AI_ENABLED")
    project_id: str = Field(default="", alias="VERTEX_AI_PROJECT_ID")
    region: str = Field(default="asia-northeast3", alias="VERTEX_AI_REGION")
    credentials_path: Optional[str] = Field(default=None, alias="VERTEX_AI_CREDENTIALS_PATH")

    # Vertex AI
    model_bucket: str = Field(default="", alias="VERTEX_AI_BUCKET_NAME")
    package_base_path: str = Field(default="ml-packages", alias="VERTEX_AI_PACKAGE_BASE_PATH")
    job_name: str = Field(default="quantiq-stock-prediction", alias="VERTEX_AI_JOB_NAME")
    machine_type: str = Field(default="n1-standard-4", alias="VERTEX_AI_MACHINE_TYPE")
    accelerator_type: str = Field(default="NVIDIA_TESLA_T4", alias="VERTEX_AI_ACCELERATOR_TYPE")
    accelerator_count: int = Field(default=1, alias="VERTEX_AI_ACCELERATOR_COUNT")
    container_uri: str = Field(
        default="us-docker.pkg.dev/vertex-ai/training/pytorch-gpu.1-13:latest",
        alias="VERTEX_AI_CONTAINER_URI"
    )


class Settings(BaseSettings):
    """통합 설정"""
    model_config = SettingsConfigDict(
        env_file=_env_files,
        env_file_encoding="utf-8",
        extra="ignore"
    )

    # 환경
    environment: str = Field(default="local", alias="ENVIRONMENT")
    log_level: str = Field(default="INFO", alias="LOG_LEVEL")
    server_port: int = Field(default=10020, alias="SERVER_PORT")

    # 하위 설정
    database: DatabaseSettings = Field(default_factory=DatabaseSettings)
    pubsub: PubSubSettings = Field(default_factory=PubSubSettings)
    api: ApiSettings = Field(default_factory=ApiSettings)
    slack: SlackSettings = Field(default_factory=SlackSettings)
    gcp: GcpSettings = Field(default_factory=GcpSettings)

    # 기존 호환성을 위한 플랫 속성들 (deprecated, 점진적 제거 예정)
    @property
    def MONGODB_URI(self) -> str:
        return self.database.mongodb_uri

    @property
    def MONGODB_DB_NAME(self) -> str:
        return self.database.mongodb_db_name

    @property
    def FRED_API_KEY(self) -> str:
        return self.api.fred_api_key

    @property
    def SLACK_WEBHOOK_URL(self) -> str:
        return self.slack.webhook_url

    @property
    def SLACK_WEBHOOK_URL_TRADING(self) -> str:
        return self.slack.webhook_url_trading

    @property
    def SLACK_WEBHOOK_URL_ANALYSIS(self) -> str:
        return self.slack.webhook_url_analysis

    @property
    def SLACK_WEBHOOK_URL_NEWS(self) -> str:
        return self.slack.webhook_url_news

    @property
    def SLACK_WEBHOOK_URL_SCHEDULER(self) -> str:
        return self.slack.webhook_url_scheduler

    @property
    def SLACK_WEBHOOK_URL_ERROR(self) -> str:
        return self.slack.webhook_url_error

    @property
    def SLACK_ENABLED(self) -> bool:
        return self.slack.enabled

    @property
    def SLACK_BOT_TOKEN(self) -> str:
        return self.slack.bot_token

    @property
    def POSTGRES_HOST(self) -> str:
        return self.database.postgres_host

    @property
    def POSTGRES_PORT(self) -> int:
        return self.database.postgres_port

    @property
    def POSTGRES_DB(self) -> str:
        return self.database.postgres_db

    @property
    def POSTGRES_USER(self) -> str:
        return self.database.postgres_user

    @property
    def POSTGRES_PASSWORD(self) -> str:
        return self.database.postgres_password

    # 소문자 속성 (새 스타일)
    @property
    def postgres_host(self) -> str:
        return self.database.postgres_host

    @property
    def postgres_port(self) -> int:
        return self.database.postgres_port

    @property
    def postgres_db(self) -> str:
        return self.database.postgres_db

    @property
    def postgres_user(self) -> str:
        return self.database.postgres_user

    @property
    def postgres_password(self) -> str:
        return self.database.postgres_password


# 싱글톤 인스턴스
_settings: Settings = None


def get_settings() -> Settings:
    """Settings 싱글톤 반환"""
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings


# 기존 호환성을 위한 직접 export (deprecated)
settings = get_settings()
