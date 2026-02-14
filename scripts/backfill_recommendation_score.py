"""
기존 stock_recommendations 문서의 recommendation_score 백필 스크립트

기존 데이터에서 technical_indicators를 읽어 recommendation_score를 재계산합니다.

사용법:
    MONGODB_URI="mongodb://..." python scripts/backfill_recommendation_score.py
"""
import os
import sys
from pymongo import MongoClient

MONGODB_URI = os.getenv("MONGODB_URI")
if not MONGODB_URI:
    print("MONGODB_URI 환경 변수를 설정하세요.")
    sys.exit(1)

client = MongoClient(MONGODB_URI)
db = client["stock_trading"]
coll = db["stock_recommendations"]

updated = 0
skipped = 0

cursor = coll.find({"recommendation_score": {"$exists": False}})
for doc in cursor:
    tech = doc.get("technical_indicators") or {}
    rsi_val = tech.get("rsi")
    golden_cross = tech.get("golden_cross", False)
    macd_buy = tech.get("macd_buy_signal", False)

    if rsi_val is None:
        skipped += 1
        continue

    rsi_score = max(0.0, (50.0 - float(rsi_val)) / 50.0)
    macd_score = 1.0 if macd_buy else 0.0
    sma_score = 1.0 if golden_cross else 0.0
    recommendation_score = round((sma_score * 0.4) + (rsi_score * 0.3) + (macd_score * 0.3), 4)

    coll.update_one(
        {"_id": doc["_id"]},
        {"$set": {"recommendation_score": recommendation_score}}
    )
    updated += 1

# Also backfill documents where recommendation_score is null
cursor_null = coll.find({"recommendation_score": None})
for doc in cursor_null:
    tech = doc.get("technical_indicators") or {}
    rsi_val = tech.get("rsi")
    golden_cross = tech.get("golden_cross", False)
    macd_buy = tech.get("macd_buy_signal", False)

    if rsi_val is None:
        skipped += 1
        continue

    rsi_score = max(0.0, (50.0 - float(rsi_val)) / 50.0)
    macd_score = 1.0 if macd_buy else 0.0
    sma_score = 1.0 if golden_cross else 0.0
    recommendation_score = round((sma_score * 0.4) + (rsi_score * 0.3) + (macd_score * 0.3), 4)

    coll.update_one(
        {"_id": doc["_id"]},
        {"$set": {"recommendation_score": recommendation_score}}
    )
    updated += 1

print(f"Backfill complete: {updated} updated, {skipped} skipped")
client.close()
