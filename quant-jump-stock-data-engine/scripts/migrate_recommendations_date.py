"""
stock_recommendations.date string → ISODate 일괄 마이그레이션 (1회성).

배경 (2026-05-18 사고):
  technical_analysis.py:148 (writer) 가 date 를 "%Y-%m-%d" string 으로 저장.
  sync_service.py:_query_recommendations_by_date 가 Date 타입 query → BSON 타입 불일치.
  → "can't convert from BSON type string to Date" 만성 에러.
  → sync_service 가 3중 fallback (ISODate / string / $expr) 사용
  → fallback 도 실패 시 stale 5/13 분 데이터 며칠째 재사용

마이그레이션 후:
  - writer (technical_analysis.py) 가 ISODate 로 저장 (본 PR 동시 적용)
  - 기존 string 문서를 ISODate 로 변환
  - sync_service.py 의 string/$expr fallback 분기는 별도 PR 에서 제거

실행:
  cd quant-jump-stock-data-engine
  poetry run python scripts/migrate_recommendations_date.py [--dry-run]

  --dry-run: 실제 변경 없이 영향받는 문서 수만 출력
  (기본): string date 문서를 ISODate 로 변환

안전장치:
  - $type: "string" 필터로 ISODate 문서는 건드리지 않음 (idempotent)
  - 변환 실패 시 해당 문서 skip, 로그만 남기고 계속 진행
  - 전체 변환 후 잔여 string 문서 수 검증
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
from datetime import datetime

from pymongo import MongoClient
from pymongo.errors import PyMongoError

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

COLLECTION_NAME = "stock_recommendations"


def main(dry_run: bool = False) -> int:
    mongodb_uri = os.environ.get("MONGODB_URI")
    if not mongodb_uri:
        logger.error("환경변수 MONGODB_URI 미설정")
        return 1

    db_name = os.environ.get("MONGODB_DB_NAME", "stock_trading")

    client = MongoClient(mongodb_uri)
    db = client[db_name]
    col = db[COLLECTION_NAME]

    # 영향 대상 카운트
    string_count = col.count_documents({"date": {"$type": "string"}})
    isodate_count = col.count_documents({"date": {"$type": "date"}})
    total = col.estimated_document_count()

    logger.info(f"컬렉션 {db_name}.{COLLECTION_NAME} 분포:")
    logger.info(f"  총 문서: {total}")
    logger.info(f"  string date: {string_count}")
    logger.info(f"  ISODate: {isodate_count}")
    logger.info(f"  기타/missing: {total - string_count - isodate_count}")

    if string_count == 0:
        logger.info("✅ 변환 대상 0건 — 마이그레이션 불필요")
        return 0

    if dry_run:
        logger.info(f"[DRY-RUN] {string_count}건 변환 예정 — 실행 안 함")
        return 0

    converted = 0
    failed = 0
    cursor = col.find({"date": {"$type": "string"}}, {"_id": 1, "date": 1})
    for doc in cursor:
        try:
            dt = datetime.strptime(doc["date"], "%Y-%m-%d")
            col.update_one({"_id": doc["_id"]}, {"$set": {"date": dt}})
            converted += 1
            if converted % 100 == 0:
                logger.info(f"  진행: {converted}/{string_count}")
        except (ValueError, PyMongoError) as e:
            failed += 1
            logger.warning(f"  skip _id={doc['_id']} (date={doc.get('date')!r}): {e}")

    # 검증
    remaining = col.count_documents({"date": {"$type": "string"}})
    logger.info(f"마이그레이션 완료: {converted} 변환, {failed} 실패")
    logger.info(f"잔여 string date 문서: {remaining}")

    if remaining > 0:
        logger.warning(f"⚠️ {remaining}건이 여전히 string — 수동 점검 필요")
        return 2

    # 인덱스 재생성 (ISODate 기반 효율적 range query)
    logger.info("인덱스 (date, ticker) 재생성")
    col.create_index([("date", 1), ("ticker", 1)])

    logger.info("✅ 마이그레이션 성공")
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="실제 변경 없이 카운트만 출력")
    args = parser.parse_args()
    sys.exit(main(dry_run=args.dry_run))
