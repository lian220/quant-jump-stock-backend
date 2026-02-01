#!/bin/bash

# Quant Jump Stock Stop Script
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
echo " 🛑 Quant Jump Stock Stop"
echo "==========================================${NC}"
echo ""

cd "$PROJECT_ROOT"

# Check for --clean flag
if [ "$1" == "--clean" ]; then
    echo -e "${RED}⚠️  Stopping and removing application containers...${NC}"
    docker compose rm -s -f quant-jump-stock-core quant-jump-stock-data-engine
    echo -e "${GREEN}✅ Application containers removed!${NC}"
    echo ""
    echo -e "${YELLOW}💡 Infrastructure services (DB, Kafka) are still running${NC}"
elif [ "$1" == "--all" ]; then
    echo -e "${RED}⚠️  Stopping all services including infrastructure...${NC}"
    docker compose down
    echo -e "${GREEN}✅ All services stopped!${NC}"
else
    # Stop only application services (quantiq-core and quantiq-data-engine)
    echo -e "${YELLOW}📦 Stopping application services...${NC}"
    docker compose stop quant-jump-stock-core quant-jump-stock-data-engine

    echo ""
    echo -e "${GREEN}=========================================="
    echo "✅ Application services stopped!"
    echo "  - quant-jump-stock-core"
    echo "  - quant-jump-stock-data-engine"
    echo "==========================================${NC}"
    echo ""
    echo -e "${BLUE}💡 Infrastructure services still running:${NC}"
    echo "  - PostgreSQL (port 5432)"
    echo "  - MongoDB (port 27017)"
    echo "  - Kafka (port 9092)"
    echo "  - Kafka UI (port 8090)"
    echo ""
    echo -e "${YELLOW}Options:${NC}"
    echo "  ./stop.sh --clean  : Remove application containers"
    echo "  ./stop.sh --all    : Stop all services including infrastructure"
fi

echo ""
