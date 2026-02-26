"""
MongoDB 프로덕션 데이터 품질 검증 스크립트

5개 컬렉션의 백테스트 활용 가능성을 실제 데이터로 검증합니다.

검증 항목:
1. stock_predictions: ML 예측 방향 정확도 (Direction Accuracy)
2. sentiment_analysis: 감정 점수 ↔ 실제 수익률 상관관계
3. stock_recommendations: 추천 종목 실제 수익률
4. stock_analysis_results: 추천(BUY/SELL) 정확도
5. daily_stock_data: FRED 경제지표 존재 여부

사용법:
    cd quant-jump-stock-data-engine
    python scripts/validate_data_quality.py
"""
import os
import sys
from pathlib import Path
from collections import defaultdict
from datetime import datetime

# .env 로드
try:
    from dotenv import load_dotenv
    project_root = Path(__file__).parent.parent.parent
    for env_name in [".env.db.prod", ".env.db.local", ".env.local", ".env.common"]:
        env_file = project_root / env_name
        if env_file.exists():
            load_dotenv(env_file, override=False)
            print(f"  env: {env_name}")

    mongodb_uri = os.environ.get("MONGODB_URI", "")
    if "mongodb:" in mongodb_uri and "localhost" not in mongodb_uri and "mongodb+srv" not in mongodb_uri:
        os.environ["MONGODB_URI"] = mongodb_uri.replace("mongodb:27017", "localhost:27017")
except Exception as e:
    print(f"  env 로드 실패: {e}")

from pymongo import MongoClient
import numpy as np


def get_db():
    uri = os.environ.get("MONGODB_URI")
    if not uri:
        raise ValueError("MONGODB_URI 환경변수 없음")
    client = MongoClient(uri)
    return client["stock_trading"], client


def print_header(title: str):
    print(f"\n{'='*80}")
    print(f"  {title}")
    print(f"{'='*80}")


def print_section(title: str):
    print(f"\n{'─'*60}")
    print(f"  {title}")
    print(f"{'─'*60}")


def print_verdict(name: str, score: float, threshold: float, unit: str = "%"):
    if score >= threshold:
        icon = "✅ 활용 가능"
    elif score >= threshold * 0.85:
        icon = "⚠️  경계선"
    else:
        icon = "❌ 활용 불가"
    print(f"\n  >>> {icon}: {name} = {score:.2f}{unit} (기준: {threshold}{unit})")


def build_price_index(db):
    """daily_stock_data에서 (ticker, date_str) → close 인덱스 구축"""
    print("  주가 인덱스 구축 중...")
    coll = db["daily_stock_data"]
    docs = list(coll.find({}, {"date": 1, "stocks": 1}).sort("date", 1))

    # date_str → {ticker: close}
    date_prices = {}
    sorted_dates = []

    for doc in docs:
        d = str(doc["date"])[:10]  # datetime or string → "YYYY-MM-DD"
        stocks = doc.get("stocks", {})
        prices = {}
        for ticker, data in stocks.items():
            close = data.get("close") or data.get("close_price")
            if close is not None:
                prices[ticker] = float(close)
        date_prices[d] = prices
        sorted_dates.append(d)

    sorted_dates = sorted(set(sorted_dates))
    date_to_idx = {d: i for i, d in enumerate(sorted_dates)}
    print(f"  → {len(sorted_dates)}일, {len(set(t for p in date_prices.values() for t in p))} 종목")
    return date_prices, sorted_dates, date_to_idx


