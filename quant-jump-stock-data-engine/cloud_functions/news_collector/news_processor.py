"""
NewsProcessor: 수집된 뉴스 스코어링 + 구독자 알림 처리

Core API의 NewsScorer / NewsSubscriptionService 로직을 Python으로 포팅.
Cloud Function에서 직접 처리하여 Pub/Sub 토픽 불필요.
"""

import json
import logging
import os
import requests
from datetime import datetime, timezone, timedelta
from typing import List, Optional, Tuple

import psycopg2.pool
from pymongo.database import Database

logger = logging.getLogger("news-collector")

KST = timezone(timedelta(hours=9))

# Source weights (NewsScorer.kt와 동일)
_SOURCE_WEIGHTS = {
    "reuters": 0.15,
    "로이터": 0.15,
    "블룸버그": 0.15,
    "파이낸셜타임즈": 0.15,
    "financial-juice": 0.10,
}
_DEFAULT_SOURCE_WEIGHT = 0.05
IMPORTANCE_THRESHOLD = 0.4
HIGH_IMPORTANCE_THRESHOLD = 0.7


class NewsProcessor:
    """수집된 뉴스 스코어링 → MongoDB 업데이트 → 인앱 알림 → Slack 알림"""

    def __init__(
        self,
        mongo_db: Database,
        pg_pool: psycopg2.pool.SimpleConnectionPool,
        slack_webhook_url: Optional[str] = None,
    ):
        self._db = mongo_db
        self._pg_pool = pg_pool
        self._slack_webhook_url = slack_webhook_url
        self._category_weights: dict = {}
        self._tag_mappings: dict = {}   # (source, raw_tag) → category_name
        self._weights_loaded = False

    # ──────────────────────────────────────────
    # 카테고리 데이터 로드 (cold start 1회)
    # ──────────────────────────────────────────

    def _load_category_data(self):
        if self._weights_loaded:
            return
        conn = self._pg_pool.getconn()
        try:
            with conn.cursor() as cur:
                cur.execute("SELECT name, weight FROM news_categories WHERE is_active = TRUE")
                self._category_weights = {row[0]: row[1] for row in cur.fetchall()}

                cur.execute("""
                    SELECT m.source, m.source_tag, c.name
                    FROM news_source_tag_mappings m
                    JOIN news_categories c ON c.id = m.category_id
                """)
                for source, tag, category in cur.fetchall():
                    self._tag_mappings[(source, tag)] = category
        finally:
            self._pg_pool.putconn(conn)
        self._weights_loaded = True
        logger.info(
            f"카테고리 데이터 로드: {len(self._category_weights)}개 카테고리, "
            f"{len(self._tag_mappings)}개 태그 매핑"
        )

    # ──────────────────────────────────────────
    # 스코어링 (NewsScorer.kt와 동일 로직)
    # ──────────────────────────────────────────

    def _resolve_tags(self, source: str, raw_tags: List[str]) -> List[str]:
        resolved = []
        for tag in raw_tags:
            mapped = self._tag_mappings.get((source, tag))
            resolved.append(mapped if mapped else tag)
        return list(dict.fromkeys(resolved))  # 순서 유지하며 중복 제거

    def _score(self, article: dict) -> Tuple[dict, float, List[str], bool]:
        """Return (updated_article, score, reasons, is_rumor)"""
        source = article.get("source", "")
        original_source = (article.get("original_source") or source).lower()
        tags = article.get("tags", [])
        tickers = article.get("tickers", [])
        content_ko = article.get("content_ko") or ""
        title_ko = article.get("title_ko", "")
        is_headline_only = article.get("is_headline_only", True)

        reasons = []

        source_score = _SOURCE_WEIGHTS.get(original_source, _DEFAULT_SOURCE_WEIGHT)
        if source_score >= 0.15:
            reasons.append(f"{article.get('original_source') or source} (고신뢰)")

        resolved_tags = self._resolve_tags(source, tags)
        category_score = max(
            (self._category_weights.get(t, 0.0) for t in resolved_tags),
            default=0.10,
        )
        top_category = max(
            resolved_tags,
            key=lambda t: self._category_weights.get(t, 0.0),
            default=None,
        )
        if top_category:
            reasons.append(top_category)

        ticker_bonus = 0.15 if len(tickers) >= 3 else (0.05 if tickers else 0.0)
        if tickers:
            reasons.append(", ".join(tickers))

        depth_bonus = 0.10 if (not is_headline_only and len(content_ko) > 100) else 0.0
        if depth_bonus > 0:
            reasons.append("본문 포함")

        is_rumor = title_ko.startswith("(카더라)")
        if is_rumor:
            reasons.append("미확인")

        score = min(source_score + category_score + ticker_bonus + depth_bonus, 1.0)
        updated = {**article, "importance_score": score, "tags": resolved_tags}
        return updated, score, reasons, is_rumor

    # ──────────────────────────────────────────
    # MongoDB 업데이트
    # ──────────────────────────────────────────

    def _update_mongodb(self, articles: List[dict]):
        collection = self._db["news"]
        for article in articles:
            try:
                collection.update_one(
                    {"source": article["source"], "external_id": article["external_id"]},
                    {"$set": {
                        "importance_score": article["importance_score"],
                        "tags": article["tags"],
                        "updated_at": datetime.now(KST),
                    }},
                )
            except Exception as e:
                logger.warning(f"MongoDB 업데이트 실패: {article.get('external_id')}, {e}")

    # ──────────────────────────────────────────
    # 인앱 알림 생성
    # ──────────────────────────────────────────

    def _create_notifications(self, scored: List[Tuple[dict, float, List[str], bool]]):
        conn = self._pg_pool.getconn()
        notifications = []
        try:
            with conn.cursor() as cur:
                for article, score, _, _ in scored:
                    if score < IMPORTANCE_THRESHOLD:
                        continue

                    priority = "HIGH" if score >= HIGH_IMPORTANCE_THRESHOLD else "NORMAL"
                    title = article.get("title_ko", "")
                    summary = (article.get("summary_ko") or article.get("content_ko") or "")[:200]
                    source_url = article.get("source_url")
                    news_id = article.get("external_id", "")
                    categories = article.get("tags", [])
                    tickers = article.get("tickers", [])
                    source_name = article.get("source", "")
                    base_meta = {"newsId": news_id, "importance": score}

                    matched = set()

                    for category in categories:
                        cur.execute("""
                            SELECT user_id FROM user_news_subscriptions
                            WHERE subscription_type = 'CATEGORY'
                              AND subscription_value = %s
                              AND notify_channel = 'IN_APP'
                              AND is_active = TRUE
                        """, (category,))
                        for (uid,) in cur.fetchall():
                            if uid not in matched:
                                matched.add(uid)
                                notifications.append((
                                    uid, "NEWS", priority, title, summary, source_url,
                                    json.dumps({**base_meta, "categoryName": category}),
                                ))

                    for ticker in tickers:
                        cur.execute("""
                            SELECT user_id FROM user_news_subscriptions
                            WHERE subscription_type = 'TICKER'
                              AND subscription_value = %s
                              AND notify_channel = 'IN_APP'
                              AND is_active = TRUE
                        """, (ticker,))
                        for (uid,) in cur.fetchall():
                            if uid not in matched:
                                matched.add(uid)
                                notifications.append((
                                    uid, "NEWS", priority, f"[{ticker}] {title}", summary, source_url,
                                    json.dumps({**base_meta, "ticker": ticker}),
                                ))

                    cur.execute("""
                        SELECT user_id FROM user_news_subscriptions
                        WHERE subscription_type = 'SOURCE'
                          AND subscription_value = %s
                          AND notify_channel = 'IN_APP'
                          AND is_active = TRUE
                    """, (source_name,))
                    for (uid,) in cur.fetchall():
                        if uid not in matched:
                            matched.add(uid)
                            notifications.append((
                                uid, "NEWS", priority, title, summary, source_url,
                                json.dumps({**base_meta, "sourceName": source_name}),
                            ))

                if notifications:
                    cur.executemany("""
                        INSERT INTO notifications
                            (user_id, type, priority, title, message, action_url, metadata)
                        VALUES (%s, %s, %s, %s, %s, %s, %s::jsonb)
                    """, notifications)
                    conn.commit()
                    logger.info(f"인앱 알림 생성: {len(notifications)}건")
        except Exception as e:
            conn.rollback()
            logger.error(f"알림 생성 실패: {e}")
        finally:
            self._pg_pool.putconn(conn)

    # ──────────────────────────────────────────
    # Slack 알림 (중요 뉴스만)
    # ──────────────────────────────────────────

    def _send_slack_alerts(self, scored: List[Tuple[dict, float, List[str], bool]]):
        if not self._slack_webhook_url:
            return
        for article, score, reasons, is_rumor in scored:
            if score < IMPORTANCE_THRESHOLD:
                continue
            emoji = "🚨" if score >= HIGH_IMPORTANCE_THRESHOLD else "📰"
            rumor_label = " [미확인]" if is_rumor else ""
            text = (
                f"{emoji} [{article.get('source', '')}]{rumor_label} {article.get('title_ko', '')}\n"
                f"점수: {score:.2f} | {', '.join(reasons)}"
            )
            try:
                requests.post(self._slack_webhook_url, json={"text": text}, timeout=5)
            except Exception as e:
                logger.warning(f"Slack 알림 실패: {e}")

    # ──────────────────────────────────────────
    # 메인 진입점
    # ──────────────────────────────────────────

    def process(self, article_ids: List[str], source: str) -> int:
        """수집된 기사 ID 리스트를 받아 스코어링 + 알림 처리. 처리된 기사 수 반환."""
        if not article_ids:
            return 0

        self._load_category_data()

        articles = list(self._db["news"].find(
            {"source": source, "external_id": {"$in": article_ids}},
            {"_id": 0},
        ))
        if not articles:
            logger.warning(f"MongoDB에서 기사 없음: source={source}, ids={article_ids}")
            return 0

        scored = [self._score(a) for a in articles]

        self._update_mongodb([a for a, _, _, _ in scored])
        self._create_notifications(scored)
        self._send_slack_alerts(scored)

        important = sum(1 for _, s, _, _ in scored if s >= IMPORTANCE_THRESHOLD)
        logger.info(f"뉴스 처리 완료: {len(articles)}건 스코어링, {important}건 중요")
        return len(articles)
