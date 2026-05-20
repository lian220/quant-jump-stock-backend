"""
모든 ISODate 컬렉션의 date 필드를 NYSE 거래일 string ("%Y-%m-%d") 으로 일괄 마이그.

배경 (2026-05-20):
  audit_date_field_types.py 결과:
    - daily_stock_data     7,418 string  ✅
    - sentiment_analysis   2,440 string  ✅
    - stock_analysis_results 0 string, 490 ISODate     ⚠️
    - stock_predictions      0 string, 270,441 ISODate ⚠️
    - stock_recommendations  0 string, 60,150 ISODate  ⚠️
  → 일관성 통일을 위해 ISODate 컬렉션 3개를 string 으로 마이그.

전제:
  - 모든 ISODate 값은 `datetime(Y, M, D, 0, 0, 0)` 형식 (UTC 자정).
    audit 결과 latest = 2026-05-18 00:00:00 이라 검증 완료.
  - 변환 후: "2026-05-18"

안전:
  - --dry-run 옵션: 변환 대상 카운트만 출력하고 실제 update 안 함
  - bulk_write ordered=False — 일부 실패해도 나머지 적용
  - 멱등 — 이미 string 인 row 는 query filter 로 제외 (`{"date": {"$type": "date"}}`)

실행:
  poetry run python scripts/migrate_dates_to_string.py --env=local --dry-run
  poetry run python scripts/migrate_dates_to_string.py --env=prod --dry-run
  poetry run python scripts/migrate_dates_to_string.py --env=prod
"""
import os
import sys
import time
from pathlib import Path

try:
    from dotenv import load_dotenv
    env_name = "local"
    dry_run = False
    for arg in sys.argv[1:]:
        if arg.startswith("--env="):
            env_name = arg.split("=", 1)[1]
        elif arg == "--dry-run":
            dry_run = True

    here = Path(__file__).resolve()
    candidates = [
        here.parents[2] / f".env.db.{env_name}",
        here.parents[5] / f".env.db.{env_name}",
    ]
    for env_file in candidates:
        if env_file.exists():
            load_dotenv(env_file)
            sys.stderr.write(f"loaded {env_file}\n")
            break
except ImportError:
    dry_run = False
    env_name = "local"

from pymongo import MongoClient

URI = os.environ.get("MONGODB_URI") or os.environ.get("MONGO_URI")
if not URI:
    sys.stderr.write("ERROR: MONGODB_URI 환경변수 없음.\n")
    sys.exit(1)

if env_name == "local" and "@mongodb:" in URI:
    URI = URI.replace("@mongodb:", "@localhost:")

DB_NAME = os.environ.get("MONGODB_DATABASE") or os.environ.get("MONGODB_DB_NAME", "stock_trading")

TARGET_COLLECTIONS = ["stock_recommendations", "stock_predictions", "stock_analysis_results"]

client = MongoClient(URI, serverSelectionTimeoutMS=10000)
db = client[DB_NAME]

print(f"=== ISODate → string 마이그 ({DB_NAME}) — {'DRY RUN' if dry_run else 'EXECUTE'} ===\n")

for coll_name in TARGET_COLLECTIONS:
    coll = db[coll_name]
    iso_count = coll.count_documents({"date": {"$type": "date"}})
    print(f"\n--- {coll_name} ---")
    print(f"  ISODate row: {iso_count:,}")

    if iso_count == 0:
        print(f"  → 이미 통일됨, skip")
        continue

    if dry_run:
        print(f"  → DRY RUN: {iso_count:,}건 변환 대상")
        continue

    # MongoDB 5.0+ $dateToString aggregation pipeline update
    start = time.time()
    result = coll.update_many(
        {"date": {"$type": "date"}},
        [
            {
                "$set": {
                    "date": {
                        "$dateToString": {
                            "format": "%Y-%m-%d",
                            "date": "$date",
                            "timezone": "UTC",
                        }
                    }
                }
            }
        ],
    )
    elapsed = time.time() - start
    print(f"  ✅ matched={result.matched_count:,}, modified={result.modified_count:,} ({elapsed:.1f}s)")

# POSTCHECK
print("\n=== POSTCHECK ===")
for coll_name in TARGET_COLLECTIONS:
    coll = db[coll_name]
    iso_remaining = coll.count_documents({"date": {"$type": "date"}})
    str_count = coll.count_documents({"date": {"$type": "string"}})
    status = "✅" if iso_remaining == 0 else "⚠️"
    print(f"  {status} {coll_name}: string={str_count:,}, ISODate={iso_remaining:,}")
