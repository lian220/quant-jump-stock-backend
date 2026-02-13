#!/bin/bash
# .env.common을 GCP Secret Manager에 업로드
# 사용법: ./upload-env-common.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env.common"

SECRET_NAME="qjs-env-common"
PROJECT_ID="focal-limiter-486614-u8"

# 색상
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}=== .env.common → Secret Manager 업로드 ===${NC}"
echo ""
echo "  프로젝트: $PROJECT_ID"
echo "  Secret: $SECRET_NAME"
echo "  파일: $ENV_FILE"
echo ""

# .env.common 파일 존재 확인
if [ ! -f "$ENV_FILE" ]; then
    echo -e "${RED}❌ .env.common 파일이 없습니다: $ENV_FILE${NC}"
    exit 1
fi

# GCP 계정 확인
echo -e "${BLUE}🔐 GCP 인증 확인 중...${NC}"
if [ -n "$GOOGLE_APPLICATION_CREDENTIALS" ]; then
    echo "  서비스 계정: $GOOGLE_APPLICATION_CREDENTIALS"
else
    echo "  gcloud auth 사용 중"
fi

# 업로드
echo ""
echo -e "${BLUE}📤 업로드 중...${NC}"
gcloud secrets versions add "$SECRET_NAME" \
    --data-file="$ENV_FILE" \
    --project="$PROJECT_ID"

echo ""
echo -e "${GREEN}✅ 업로드 완료!${NC}"
echo ""
echo "확인 방법:"
echo "  gcloud secrets versions access latest --secret=$SECRET_NAME --project=$PROJECT_ID"
