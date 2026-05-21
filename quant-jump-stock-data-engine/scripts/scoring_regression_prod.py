"""PR 1 prod regression — composite_score 신/구 산식 drift 정량화.

⚠ 운영 안전성:
  - PostgreSQL prediction_results 의 저장된 신호 + 구 composite_score 를 읽어,
    신 ScoringPolicy 로 재계산하여 비교. **MongoDB 미사용 (composite 저장처가 PG)**.
  - **Read-only PostgreSQL 계정 권장** (Supabase RO role). 본 스크립트는 SELECT 만 수행.
  - .env.db.prod 직접 로드 금지. 환경변수로 주입.
  - Agent 자동 실행 금지. 사람이 PR 검토 직전 1회 실행.

실행 (운영자):
  PG_HOST_RO=<host> \\
  PG_PORT_RO=5432 \\
  PG_USER_RO=<readonly-user> \\
  PG_PASSWORD_RO=<password> \\
  PG_NAME_RO=<dbname> \\
  SCORING_SPEC_PATH=/Users/.../scoring_spec.yaml \\
  poetry run python scripts/scoring_regression_prod.py --days 7 --threshold 0.01

목적:
  - PR 1 머지 전, 운영 데이터 N일치에 대해 신 산식 결과를 재계산하여 drift 정량화.
  - drift > threshold 인 ticker 가 0 인 게 이상적이나, comprehensive_report 의 ad-hoc
    rescale 제거로 일부 drift 가 예상됨. 드리프트 정도를 사람이 검토 후 PR 머지 결정.

종료 코드:
  0 — drift 없음 또는 데이터 없음 (경고 표시)
  1 — drift 감지됨 — JSON 파일 생성됨
  2 — 환경 설정 오류 (PG_*_RO 누락)
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
    import psycopg2
    from psycopg2.extras import RealDictCursor
except ImportError:
    print("❌ psycopg2 미설치. poetry install 필요.", file=sys.stderr)
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


def _get_pg_connection_params(logger: logging.Logger) -> dict[str, Any] | None:
    """PG_*_RO env 검증. 누락 항목 있으면 None 반환."""
    required = {
        "host": "PG_HOST_RO",
        "user": "PG_USER_RO",
        "password": "PG_PASSWORD_RO",
        "dbname": "PG_NAME_RO",
    }
    params: dict[str, Any] = {}
    missing = []
    for key, env in required.items():
        v = os.environ.get(env)
        if not v:
            missing.append(env)
        else:
            params[key] = v
    if missing:
        logger.error("필수 환경변수 누락 (read-only PG 계정 권장): %s", ", ".join(missing))
        return None
    params["port"] = int(os.environ.get("PG_PORT_RO", "5432"))
    return params


def main(days: int, threshold: float, output_path: str, limit: int | None) -> int:
    logger = _setup_logging()
    _add_repo_root_to_path()

    from domain.recommendation.scoring_policy import ScoringPolicy  # noqa: E402

    pg_params = _get_pg_connection_params(logger)
    if pg_params is None:
        return 2

    policy = ScoringPolicy.load_default()
    logger.info(
        "✓ ScoringPolicy 로드: version=%s, composite_max=%s",
        policy.formula_version,
        policy.composite_max,
    )

    # 연결 — context manager 로 보장 close
    with psycopg2.connect(**pg_params) as conn:
        return _run_regression(conn, policy, days, threshold, output_path, limit, logger)


def _run_regression(
    conn: Any,
    policy: Any,
    days: int,
    threshold: float,
    output_path: str,
    limit: int | None,
    logger: logging.Logger,
) -> int:
    cutoff_date = (datetime.now(timezone.utc) - timedelta(days=days)).date()

    # prediction_results: 신호 + 구 composite 한 행에 모두 있음 → 단일 쿼리 (M12 N+1 무관)
    sql = """
        SELECT
            ticker, stock_name, analysis_date,
            ai_rise_probability, sentiment_score, sentiment_news_count,
            tech_golden_cross, tech_rsi, tech_macd_buy_signal, tech_signals_count,
            composite_score, composite_grade, is_recommended
        FROM prediction_results
        WHERE analysis_date >= %s
          AND is_recommended = TRUE
        ORDER BY analysis_date DESC, ticker
    """
    if limit is not None:
        sql += f"\n        LIMIT {int(limit)}"  # bounded int; safe

    with conn.cursor(cursor_factory=RealDictCursor) as cur:
        cur.execute(sql, (cutoff_date,))
        rows = cur.fetchall()

    if not rows:
        logger.warning("⚠ regression 대상 0건 (cutoff=%s). 데이터 없음으로 종료.", cutoff_date)
        return 0

    logger.info("총 %d 건 검증 대상 (since %s)", len(rows), cutoff_date)

    # 신 산식 재계산 및 비교
    drifts: list[dict[str, Any]] = []
    grade_changes = 0
    label_changes = 0
    processed = 0

    for row in rows:
        ticker = row["ticker"]
        date = row["analysis_date"]
        old_composite = float(row.get("composite_score") or 0)
        old_grade = row.get("composite_grade") or ""

        # 입력 신호 (PG 는 0~1 normalized 형태로 저장)
        rise_prob = row.get("ai_rise_probability")
        sent_raw = row.get("sentiment_score")
        tech_indicators = {
            "golden_cross": bool(row.get("tech_golden_cross")),
            "rsi": row.get("tech_rsi"),
            "macd_buy_signal": bool(row.get("tech_macd_buy_signal")),
        }
        # rsi 가 None 이면 indicator 에서 제거 (Decimal 변환 시 오류 방지)
        if tech_indicators["rsi"] is None:
            tech_indicators.pop("rsi")

        # 신 산식
        ai_score = policy.ai_score_from_normalized(
            Decimal(str(rise_prob)) if rise_prob is not None else None
        )
        tech_score = policy.tech_score_from_indicators(tech_indicators)
        tech_signals = policy.count_tech_signals(tech_indicators)
        sentiment_score = policy.sentiment_score_from_raw(
            Decimal(str(sent_raw)) if sent_raw is not None else None
        )

        new_score = policy.compose_components(
            ai_score=ai_score,
            tech_score=tech_score,
            sentiment_score=sentiment_score,
            has_ai=rise_prob is not None,
            has_tech=bool(tech_indicators),
            has_sentiment=sent_raw is not None,
            tech_signal_count=tech_signals,
        )
        new_composite = float(new_score.composite_score)

        delta = abs(new_composite - old_composite)
        processed += 1

        if new_score.grade != old_grade and old_grade:
            grade_changes += 1
        # label 비교를 위한 기준은 PG 에 없음 (recommendation_grade enum 은 별도) — skip

        if delta > threshold:
            drifts.append(
                {
                    "ticker": ticker,
                    "stock_name": row.get("stock_name"),
                    "date": str(date),
                    "old_composite": round(old_composite, 4),
                    "new_composite": round(new_composite, 4),
                    "delta": round(delta, 4),
                    "old_grade": old_grade,
                    "new_grade": new_score.grade,
                    "new_label": new_score.recommendation_label,
                    "grade_changed": new_score.grade != old_grade and bool(old_grade),
                    "missing_axes": list(new_score.missing_axes),
                }
            )

    logger.info(
        "처리 완료: %d 건, drift > %.4f: %d 건, grade 변경: %d 건",
        processed, threshold, len(drifts), grade_changes,
    )

    if drifts:
        # delta 내림차순 정렬 — 가장 큰 drift 먼저
        drifts.sort(key=lambda r: -r["delta"])
        result = {
            "spec_version": policy.formula_version,
            "composite_max": float(policy.composite_max),
            "cutoff_date": str(cutoff_date),
            "threshold": threshold,
            "total_processed": processed,
            "drift_count": len(drifts),
            "grade_changes": grade_changes,
            "drifts": drifts,
        }
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(result, f, indent=2, ensure_ascii=False)
        logger.error(
            "❌ %d 건 drift 감지 (grade 변경 %d). 상세 파일: %s (PR 검토 시 첨부)",
            len(drifts), grade_changes, output_path,
        )
        return 1

    logger.info("✅ 의도 외 drift 없음. PR 머지 진행 가능.")
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="PR 1 prod regression — composite_score 신/구 산식 drift 정량화 (read-only PG).",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
예시:
  PG_HOST_RO=<host> \\
  PG_PORT_RO=5432 \\
  PG_USER_RO=<readonly-user> \\
  PG_PASSWORD_RO=<password> \\
  PG_NAME_RO=<dbname> \\
  SCORING_SPEC_PATH=/path/to/scoring_spec.yaml \\
  poetry run python scripts/scoring_regression_prod.py --days 7 --threshold 0.01

종료 코드:
  0  drift 없음 또는 데이터 없음
  1  drift 감지됨 (--output 파일 생성)
  2  환경 설정 오류 (PG_*_RO 누락)

⚠  본 스크립트는 절대 PostgreSQL 에 write 하지 않습니다 (SELECT only).
   그러나 read-only 계정 (Supabase RO role) URI 사용을 강력 권장합니다.
        """,
    )
    parser.add_argument("--days", type=int, default=7, help="스캔할 최근 일수 (기본값: 7)")
    parser.add_argument(
        "--threshold", type=float, default=0.01,
        help="composite delta 허용치; |new - old| > threshold 시 drift 판정 (기본값: 0.01)",
    )
    parser.add_argument(
        "--output", default="regression_unexpected.json",
        help="drift 기록 출력 경로 (기본값: regression_unexpected.json)",
    )
    parser.add_argument(
        "--limit", type=int, default=None,
        help="처리할 최대 건수 (디버깅용, 기본값: 전체)",
    )
    args = parser.parse_args()
    sys.exit(main(args.days, args.threshold, args.output, args.limit))
