#!/bin/bash
# ============================================================
# GCE VM 초기 설정 스크립트
# Usage: SSH 접속 후 1회 실행
# ============================================================

set -e

APP_DIR="/opt/quant-jump-stock"
GITHUB_REPO="https://github.com/lian220/quant-jump-stock.git"

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

# deploy 사용자가 아니면 경고
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

if [ -d "$APP_DIR" ]; then
    log_warn "$APP_DIR 이미 존재합니다"
    read -p "삭제하고 새로 생성하시겠습니까? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        sudo rm -rf "$APP_DIR"
        log_info "기존 디렉토리 삭제"
    fi
fi

sudo mkdir -p "$APP_DIR"
sudo chown -R $CURRENT_USER:$CURRENT_USER "$APP_DIR"
log_info "디렉토리 생성: $APP_DIR"

# ── 3. Git Clone ────────────────────────────────────
log_step "3. 저장소 클론"

cd "$APP_DIR"
git clone "$GITHUB_REPO" .
log_info "저장소 클론 완료"

# ── 4. 환경 변수 설정 ────────────────────────────────────
log_step "4. 환경 변수 설정"

log_info "Secret Manager에서 환경 변수를 가져옵니다..."

# Secret Manager에서 환경 변수 가져오기
gcloud secrets versions access latest --secret=qjs-env-common \
    > ./quant-jump-stock-backend/.env.common 2>/dev/null || {
    log_error "qjs-env-common 가져오기 실패"
    log_warn "Secret Manager에 환경 변수가 등록되어 있는지 확인하세요"
    exit 1
}

gcloud secrets versions access latest --secret=qjs-env-db-prod \
    > ./quant-jump-stock-backend/.env.db.prod 2>/dev/null || {
    log_error "qjs-env-db-prod 가져오기 실패"
    exit 1
}

gcloud secrets versions access latest --secret=qjs-env-prod \
    > ./quant-jump-stock-backend/.env.prod 2>/dev/null || {
    log_error "qjs-env-prod 가져오기 실패"
    exit 1
}

# Vertex AI credentials
log_info "Vertex AI credentials 다운로드..."
mkdir -p "$APP_DIR/quant-jump-stock-backend/credentials"
gcloud secrets versions access latest --secret=qjs-vertex-ai-key \
    > "$APP_DIR/quant-jump-stock-backend/credentials/vertex-ai-key.json" 2>/dev/null || {
    log_warn "qjs-vertex-ai-key not found in Secret Manager (Vertex AI disabled)"
}
if [ -f "$APP_DIR/quant-jump-stock-backend/credentials/vertex-ai-key.json" ] && \
   [ -s "$APP_DIR/quant-jump-stock-backend/credentials/vertex-ai-key.json" ]; then
    chmod 600 "$APP_DIR/quant-jump-stock-backend/credentials/vertex-ai-key.json"
    log_info "Vertex AI credentials 설정 완료"
else
    log_warn "Vertex AI credentials 파일이 비어있습니다. 나중에 수동으로 설정하세요."
fi

# docker-compose용 .env 생성 (env 파일 3개 병합)
: > .env
grep -v '^\s*#' ./quant-jump-stock-backend/.env.common | grep -v '^\s*$' >> .env
grep -v '^\s*#' ./quant-jump-stock-backend/.env.db.prod | grep -v '^\s*$' >> .env
grep -v '^\s*#' ./quant-jump-stock-backend/.env.prod | grep -v '^\s*$' >> .env

# Artifact Registry 경로 추가
GCP_PROJECT_ID=$(gcloud config get-value project)
echo "AR_REPO=asia-northeast3-docker.pkg.dev/${GCP_PROJECT_ID}/qjs-docker" >> .env

log_info "환경 변수 설정 완료"

# ── 5. SSL 디렉토리 생성 ────────────────────────────────────
log_step "5. SSL 디렉토리 생성"

mkdir -p "$APP_DIR/ssl"
log_info "SSL 디렉토리 생성: $APP_DIR/ssl"
log_warn "SSL 인증서는 나중에 setup-ssl.sh로 발급하세요"

# ── 6. Docker 이미지 Pull ────────────────────────────────────
log_step "6. Docker 이미지 Pull"

docker compose -f docker-compose.prod.yml pull
log_info "이미지 Pull 완료"

# ── 7. 서비스 시작 ────────────────────────────────────
log_step "7. 서비스 시작"

docker compose -f docker-compose.prod.yml up -d
log_info "서비스 시작됨"

echo ""
log_info "10초 대기 후 Health Check..."
sleep 10

# ── 8. Health Check ────────────────────────────────────
log_step "8. Health Check"

# Nginx
if curl -sf http://localhost/health > /dev/null 2>&1; then
    log_info "Nginx: ✓ Healthy"
else
    log_error "Nginx: ✗ Not responding"
fi

# Core API
CORE_HEALTHY=false
for i in $(seq 1 30); do
    if curl -sf http://localhost/api/actuator/health > /dev/null 2>&1; then
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

# Data Engine
DATA_ENGINE_HEALTHY=false
for i in $(seq 1 20); do
    if curl -sf http://localhost/data-api/health > /dev/null 2>&1; then
        log_info "Data Engine: ✓ Healthy"
        DATA_ENGINE_HEALTHY=true
        break
    fi
    sleep 2
done

if [ "$DATA_ENGINE_HEALTHY" = false ]; then
    log_error "Data Engine: ✗ Not responding after 40s"
fi

# ── 9. 완료 ────────────────────────────────────
echo ""
echo "════════════════════════════════════════════════════════"
echo "  초기 설정 완료!"
echo "════════════════════════════════════════════════════════"
echo ""
echo "다음 단계:"
echo "  1. SSL 인증서 발급 (https 사용 시):"
echo "     cd $APP_DIR"
echo "     sudo ./deploy/setup-ssl.sh"
echo ""
echo "  2. 서비스 관리:"
echo "     ./deploy/deploy.sh status   # 상태 확인"
echo "     ./deploy/deploy.sh logs     # 로그 확인"
echo "     ./deploy/deploy.sh restart  # 재시작"
echo ""
echo "  3. Health Check:"
echo "     curl http://localhost/health"
echo "     curl http://localhost/api/actuator/health"
echo ""
echo "  4. 외부 접속:"
echo "     curl http://$(curl -s ifconfig.me)/api/actuator/health"
echo ""
