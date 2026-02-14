#!/usr/bin/env python3
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '../quant-jump-stock-data-engine/src'))

from core.database import MongoDB
from datetime import datetime, timedelta

db = MongoDB.get_db()

# 최근 7일 날짜별 카운트
end_date = datetime.now()
start_date = end_date - timedelta(days=7)

print(f"기간: {start_date.strftime('%Y-%m-%d')} ~ {end_date.strftime('%Y-%m-%d')}")
print()

# 날짜별 그룹핑
pipeline = [
    {"$match": {"date": {"$gte": start_date, "$lte": end_date}}},
    {"$group": {
        "_id": {"$dateToString": {"format": "%Y-%m-%d", "date": "$date"}},
        "count": {"$sum": 1},
        "tickers": {"$addToSet": "$ticker"}
    }},
    {"$sort": {"_id": -1}}
]

results = list(db.stock_predictions.aggregate(pipeline))

print("MongoDB stock_predictions 최근 7일:")
for r in results:
    print(f"  {r['_id']}: {r['count']}개 (종목 {len(r['tickers'])}개)")

print()
print(f"총 {len(results)}일치 데이터 있음")