# ═══════════════════════════════════════════════════════════════
# 검증 1: stock_predictions - ML 예측 방향 정확도
# ═══════════════════════════════════════════════════════════════
def validate_predictions(db, date_prices, sorted_dates, date_to_idx):
    print_header("검증 1: stock_predictions - ML 예측 방향 정확도")

    coll = db["stock_predictions"]
    total_count = coll.count_documents({})
    print(f"\n  총 레코드: {total_count:,}")

    if total_count == 0:
        print("  ❌ 데이터 없음")
        return

    # 데이터 로드
    docs = list(coll.find({}, {
        "date": 1, "ticker": 1, "predicted_price": 1,
        "actual_price": 1, "forecast_horizon": 1
    }))

    # 방향 정확도: predicted_price > actual_price → 모델이 "가격이 더 높아야" 라고 예측
    # 실제 14일 후 가격이 actual_price보다 올랐는지 확인
    print_section("1-1. 14일 후 방향 정확도 (predicted vs actual future)")

    correct = 0
    total = 0
    ticker_stats = defaultdict(lambda: {"correct": 0, "total": 0})

    for doc in docs:
        ticker = doc.get("ticker")
        d = str(doc.get("date", ""))[:10]
        predicted = doc.get("predicted_price")
        actual_now = doc.get("actual_price")
        horizon = doc.get("forecast_horizon", 14)

        if not all([ticker, d, predicted, actual_now]):
            continue

        predicted = float(predicted)
        actual_now = float(actual_now)

        if actual_now <= 0:
            continue

        # 14거래일 후 실제 가격 찾기
        if d not in date_to_idx:
            continue
        idx = date_to_idx[d]
        future_idx = idx + horizon
        if future_idx >= len(sorted_dates):
            continue

        future_date = sorted_dates[future_idx]
        future_price = date_prices.get(future_date, {}).get(ticker)
        if future_price is None:
            continue

        # 방향 비교
        predicted_up = predicted > actual_now
        actual_up = future_price > actual_now

        total += 1
        ticker_stats[ticker]["total"] += 1
        if predicted_up == actual_up:
            correct += 1
            ticker_stats[ticker]["correct"] += 1

    if total > 0:
        acc = correct / total * 100
        print(f"\n  전체 방향 정확도: {correct}/{total} = {acc:.1f}% (랜덤 기준: 50%)")
        print_verdict("Direction Accuracy", acc, 55)
    else:
        print("  ❌ 매칭 가능한 데이터 없음 (날짜 범위 불일치)")
        # 날짜 범위 디버깅
        pred_dates = [str(d.get("date", ""))[:10] for d in docs[:5]]
        print(f"  predictions 날짜 샘플: {pred_dates}")
        print(f"  daily_stock_data 날짜 범위: {sorted_dates[0]} ~ {sorted_dates[-1]}")
        return

    # 종목별
    print_section("1-2. 종목별 Direction Accuracy")
    print(f"\n  {'종목':<8} {'정확도':>8} {'맞음/전체':>12}")
    print(f"  {'-'*32}")

    good_tickers = []
    for ticker, stats in sorted(ticker_stats.items(), key=lambda x: -x[1]["total"]):
        if stats["total"] >= 10:
            t_acc = stats["correct"] / stats["total"] * 100
            marker = "✅" if t_acc >= 55 else "❌"
            print(f"  {ticker:<8} {t_acc:>7.1f}% {stats['correct']:>4}/{stats['total']:<4} {marker}")
            if t_acc >= 55:
                good_tickers.append((ticker, t_acc))

    if good_tickers:
        print(f"\n  활용 가능 종목 ({len(good_tickers)}개): {', '.join(t for t,_ in good_tickers)}")
    else:
        print(f"\n  55% 이상 종목 없음")

    # 예측 강도별 (|predicted - actual| / actual 크기별)
    print_section("1-3. 예측 강도별 정확도")

    bins = [(0, 0.02, "약 (0~2%)"), (0.02, 0.05, "보통 (2~5%)"),
            (0.05, 0.10, "강 (5~10%)"), (0.10, 1.0, "매우 강 (10%+)")]

    for low, high, label in bins:
        bc, bt = 0, 0
        for doc in docs:
            ticker = doc.get("ticker")
            d = str(doc.get("date", ""))[:10]
            predicted = doc.get("predicted_price")
            actual_now = doc.get("actual_price")
            if not all([ticker, d, predicted, actual_now]):
                continue
            predicted, actual_now = float(predicted), float(actual_now)
            if actual_now <= 0 or d not in date_to_idx:
                continue
            strength = abs(predicted - actual_now) / actual_now
            if not (low <= strength < high):
                continue
            idx = date_to_idx[d]
            fi = idx + 14
            if fi >= len(sorted_dates):
                continue
            fp = date_prices.get(sorted_dates[fi], {}).get(ticker)
            if fp is None:
                continue
            bt += 1
            if (predicted > actual_now) == (fp > actual_now):
                bc += 1
        if bt > 0:
            ba = bc / bt * 100
            marker = "✅" if ba >= 55 else "❌"
            print(f"  {label:<20} {ba:>6.1f}% ({bc}/{bt}) {marker}")
        else:
            print(f"  {label:<20} 데이터 없음")


