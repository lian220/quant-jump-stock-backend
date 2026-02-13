#!/usr/bin/env python3
"""MongoDB → PostgreSQL 수동 동기화 스크립트"""

import sys
import os

# 프로젝트 루트를 Python 경로에 추가
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'src'))

from application.recommendation.sync_service import RecommendationSyncService

if __name__ == "__main__":
    # 2026-02-02 데이터를 동기화 (모든 분석 데이터가 있는 마지막 날짜)
    analysis_date = "2026-02-02"

    print(f"🔄 Starting sync for {analysis_date}...")

    sync_service = RecommendationSyncService()
    result = sync_service.sync_latest_recommendations(analysis_date)

    print(f"✅ Sync completed!")
    print(f"📊 Result: {result}")
