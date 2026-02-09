# GCE VM 배포 가이드

**최종 업데이트:** 2026-02-10
**VM IP:** 34.64.166.56
**도메인:** https://api.alphafoundry.app

---

## 📋 목차

1. [신규 배포 (최초 1회)](#신규-배포-최초-1회)
2. [일반 배포 (코드 변경)](#일반-배포-코드-변경)
3. [nginx 설정 변경](#nginx-설정-변경)
4. [운영 명령어](#운영-명령어)
5. [문제 해결](#문제-해결)
6. [중요 이슈 및 해결방법](#중요-이슈-및-해결방법)

---

## 🚀 신규 배포 (최초 1회)

### 1. Terraform 인프라 생성
```bash
cd terraform
terraform init
terraform apply
# VM IP 메모: 34.64.166.56
```

### 2. Secret Manager 환경변수 등록
```bash
gcloud secrets create qjs-env-common \
  --data-file=./quant-jump-stock-backend/.env.common

gcloud secrets create qjs-env-prod \
  --data-file=./quant-jump-stock-backend/.env.prod
```

### 3. GitHub Secrets 설정
```
Settings → Secrets → Actions:
- GCE_HOST: 34.64.166.56
- GCE_USERNAME: deploy
- GCE_SSH_PRIVATE_KEY: (SSH 개인키)
- GCP_PROJECT_ID: focal-limiter-486614-u8
- GCP_SA_KEY: (Service Account JSON)
- API_BASE_URL: https://api.alphafoundry.app
```

### 4. VM 초기 설정
```bash
ssh deploy@34.64.166.56
cd /home/deploy/app
git clone https://github.com/lian220/quant-jump-stock.git .
./quant-jump-stock-backend/deploy/setup-gce.sh
```

### 5. SSL 인증서 발급
```bash
sudo ./deploy/setup-ssl.sh
```

---

## 🔄 일반 배포 (코드 변경)

### 자동 배포 (권장)
```bash
git push origin main
# GitHub Actions가 자동으로 빌드 & 배포
```

### 배포 확인
```bash
# GitHub Actions 로그
# https://github.com/lian220/quant-jump-stock/actions

# VM 상태 확인
ssh deploy@34.64.166.56
cd /home/deploy/app
./quant-jump-stock-backend/deploy/deploy.sh status
./quant-jump-stock-backend/deploy/deploy.sh health
```

---

## ⚠️ nginx 설정 변경

**nginx는 VM 로컬 빌드되므로 수동 재시작 필수**

```bash
# 1. 로컬에서 변경
vim nginx/nginx.conf
git commit -am "fix: nginx 설정 수정"
git push origin main

# 2. VM에서 수동 반영
ssh deploy@34.64.166.56
cd /home/deploy/app
git pull origin main
docker compose -f docker-compose.prod.yml restart nginx

# 3. 확인
curl http://localhost/health
curl http://localhost/api/actuator/health
```

---

## 🛠️ 운영 명령어

### 서비스 관리
```bash
cd /home/deploy/app

# 상태 확인
./quant-jump-stock-backend/deploy/deploy.sh status

# 로그 확인
./quant-jump-stock-backend/deploy/deploy.sh logs          # 전체
./quant-jump-stock-backend/deploy/deploy.sh logs core    # Core만
./quant-jump-stock-backend/deploy/deploy.sh logs nginx   # Nginx만

# 재시작
./quant-jump-stock-backend/deploy/deploy.sh restart

# 완전 재배포
./quant-jump-stock-backend/deploy/deploy.sh redeploy

# Health Check
./quant-jump-stock-backend/deploy/deploy.sh health

# 디스크 정리
./quant-jump-stock-backend/deploy/deploy.sh cleanup
```

### 환경 변수 업데이트
```bash
# 로컬에서
gcloud secrets versions add qjs-env-prod \
  --data-file=./quant-jump-stock-backend/.env.prod

# VM에서
./quant-jump-stock-backend/deploy/deploy.sh restart
```

---

## 🔍 문제 해결

### 502 Bad Gateway
```bash
# 1. 컨테이너 상태
docker ps -a | grep qjs

# 2. Core 로그
docker compose -f docker-compose.prod.yml logs core --tail=100

# 3. 네트워크 확인
docker network ls
docker network inspect quant-jump-stock_default

# 4. Health Check
curl http://localhost:10010/actuator/health  # Core 직접
curl http://localhost/api/actuator/health    # Nginx 경유
```

### 500 Internal Server Error
```bash
# 1. Core 로그에서 실제 에러 확인
docker logs qjs-core --tail=200 | grep -A 10 "ERROR"

# 2. NoResourceFoundException 발생 시
# → nginx proxy_pass trailing slash 확인
# → Spring Controller 경로 매핑 확인
```

### Core 컨테이너 시작 실패
```bash
# 메모리 확인
docker stats qjs-core

# 의존성 서비스 확인
docker ps | grep -E "kafka|zookeeper"

# 재시작
docker compose -f docker-compose.prod.yml restart core

# 완전 재생성
docker compose -f docker-compose.prod.yml up -d --force-recreate core
```

### CORS 에러
**SecurityConfig.kt 확인:**
```kotlin
configuration.allowedOrigins = listOf(
    "https://alphafoundry.app",              // Frontend
    "https://backoffice.alphafoundry.app",   // Backoffice
    "https://qjs-frontend-*.run.app",        // Cloud Run
    "https://qjs-backoffice-*.run.app"
)
```

---

## 🚨 중요 이슈 및 해결방법

### 이슈 #1: nginx proxy_pass 경로 매핑 오류

#### 문제 상황
- Frontend에서 API 호출 시 500 Internal Server Error
- Core 로그: `NoResourceFoundException: No static resource v1/marketplace/strategies`
- Spring이 API 요청을 static resource로 인식

#### 근본 원인
**잘못된 nginx 설정 (trailing slash 사용):**
```nginx
location /api/ {
    proxy_pass http://core_api/;    # ❌ trailing slash가 /api/를 제거함
}
```

**실제 동작:**
```
요청: /api/v1/marketplace/strategies
  ↓
nginx가 /api/ 제거:
  ↓
proxy: http://qjs-core:10010/v1/marketplace/strategies
  ↓
Spring Controller 기대: /api/v1/marketplace/strategies
  ↓
❌ 매핑 실패 → NoResourceFoundException
```

#### 올바른 설정 (trailing slash 제거)
```nginx
location /api/ {
    proxy_pass http://core_api;     # ✅ trailing slash 없음 = 경로 유지
}
```

**올바른 동작:**
```
요청: /api/v1/marketplace/strategies
  ↓
nginx가 전체 경로 유지:
  ↓
proxy: http://qjs-core:10010/api/v1/marketplace/strategies
  ↓
Spring Controller: @RequestMapping("/api/v1/marketplace")
  ↓
✅ 매핑 성공
```

#### 적용 방법
```bash
# 1. 로컬 nginx.conf 수정 (4곳 모두)
vim nginx/nginx.conf

# HTTP 블록 (2곳)
location /api/ {
    proxy_pass http://core_api;     # trailing slash 제거
}
location /data-api/ {
    proxy_pass http://data_engine;  # trailing slash 제거
}

# HTTPS 블록 (2곳)
location /api/ {
    proxy_pass http://core_api;     # trailing slash 제거
}
location /data-api/ {
    proxy_pass http://data_engine;  # trailing slash 제거
}

# 2. Git push
git add nginx/nginx.conf
git commit -m "fix: nginx proxy_pass trailing slash 제거 - API 경로 유지"
git push origin main

# 3. VM 적용
ssh deploy@34.64.166.56
cd /home/deploy/app
git pull origin main
docker compose -f docker-compose.prod.yml restart nginx

# 4. 테스트
curl http://localhost/api/actuator/health
curl http://localhost/api/v1/marketplace/strategies
```

#### nginx proxy_pass 규칙 요약
| 설정 | 동작 | 예시 |
|------|------|------|
| `proxy_pass http://backend;` | 경로 유지 | `/api/users` → `/api/users` ✅ |
| `proxy_pass http://backend/;` | location 경로 제거 | `/api/users` → `/users` ❌ |
| `proxy_pass http://backend/v2/;` | location 무시, URI 사용 | `/api/users` → `/v2/users` |

**우리 케이스:** Spring Controller가 `/api/v1/...` 경로를 사용하므로, **trailing slash 없이** 전체 경로를 유지해야 함

---

### 이슈 #2: Docker 네트워크 미스매치

#### 문제 상황
- nginx가 `qjs-core` 호스트명 해석 불가
- `502 Bad Gateway` 발생

#### 원인
```bash
nginx: app_default 네트워크
core: quant-jump-stock_default 네트워크
```

#### 해결
```bash
sudo docker network connect quant-jump-stock_default qjs-nginx
```

---

## 📊 모니터링

### 리소스 사용량
```bash
# 실시간 모니터링
docker stats

# 디스크
df -h
du -sh /var/lib/docker
```

### 로그
```bash
# Docker 로그
docker compose -f docker-compose.prod.yml logs

# Nginx 로그
docker exec qjs-nginx cat /var/log/nginx/access.log
docker exec qjs-nginx cat /var/log/nginx/error.log
```

---

## 🔐 보안

### Secret Manager
```bash
# 최초 등록
gcloud secrets create qjs-env-common --data-file=./quant-jump-stock-backend/.env.common
gcloud secrets create qjs-env-prod --data-file=./quant-jump-stock-backend/.env.prod

# 업데이트
gcloud secrets versions add qjs-env-common --data-file=./quant-jump-stock-backend/.env.common
gcloud secrets versions add qjs-env-prod --data-file=./quant-jump-stock-backend/.env.prod
```

### 방화벽
```bash
# Terraform 자동 생성
# - 22 (SSH)
# - 80 (HTTP)
# - 443 (HTTPS)

# 확인
sudo ufw status
```

---

## 🆘 긴급 대응

### 서비스 중단
```bash
cd /home/deploy/app
./quant-jump-stock-backend/deploy/deploy.sh stop
```

### 롤백
```bash
# 1. 이전 이미지 확인
gcloud artifacts docker images list \
  asia-northeast3-docker.pkg.dev/<PROJECT_ID>/qjs-docker/qjs-core

# 2. docker-compose.prod.yml 수정
# image: <AR_REPO>/qjs-core:latest → :previous-tag

# 3. 재시작
./quant-jump-stock-backend/deploy/deploy.sh restart
```

### 디버그 모드
```bash
# Core 컨테이너 접속
docker exec -it qjs-core /bin/bash

# 환경 변수 확인
env | grep -E "DB|KAFKA|MONGO"

# 네트워크 테스트
curl http://kafka:29092
```

---

## ✅ 배포 체크리스트

### 코드 변경 시
- [ ] `git push origin main`
- [ ] GitHub Actions 성공 확인
- [ ] Health Check: `curl https://api.alphafoundry.app/api/actuator/health`
- [ ] Frontend 실제 API 호출 테스트

### nginx 변경 시
- [ ] **proxy_pass trailing slash 제거 확인** (중요!)
- [ ] Git push
- [ ] **VM SSH 접속 → git pull → restart nginx** (수동 필수)
- [ ] Health Check
- [ ] Frontend 실제 API 호출 테스트

### 환경 변수 변경 시
- [ ] Secret Manager 업데이트
- [ ] VM에서 서비스 재시작
- [ ] 환경 변수 로드 확인

---

**문의:** GitHub Issues 또는 Slack