# ═══════════════════════════════════════════════════════════════
# 검증 2: sentiment_analysis - 감정↔수익률
# ═══════════════════════════════════════════════════════════════
def validate_sentiment(db, date_prices, sorted_dates, date_to_idx):
    print_header("검증 2: sentiment_analysis - 감정 점수 ↔ 수익률")

    coll = db["sentiment_analysis"]
    total_count = coll.count_documents({})
    print(f"\n  총 레코드: {total_count:,}")

    if total_count == 0:
        print("  ❌ 데이터 없음")
        return

    docs = list(coll.find({}, {
        "date": 1, "ticker": 1, "average_sentiment_score": 1, "article_count": 1
    }))

    # 상관관계: sentiment → forward return
    print_section("2-1. 감정 점수 ↔ N일 후 수익률 상관관계")

    for forward_days in [1, 3, 5, 10]:
        scores, returns = [], []
        for doc in docs:
            ticker = doc.get("ticker")
            d = str(doc.get("date", ""))[:10]
            score = doc.get("average_sentiment_score")
            if not all([ticker, d]) or score is None:
                continue
            score = float(score)
            if d not in date_to_idx:
                continue
            idx = date_to_idx[d]
            fi = idx + forward_days
            if fi >= len(sorted_dates):
                continue
            cp = date_prices.get(d, {}).get(ticker)
            fp = date_prices.get(sorted_dates[fi], {}).get(ticker)
            if cp and fp and cp > 0:
                ret = (fp - cp) / cp * 100
                scores.append(score)
                returns.append(ret)

        if len(scores) >= 10:
            corr = np.corrcoef(scores, returns)[0][1]
            marker = "✅" if abs(corr) >= 0.05 else "❌"
            print(f"  {forward_days:>2}일 후: corr = {corr:>8.4f}  (N={len(scores):,}) {marker}")
        else:
            print(f"  {forward_days:>2}일 후: 데이터 부족 ({len(scores)}건)")

    # 분위별 수익률
    print_section("2-2. 감정 점수 분위별 다음날 평균 수익률")

    pairs = []
    for doc in docs:
        ticker = doc.get("ticker")
        d = str(doc.get("date", ""))[:10]
        score = doc.get("average_sentiment_score")
        if not all([ticker, d]) or score is None:
            continue
        if d not in date_to_idx:
            continue
        idx = date_to_idx[d]
        if idx + 1 >= len(sorted_dates):
            continue
        cp = date_prices.get(d, {}).get(ticker)
        nd = sorted_dates[idx + 1]
        np_ = date_prices.get(nd, {}).get(ticker)
        if cp and np_ and cp > 0:
            pairs.append((float(score), (np_ - cp) / cp * 100))

    if len(pairs) < 20:
        print("  ❌ 데이터 부족")
        return

    pairs.sort(key=lambda x: x[0])
    n = len(pairs)
    qs = n // 5

    print(f"\n  {'분위':<15} {'감정 범위':>22} {'평균 수익률':>12} {'N':>6}")
    print(f"  {'-'*60}")

    qr = []
    for i, label in enumerate(["하위 20%", "20~40%", "40~60%", "60~80%", "상위 20%"]):
        start = i * qs
        end = (i + 1) * qs if i < 4 else n
        subset = pairs[start:end]
        s_vals = [s for s, r in subset]
        r_vals = [r for s, r in subset]
        avg = np.mean(r_vals)
        qr.append(avg)
        print(f"  {label:<15} {min(s_vals):>8.4f} ~ {max(s_vals):>8.4f} {avg:>10.4f}%  {len(subset):>5}")

    spread = qr[-1] - qr[0]
    print(f"\n  상위-하위 수익률 차이: {spread:+.4f}%p")
    print_verdict("감정→수익률 스프레드", abs(spread), 0.05, "%p")


