#!/bin/bash
# ============================================================
# GCE VM 초기 설정 스크립트
# Usage: SSH 접속 후 1회 실행
# ============================================================
# VM 구성: Core API (Spring Boot) + Nginx (SSL Reverse Proxy)
# 설정 파일(docker-compose.prod.yml, nginx.conf)은 GitHub Actions에서 SCP로 전송
# 환경 변수는 Secret Manager에서 로드

set -e

APP_DIR="/opt/quant-jump-stock"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}✓${NC} $1"; }
log_warn() { echo -e "${YELLOW}⚠${NC} $1"; }
log_error() { echo -e "${RED}✗${NC} $1"; }
log_step() { echo -e "${BLUE}➤${NC} $1"; }

echo "════════════════════════════════════════════════════════"
echo "  Quant Jump Stock - GCE VM 초기 설정"
echo "════════════════════════════════════════════════════════"
echo ""

# ── 1. 사용자 확인 ────────────────────────────────────
log_step "1. 사용자 확인"
CURRENT_USER=$(whoami)
log_info "Current user: $CURRENT_USER"

if [ "$CURRENT_USER" != "deploy" ]; then
    log_warn "권장: 'deploy' 사용자로 실행하세요"
    read -p "계속하시겠습니까? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# ── 2. 디렉토리 생성 ────────────────────────────────────
log_step "2. 애플리케이션 디렉토리 생성"

sudo mkdir -p "$APP_DIR"
sudo mkdir -p "$APP_DIR/quant-jump-stock-backend"
sudo mkdir -p "$APP_DIR/nginx"
sudo mkdir -p "$APP_DIR/ssl"
sudo chown -R $CURRENT_USER:$CURRENT_USER "$APP_DIR"
log_info "디렉토리 생성: $APP_DIR"

# ── 3. 환경 변수 설정 ────────────────────────────────────
log_step "3. 환경 변수 설정 (Secret Manager)"

log_info "Secret Manager에서 환경 변수를 가져옵니다..."

gcloud secrets versions access latest --secret=qjs-env-common \
    > "$APP_DIR/quant-jump-stock-backend/.env.common" 2>/dev/null || {
    log_error "qjs-env-common 가져오기 실패"
    exit 1
}

gcloud secrets versions access latest --secret=qjs-env-db-prod \
    > "$APP_DIR/quant-jump-stock-backend/.env.db.prod" 2>/dev/null || {
    log_error "qjs-env-db-prod 가져오기 실패"
    exit 1
}

gcloud secrets versions access latest --secret=qjs-env-prod \
    > "$APP_DIR/quant-jump-stock-backend/.env.prod" 2>/dev/null || {
    log_error "qjs-env-prod 가져오기 실패"
    exit 1
}

# docker-compose ${AR_REPO} 치환용 .env
grep '^AR_REPO=' "$APP_DIR/quant-jump-stock-backend/.env.prod" > "$APP_DIR/.env" 2>/dev/null || {
    GCP_PROJECT_ID=$(gcloud config get-value project)
    echo "AR_REPO=asia-northeast3-docker.pkg.dev/${GCP_PROJECT_ID}/qjs-docker" > "$APP_DIR/.env"
}

log_info "환경 변수 설정 완료"

# ── 4. SSL 인증서 발급 ────────────────────────────────────
log_step "4. SSL 인증서 발급 (Let's Encrypt)"

which certbot > /dev/null 2>&1 || {
    sudo apt-get update -qq
    sudo apt-get install -y certbot
}

if [ -d "/etc/letsencrypt/live/api.alphafoundry.app" ]; then
    log_info "SSL 인증서 이미 존재"
    sudo cp /etc/letsencrypt/live/api.alphafoundry.app/fullchain.pem "$APP_DIR/ssl/"
    sudo cp /etc/letsencrypt/live/api.alphafoundry.app/privkey.pem "$APP_DIR/ssl/"
else
    log_info "SSL 인증서 발급 중..."
    sudo certbot certonly --standalone \
      -d api.alphafoundry.app \
      --non-interactive \
      --agree-tos \
      -m admin@alphafoundry.app

    sudo cp /etc/letsencrypt/live/api.alphafoundry.app/fullchain.pem "$APP_DIR/ssl/"
    sudo cp /etc/letsencrypt/live/api.alphafoundry.app/privkey.pem "$APP_DIR/ssl/"
fi

sudo chown -R $CURRENT_USER:$CURRENT_USER "$APP_DIR/ssl"
log_info "SSL 인증서 설정 완료"

# ── 5. 설정 파일 확인 ────────────────────────────────────
log_step "5. 설정 파일 확인"

if [ ! -f "$APP_DIR/docker-compose.prod.yml" ]; then
    log_error "docker-compose.prod.yml 없음 — GitHub Actions 배포 후 생성됩니다"
    log_warn "수동 배포: git에서 docker-compose.prod.yml, nginx/nginx.conf를 SCP로 복사하세요"
else
    log_info "docker-compose.prod.yml 존재"
fi

if [ ! -f "$APP_DIR/nginx/nginx.conf" ]; then
    log_warn "nginx/nginx.conf 없음 — GitHub Actions 배포 후 생성됩니다"
else
    log_info "nginx/nginx.conf 존재"
fi

# ── 6. Docker 이미지 Pull & 서비스 시작 ────────────────────────
if [ -f "$APP_DIR/docker-compose.prod.yml" ]; then
    log_step "6. Docker 이미지 Pull & 서비스 시작"

    cd "$APP_DIR"
    docker compose -f docker-compose.prod.yml pull
    docker compose -f docker-compose.prod.yml up -d
    log_info "서비스 시작됨"

    echo ""
    log_info "60초 대기 후 Health Check..."
    sleep 60

    # ── 7. Health Check ────────────────────────────────────
    log_step "7. Health Check"

    # Nginx
    if curl -sf http://localhost/health > /dev/null 2>&1; then
        log_info "Nginx: ✓ Healthy"
    else
        log_error "Nginx: ✗ Not responding"
    fi

    # Core API
    CORE_HEALTHY=false
    for i in $(seq 1 30); do
        if curl -sf http://localhost/actuator/health > /dev/null 2>&1; then
            log_info "Core API: ✓ Healthy"
            CORE_HEALTHY=true
            break
        fi
        sleep 2
    done

    if [ "$CORE_HEALTHY" = false ]; then
        log_error "Core API: ✗ Not responding after 60s"
        log_warn "로그 확인: docker compose -f docker-compose.prod.yml logs core"
    fi
else
    log_warn "docker-compose.prod.yml 없어서 서비스 시작 건너뜀"
    log_warn "GitHub Actions에서 첫 배포 후 수동으로 시작하세요:"
    log_warn "  cd $APP_DIR && docker compose -f docker-compose.prod.yml up -d"
fi

# ── 완료 ────────────────────────────────────
echo ""
echo "════════════════════════════════════════════════════════"
echo "  초기 설정 완료!"
echo "════════════════════════════════════════════════════════"
echo ""
echo "VM 구성:"
echo "  - Core API (Spring Boot) + Nginx (HTTPS Reverse Proxy)"
echo "  - Data Engine / Frontend / Backoffice → Cloud Run"
echo ""
echo "서비스 관리:"
echo "  ./deploy/deploy.sh status   # 상태 확인"
echo "  ./deploy/deploy.sh logs     # 로그 확인"
echo "  ./deploy/deploy.sh restart  # 재시작"
echo "  ./deploy/deploy.sh ssl      # SSL 인증서 갱신"
echo ""
echo "Health Check:"
echo "  curl https://api.alphafoundry.app/actuator/health"
echo ""
