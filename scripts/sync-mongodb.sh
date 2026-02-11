#!/bin/bash

# ==============================================================================
# MongoDB Production to Local Sync Script
# ==============================================================================
# This script migrates all data from the production MongoDB (Atlas) to local.
# It reads environment variables from .env.prod and .env.local.
# ==============================================================================

set -e

# ANSI Color Codes
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}운영 MongoDB 데이터를 로컬로 동기화하는 중...${NC}"

# Navigate to project root (script is in scripts/)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( dirname "$SCRIPT_DIR" )"
cd "$PROJECT_ROOT"

# 1. Load Environment Variables
if [ -f .env.prod ]; then
    echo -e "${GREEN}.env.prod${NC} 파일을 로드 중..."
    PROD_URI=$(grep "^MONGODB_URI=" .env.prod | cut -d'=' -f2-)
    # Trim quotes if they exist
    PROD_URI=$(echo $PROD_URI | sed "s/^'//;s/'$//;s/^\"//;s/\"$//")
else
    echo -e "${RED}오류: .env.prod 파일을 찾을 수 없습니다!${NC}"
    exit 1
fi

if [ -f .env.local ]; then
    echo -e "${GREEN}.env.local${NC} 파일을 로드 중..."
    LOCAL_URI=$(grep "^MONGODB_URI=" .env.local | cut -d'=' -f2-)
    # Trim quotes
    LOCAL_URI=$(echo $LOCAL_URI | sed "s/^'//;s/'$//;s/^\"//;s/\"$//")
    
    # Inside the container, 'mongodb' hostname might not resolve to localhost 
    # unless specifically configured. We use 127.0.0.1 for the internal command.
    LOCAL_RESTORE_URI=$(echo $LOCAL_URI | sed "s/@mongodb:/@localhost:/")
else
    echo -e "${RED}오류: .env.local 파일을 찾을 수 없습니다!${NC}"
    exit 1
fi

if [ -z "$PROD_URI" ] || [ -z "$LOCAL_URI" ]; then
    echo -e "${RED}오류: 환경 파일에서 MONGODB_URI를 찾을 수 없습니다.${NC}"
    exit 1
fi

# 2. Check Docker and Container Status
CONTAINER_NAME="qjs-mongodb"

if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo -e "${RED}오류: Docker 컨테이너 '${CONTAINER_NAME}'가 실행 중이 아닙니다.${NC}"
    echo -e "'docker-compose up -d mongodb'를 먼저 실행해주세요."
    exit 1
fi

# 3. Confirmation
echo -e "${RED}경고: 이 작업은 로컬 MongoDB의 모든 데이터를 삭제(DROP)합니다!${NC}"
echo -e "소스: ${YELLOW}운영 MongoDB (Atlas)${NC}"
echo -e "대상: ${GREEN}로컬 Docker 컨테이너 (${CONTAINER_NAME})${NC}"
read -p "정말로 진행하시겠습니까? (y/N) " confirm
if [[ ! $confirm =~ ^[Yy]$ ]]; then
    echo -e "작업이 취소되었습니다."
    exit 0
fi

# 4. Perform Sync
echo -e "${YELLOW}데이터 이전 중... 데이터 양에 따라 시간이 걸릴 수 있습니다.${NC}"

# We run the command inside the mongo container to avoid needing local mongo tools
# We use --archive to pipe dump into restore directly
docker exec -i "$CONTAINER_NAME" sh -c "mongodump --uri=\"$PROD_URI\" --archive | mongorestore --uri=\"$LOCAL_RESTORE_URI\" --archive --drop"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}운영 MongoDB 데이터를 로컬로 성공적으로 동기화했습니다!${NC}"
else
    echo -e "${RED}동기화에 실패했습니다.${NC}"
    exit 1
fi
