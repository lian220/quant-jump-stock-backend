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


def _detect_env_file() -> Optional[str]:
    """환경에 맞는 .env 파일 경로 반환"""
    is_docker = os.path.exists('/.dockerenv') or os.getenv('KUBERNETES_SERVICE_HOST') is not None

    if is_docker:
        return None  # Docker 환경에서는 환경변수 직접 사용

    # 로컬 개발 환경
    env_local_path = Path(__file__).parent.parent.parent / ".env.local"
    if env_local_path.exists():
        return str(env_local_path)

    return None


class DatabaseSettings(BaseSettings):
    """데이터베이스 설정"""
    model_config = SettingsConfigDict(env_prefix="", extra="ignore")

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
    model_config = SettingsConfigDict(env_prefix="", extra="ignore")

    project_id: str = Field(default="quantiq-local", alias="PUBSUB_PROJECT_ID")
    emulator_host: str = Field(default="", alias="PUBSUB_EMULATOR_HOST")

    # Topics (dot notation - adapter 레이어에서 hyphen으로 변환)
    topic_analysis_request: str = "quantiq.analysis.request"
    topic_analysis_completed: str = "quantiq.analysis.completed"
    topic_economic_data_update_request: str = "economic.data.update.request"
    topic_economic_data_updated: str = "economic.data.updated"
    topic_technical_analysis_request: str = "analysis.technical.request"
    topic_sentiment_analysis_request: str = "analysis.sentiment.request"


class ApiSettings(BaseSettings):
    """외부 API 설정"""
    model_config = SettingsConfigDict(env_prefix="", extra="ignore")

    fred_api_key: str = Field(default="", alias="FRED_API_KEY")
    alpha_vantage_api_key: str = Field(default="", alias="ALPHA_VANTAGE_API_KEY")


class SlackSettings(BaseSettings):
    """Slack 설정"""
    model_config = SettingsConfigDict(env_prefix="", extra="ignore")

    webhook_url: str = Field(default="", alias="SLACK_WEBHOOK_URL")
    webhook_url_analysis: str = Field(default="", alias="SLACK_WEBHOOK_URL_ANALYSIS")
    bot_token: str = Field(default="", alias="SLACK_BOT_TOKEN")
    channel: str = Field(default="#trading-alerts", alias="SLACK_CHANNEL")
    enabled: bool = Field(default=True, alias="SLACK_ENABLED")


class GcpSettings(BaseSettings):
    """GCP 설정"""
    model_config = SettingsConfigDict(env_prefix="", extra="ignore")

    enabled: bool = Field(default=False, alias="GCP_ENABLED")
    project_id: str = Field(default="", alias="GCP_PROJECT_ID")
    region: str = Field(default="asia-northeast3", alias="GCP_REGION")
    credentials_path: Optional[str] = Field(default=None, alias="VERTEX_AI_CREDENTIALS_PATH")

    # Vertex AI
    model_bucket: str = Field(default="", alias="GCP_BUCKET_NAME")
    package_base_path: str = Field(default="ml-packages", alias="GCP_PACKAGE_BASE_PATH")
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
        env_file=_detect_env_file(),
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
    def SLACK_BOT_TOKEN(self) -> str:
        return self.slack.bot_token

    @property
    def SLACK_CHANNEL(self) -> str:
        return self.slack.channel

    @property
    def SLACK_WEBHOOK_URL_ANALYSIS(self) -> str:
        return self.slack.webhook_url_analysis

    @property
    def SLACK_ENABLED(self) -> bool:
        return self.slack.enabled

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
