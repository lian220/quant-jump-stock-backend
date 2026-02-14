#!/bin/bash
###############################################################################
# 추천 종목 데이터 백필 스크립트
#
# GCE VM에서 실행하여 최근 7일치 추천 데이터를 생성합니다.
# Usage: ./backfill_recommendations.sh
###############################################################################

set -e  # Exit on error

# 설정
API_HOST="http://localhost:10010"
ENDPOINT="/api/predictions/buy-signals"
MIN_CONFIDENCE="0.7"

# 현재 날짜 (GCE VM의 시스템 날짜 사용)
TODAY=$(date +%Y-%m-%d)
echo "======================================"
echo "📅 오늘 날짜: $TODAY"
echo "🔄 최근 7일치 추천 데이터 백필 시작..."
echo "======================================"

# 최근 7일 날짜 배열 생성
DATES=()
for i in {6..0}; do
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        DATE=$(date -v-${i}d +%Y-%m-%d)
    else
        # Linux (GCE VM)
        DATE=$(date -d "$TODAY - $i days" +%Y-%m-%d)
    fi
    DATES+=("$DATE")
done

# 각 날짜별로 API 호출
SUCCESS_COUNT=0
FAIL_COUNT=0

for DATE in "${DATES[@]}"; do
    echo ""
    echo "📊 날짜: $DATE 처리 중..."

    URL="${API_HOST}${ENDPOINT}?date=${DATE}&minConfidence=${MIN_CONFIDENCE}"

    # API 호출
    RESPONSE=$(curl -s -w "\n%{http_code}" "$URL")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
    BODY=$(echo "$RESPONSE" | sed '$d')

    if [ "$HTTP_CODE" -eq 200 ]; then
        # 응답에서 count 추출 (jq가 없을 경우 전체 응답 출력)
        if command -v jq &> /dev/null; then
            COUNT=$(echo "$BODY" | jq 'length')
            echo "✅ 성공 (HTTP $HTTP_CODE) - $COUNT개 종목 추천"
        else
            echo "✅ 성공 (HTTP $HTTP_CODE)"
            echo "$BODY" | head -c 200
            echo "..."
        fi
        ((SUCCESS_COUNT++))
    else
        echo "❌ 실패 (HTTP $HTTP_CODE)"
        echo "응답: $BODY"
        ((FAIL_COUNT++))
    fi

    # Rate limit 방지 (1초 대기)
    sleep 1
done

echo ""
echo "======================================"
echo "📊 백필 완료"
echo "✅ 성공: $SUCCESS_COUNT일"
echo "❌ 실패: $FAIL_COUNT일"
echo "======================================"

# 실패가 있으면 exit code 1
if [ $FAIL_COUNT -gt 0 ]; then
    exit 1
fi
