#!/bin/bash

# Quant Jump Stock Backend Start Script
set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

# Default options
ENV_MODE="local"
SKIP_INFRA=false
SKIP_BUILD=false
FORCE_REBUILD=false

# Usage
usage() {
    echo "Usage: $0 [local|prod] [options]"
    echo ""
    echo "Environments:"
    echo "  local     Use local environment (default)"
    echo "  prod      Use production DB (PostgreSQL/MongoDB only, no local containers)"
    echo ""
    echo "Options:"
    echo "  --skip-infra    Skip infrastructure startup"
    echo "  --skip-build    Skip build step"
    echo "  --rebuild       Force rebuild containers"
    echo "  --help          Show this help"
    echo ""
    echo "Examples:"
    echo "  $0              # Start with local environment"
    echo "  $0 local        # Explicitly local environment"
    echo "  $0 prod         # Production DB with local Kafka"
    echo "  $0 prod --skip-build"
    exit 0
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        local)
            ENV_MODE="local"
            shift
            ;;
        prod)
            ENV_MODE="prod"
            shift
            ;;
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
        --help|-h)
            usage
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            usage
            ;;
    esac
done

# Set env file based on mode
if [ "$ENV_MODE" = "prod" ]; then
    ENV_FILE=".env.prod"
else
    ENV_FILE=".env.local"
fi

echo ""
echo -e "${BLUE}=========================================="
echo " 🚀 Quant Jump Stock Backend Start"
echo "==========================================${NC}"
ENV_MODE_UPPER=$(echo "$ENV_MODE" | tr '[:lower:]' '[:upper:]')
echo -e "${CYAN}Environment: ${ENV_MODE_UPPER}${NC}"
echo -e "${CYAN}Config file: ${ENV_FILE}${NC}"
echo ""

cd "$PROJECT_ROOT"

# Load environment (순서: .env.common → .env.local/.env.prod)
set -a
if [ -f ".env.common" ]; then
    source ".env.common"
fi
if [ -f "$ENV_FILE" ]; then
    source "$ENV_FILE"
    echo -e "${GREEN}✓ Loaded: .env.common → ${ENV_FILE}${NC}"
else
    echo -e "${RED}❌ ${ENV_FILE} not found!${NC}"
    exit 1
fi
set +a

# Build Quant Jump Stock Core
if [ "$SKIP_BUILD" = false ]; then
    echo -e "${YELLOW}🔨 Building Quant Jump Stock Core...${NC}"
    cd quant-jump-stock-core

    # Use Java 21
    if [ -d "/Users/sfn1/Library/Java/JavaVirtualMachines/corretto-21.0.7/Contents/Home" ]; then
        export JAVA_HOME="/Users/sfn1/Library/Java/JavaVirtualMachines/corretto-21.0.7/Contents/Home"
    fi

    ./gradlew clean build -x test
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Build failed!${NC}"
        exit 1
    fi
    cd ..
    echo -e "${GREEN}✓ Build completed${NC}"
fi

# Start infrastructure based on mode
if [ "$SKIP_INFRA" = false ]; then
    echo -e "${YELLOW}📦 Starting infrastructure...${NC}"

    if [ "$ENV_MODE" = "prod" ]; then
        # Prod mode: Only start Kafka (use remote PostgreSQL/MongoDB)
        echo -e "${CYAN}   → Using production PostgreSQL: ${DB_HOST}${NC}"
        echo -e "${CYAN}   → Using production MongoDB: ${MONGO_URL}${NC}"
        docker compose up -d zookeeper kafka kafka-ui
    else
        # Local mode: Start all infrastructure with local profile
        docker compose --profile local up -d zookeeper kafka kafka-ui postgresql mongodb

        # Wait for PostgreSQL
        echo -e "${YELLOW}⏳ Waiting for PostgreSQL...${NC}"
        for i in {1..30}; do
            if docker exec qjs-postgres pg_isready -U quantiq_user &> /dev/null; then
                echo -e "${GREEN}✓ PostgreSQL ready${NC}"
                break
            fi
            if [ $i -eq 30 ]; then
                echo -e "${RED}⚠ PostgreSQL timeout${NC}"
            fi
            sleep 1
        done
    fi

    # Wait for Kafka
    echo -e "${YELLOW}⏳ Waiting for Kafka...${NC}"
    sleep 5

    # Kafka topic creation
    echo -e "${YELLOW}📝 Creating Kafka topics...${NC}"
    if [ -f "$PROJECT_ROOT/scripts/setup/create-kafka-topics.sh" ]; then
        bash "$PROJECT_ROOT/scripts/setup/create-kafka-topics.sh" 2>/dev/null || true
        echo -e "${GREEN}✓ Kafka topics ready${NC}"
    fi

    echo -e "${GREEN}✓ Infrastructure ready${NC}"
fi

# Start backend applications with correct environment
echo -e "${YELLOW}🎯 Starting backend applications (${ENV_MODE} mode)...${NC}"

# Set ENV_FILE for docker-compose variable substitution
export ENV_FILE="$ENV_FILE"

if [ "$FORCE_REBUILD" = true ]; then
    docker compose --env-file "$ENV_FILE" up -d --build quant-jump-stock-data-engine quant-jump-stock-core
else
    docker compose --env-file "$ENV_FILE" up -d quant-jump-stock-data-engine quant-jump-stock-core
fi

# Wait for Core API
echo -e "${YELLOW}⏳ Waiting for Core API...${NC}"
for i in {1..60}; do
    if curl -s http://localhost:10010/actuator/health &> /dev/null; then
        echo -e "${GREEN}✓ Core API ready${NC}"
        break
    fi
    if [ $i -eq 60 ]; then
        echo -e "${YELLOW}⚠ Core API not responding yet (check logs: docker logs qjs-core)${NC}"
    fi
    sleep 2
done

echo ""
echo -e "${GREEN}=========================================="
echo "✅ Backend Started! (${ENV_MODE_UPPER} mode)"
echo "==========================================${NC}"
echo ""
echo "📊 Endpoints:"
echo "   • Core API:    http://localhost:10010"
echo "   • Data Engine: http://localhost:10020"
echo "   • Kafka UI:    http://localhost:8089"
echo "   • Swagger UI:  http://localhost:10010/swagger-ui.html"

if [ "$ENV_MODE" = "local" ]; then
    echo "   • PostgreSQL:  localhost:5432"
    echo "   • MongoDB:     localhost:27017"
else
    echo "   • PostgreSQL:  ${DB_HOST}:${DB_PORT} (production)"
    echo "   • MongoDB:     ${MONGO_URL} (production)"
fi
echo ""
echo "📝 Logs: docker logs -f qjs-core"
echo ""
