"""
운영 MongoDB 데이터를 로컬로 마이그레이션

사용법:
    python scripts/migrate_from_production.py

환경변수:
    PROD_MONGODB_URI: 운영 MongoDB URI (.env.prod)
    LOCAL_MONGODB_URI: 로컬 MongoDB URI (.env.local)
"""
import os
import sys
from pathlib import Path
from pymongo import MongoClient, ASCENDING
from datetime import datetime, timezone
from typing import Dict, List, Any

# 프로젝트 루트에서 .env 파일 로드
try:
    from dotenv import load_dotenv, dotenv_values
    project_root = Path(__file__).parent.parent.parent

    # .env.prod에서 운영 URI 로드 (MONGODB_URI)
    prod_env = dotenv_values(project_root / ".env.prod")
    os.environ["PROD_MONGODB_URI"] = prod_env.get("MONGODB_URI", "")

    # .env.local에서 로컬 URI 로드 (MONGODB_URI)
    local_env = dotenv_values(project_root / ".env.local")
    local_uri = local_env.get("MONGODB_URI", "")

    # Docker 컨테이너 이름 'mongodb'를 'localhost'로 치환 (스크립트는 호스트에서 실행)
    local_uri = local_uri.replace("mongodb:27017", "localhost:27017")
    os.environ["LOCAL_MONGODB_URI"] = local_uri

    print(f"✅ .env 파일 로드 완료: {project_root}")
except ImportError:
    print("⚠️  python-dotenv not installed. Using environment variables directly.")
except Exception as e:
    print(f"⚠️  .env 파일 로드 실패: {e}")

# 색상 출력용
class Colors:
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    RED = '\033[91m'
    BLUE = '\033[94m'
    END = '\033[0m'

def print_success(msg):
    print(f"{Colors.GREEN}✅ {msg}{Colors.END}")

def print_warning(msg):
    print(f"{Colors.YELLOW}⚠️  {msg}{Colors.END}")

def print_error(msg):
    print(f"{Colors.RED}❌ {msg}{Colors.END}")

def print_info(msg):
    print(f"{Colors.BLUE}ℹ️  {msg}{Colors.END}")


def normalize_date(date_value: Any) -> str:
    """
    날짜를 "YYYY-MM-DD" 문자열 형식으로 통일

    Args:
        date_value: datetime 객체 또는 문자열

    Returns:
        "YYYY-MM-DD" 형식의 문자열
    """
    if isinstance(date_value, datetime):
        return date_value.strftime("%Y-%m-%d")
    elif isinstance(date_value, str):
        # 이미 문자열이면 그대로 반환 (형식 검증 생략)
        return date_value
    else:
        raise ValueError(f"지원하지 않는 date 타입: {type(date_value)}")


def ensure_unique_index(collection, index_name: str = "ticker_date_unique"):
    """
    (ticker, date) compound unique index 생성

    Args:
        collection: MongoDB 컬렉션
        index_name: 인덱스 이름
    """
    try:
        # 기존 인덱스 확인
        existing_indexes = collection.index_information()

        if index_name in existing_indexes:
            print_info(f"  Unique index '{index_name}' 이미 존재")
        else:
            # Compound unique index 생성
            collection.create_index(
                [("ticker", ASCENDING), ("date", ASCENDING)],
                unique=True,
                name=index_name
            )
            print_success(f"  Unique index '{index_name}' 생성 완료")
    except Exception as e:
        print_warning(f"  인덱스 생성 중 오류 (무시): {e}")


def get_mongodb_connections():
    """운영 및 로컬 MongoDB 연결"""
    # 운영 MongoDB URI
    prod_uri = os.environ.get("PROD_MONGODB_URI")
    if not prod_uri:
        print_error("PROD_MONGODB_URI 환경변수가 설정되지 않았습니다.")
        print_info("예시: export PROD_MONGODB_URI='mongodb://user:pass@production-host:27017/stock_trading?authSource=admin'")
        exit(1)

    # 로컬 MongoDB URI
    local_uri = os.environ.get("LOCAL_MONGODB_URI") or os.environ.get("MONGODB_URI")
    if not local_uri:
        local_uri = "mongodb://quantiq_user:quantiq_password@localhost:27017/stock_trading?authSource=admin"
        print_warning(f"LOCAL_MONGODB_URI 미설정, 기본값 사용: {local_uri}")

    try:
        print_info("운영 MongoDB 연결 중...")
        prod_client = MongoClient(prod_uri, serverSelectionTimeoutMS=5000)
        prod_client.admin.command('ping')
        prod_db = prod_client['stock_trading']
        print_success("운영 MongoDB 연결 성공")

        print_info("로컬 MongoDB 연결 중...")
        local_client = MongoClient(local_uri, serverSelectionTimeoutMS=5000)
        local_client.admin.command('ping')
        local_db = local_client['stock_trading']
        print_success("로컬 MongoDB 연결 성공")

        return prod_db, local_db, prod_client, local_client

    except Exception as e:
        print_error(f"MongoDB 연결 실패: {e}")
        exit(1)