# ═══════════════════════════════════════════════════════════════
# 검증 3: stock_recommendations - 추천 종목 수익률
# ═══════════════════════════════════════════════════════════════
def validate_recommendations(db, date_prices, sorted_dates, date_to_idx):
    print_header("검증 3: stock_recommendations - 추천 종목 수익률")

    coll = db["stock_recommendations"]
    total_count = coll.count_documents({})
    print(f"\n  총 레코드: {total_count:,}")

    if total_count == 0:
        print("  ❌ 데이터 없음")
        return

    docs = list(coll.find({}, {
        "date": 1, "ticker": 1, "is_recommended": 1, "technical_indicators": 1
    }))

    # 추천 vs 비추천 수익률 (1일, 5일, 10일)
    for fd in [1, 5, 10]:
        print_section(f"3-{fd}. 추천 vs 비추천 {fd}일 후 수익률")

        rec_returns = []
        not_rec_returns = []

        for doc in docs:
            ticker = doc.get("ticker")
            d = str(doc.get("date", ""))[:10]
            is_rec = doc.get("is_recommended", False)
            if not ticker or not d or d not in date_to_idx:
                continue
            idx = date_to_idx[d]
            fi = idx + fd
            if fi >= len(sorted_dates):
                continue
            cp = date_prices.get(d, {}).get(ticker)
            fp = date_prices.get(sorted_dates[fi], {}).get(ticker)
            if cp and fp and cp > 0:
                ret = (fp - cp) / cp * 100
                if is_rec:
                    rec_returns.append(ret)
                else:
                    not_rec_returns.append(ret)

        if rec_returns and not_rec_returns:
            rec_avg = np.mean(rec_returns)
            not_avg = np.mean(not_rec_returns)
            diff = rec_avg - not_avg
            rec_wr = sum(1 for r in rec_returns if r > 0) / len(rec_returns) * 100
            not_wr = sum(1 for r in not_rec_returns if r > 0) / len(not_rec_returns) * 100
            print(f"\n  추천   : 평균 {rec_avg:>+8.4f}%  승률 {rec_wr:.1f}%  (N={len(rec_returns):,})")
            print(f"  비추천 : 평균 {not_avg:>+8.4f}%  승률 {not_wr:.1f}%  (N={len(not_rec_returns):,})")
            print(f"  차이   : {diff:>+8.4f}%p")
            print_verdict(f"추천 {fd}일 수익률 차이", diff, 0.05, "%p")
        else:
            print(f"  추천: {len(rec_returns)}건, 비추천: {len(not_rec_returns)}건")

    # RSI 분위별 수익률 (technical_indicators.rsi)
    print_section("3-R. RSI 분위별 5일 후 수익률")

    rsi_pairs = []
    for doc in docs:
        ticker = doc.get("ticker")
        d = str(doc.get("date", ""))[:10]
        ti = doc.get("technical_indicators", {})
        rsi = ti.get("rsi") if ti else None
        if not all([ticker, d]) or rsi is None or d not in date_to_idx:
            continue
        idx = date_to_idx[d]
        if idx + 5 >= len(sorted_dates):
            continue
        cp = date_prices.get(d, {}).get(ticker)
        fp = date_prices.get(sorted_dates[idx + 5], {}).get(ticker)
        if cp and fp and cp > 0:
            rsi_pairs.append((float(rsi), (fp - cp) / cp * 100))

    if len(rsi_pairs) >= 20:
        rsi_pairs.sort(key=lambda x: x[0])
        n = len(rsi_pairs)
        qs = n // 5
        print(f"\n  {'RSI 구간':<20} {'평균 수익률':>12} {'N':>6}")
        print(f"  {'-'*42}")
        for i, label in enumerate(["과매도 (최저)", "저RSI", "중립", "고RSI", "과매수 (최고)"]):
            start = i * qs
            end = (i + 1) * qs if i < 4 else n
            subset = rsi_pairs[start:end]
            avg = np.mean([r for _, r in subset])
            rsi_range = f"{min(s for s,_ in subset):.0f}~{max(s for s,_ in subset):.0f}"
            print(f"  {label:<14} ({rsi_range:>7}) {avg:>10.4f}%  {len(subset):>5}")
    else:
        print(f"  RSI 데이터 부족 ({len(rsi_pairs)}건)")


