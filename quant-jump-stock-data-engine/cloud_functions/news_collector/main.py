"""
Cloud Function: 뉴스 수집기

Cloud Scheduler → HTTP 트리거 → 뉴스 수집 → Pub/Sub 발행
기존 3-hop (Quartz → Pub/Sub → Data Engine) 구조를 1-hop으로 단순화.

시크릿: 기존 Secret Manager 볼륨 마운트 사용
  /secrets/common/env  → qjs-env-common
  /secrets/db-prod/env → qjs-env-db-prod
  /secrets/prod/env    → qjs-env-prod
환경변수: GCP_PROJECT_ID (Pub/Sub 발행용, deploy 시 주입)
"""

import json
import logging
import os
import sys
import time
from datetime import datetime, timezone, timedelta
from pathlib import Path

import functions_framework
import psycopg2.pool
from pymongo import MongoClient

from application.news.news_collection_service import NewsCollectionService
from adapter.output.external.saveticker_client import SaveTickerClient
from adapter.output.mongodb.news_repository import MongoNewsRepository
from adapter.output.postgresql.collector_state_repository import PostgresCollectorStateRepository

# Cloud Functions gen2 (Cloud Run) 로깅: stderr에 직접 출력
handler = logging.StreamHandler(sys.stderr)
handler.setFormatter(logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s"))
logger = logging.getLogger("news-collector")
logger.setLevel(logging.INFO)
logger.addHandler(handler)
logger.propagate = False

KST = timezone(timedelta(hours=9))

# ── 글로벌 인스턴스 (warm 인스턴스 재사용) ──────────────────────
_service: NewsCollectionService | None = None
_publisher = None
_secrets_loaded = False


def _load_secrets():
    """Secret Manager 볼륨 마운트에서 .env 파일 로드 → os.environ에 주입"""
    global _secrets_loaded
    if _secrets_loaded:
        return
    _secrets_loaded = True

    secret_paths = [
        Path("/secrets/common/env"),    # qjs-env-common
        Path("/secrets/db-prod/env"),   # qjs-env-db-prod
        Path("/secrets/prod/env"),      # qjs-env-prod (후자가 오버라이드)
    ]
    for path in secret_paths:
        if not path.exists():
            continue
        for line in path.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            key = key.strip()
            value = value.strip().strip('"').strip("'")
            if key:
                os.environ[key] = value
        logger.info(f"시크릿 로드: {path}")


def _init_service() -> NewsCollectionService:
    """서비스 초기화 (cold start 시 1회)"""
    global _service
    if _service is not None:
        return _service

    _load_secrets()

    # 필수 환경변수 검증 — Secret Manager에서 못 읽으면 즉시 실패
    required_vars = ["MONGODB_URI", "DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD"]
    missing = [v for v in required_vars if not os.environ.get(v)]
    if missing:
        raise RuntimeError(f"필수 환경변수 누락 (Secret Manager 볼륨 마운트 확인): {missing}")

    logger.info(f"환경변수 로드 완료: DB_HOST={os.environ['DB_HOST']}, DB_NAME={os.environ['DB_NAME']}")

    # MongoDB
    mongo_uri = os.environ["MONGODB_URI"]
    mongo_client = MongoClient(mongo_uri)
    db = mongo_client.get_default_database()

    # PostgreSQL connection pool
    pg_pool = psycopg2.pool.SimpleConnectionPool(
        minconn=1,
        maxconn=3,
        host=os.environ["DB_HOST"],
        port=int(os.environ["DB_PORT"]),
        dbname=os.environ["DB_NAME"],
        user=os.environ["DB_USER"],
        password=os.environ["DB_PASSWORD"],
        sslmode=os.environ.get("DB_SSLMODE", "require"),
    )

    saveticker_client = SaveTickerClient()
    news_repo = MongoNewsRepository(db)
    state_repo = PostgresCollectorStateRepository(pool=pg_pool)

    _service = NewsCollectionService(
        saveticker_client=saveticker_client,
        news_repository=news_repo,
        collector_state_repository=state_repo,
    )
    logger.info("NewsCollectionService initialized (cold start)")
    return _service


def _get_publisher():
    """Pub/Sub publisher lazy 초기화"""
    global _publisher
    if _publisher is not None:
        return _publisher

    project_id = os.environ.get("GCP_PROJECT_ID")
    if not project_id:
        logger.info("GCP_PROJECT_ID not set - Pub/Sub publish disabled")
        return None

    try:
        from google.cloud import pubsub_v1
        _publisher = pubsub_v1.PublisherClient()
        return _publisher
    except Exception as e:
        logger.warning(f"Pub/Sub publisher 초기화 실패: {e}")
        return None


def _publish_result(result: dict, duration: float) -> None:
    """수집 결과를 Pub/Sub에 발행"""
    publisher = _get_publisher()
    project_id = os.environ.get("GCP_PROJECT_ID")
    if not publisher or not project_id:
        return

    topic_path = publisher.topic_path(project_id, "quantiq.news.collected")
    payload = {
        "status": "success",
        "timestamp": datetime.now(KST).isoformat(),
        "source": result.get("source", "SAVETICKER"),
        "articleIds": result.get("article_ids", []),
        "collectedCount": result.get("collected_count", 0),
        "duration": duration,
    }
    try:
        future = publisher.publish(topic_path, json.dumps(payload).encode("utf-8"))
        future.result(timeout=10)
        logger.info(f"Pub/Sub 발행 완료: {result.get('collected_count', 0)}건")
    except Exception as e:
        logger.warning(f"Pub/Sub 발행 실패 (수집은 성공): {e}")


@functions_framework.http
def collect_news(request):
    """HTTP 트리거 엔트리포인트

    Cloud Scheduler가 POST로 호출.
    요청 본문 JSON의 'source' 필드로 뉴스 소스 지정 (기본: SAVETICKER).
    """
    start_time = time.time()

    try:
        # 요청에서 소스 파라미터 추출
        source = "SAVETICKER"
        if request.is_json:
            body = request.get_json(silent=True) or {}
            source = body.get("source", source)

        service = _init_service()
        result = service.collect(source=source)

        duration = time.time() - start_time

        # 수집 성공 시 Pub/Sub 발행
        if result.get("collected_count", 0) > 0:
            _publish_result(result, duration)

        response = {
            "status": "success",
            "collected_count": result.get("collected_count", 0),
            "source": source,
            "duration_ms": int(duration * 1000),
        }
        logger.info(f"뉴스 수집 완료: {response}")
        return (json.dumps(response, ensure_ascii=False), 200, {"Content-Type": "application/json"})

    except Exception as e:
        duration = time.time() - start_time
        logger.exception(f"뉴스 수집 실패: {e}")
        error_response = {
            "status": "error",
            "error": type(e).__name__,
            "duration_ms": int(duration * 1000),
        }
        return (json.dumps(error_response, ensure_ascii=False), 500, {"Content-Type": "application/json"})