def migrate_collection(
    prod_db,
    local_db,
    collection_name: str,
    start_date: str = "2026-01-01"
):
    """
    컬렉션 마이그레이션 (날짜 형식 통일 + Unique Index 생성)

    Args:
        prod_db: 운영 MongoDB 데이터베이스
        local_db: 로컬 MongoDB 데이터베이스
        collection_name: 컬렉션 이름
        start_date: 마이그레이션 시작 날짜 (YYYY-MM-DD)
    """
    print(f"\n{'='*60}")
    print_info(f"컬렉션 마이그레이션: {collection_name}")
    print(f"{'='*60}")

    prod_coll = prod_db[collection_name]
    local_coll = local_db[collection_name]

    # 1. Compound Unique Index 생성 (중복 방지)
    print_info("Compound Unique Index 생성 중...")
    ensure_unique_index(local_coll, index_name=f"{collection_name}_ticker_date_unique")

    # 2. 운영 데이터 조회 (2026-01-01 이후)
    start_date_obj = datetime.strptime(start_date, "%Y-%m-%d")

    # 문자열과 datetime 모두 지원하도록 $or 사용
    query = {
        "$or": [
            {"date": {"$gte": start_date}},  # 문자열 형식
            {"date": {"$gte": start_date_obj}}  # datetime 형식
        ]
    }

    print_info(f"운영 데이터 조회 중... (date >= {start_date})")
    prod_docs = list(prod_coll.find(query))

    if not prod_docs:
        print_warning(f"{collection_name}: 데이터 없음")
        return 0

    print_success(f"운영에서 {len(prod_docs)} 건 조회됨")

    # 3. 로컬 기존 데이터 확인
    existing_count = local_coll.count_documents({})
    print_info(f"로컬 기존 데이터: {existing_count} 건")

    # 4. 날짜 형식 통일 + 중복 제거 (ticker + date 기준)
    print_info("날짜 형식 통일 ('YYYY-MM-DD') + 중복 제거 중...")

    # 로컬에 이미 있는 (ticker, date) 조합 조회
    existing_keys = set()
    for doc in local_coll.find({}, {"ticker": 1, "date": 1}):
        ticker = doc.get("ticker")
        date_str = normalize_date(doc.get("date"))
        existing_keys.add((ticker, date_str))

    # 새로운 데이터만 필터링 + 날짜 형식 변환
    new_docs = []
    for doc in prod_docs:
        ticker = doc.get("ticker")
        date_value = doc.get("date")

        # 날짜를 "YYYY-MM-DD" 문자열로 통일
        date_str = normalize_date(date_value)

        # 중복 체크
        if (ticker, date_str) not in existing_keys:
            # _id 제거 (로컬에서 새로 생성)
            doc.pop("_id", None)
            # 날짜 형식 변환
            doc["date"] = date_str
            new_docs.append(doc)

    if not new_docs:
        print_warning("새로운 데이터가 없습니다 (모두 이미 존재)")
        return 0

    print_success(f"새로운 데이터: {len(new_docs)} 건")

    # 5. 로컬에 삽입
    print_info("로컬 MongoDB에 삽입 중...")
    try:
        result = local_coll.insert_many(new_docs, ordered=False)
        print_success(f"삽입 완료: {len(result.inserted_ids)} 건")
    except Exception as e:
        # Unique Index 위반 등의 경우 (이미 처리했으므로 발생 안 해야 함)
        print_error(f"삽입 중 오류: {e}")
        return 0

    # 6. 통계 출력
    print(f"\n📊 마이그레이션 통계:")
    print(f"   - 운영 조회: {len(prod_docs)} 건")
    print(f"   - 로컬 기존: {existing_count} 건")
    print(f"   - 신규 삽입: {len(new_docs)} 건")
    print(f"   - 로컬 총계: {local_coll.count_documents({})} 건")

    return len(new_docs)


def show_sample_data(db, collection_name: str, limit: int = 3):
    """샘플 데이터 출력"""
    coll = db[collection_name]
    print(f"\n📋 {collection_name} 샘플 데이터:")

    docs = list(coll.find().sort("date", -1).limit(limit))

    for i, doc in enumerate(docs, 1):
        print(f"\n  [{i}] Ticker: {doc.get('ticker')}, Date: {doc.get('date')}")

        if collection_name == "stock_predictions":
            print(f"      Predicted: {doc.get('predicted_price')}, Actual: {doc.get('actual_price')}")
            print(f"      Horizon: {doc.get('forecast_horizon')} days")

        elif collection_name == "sentiment_analysis":
            print(f"      Score: {doc.get('average_sentiment_score')}, Count: {doc.get('article_count')}")

        elif collection_name == "stock_analysis":
            print(f"      Recommendation: {doc.get('recommendation')}, Score: {doc.get('recommendation_score')}")
            print(f"      Analysis: {doc.get('analysis_type', 'N/A')}")


def main():
    """메인 실행 함수"""
    print("\n" + "="*60)
    print("🚀 운영 → 로컬 MongoDB 마이그레이션")
    print("="*60)

    # MongoDB 연결
    prod_db, local_db, prod_client, local_client = get_mongodb_connections()

    try:
        # 마이그레이션할 컬렉션
        collections = [
            "sentiment_analysis",
            "stock_predictions",
            "stock_analysis"
        ]

        total_migrated = 0

        for collection_name in collections:
            migrated = migrate_collection(
                prod_db,
                local_db,
                collection_name,
                start_date="2025-01-01"  # 작년 데이터까지 마이그레이션
            )
            total_migrated += migrated

        # 완료 후 샘플 데이터 출력
        print(f"\n{'='*60}")
        print_success("마이그레이션 완료!")
        print(f"{'='*60}")
        print(f"\n📈 전체 마이그레이션: {total_migrated} 건")

        for collection_name in collections:
            show_sample_data(local_db, collection_name, limit=2)

    except Exception as e:
        print_error(f"마이그레이션 실패: {e}")
        import traceback
        traceback.print_exc()
        exit(1)

    finally:
        # 연결 종료
        prod_client.close()
        local_client.close()
        print_info("\nMongoDB 연결 종료")


if __name__ == "__main__":
    main()
