import os
from pathlib import Path
from dotenv import load_dotenv

# .env.local 파일 우선 로드 (루트 디렉토리)
env_local_path = Path(__file__).parent.parent.parent / ".env.local"
if env_local_path.exists():
    load_dotenv(env_local_path)
    print(f"✅ 환경변수 로드: {env_local_path}")
else:
    # Fallback: .env 파일 로드
    load_dotenv()
    print("⚠️ .env.local 파일 없음, .env 사용")

class Settings:
    # MongoDB
    MONGODB_URI = os.getenv("MONGODB_URI", "mongodb://localhost:27017")
    MONGODB_DB_NAME = os.getenv("MONGODB_DB_NAME", "stock_trading")

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
