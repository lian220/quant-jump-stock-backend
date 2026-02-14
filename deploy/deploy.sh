#!/bin/bash
# ============================================================
# Quant Jump Stock - VM 운영 스크립트
# Usage: ./deploy.sh [start|stop|restart|status|logs|pull]
# ============================================================

set -e

APP_DIR="/opt/quant-jump-stock"
COMPOSE_FILE="docker-compose.prod.yml"
PROJECT_NAME="qjs"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}✓${NC} $1"; }
log_warn() { echo -e "${YELLOW}⚠${NC} $1"; }
log_error() { echo -e "${RED}✗${NC} $1"; }

# ── 환경 변수 로드 ────────────────────────────────────
load_env() {
    log_info "Loading environment variables from Secret Manager..."

    cd "$APP_DIR"

    # Secret Manager에서 환경 변수 가져오기
    gcloud secrets versions access latest --secret=qjs-env-common \
        > ./quant-jump-stock-backend/.env.common 2>/dev/null || {
        log_error "Failed to fetch qjs-env-common"
        exit 1
    }

    gcloud secrets versions access latest --secret=qjs-env-db-prod \
        > ./quant-jump-stock-backend/.env.db.prod 2>/dev/null || {
        log_error "Failed to fetch qjs-env-db-prod"
        exit 1
    }

    gcloud secrets versions access latest --secret=qjs-env-prod \
        > ./quant-jump-stock-backend/.env.prod 2>/dev/null || {
        log_error "Failed to fetch qjs-env-prod"
        exit 1
    }

    # Validate
    if [ ! -s ./quant-jump-stock-backend/.env.common ] || \
       [ ! -s ./quant-jump-stock-backend/.env.db.prod ] || \
       [ ! -s ./quant-jump-stock-backend/.env.prod ]; then
        log_error "Environment files are empty"
        exit 1
    fi

    # Vertex AI credentials
    mkdir -p ./quant-jump-stock-backend/credentials
    gcloud secrets versions access latest --secret=qjs-vertex-ai-key \
        > ./quant-jump-stock-backend/credentials/vertex-ai-key.json 2>/dev/null || {
        log_warn "qjs-vertex-ai-key not found in Secret Manager (Vertex AI disabled)"
    }
    if [ -f ./quant-jump-stock-backend/credentials/vertex-ai-key.json ] && \
       [ -s ./quant-jump-stock-backend/credentials/vertex-ai-key.json ]; then
        chmod 600 ./quant-jump-stock-backend/credentials/vertex-ai-key.json
        log_info "Vertex AI credentials loaded"
    fi

    # docker-compose용 .env 생성 (env 파일 3개 병합)
    : > .env
    grep -v '^\s*#' ./quant-jump-stock-backend/.env.common | grep -v '^\s*$' >> .env
    grep -v '^\s*#' ./quant-jump-stock-backend/.env.db.prod | grep -v '^\s*$' >> .env
    grep -v '^\s*#' ./quant-jump-stock-backend/.env.prod | grep -v '^\s*$' >> .env

    # Artifact Registry 경로 추가
    GCP_PROJECT_ID=$(gcloud config get-value project)
    echo "AR_REPO=asia-northeast3-docker.pkg.dev/${GCP_PROJECT_ID}/qjs-docker" >> .env

    log_info "Environment loaded successfully"
}

# ── 서비스 시작 ────────────────────────────────────
start_services() {
    log_info "Starting services..."

    load_env

    cd "$APP_DIR"
    docker compose -f "$COMPOSE_FILE" up -d

    log_info "Services started. Checking health..."
    sleep 10

    # Health check
    check_health
}

# ── 서비스 중지 ────────────────────────────────────
stop_services() {
    log_warn "Stopping services..."

    cd "$APP_DIR"
    docker compose -f "$COMPOSE_FILE" down

    log_info "Services stopped"
}

# ── 서비스 재시작 ────────────────────────────────────
restart_services() {
    log_warn "Restarting services..."

    stop_services
    sleep 5
    start_services
}

# ── 상태 확인 ────────────────────────────────────
check_status() {
    cd "$APP_DIR"
    docker compose -f "$COMPOSE_FILE" ps
}

