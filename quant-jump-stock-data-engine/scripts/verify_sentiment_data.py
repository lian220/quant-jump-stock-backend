"""
감성 분석 데이터 로딩 검증

사용법:
    python scripts/verify_sentiment_data.py
"""
import os
import sys
from pathlib import Path
from datetime import date, timedelta

# 프로젝트 루트에서 .env 파일 로드
try:
    from dotenv import load_dotenv
    # quant-jump-stock-backend/.env.local 사용
    project_root = Path(__file__).parent.parent.parent
    env_file = project_root / ".env.local"
    if not env_file.exists():
        env_file = project_root / ".env"
    load_dotenv(env_file)

    # MongoDB URI 확인
    mongodb_uri = os.environ.get("MONGODB_URI")
    if mongodb_uri:
        # Docker 컨테이너 이름 'mongodb'를 'localhost'로 치환
        os.environ["MONGODB_URI"] = mongodb_uri.replace("mongodb:27017", "localhost:27017")
        print(f"✅ 환경 변수 로드: {env_file}")
    else:
        print(f"⚠️  MONGODB_URI not found in {env_file}")
except Exception as e:
    print(f"⚠️  .env 파일 로드 실패: {e}")

# src 경로 추가
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))

from application.backtest.data_loader_mongo import MongoDataLoader


def main():
    print("\n" + "="*80)
    print("🔍 감성 분석 데이터 로딩 검증")
    print("="*80)

    # MongoDataLoader 초기화
    loader = MongoDataLoader()

    # 테스트 종목 및 기간
    symbols = ["AAPL", "NVDA", "TSLA", "MSFT"]
    end_date = date.today()
    start_date = end_date - timedelta(days=90)  # 최근 3개월

    print(f"\n📅 기간: {start_date} ~ {end_date}")
    print(f"📊 종목: {symbols}")

    # 1. 기본 OHLCV만 로드
    print("\n" + "-"*80)
    print("1️⃣  기본 OHLCV 데이터 로드")
    print("-"*80)

    data_basic = loader.load(symbols, start_date, end_date)

    for symbol in symbols:
        if symbol in data_basic:
            df = data_basic[symbol]
            print(f"\n✅ {symbol}:")
            print(f"   - 데이터 행수: {len(df)}")
            print(f"   - 컬럼: {list(df.columns)}")
            print(f"   - 기간: {df.index.min()} ~ {df.index.max()}")

    # 2. 감성 분석 포함 로드
    print("\n" + "-"*80)
    print("2️⃣  감성 분석 데이터 포함 로드")
    print("-"*80)

    data_sentiment = loader.load(
        symbols,
        start_date,
        end_date,
        include_sentiment=True
    )

    for symbol in symbols:
        if symbol in data_sentiment:
            df = data_sentiment[symbol]
            print(f"\n✅ {symbol}:")
            print(f"   - 데이터 행수: {len(df)}")
            print(f"   - 컬럼: {list(df.columns)}")

            # 감성 점수 통계
            if "sentiment_score" in df.columns:
                sentiment_stats = df["sentiment_score"].describe()
                print(f"\n   📊 sentiment_score 통계:")
                print(f"      - 평균: {sentiment_stats['mean']:.4f}")
                print(f"      - 최소: {sentiment_stats['min']:.4f}")
                print(f"      - 최대: {sentiment_stats['max']:.4f}")
                print(f"      - 중앙값: {sentiment_stats['50%']:.4f}")

                # 감성 점수 > 0 인 날짜 비율
                positive_sentiment = (df["sentiment_score"] > 0).sum()
                print(f"      - 감성 점수 > 0: {positive_sentiment}/{len(df)} ({positive_sentiment/len(df)*100:.1f}%)")

                # 감성 점수 > 0.2 인 날짜
                high_sentiment = df[df["sentiment_score"] > 0.2]
                if len(high_sentiment) > 0:
                    print(f"\n   🔥 sentiment_score > 0.2인 날짜 ({len(high_sentiment)}일):")
                    for idx, row in high_sentiment.head(5).iterrows():
                        print(f"      - {idx.date()}: score={row['sentiment_score']:.4f}, count={row['sentiment_count']}")

    # 3. 추천 데이터 포함 로드
    print("\n" + "-"*80)
    print("3️⃣  추천 데이터 포함 로드")
    print("-"*80)

    data_recommendations = loader.load(
        symbols,
        start_date,
        end_date,
        include_recommendations=True
    )

    for symbol in symbols:
        if symbol in data_recommendations:
            df = data_recommendations[symbol]
            print(f"\n✅ {symbol}:")

            # 추천 여부 통계
            if "is_recommended" in df.columns:
                recommended_count = df["is_recommended"].sum()
                print(f"   - 추천된 날짜: {recommended_count}/{len(df)} ({recommended_count/len(df)*100:.1f}%)")

                if recommended_count > 0:
                    recommended_days = df[df["is_recommended"] == True]
                    print(f"\n   ⭐ 추천된 날짜 (최대 5개):")
                    for idx, row in recommended_days.head(5).iterrows():
                        print(f"      - {idx.date()}: rec_rsi={row['rec_rsi']:.2f}, rec_score={row['rec_score']:.2f}")

    # 4. 전체 지표 포함 로드
    print("\n" + "-"*80)
    print("4️⃣  전체 지표 포함 로드 (OHLCV + Sentiment + Recommendations)")
    print("-"*80)

    data_all = loader.load(
        symbols,
        start_date,
        end_date,
        include_sentiment=True,
        include_recommendations=True
    )

    for symbol in symbols:
        if symbol in data_all:
            df = data_all[symbol]
            print(f"\n✅ {symbol}:")
            print(f"   - 총 컬럼 수: {len(df.columns)}")
            print(f"   - 컬럼: {list(df.columns)}")
            print(f"\n   샘플 데이터 (최근 3일):")
            print(df.tail(3).to_string())

    # 5. SCREENING 조건 시뮬레이션
    print("\n" + "-"*80)
    print("5️⃣  SCREENING 조건 시뮬레이션")
    print("-"*80)

    print("\n조건: sentiment_score > 0.2 AND sentiment_count >= 10")

    screening_results = []

    for symbol in symbols:
        if symbol in data_all:
            df = data_all[symbol]

            # SCREENING 조건 적용
            filtered = df[
                (df["sentiment_score"] > 0.2) &
                (df["sentiment_count"] >= 10)
            ]

            if len(filtered) > 0:
                screening_results.append({
                    "symbol": symbol,
                    "matched_days": len(filtered),
                    "total_days": len(df),
                    "percentage": len(filtered) / len(df) * 100
                })

                print(f"\n✅ {symbol}: {len(filtered)}/{len(df)} 일 ({len(filtered)/len(df)*100:.1f}%)")
                print(f"   매칭된 날짜 (최대 5개):")
                for idx, row in filtered.head(5).iterrows():
                    print(f"   - {idx.date()}: score={row['sentiment_score']:.4f}, count={int(row['sentiment_count'])}, close={row['close']:.2f}")

    if not screening_results:
        print("\n⚠️  조건을 만족하는 데이터가 없습니다.")
        print("💡 조건을 완화하거나 다른 지표를 사용해보세요:")
        print("   - sentiment_score > 0.1")
        print("   - sentiment_count >= 5")
    else:
        print(f"\n📊 SCREENING 결과: {len(screening_results)}개 종목이 조건 충족")

    # 연결 종료
    loader.close()

    print("\n" + "="*80)
    print("✅ 검증 완료!")
    print("="*80)


if __name__ == "__main__":
    main()
