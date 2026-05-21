"""PR 1 prod regression — composite_score 신/구 산식 drift 정량화.

⚠ 운영 안전성:
  - Read-only MongoDB 계정으로만 실행. 본 스크립트는 절대 write 안 함.
  - .env.db.prod 직접 로드 금지. 환경변수로 주입.
  - agent 자동 실행 금지. 사람이 PR 검토 직전 1회 실행.

실행 (운영자):
  MONGODB_URI_RO='mongodb+srv://readonly:...' \\
  MONGODB_DB_NAME=stock_trading \\
  SCORING_SPEC_PATH=/Users/.../scoring_spec.yaml \\
  poetry run python scripts/scoring_regression_prod.py --days 7 --threshold 0.01

목적:
  - PR 1 머지 전, 운영 데이터 N일치에 대해 신 산식 결과를 재계산하여 drift 정량화.
  - drift > threshold 인 ticker 가 0 인 게 이상적이나, comprehensive_report 의 ad-hoc
    rescale 제거로 일부 drift 가 예상됨. 드리프트 정도를 사람이 검토 후 PR 머지 결정.

종료 코드:
  0 — drift 없음 또는 데이터 없음 (경고 표시)
  1 — drift 감지됨 — JSON 파일 생성됨
  2 — 환경 설정 오류 (MONGODB_URI_RO 미설정)
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import sys
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from typing import Any

try:
    from pymongo import MongoClient
except ImportError:
    print("❌ pymongo 미설치. poetry install 필요.", file=sys.stderr)
    sys.exit(2)


def _setup_logging() -> logging.Logger:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
    return logging.getLogger("scoring_regression")


def _add_repo_root_to_path() -> None:
    """src 디렉토리를 sys.path 에 추가 (poetry run 시 PYTHONPATH 미설정 케이스)."""
    here = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.dirname(here)  # data-engine root
    src = os.path.join(repo_root, "src")
    if src not in sys.path:
        sys.path.insert(0, src)


def main(days: int, threshold: float, output_path: str, limit: int | None) -> int:
    logger = _setup_logging()
    _add_repo_root_to_path()

    from domain.recommendation.scoring_policy import ScoringPolicy  # noqa: E402

    uri = os.environ.get("MONGODB_URI_RO")
    db_name = os.environ.get("MONGODB_DB_NAME", "stock_trading")
    if not uri:
        logger.error("MONGODB_URI_RO 환경변수 필수 (read-only 계정 권장).")
        return 2

    policy = ScoringPolicy.load_default()
    logger.info(
        "✓ ScoringPolicy 로드: version=%s, composite_max=%s",
        policy.formula_version,
        policy.composite_max,
    )

    # 연결 (read-only intent — uri 에서 권한 제어). context manager 로 보장 close.
    with MongoClient(uri, serverSelectionTimeoutMS=10000) as client:
        db = client[db_name]
        return _run_regression(db, policy, days, threshold, output_path, limit, logger)


def _run_regression(
    db: Any,
    policy: Any,
    days: int,
    threshold: float,
    output_path: str,
    limit: int | None,
    logger: logging.Logger,
) -> int:
    cutoff = (datetime.now(timezone.utc) - timedelta(days=days)).strftime("%Y-%m-%d")

    # 추천 이력 일괄 조회 (저장된 composite_score 포함)
    query = {"date": {"$gte": cutoff}, "is_recommended": True}
    projection = {
        "ticker": 1,
        "date": 1,
        "technical_indicators": 1,
        "composite_score": 1,
        "ai_score": 1,
        "sentiment_score": 1,
        "tech_score": 1,
        "ai_rise_probability": 1,
        "sentiment_normalized_score": 1,
    }
    rec_docs = list(db.stock_recommendations.find(query, projection))
    if limit is not None:
        rec_docs = rec_docs[:limit]
    if not rec_docs:
        logger.warning("⚠ regression 대상 0건 (cutoff=%s). 데이터 없음으로 종료.", cutoff)
        return 0

    logger.info("총 %d 건 검증 대상 (since %s)", len(rec_docs), cutoff)

    # M12: $in 으로 일괄 조회 — N+1 쿼리 방지
    tickers = list({d["ticker"] for d in rec_docs})
    dates = list({d["date"] for d in rec_docs})

    ar_map: dict[tuple, dict[str, Any]] = {}
    for d in db.stock_analysis_results.find(
        {"ticker": {"$in": tickers}, "date": {"$in": dates}},
        {"ticker": 1, "date": 1, "predictions": 1},
    ):
        ar_map[(d["ticker"], d["date"])] = d

    sentiment_map: dict[tuple, float] = {}
    for d in db.sentiment_analysis.find(
        {"ticker": {"$in": tickers}, "date": {"$in": dates}},
        {"ticker": 1, "date": 1, "average_sentiment_score": 1},
    ):
        sentiment_map[(d["ticker"], d["date"])] = d.get("average_sentiment_score", 0)

    logger.info(
        "보조 데이터 조회 완료: analysis_results=%d, sentiment=%d",
        len(ar_map),
        len(sentiment_map),
    )

    # 신 산식 재계산 및 비교
    drifts: list[dict[str, Any]] = []
    processed = 0

    for doc in rec_docs:
        ticker = doc["ticker"]
        date = doc["date"]
        old_composite = float(doc.get("composite_score") or 0)

        # 입력 신호 추출
        ar = ar_map.get((ticker, date), {})
        predictions = ar.get("predictions") or {}
        # stock_analysis_results 는 raw % 저장 (예: 20.0); sync_service 가 normalize
        rise_pct_raw = predictions.get("rise_probability")

        sent_raw = sentiment_map.get((ticker, date))
        tech_indicators = doc.get("technical_indicators")

        # 신 SSoT 산식 — 단일 호출
        new_score = policy.score_from_raw_signals(
            rise_pct=Decimal(str(rise_pct_raw)) if rise_pct_raw is not None else None,
            sentiment_raw=Decimal(str(sent_raw)) if sent_raw is not None else None,
            tech_indicators=tech_indicators,
        )
        new_composite = float(new_score.composite_score)

        delta = abs(new_composite - old_composite)
        processed += 1

        if delta > threshold:
            drifts.append(
                {
                    "ticker": ticker,
                    "date": date,
                    "old_composite": round(old_composite, 4),
                    "new_composite": round(new_composite, 4),
                    "delta": round(delta, 4),
                    "new_grade": new_score.grade,
                    "new_label": new_score.recommendation_label,
                    "missing_axes": list(new_score.missing_axes),
                }
            )

    logger.info("처리 완료: %d 건, drift > %.4f: %d 건", processed, threshold, len(drifts))

    if drifts:
        # delta 내림차순 정렬 — 가장 큰 drift 먼저
        drifts.sort(key=lambda r: -r["delta"])
        result = {
            "spec_version": policy.formula_version,
            "composite_max": float(policy.composite_max),
            "cutoff_date": cutoff,
            "threshold": threshold,
            "total_processed": processed,
            "drift_count": len(drifts),
            "drifts": drifts,
        }
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(result, f, indent=2, ensure_ascii=False)
        logger.error(
            "❌ %d 건 drift 감지. 상세 파일: %s (PR 검토 시 첨부)", len(drifts), output_path
        )
        return 1

    logger.info("✅ 의도 외 drift 없음. PR 머지 진행 가능.")
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="PR 1 prod regression — composite_score 신/구 산식 drift 정량화 (read-only).",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
예시:
  MONGODB_URI_RO='mongodb+srv://readonly:...@host/db' \\
  MONGODB_DB_NAME=stock_trading \\
  SCORING_SPEC_PATH=/path/to/scoring_spec.yaml \\
  poetry run python scripts/scoring_regression_prod.py --days 7 --threshold 0.01

종료 코드:
  0  drift 없음 또는 데이터 없음
  1  drift 감지됨 (--output 파일 생성)
  2  환경 설정 오류 (MONGODB_URI_RO 미설정)

⚠  본 스크립트는 절대 MongoDB 에 write 하지 않습니다.
   read-only 계정 URI 사용을 강력 권장합니다.
        """,
    )
    parser.add_argument("--days", type=int, default=7, help="스캔할 최근 일수 (기본값: 7)")
    parser.add_argument(
        "--threshold",
        type=float,
        default=0.01,
        help="composite delta 허용치; |new - old| > threshold 시 drift 판정 (기본값: 0.01)",
    )
    parser.add_argument(
        "--output",
        default="regression_unexpected.json",
        help="drift 기록 출력 경로 (기본값: regression_unexpected.json)",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="처리할 최대 건수 (디버깅용, 기본값: 전체)",
    )
    args = parser.parse_args()
    sys.exit(main(args.days, args.threshold, args.output, args.limit))
