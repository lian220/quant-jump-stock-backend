#!/bin/bash
# GCS에 ML 패키지 업로드 후 Vertex AI 실행

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
DATA_ENGINE_DIR="$BACKEND_DIR/quant-jump-stock-data-engine"

echo "=========================================="
echo "🚀 ML 패키지 GCS 업로드 및 Vertex AI 실행"
echo "=========================================="
echo ""

# 1. 환경 변수 로드
ENV_FILE="${ENV_FILE:-$BACKEND_DIR/.env.local}"
if [ ! -f "$ENV_FILE" ]; then
    echo "❌ 오류: $ENV_FILE 파일을 찾을 수 없습니다."
    exit 1
fi

echo "📋 환경변수 로드: $ENV_FILE"
set -a
source "$ENV_FILE"
set +a
echo ""

# 2. GCP 설정 확인
if [ "$GCP_ENABLED" != "true" ]; then
    echo "❌ 오류: GCP_ENABLED=true로 설정해주세요."
    exit 1
fi

if [ -z "$GCP_PROJECT_ID" ]; then
    echo "❌ 오류: GCP_PROJECT_ID를 설정해주세요."
    exit 1
fi

if [ -z "$GCS_BUCKET" ]; then
    echo "❌ 오류: GCS_BUCKET을 설정해주세요."
    exit 1
fi

GCS_BUCKET="${GCS_BUCKET:-quantiq-ml-models}"
GCS_PACKAGE_BASE_PATH="${GCS_PACKAGE_BASE_PATH:-ml-packages}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
PACKAGE_NAME="quantiq-ml-package-${TIMESTAMP}.tar.gz"
GCS_PACKAGE_URI="gs://${GCS_BUCKET}/${GCS_PACKAGE_BASE_PATH}/${PACKAGE_NAME}"

echo "✅ GCP 설정 확인 완료"
echo "  프로젝트: $GCP_PROJECT_ID"
echo "  버킷: $GCS_BUCKET"
echo "  패키지 경로: $GCS_PACKAGE_BASE_PATH"
echo "  패키지명: $PACKAGE_NAME"
echo ""

# 3. 패키지 생성
echo "📦 ML 패키지 생성 중..."
cd "$DATA_ENGINE_DIR"

# src 디렉토리와 requirements.txt 패키징
TAR_FILE="/tmp/${PACKAGE_NAME}"
tar -czf "$TAR_FILE" \
    --exclude='__pycache__' \
    --exclude='*.pyc' \
    --exclude='.pytest_cache' \
    --exclude='tests' \
    src/ \
    || { echo "❌ 패키지 생성 실패"; exit 1; }

echo "✅ 패키지 생성 완료: $TAR_FILE"
ls -lh "$TAR_FILE"
echo ""

# 4. GCS 업로드
echo "☁️  GCS 업로드 중..."
if ! command -v gcloud &> /dev/null; then
    echo "❌ 오류: gcloud CLI가 설치되어 있지 않습니다."
    echo "   설치 방법: https://cloud.google.com/sdk/docs/install"
    exit 1
fi

# 인증 확인
if [ -n "$GOOGLE_APPLICATION_CREDENTIALS" ]; then
    echo "  인증: 서비스 계정 ($GOOGLE_APPLICATION_CREDENTIALS)"
    gcloud auth activate-service-account --key-file="$GOOGLE_APPLICATION_CREDENTIALS" 2>/dev/null || true
else
    echo "  인증: gcloud default"
fi

# 프로젝트 설정
gcloud config set project "$GCP_PROJECT_ID" 2>/dev/null

# 버킷 존재 확인
if ! gsutil ls "gs://${GCS_BUCKET}" &> /dev/null; then
    echo "❌ 오류: GCS 버킷 'gs://${GCS_BUCKET}'에 접근할 수 없습니다."
    echo "   버킷을 생성하거나 권한을 확인해주세요."
    exit 1
fi

# 업로드
gsutil cp "$TAR_FILE" "$GCS_PACKAGE_URI" || {
    echo "❌ GCS 업로드 실패"
    rm -f "$TAR_FILE"
    exit 1
}

echo "✅ GCS 업로드 완료: $GCS_PACKAGE_URI"
rm -f "$TAR_FILE"
echo ""

# 5. Vertex AI 실행
echo "🤖 Vertex AI 예측 작업 시작..."
echo ""

API_URL="http://localhost:10010/api/v1/vertex-ai/run"
RESPONSE=$(curl -s -X POST "$API_URL" \
    -H "Content-Type: application/json" \
    -d "{
        \"package_uri\": \"$GCS_PACKAGE_URI\"
    }")

echo "📡 API 응답:"
echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE"
echo ""

# 6. 결과 확인
SUCCESS=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('success', False))" 2>/dev/null || echo "false")

if [ "$SUCCESS" = "True" ] || [ "$SUCCESS" = "true" ]; then
    REQUEST_ID=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('requestId', 'N/A'))" 2>/dev/null || echo "N/A")
    echo "=========================================="
    echo "✅ Vertex AI 작업 시작 완료!"
    echo "=========================================="
    echo ""
    echo "📋 작업 정보:"
    echo "  Request ID: $REQUEST_ID"
    echo "  패키지: $GCS_PACKAGE_URI"
    echo "  예상 소요 시간: 3-5분"
    echo ""
    echo "📊 상태 확인:"
    echo "  curl http://localhost:10010/api/v1/vertex-ai/status?jobId=$REQUEST_ID"
    echo ""
else
    echo "=========================================="
    echo "❌ Vertex AI 작업 시작 실패"
    echo "=========================================="
    echo ""
    MESSAGE=$(echo "$RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('message', 'Unknown error'))" 2>/dev/null || echo "Unknown error")
    echo "오류: $MESSAGE"
    exit 1
fi
