#!/bin/bash

# Quant Jump Stock Backend Start Script
set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARENT_DIR="$(dirname "$PROJECT_ROOT")"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

# Options
SKIP_INFRA=false
SKIP_BUILD=false
FORCE_REBUILD=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-infra)
            SKIP_INFRA=true
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --rebuild)
            FORCE_REBUILD=true
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

echo ""
echo -e "${BLUE}=========================================="
echo " 🚀 Quant Jump Stock Backend Start"
echo "==========================================${NC}"
echo ""

cd "$PROJECT_ROOT"

# Build Quant Jump Stock Core
if [ "$SKIP_BUILD" = false ]; then
    echo -e "${YELLOW}🔨 Building Quant Jump Stock Core...${NC}"
    cd quant-jump-stock-core
    ./gradlew clean build -x test
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Build failed!${NC}"
        exit 1
    fi
    cd ..
    echo -e "${GREEN}✓ Build completed${NC}"
fi

# Start infrastructure
if [ "$SKIP_INFRA" = false ]; then
    echo -e "${YELLOW}📦 Starting infrastructure...${NC}"
    cd "$PARENT_DIR"
    docker compose up -d zookeeper kafka kafka-ui postgresql mongodb

    # Wait for PostgreSQL
    echo -e "${YELLOW}⏳ Waiting for PostgreSQL...${NC}"
    for i in {1..30}; do
        if docker exec quantiq-postgres pg_isready -U quantiq_user &> /dev/null; then
            echo -e "${GREEN}✓ PostgreSQL ready${NC}"
            break
        fi
        if [ $i -eq 30 ]; then
            echo -e "${RED}⚠ PostgreSQL timeout${NC}"
        fi
        sleep 1
    done

    # Wait for Kafka
    echo -e "${YELLOW}⏳ Waiting for Kafka...${NC}"
    sleep 5

    # Kafka 토픽 생성
    echo -e "${YELLOW}📝 Creating Kafka topics...${NC}"
    if [ -f "$PROJECT_ROOT/scripts/setup/create-kafka-topics.sh" ]; then
        bash "$PROJECT_ROOT/scripts/setup/create-kafka-topics.sh"
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✓ Kafka topics created${NC}"
        else
            echo -e "${YELLOW}⚠ Some topics may already exist${NC}"
        fi
    else
        echo -e "${RED}⚠ Kafka topic creation script not found${NC}"
    fi

    echo -e "${GREEN}✓ Kafka ready${NC}"
fi

# Start backend applications
cd "$PARENT_DIR"
if [ "$FORCE_REBUILD" = true ]; then
    echo -e "${YELLOW}🎯 Rebuilding and starting backend applications...${NC}"
    docker compose up -d --build quantiq-data-engine quantiq-core
else
    echo -e "${YELLOW}🎯 Starting backend applications...${NC}"
    docker compose up -d quantiq-data-engine quantiq-core
fi

# Wait for Core API
if [ "$SKIP_INFRA" = false ]; then
    echo -e "${YELLOW}⏳ Waiting for Core API...${NC}"
    for i in {1..60}; do
        if curl -s http://localhost:10010/actuator/health &> /dev/null; then
            echo -e "${GREEN}✓ Core API ready${NC}"
            break
        fi
        if [ $i -eq 60 ]; then
            echo -e "${YELLOW}⚠ Core API not responding yet (may still be starting)${NC}"
        fi
        sleep 2
    done
fi

echo ""
echo -e "${GREEN}=========================================="
echo "✅ Backend Started!"
echo "==========================================${NC}"
echo ""
if [ "$SKIP_INFRA" = false ]; then
    echo "📊 Endpoints:"
    echo "   • Core API:    http://localhost:10010"
    echo "   • Data Engine: http://localhost:10020"
    echo "   • Kafka UI:    http://localhost:8089"
    echo "   • Swagger UI:  http://localhost:10010/swagger-ui.html"
    echo "   • PostgreSQL:  localhost:5433"
    echo "   • MongoDB:     localhost:27017"
    echo ""
else
    echo "📊 Backend Services:"
    echo "   • Core API:    http://localhost:10010"
    echo "   • Data Engine: http://localhost:10020"
    echo ""
fi
