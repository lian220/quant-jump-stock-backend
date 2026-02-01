#!/bin/bash

# Quant Jump Stock Start Script
set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo ""
echo -e "${BLUE}=========================================="
echo " 🚀 Quant Jump Stock Start"
echo "==========================================${NC}"
echo ""

cd "$PROJECT_ROOT"

# Build Quant Jump Stock Core
echo -e "${YELLOW}🔨 Building Quant Jump Stock Core...${NC}"
cd quant-jump-stock-core
./gradlew clean build -x test
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Build failed!${NC}"
    exit 1
fi
cd ..
echo -e "${GREEN}✓ Build completed${NC}"

# Start infrastructure
echo -e "${YELLOW}📦 Starting infrastructure...${NC}"
docker compose up -d zookeeper kafka kafka-ui postgresql mongodb

# Wait for PostgreSQL
echo -e "${YELLOW}⏳ Waiting for PostgreSQL...${NC}"
for i in {1..30}; do
    if docker exec qjs-postgres pg_isready -U quantiq_user &> /dev/null; then
        echo -e "${GREEN}✓ PostgreSQL ready${NC}"
        break
    fi
    sleep 1
done

# Wait for Kafka
echo -e "${YELLOW}⏳ Waiting for Kafka...${NC}"
sleep 3
echo -e "${GREEN}✓ Kafka ready${NC}"

# Rebuild and start applications
echo -e "${YELLOW}🎯 Rebuilding and starting applications...${NC}"
docker compose up -d --build quant-jump-stock-data-engine quant-jump-stock-core

echo ""
echo -e "${GREEN}=========================================="
echo "✅ Quant Jump Stock Started!"
echo "==========================================${NC}"
echo ""
echo "📊 Endpoints:"
echo "   • Core API:    http://localhost:10010"
echo "   • Data Engine: http://localhost:10020"
echo "   • Kafka UI:    http://localhost:8089"
echo "   • Swagger UI:  http://localhost:10010/swagger-ui.html"
echo "   • PostgreSQL:  localhost:5433"
echo "   • MongoDB:     localhost:27017"
echo ""
