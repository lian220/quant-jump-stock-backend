"""
로컬 MongoDB의 날짜 형식을 "YYYY-MM-DD" 문자열로 통일

사용법:
    python scripts/normalize_dates_in_local.py

환경변수:
    MONGODB_URI: 로컬 MongoDB URI (.env.local)
"""
import os
import sys
from pathlib import Path
from pymongo import MongoClient
from datetime import datetime
from typing import Any

# 프로젝트 루트에서 .env 파일 로드
try:
    from dotenv import dotenv_values
    project_root = Path(__file__).parent.parent.parent

    # .env.local에서 로컬 URI 로드
    local_env = dotenv_values(project_root / ".env.local")
    local_uri = local_env.get("MONGODB_URI", "")

    # Docker 컨테이너 이름 'mongodb'를 'localhost'로 치환
    local_uri = local_uri.replace("mongodb:27017", "localhost:27017")

    print(f"✅ .env.local 로드 완료: {project_root}")
except Exception as e:
    print(f"⚠️  .env 파일 로드 실패: {e}")
    local_uri = "mongodb://quantiq_user:quantiq_password@localhost:27017/stock_trading?authSource=admin"


# 색상 출력
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
        # 이미 문자열이면 그대로 반환
        # "YYYY-MM-DD HH:MM:SS" 형식이면 날짜 부분만 추출
        if len(date_value) > 10:
            return date_value[:10]
        return date_value
    else:
        return str(date_value)


def normalize_collection_dates(collection, collection_name: str):
    """
    컬렉션의 모든 문서의 날짜 형식을 "YYYY-MM-DD"로 통일

    Args:
        collection: MongoDB 컬렉션
        collection_name: 컬렉션 이름
    """
    print(f"\n{'='*60}")
    print_info(f"컬렉션 날짜 형식 통일: {collection_name}")
    print(f"{'='*60}")

    # 모든 문서 조회
    total_count = collection.count_documents({})
    print_info(f"총 문서 수: {total_count} 건")

    if total_count == 0:
        print_warning("문서가 없습니다")
        return 0

    # 날짜 형식이 datetime인 문서만 조회
    docs = list(collection.find({}, {"_id": 1, "ticker": 1, "date": 1}))

    updated_count = 0
    datetime_count = 0
    string_count = 0

    for doc in docs:
        doc_id = doc["_id"]
        date_value = doc.get("date")

        if date_value is None:
            continue

        # datetime 객체인 경우만 변환
        if isinstance(date_value, datetime):
            datetime_count += 1
            date_str = normalize_date(date_value)

            # 업데이트
            collection.update_one(
                {"_id": doc_id},
                {"$set": {"date": date_str}}
            )
            updated_count += 1

        elif isinstance(date_value, str):
            string_count += 1
            # 문자열이지만 시간이 포함된 경우 ("YYYY-MM-DD HH:MM:SS")
            if len(date_value) > 10:
                date_str = date_value[:10]
                collection.update_one(
                    {"_id": doc_id},
                    {"$set": {"date": date_str}}
                )
                updated_count += 1

    # 통계 출력
    print(f"\n📊 변환 통계:")
    print(f"   - 총 문서: {total_count} 건")
    print(f"   - datetime 타입: {datetime_count} 건")
    print(f"   - string 타입: {string_count} 건")
    print(f"   - 업데이트: {updated_count} 건")

    if updated_count > 0:
        print_success(f"{collection_name}: {updated_count} 건 날짜 형식 통일 완료")
    else:
        print_info(f"{collection_name}: 이미 모든 날짜가 문자열 형식입니다")

    return updated_count


def show_sample_dates(collection, collection_name: str, limit: int = 3):
    """샘플 데이터의 날짜 형식 확인"""
    print(f"\n📋 {collection_name} 샘플 날짜 형식:")

    docs = list(collection.find({}, {"ticker": 1, "date": 1}).limit(limit))

    for i, doc in enumerate(docs, 1):
        date_value = doc.get("date")
        date_type = type(date_value).__name__
        print(f"  [{i}] Ticker: {doc.get('ticker')}, Date: {date_value} (type: {date_type})")


def main():
    """메인 실행 함수"""
    print("\n" + "="*60)
    print("🔄 로컬 MongoDB 날짜 형식 통일")
    print("="*60)

    # MongoDB 연결
    try:
        print_info("로컬 MongoDB 연결 중...")
        client = MongoClient(local_uri, serverSelectionTimeoutMS=5000)
        client.admin.command('ping')
        db = client['stock_trading']
        print_success("로컬 MongoDB 연결 성공")
    except Exception as e:
        print_error(f"MongoDB 연결 실패: {e}")
        exit(1)

    try:
        # 통일할 컬렉션
        collections = [
            "sentiment_analysis",
            "stock_predictions",
            "stock_analysis"
        ]

        total_updated = 0

        for collection_name in collections:
            coll = db[collection_name]
            updated = normalize_collection_dates(coll, collection_name)
            total_updated += updated

        # 완료 후 샘플 데이터 확인
        print(f"\n{'='*60}")
        print_success("날짜 형식 통일 완료!")
        print(f"{'='*60}")
        print(f"\n📈 전체 업데이트: {total_updated} 건")

        for collection_name in collections:
            show_sample_dates(db[collection_name], collection_name, limit=2)

    except Exception as e:
        print_error(f"날짜 형식 통일 실패: {e}")
        import traceback
        traceback.print_exc()
        exit(1)

    finally:
        # 연결 종료
        client.close()
        print_info("\nMongoDB 연결 종료")


if __name__ == "__main__":
    main()
