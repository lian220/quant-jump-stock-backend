#!/usr/bin/env python3
"""
MongoDB 인덱스 생성 스크립트

백테스트 성능 최적화를 위한 인덱스:
1. date 필드 (범위 쿼리 최적화)
2. date + stocks.ticker 복합 인덱스 (종목별 조회 최적화)

사용법:
  # .env.local의 URI 사용 (로컬)
  python scripts/create_mongo_indexes.py

  # 프로덕션 URI 직접 지정
  python scripts/create_mongo_indexes.py "mongodb+srv://user:pass@cluster.mongodb.net/"

  # 환경 변수로 지정
  MONGODB_URI="mongodb+srv://..." python scripts/create_mongo_indexes.py
"""

import os
import sys
from pathlib import Path

# 프로젝트 루트를 Python 경로에 추가
project_root = Path(__file__).parent.parent / "src"
sys.path.insert(0, str(project_root))

from pymongo import MongoClient, ASCENDING
from dotenv import load_dotenv

# MongoDB 연결 URI 결정
MONGODB_URI = None

# 1. 커맨드라인 인자
if len(sys.argv) > 1:
    MONGODB_URI = sys.argv[1]
    print(f"✅ Using MongoDB URI from command line argument")
# 2. 환경 변수
elif os.environ.get("MONGODB_URI"):
    MONGODB_URI = os.environ.get("MONGODB_URI")
    print(f"✅ Using MongoDB URI from environment variable")
# 3. .env.local
else:
    env_path = Path(__file__).parent.parent.parent / ".env.local"
    if env_path.exists():
        load_dotenv(env_path)
        MONGODB_URI = os.environ.get("MONGODB_URI")
        print(f"✅ Loaded environment from {env_path}")
    else:
        print(f"⚠️  .env.local not found at {env_path}")

if not MONGODB_URI:
    print("❌ MONGODB_URI is required")
    print("\n사용법:")
    print("  python scripts/create_mongo_indexes.py <MONGODB_URI>")
    print("  또는")
    print("  MONGODB_URI='mongodb+srv://...' python scripts/create_mongo_indexes.py")
    sys.exit(1)

# localhost를 mongodb로 변경 (Docker Compose 환경)
if "localhost:27017" in MONGODB_URI or "127.0.0.1:27017" in MONGODB_URI:
    print("⚠️  로컬 MongoDB가 실행 중인지 확인하세요")

DB_NAME = os.environ.get("MONGODB_DB_NAME", "stock_trading")
COLLECTION_NAME = "daily_stock_data"

print(f"📊 Connecting to MongoDB: {DB_NAME}.{COLLECTION_NAME}")

try:
    client = MongoClient(MONGODB_URI)
    db = client[DB_NAME]
    collection = db[COLLECTION_NAME]

    # 연결 테스트
    client.admin.command('ping')
    print("✅ MongoDB connection successful\n")

    # 기존 인덱스 확인
    print("📋 Existing indexes:")
    existing_indexes = collection.list_indexes()
    for idx in existing_indexes:
        print(f"  - {idx['name']}: {idx.get('key', {})}")
    print()

    # 인덱스 생성
    indexes_to_create = []

    # 1. date 필드 인덱스 (범위 쿼리 최적화)
    print("🔧 Creating index on 'date' field...")
    result1 = collection.create_index(
        [("date", ASCENDING)],
        name="idx_date",
        background=True
    )
    print(f"   ✅ Index created: {result1}")
    indexes_to_create.append(result1)

    # 2. 대표 종목들에 대한 복합 인덱스 (date + stocks.ticker)
    # 백테스트에서 자주 사용되는 종목들
    popular_tickers = [
        "AAPL", "NVDA", "MSFT", "GOOGL", "AMZN",
        "META", "TSLA", "AMD", "NFLX", "AVGO"
    ]

    print(f"\n🔧 Creating compound indexes for {len(popular_tickers)} popular tickers...")
    for ticker in popular_tickers:
        try:
            idx_name = f"idx_date_stocks_{ticker}"
            result = collection.create_index(
                [("date", ASCENDING), (f"stocks.{ticker}", ASCENDING)],
                name=idx_name,
                background=True,
                sparse=True  # NULL 값이 있는 문서는 인덱스에서 제외
            )
            print(f"   ✅ {idx_name}: {result}")
            indexes_to_create.append(result)
        except Exception as e:
            print(f"   ⚠️  Failed to create index for {ticker}: {e}")

    # 3. yfinance_indicators 벤치마크 인덱스
    print("\n🔧 Creating indexes for benchmark tickers...")
    benchmark_tickers = ["^GSPC", "^IXIC", "^DJI", "SPY", "QQQ"]
    for ticker in benchmark_tickers:
        try:
            idx_name = f"idx_date_yfinance_{ticker.replace('^', 'caret_')}"
            result = collection.create_index(
                [("date", ASCENDING), (f"yfinance_indicators.{ticker}", ASCENDING)],
                name=idx_name,
                background=True,
                sparse=True
            )
            print(f"   ✅ {idx_name}: {result}")
            indexes_to_create.append(result)
        except Exception as e:
            print(f"   ⚠️  Failed to create index for {ticker}: {e}")

    # 최종 인덱스 목록 확인
    print("\n📋 Final index list:")
    final_indexes = collection.list_indexes()
    for idx in final_indexes:
        print(f"  - {idx['name']}: {idx.get('key', {})}")

    # 인덱스 통계
    stats = db.command("collStats", COLLECTION_NAME)
    total_indexes = stats.get("nindexes", 0)
    total_index_size = stats.get("totalIndexSize", 0) / (1024 * 1024)  # MB

    print(f"\n📊 Collection Statistics:")
    print(f"  - Total documents: {stats.get('count', 0):,}")
    print(f"  - Total indexes: {total_indexes}")
    print(f"  - Total index size: {total_index_size:.2f} MB")
    print(f"  - Collection size: {stats.get('size', 0) / (1024 * 1024):.2f} MB")

    print("\n✅ Index creation completed successfully!")
    print("\n💡 Tip: 백테스트 성능이 크게 개선될 것입니다.")

except Exception as e:
    print(f"❌ Error: {e}")
    sys.exit(1)
finally:
    if 'client' in locals():
        client.close()
        print("\n🔌 MongoDB connection closed")