# ═══════════════════════════════════════════════════════════════
# 검증 4: stock_analysis_results - BUY/SELL 추천 정확도
# ═══════════════════════════════════════════════════════════════
def validate_analysis_results(db, date_prices, sorted_dates, date_to_idx):
    print_header("검증 4: stock_analysis_results - BUY/SELL 추천 정확도")

    coll = db["stock_analysis_results"]
    total_count = coll.count_documents({})
    print(f"\n  총 레코드: {total_count:,}")

    if total_count == 0:
        print("  ❌ 데이터 없음")
        return

    docs = list(coll.find({}, {
        "date": 1, "ticker": 1, "recommendation": 1,
        "predictions": 1, "metrics": 1
    }))

    # BUY/SELL 추천 정확도
    for fd in [5, 10, 14]:
        print_section(f"4-{fd}. BUY/SELL 추천 → {fd}일 후 수익률")

        buy_returns = []
        sell_returns = []

        for doc in docs:
            ticker = doc.get("ticker")
            d = str(doc.get("date", ""))[:10]
            rec = doc.get("recommendation", "").upper()
            if not ticker or not d or rec not in ("BUY", "SELL"):
                continue
            if d not in date_to_idx:
                continue
            idx = date_to_idx[d]
            fi = idx + fd
            if fi >= len(sorted_dates):
                continue
            cp = date_prices.get(d, {}).get(ticker)
            fp = date_prices.get(sorted_dates[fi], {}).get(ticker)
            if cp and fp and cp > 0:
                ret = (fp - cp) / cp * 100
                if rec == "BUY":
                    buy_returns.append(ret)
                else:
                    sell_returns.append(ret)

        if buy_returns or sell_returns:
            if buy_returns:
                buy_avg = np.mean(buy_returns)
                buy_wr = sum(1 for r in buy_returns if r > 0) / len(buy_returns) * 100
                print(f"  BUY  추천: 평균 {buy_avg:>+8.4f}%  승률 {buy_wr:.1f}%  (N={len(buy_returns)})")
            if sell_returns:
                sell_avg = np.mean(sell_returns)
                sell_wr = sum(1 for r in sell_returns if r < 0) / len(sell_returns) * 100
                print(f"  SELL 추천: 평균 {sell_avg:>+8.4f}%  하락률 {sell_wr:.1f}%  (N={len(sell_returns)})")
            if buy_returns and sell_returns:
                diff = np.mean(buy_returns) - np.mean(sell_returns)
                print(f"  BUY-SELL 차이: {diff:>+8.4f}%p")
                print_verdict(f"BUY/SELL {fd}일 구분력", diff, 0.1, "%p")
        else:
            print("  ❌ 매칭 데이터 없음")

    # rise_probability 상관관계
    print_section("4-P. rise_probability ↔ 실제 수익률")

    pairs = []
    for doc in docs:
        ticker = doc.get("ticker")
        d = str(doc.get("date", ""))[:10]
        preds = doc.get("predictions", {})
        rp = preds.get("rise_probability") if preds else None
        if not all([ticker, d]) or rp is None or d not in date_to_idx:
            continue
        idx = date_to_idx[d]
        if idx + 14 >= len(sorted_dates):
            continue
        cp = date_prices.get(d, {}).get(ticker)
        fp = date_prices.get(sorted_dates[idx + 14], {}).get(ticker)
        if cp and fp and cp > 0:
            pairs.append((float(rp), (fp - cp) / cp * 100))

    if len(pairs) >= 10:
        corr = np.corrcoef([p[0] for p in pairs], [p[1] for p in pairs])[0][1]
        marker = "✅" if abs(corr) >= 0.1 else "❌"
        print(f"  rise_probability ↔ 14일 수익률: corr = {corr:.4f}  (N={len(pairs)}) {marker}")
    else:
        print(f"  데이터 부족 ({len(pairs)}건)")

    # accuracy (MAPE 기반) 분포
    print_section("4-M. 모델 accuracy (100-MAPE) 분포")
    accuracies = []
    for doc in docs:
        m = doc.get("metrics", {})
        if m and m.get("accuracy") is not None:
            accuracies.append(float(m["accuracy"]))

    if accuracies:
        arr = np.array(accuracies)
        print(f"  평균: {np.mean(arr):.2f}%")
        print(f"  중앙값: {np.median(arr):.2f}%")
        print(f"  최소~최대: {np.min(arr):.2f}% ~ {np.max(arr):.2f}%")
        print(f"  표준편차: {np.std(arr):.2f}%")
        print(f"\n  ⚠️  이 accuracy는 100-MAPE(가격 근접도)이며 방향 정확도가 아님!")


