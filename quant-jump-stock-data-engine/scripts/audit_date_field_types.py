"""
MongoDB date 필드 타입 일관성 audit.

목적: 모든 컬렉션의 `date` 필드가 string vs ISODate 어느 타입인지, 혼재 여부 확인.
근거: 2026-05-20 freshness 알람 사고 분석 — stock_recommendations 만 ISODate 였고
나머지는 string. 전체 string 통일 결정 후 audit 으로 마이그 대상 식별.

보안:
- MongoDB URI 는 환경변수에서만 읽음. stdout 으로 출력 안 함.
- 결과 표만 출력 (URI / 비밀번호 / hostname 노출 없음).

실행:
    poetry run python scripts/audit_date_field_types.py
또는:
    export MONGODB_URI=...; python scripts/audit_date_field_types.py
"""
import os
import sys
from pathlib import Path

try:
    from dotenv import load_dotenv
    # CLI: --env local|prod (기본 local). 시스템 MONGODB_URI 가 set 되어 있으면 그게 우선.
    env_name = "local"
    if len(sys.argv) > 1 and sys.argv[1].startswith("--env="):
        env_name = sys.argv[1].split("=", 1)[1]
    elif len(sys.argv) > 1 and sys.argv[1] in ("--env",) and len(sys.argv) > 2:
        env_name = sys.argv[2]
    # 검색 후보: data-engine repo 의 backend root.
    # worktree 의 경우 메인 worktree (../../../../) 까지 fallback.
    here = Path(__file__).resolve()
    candidates = [
        here.parents[2] / f".env.db.{env_name}",  # data-engine repo root
        here.parents[5] / f".env.db.{env_name}",  # backend root (worktree → .claude/worktrees/<name>/data-engine/scripts)
        here.parents[3] / f".env.db.{env_name}",  # fallback
    ]
    for env_file in candidates:
        if env_file.exists():
            load_dotenv(env_file)
            sys.stderr.write(f"loaded {env_file}\n")
            break
    else:
        sys.stderr.write(f"no .env.db.{env_name} found in {candidates}\n")
except ImportError:
    pass

from pymongo import MongoClient

URI = os.environ.get("MONGODB_URI") or os.environ.get("MONGO_URI")
if not URI:
    sys.stderr.write("ERROR: MONGODB_URI 환경변수 없음.\n")
    sys.exit(1)

# docker-compose 의 hostname `mongodb` 는 host machine 에서 풀이 안 됨.
# local env 일 때 localhost 로 자동 치환.
if env_name == "local" and "@mongodb:" in URI:
    URI = URI.replace("@mongodb:", "@localhost:")
    sys.stderr.write("hostname `mongodb` → `localhost` (local env)\n")

DB_NAME = os.environ.get("MONGODB_DATABASE") or os.environ.get("MONGODB_DB_NAME", "stock_trading")

client = MongoClient(URI, serverSelectionTimeoutMS=10000)
db = client[DB_NAME]

print(f"=== {DB_NAME} 컬렉션 date 필드 타입 audit ===\n")
print(f"{'Collection':<40} {'string':<10} {'date':<10} {'other':<10} {'no date':<10} {'latest':<25}")
print("-" * 110)

for coll_name in sorted(db.list_collection_names()):
    if coll_name.startswith("system.") or coll_name.startswith("fs."):
        continue

    coll = db[coll_name]

    # date 필드 존재하는 문서만 sampling 으로 타입 그룹화
    pipeline = [
        {"$match": {"date": {"$exists": True}}},
        {
            "$group": {
                "_id": {"$type": "$date"},
                "count": {"$sum": 1},
                "latest": {"$max": "$date"},
            }
        },
    ]
    try:
        result = list(coll.aggregate(pipeline, maxTimeMS=30000))
    except Exception as e:
        print(f"{coll_name:<40}  ERROR: {e}")
        continue

    if not result:
        # date 필드 없는 컬렉션 — 표시 안 함 (관심 대상 아님)
        continue

    counts = {row["_id"]: row["count"] for row in result}
    latest_per_type = {row["_id"]: row["latest"] for row in result}

    string_n = counts.get("string", 0)
    date_n = counts.get("date", 0)
    other_n = sum(c for t, c in counts.items() if t not in ("string", "date"))

    total_with_date = sum(counts.values())
    # 전체 row 와 비교해서 date 가 일부에만 있는 경우 확인
    total_all = coll.estimated_document_count()
    no_date_n = max(0, total_all - total_with_date)

    # latest 표시: 가장 흔한 타입의 latest
    primary_type = "string" if string_n >= date_n else "date"
    latest = latest_per_type.get(primary_type, "-")

    flag = ""
    if string_n > 0 and date_n > 0:
        flag = " ⚠️ MIXED"
    elif date_n > 0 and string_n == 0:
        flag = " ⚠️ ISODate"

    print(
        f"{coll_name:<40} "
        f"{string_n:<10} {date_n:<10} {other_n:<10} {no_date_n:<10} "
        f"{str(latest)[:24]:<25}{flag}"
    )

print("\n=== 마이그 대상 (date or mixed) ===")
for coll_name in sorted(db.list_collection_names()):
    if coll_name.startswith("system.") or coll_name.startswith("fs."):
        continue
    coll = db[coll_name]
    counts = {}
    try:
        for row in coll.aggregate(
            [{"$match": {"date": {"$exists": True}}}, {"$group": {"_id": {"$type": "$date"}, "count": {"$sum": 1}}}],
            maxTimeMS=30000,
        ):
            counts[row["_id"]] = row["count"]
    except Exception:
        continue
    if counts.get("date", 0) > 0:
        print(f"  - {coll_name}: ISODate {counts['date']:,}건 (string {counts.get('string', 0):,})")
