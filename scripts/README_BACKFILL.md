# 추천 종목 백필 가이드

## GCE VM에서 최근 7일치 추천 데이터 생성하기

### 방법 1: 스크립트 복사 후 실행 (권장)

```bash
# 1. 로컬에서 GCE VM 이름 확인
gcloud compute instances list

# 2. 스크립트를 VM에 복사 (VM 이름을 실제 이름으로 변경)
gcloud compute scp scripts/backfill_recommendations.sh <VM_NAME>:/tmp/

# 3. SSH 접속
gcloud compute ssh <VM_NAME>

# 4. VM에서 스크립트 실행
chmod +x /tmp/backfill_recommendations.sh
/tmp/backfill_recommendations.sh
```

### 방법 2: VM에 직접 접속해서 실행

```bash
# 1. SSH 접속
gcloud compute ssh <VM_NAME>

# 2. 스크립트 직접 생성
cat > /tmp/backfill_recommendations.sh << 'SCRIPT_EOF'
#!/bin/bash
set -e

API_HOST="http://localhost:10010"
ENDPOINT="/api/predictions/buy-signals"
MIN_CONFIDENCE="0.7"

TODAY=$(date +%Y-%m-%d)
echo "======================================"
echo "📅 오늘 날짜: $TODAY"
echo "🔄 최근 7일치 추천 데이터 백필 시작..."
echo "======================================"

# 최근 7일 날짜 배열 생성
DATES=()
for i in {6..0}; do
    DATE=$(date -d "$TODAY - $i days" +%Y-%m-%d)
    DATES+=("$DATE")
done

# 각 날짜별로 API 호출
SUCCESS_COUNT=0
FAIL_COUNT=0

for DATE in "${DATES[@]}"; do
    echo ""
    echo "📊 날짜: $DATE 처리 중..."

    URL="${API_HOST}${ENDPOINT}?date=${DATE}&minConfidence=${MIN_CONFIDENCE}"
    RESPONSE=$(curl -s -w "\n%{http_code}" "$URL")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
    BODY=$(echo "$RESPONSE" | sed '$d')

    if [ "$HTTP_CODE" -eq 200 ]; then
        if command -v jq &> /dev/null; then
            COUNT=$(echo "$BODY" | jq 'length')
            echo "✅ 성공 (HTTP $HTTP_CODE) - $COUNT개 종목 추천"
        else
            echo "✅ 성공 (HTTP $HTTP_CODE)"
            echo "$BODY" | head -c 200
            echo "..."
        fi
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    else
        echo "❌ 실패 (HTTP $HTTP_CODE)"
        echo "응답: $BODY"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi

    sleep 1
done

echo ""
echo "======================================"
echo "📊 백필 완료"
echo "✅ 성공: $SUCCESS_COUNT일"
echo "❌ 실패: $FAIL_COUNT일"
echo "======================================"

if [ $FAIL_COUNT -gt 0 ]; then
    exit 1
fi
SCRIPT_EOF

# 3. 실행 권한 부여
chmod +x /tmp/backfill_recommendations.sh

# 4. 실행
/tmp/backfill_recommendations.sh
```

## 예상 출력

```
======================================
📅 오늘 날짜: 2026-02-14
🔄 최근 7일치 추천 데이터 백필 시작...
======================================

📊 날짜: 2026-02-08 처리 중...
✅ 성공 (HTTP 200) - 5개 종목 추천

📊 날짜: 2026-02-09 처리 중...
✅ 성공 (HTTP 200) - 7개 종목 추천

...

======================================
📊 백필 완료
✅ 성공: 7일
❌ 실패: 0일
======================================
```

## 문제 해결

### Core API가 실행 중이 아닌 경우

```bash
# VM에서 Docker 상태 확인
cd /opt/quant-jump-stock
sudo docker compose -f docker-compose.prod.yml ps

# Core API 재시작
sudo ./deploy/deploy.sh restart
```

### 데이터가 생성되지 않는 경우

```bash
# Core API 로그 확인
cd /opt/quant-jump-stock
sudo docker compose -f docker-compose.prod.yml logs -f qjs-core

# PostgreSQL 직접 확인
sudo docker compose -f docker-compose.prod.yml exec postgresql psql -U quantiq_user -d quantiq
SELECT COUNT(*) FROM prediction_results WHERE date >= CURRENT_DATE - INTERVAL '7 days';
\q
```

## API 상세

- **엔드포인트**: `GET /api/predictions/buy-signals`
- **파라미터**:
  - `date` (optional): 조회 날짜 (YYYY-MM-DD), 미지정 시 오늘
  - `minConfidence` (default: 0.7): 최소 신뢰도
- **응답**: 추천 종목 배열 (ticker, confidence, score 등)