# ═══════════════════════════════════════════════════════════════
# 검증 5: FRED 경제지표 존재 여부
# ═══════════════════════════════════════════════════════════════
def validate_fred(db):
    print_header("검증 5: daily_stock_data - FRED 경제지표")

    coll = db["daily_stock_data"]

    # fred_indicators가 있는 문서 수
    fred_count = coll.count_documents({
        "fred_indicators": {"$exists": True, "$ne": None, "$ne": {}}
    })
    total = coll.count_documents({})
    print(f"\n  전체 일수: {total:,}")
    print(f"  FRED 데이터 있는 일수: {fred_count:,}")

    if fred_count == 0:
        # null이 아닌 빈 dict인지 확인
        has_field = coll.count_documents({"fred_indicators": {"$exists": True}})
        print(f"  fred_indicators 필드 존재: {has_field:,}건 (값: null 또는 {{}})")

        # 가장 오래된 문서에서 확인
        old_doc = coll.find_one(sort=[("date", 1)])
        if old_doc:
            fred = old_doc.get("fred_indicators")
            print(f"  가장 오래된 문서({old_doc.get('date')}): fred = {type(fred).__name__} / {repr(fred)[:50]}")

        # yfinance_indicators 확인 (대안)
        print_section("5-Y. yfinance_indicators (대안 경제지표)")
        doc = coll.find_one(
            {"yfinance_indicators": {"$exists": True, "$ne": None}},
            sort=[("date", -1)]
        )
        if doc:
            yf = doc.get("yfinance_indicators", {})
            print(f"  최신 날짜: {doc.get('date')}")
            print(f"  지표 수: {len(yf)}")
            for k in sorted(yf.keys()):
                data = yf[k]
                if isinstance(data, dict):
                    name = data.get("name", "")
                    close = data.get("close") or data.get("close_price")
                    print(f"    {k:<12} close={close}  ({name})")
                else:
                    print(f"    {k:<12} {data}")

        print("\n  ❌ FRED 데이터 미존재 → 레짐 분류에 yfinance_indicators 활용 필요")
    else:
        doc = coll.find_one(
            {"fred_indicators": {"$exists": True, "$ne": None, "$ne": {}}},
            sort=[("date", -1)]
        )
        if doc:
            fred = doc["fred_indicators"]
            print(f"\n  최신 FRED 날짜: {doc.get('date')}")
            print(f"  지표 수: {len(fred)}")
            for k in sorted(fred.keys())[:10]:
                print(f"    {k}: {fred[k]}")
        print("\n  ✅ FRED 데이터 활용 가능")


# ═══════════════════════════════════════════════════════════════
def main():
    print("\n" + "█" * 80)
    print("█  MongoDB 프로덕션 데이터 품질 검증")
    print("█" * 80)

    db, client = get_db()

    try:
        client.admin.command("ping")
        print("\n  ✅ MongoDB 연결 성공 (Atlas)")
    except Exception as e:
        print(f"\n  ❌ MongoDB 연결 실패: {e}")
        return

    # 컬렉션 개요
    print_section("컬렉션 개요")
    for name in ["daily_stock_data", "stock_predictions", "stock_analysis_results",
                  "stock_recommendations", "sentiment_analysis", "news"]:
        count = db[name].count_documents({})
        print(f"  {name:<30} {count:>10,}건")

    # 주가 인덱스 구축 (한 번만)
    date_prices, sorted_dates, date_to_idx = build_price_index(db)

    # 검증 실행
    validate_predictions(db, date_prices, sorted_dates, date_to_idx)
    validate_sentiment(db, date_prices, sorted_dates, date_to_idx)
    validate_recommendations(db, date_prices, sorted_dates, date_to_idx)
    validate_analysis_results(db, date_prices, sorted_dates, date_to_idx)
    validate_fred(db)

    # 종합
    print_header("종합 판단 기준")
    print("""
  ┌───────────────────────────────────────────────────────────────────┐
  │ Direction Accuracy > 55%     → ML 예측 약한 시그널로 활용        │
  │ Direction Accuracy > 60%     → ML 예측 주요 시그널로 활용        │
  │ 감정 상관계수 |r| > 0.05    → 감정 필터 보조 시그널             │
  │ 분위별 수익률 차이 > 0.1%p  → Score 기반 종목 선정              │
  │ BUY-SELL 차이 > 0.1%p       → 추천 시스템 활용 가능             │
  │ FRED 데이터 존재             → 레짐 분류 즉시 구현              │
  └───────────────────────────────────────────────────────────────────┘
""")

    client.close()
    print("  ✅ 검증 완료\n")


if __name__ == "__main__":
    main()
