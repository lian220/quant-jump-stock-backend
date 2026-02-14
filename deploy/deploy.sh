#!/bin/bash
# ============================================================
# Quant Jump Stock - VM 운영 스크립트
# Usage: ./deploy.sh [start|stop|restart|status|logs|pull|health|cleanup|redeploy|ssl]
# ============================================================
# VM 구성: Core API (Spring Boot) + Nginx (SSL Reverse Proxy)
# Data Engine / Frontend / Backoffice → Cloud Run

set -e

APP_DIR="/opt/quant-jump-stock"
COMPOSE_FILE="docker-compose.prod.yml"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

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

    # docker-compose ${AR_REPO} 치환용 .env
    grep '^AR_REPO=' ./quant-jump-stock-backend/.env.prod > .env 2>/dev/null || {
        # Fallback: GCP project에서 생성
        GCP_PROJECT_ID=$(gcloud config get-value project)
        echo "AR_REPO=asia-northeast3-docker.pkg.dev/${GCP_PROJECT_ID}/qjs-docker" > .env
    }

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
    echo ""
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"
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
    log_info "Pulling latest Core API image..."

    load_env

    cd "$APP_DIR"
    docker compose -f "$COMPOSE_FILE" pull core

    log_info "Image pulled successfully"
}

# ── Health Check ────────────────────────────────────
check_health() {
    log_info "Running health checks..."

    # Nginx
    if curl -sf http://localhost/health > /dev/null 2>&1; then
        log_info "Nginx: ✓ Healthy"
    else
        log_error "Nginx: ✗ Not responding"
    fi

    # Core API
    for i in $(seq 1 30); do
        if curl -sf http://localhost/actuator/health > /dev/null 2>&1; then
            log_info "Core API: ✓ Healthy"
            return 0
        fi
        if [ $i -eq 30 ]; then
            log_error "Core API: ✗ Not responding after 60s"
            return 1
        fi
        sleep 2
    done
}

# ── 디스크 정리 ────────────────────────────────────
cleanup() {
    log_warn "Cleaning up unused Docker resources..."

    docker image prune -af
    docker volume prune -f
    docker network prune -f

    log_info "Cleanup complete"
}

# ── 전체 재배포 (pull + restart) ────────────────────────
redeploy() {
    log_info "Starting full redeployment..."

    pull_images

    cd "$APP_DIR"
    docker compose -f "$COMPOSE_FILE" up -d --force-recreate

    log_info "Redeployment complete. Checking health..."
    sleep 10
    check_health
}

# ── SSL 인증서 설치/갱신 ────────────────────────────────
setup_ssl() {
    log_info "Setting up SSL certificate with Let's Encrypt..."

    # Certbot 설치 (없으면)
    which certbot > /dev/null 2>&1 || {
        sudo apt-get update -qq
        sudo apt-get install -y certbot
    }

    # Nginx 컨테이너 중지 (80 포트 필요)
    cd "$APP_DIR"
    docker compose -f "$COMPOSE_FILE" stop nginx

    # Let's Encrypt 인증서 발급/갱신
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

# ── 메인 ────────────────────────────────────
case "${1:-}" in
    start)      start_services ;;
    stop)       stop_services ;;
    restart)    restart_services ;;
    status)     check_status ;;
    logs)       show_logs "${2:-}" ;;
    pull)       pull_images ;;
    health)     check_health ;;
    cleanup)    cleanup ;;
    redeploy)   redeploy ;;
    ssl)        setup_ssl ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|logs [service]|pull|health|cleanup|redeploy|ssl}"
        echo ""
        echo "Commands:"
        echo "  start     - Start all services (Core API + Nginx)"
        echo "  stop      - Stop all services"
        echo "  restart   - Restart all services"
        echo "  status    - Show services status + resource usage"
        echo "  logs      - Show logs (optionally: core, nginx)"
        echo "  pull      - Pull latest Core API image"
        echo "  health    - Run health checks"
        echo "  cleanup   - Clean up unused Docker resources"
        echo "  redeploy  - Full redeploy (pull + restart)"
        echo "  ssl       - Setup/renew SSL certificate (Let's Encrypt)"
        exit 1
        ;;
esac