# ── 로그 확인 ────────────────────────────────────
show_logs() {
    SERVICE=${1:-}
    cd "$APP_DIR"

    if [ -z "$SERVICE" ]; then
        docker compose -f "$COMPOSE_FILE" logs --tail=100 -f
    else
        docker compose -f "$COMPOSE_FILE" logs --tail=100 -f "$SERVICE"
    fi
}

# ── 이미지 Pull ────────────────────────────────────
pull_images() {
    log_info "Pulling latest images from Artifact Registry..."

    load_env

    cd "$APP_DIR"
    docker compose -f "$COMPOSE_FILE" pull core data-engine

    log_info "Images pulled successfully"
}

# ── Health Check ────────────────────────────────────
check_health() {
    log_info "Running health checks..."

    # Core API
    for i in $(seq 1 30); do
        if curl -sf http://localhost/api/actuator/health > /dev/null 2>&1; then
            log_info "Core API: ✓ Healthy"
            break
        fi
        if [ $i -eq 30 ]; then
            log_error "Core API: ✗ Not responding"
            return 1
        fi
        sleep 2
    done

    # Data Engine
    for i in $(seq 1 20); do
        if curl -sf http://localhost/data-api/health > /dev/null 2>&1; then
            log_info "Data Engine: ✓ Healthy"
            break
        fi
        if [ $i -eq 20 ]; then
            log_error "Data Engine: ✗ Not responding"
            return 1
        fi
        sleep 2
    done

    log_info "All services healthy!"
}

# ── 디스크 정리 ────────────────────────────────────
cleanup() {
    log_warn "Cleaning up unused Docker resources..."

    docker image prune -af
    docker volume prune -f
    docker network prune -f

    log_info "Cleanup complete"
}

# ── 전체 재배포 (pull + restart) ────────────────────────────────
redeploy() {
    log_info "Starting full redeployment..."

    pull_images

    cd "$APP_DIR"
    docker compose -f "$COMPOSE_FILE" up -d --force-recreate

    log_info "Redeployment complete. Checking health..."
    sleep 10
    check_health
}

# ── 메인 ────────────────────────────────────
case "${1:-}" in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        restart_services
        ;;
    status)
        check_status
        ;;
    logs)
        show_logs "${2:-}"
        ;;
    pull)
        pull_images
        ;;
    health)
        check_health
        ;;
    cleanup)
        cleanup
        ;;
    redeploy)
        redeploy
        ;;
    ssl)
        setup_ssl
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|logs [service]|pull|health|cleanup|redeploy|ssl}"
        echo ""
        echo "Commands:"
        echo "  start     - Start all services"
        echo "  stop      - Stop all services"
        echo "  restart   - Restart all services"
        echo "  status    - Show services status"
        echo "  logs      - Show logs (optionally specify service: core, data-engine, nginx, kafka)"
        echo "  pull      - Pull latest images from Artifact Registry"
        echo "  health    - Run health checks"
        echo "  cleanup   - Clean up unused Docker resources"
        echo "  redeploy  - Full redeploy (pull + restart)"
        echo "  ssl       - Setup SSL certificate with Let's Encrypt"
        exit 1
        ;;
esac

# ── SSL 인증서 설치 ────────────────────────────────────
setup_ssl() {
    log_info "Setting up SSL certificate with Let's Encrypt..."
    
    # Certbot 설치
    sudo apt-get update -qq
    sudo apt-get install -y certbot
    
    # Nginx 컨테이너 중지 (80 포트 필요)
    cd "$APP_DIR"
    docker compose -f "$COMPOSE_FILE" stop nginx
    
    # Let's Encrypt 인증서 발급
    sudo certbot certonly --standalone \
      -d api.alphafoundry.app \
      --non-interactive \
      --agree-tos \
      -m admin@alphafoundry.app
    
    # 인증서를 프로젝트에 복사
    sudo mkdir -p "$APP_DIR/ssl"
    sudo cp /etc/letsencrypt/live/api.alphafoundry.app/fullchain.pem "$APP_DIR/ssl/"
    sudo cp /etc/letsencrypt/live/api.alphafoundry.app/privkey.pem "$APP_DIR/ssl/"
    sudo chown -R deploy:deploy "$APP_DIR/ssl"
    
    # Nginx 재시작
    docker compose -f "$COMPOSE_FILE" start nginx
    
    log_info "SSL certificate setup complete!"
}
