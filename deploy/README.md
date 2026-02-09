# Backend Deployment Scripts

GCE VM 배포 및 운영을 위한 스크립트 모음

## 📁 파일 목록

| 파일 | 용도 | 실행 위치 |
|------|------|-----------|
| `deploy.sh` | 서비스 운영 (시작/중지/로그) | VM |
| `setup-gce.sh` | VM 초기 설정 (1회) | VM |
| `DIAGNOSIS.md` | 배포 문제 진단 가이드 | 로컬/VM |

## 🚀 배포 프로세스

### 1. Terraform으로 인프라 생성
```bash
cd terraform
terraform init
terraform apply
```

### 2. VM 초기 설정 (최초 1회)
```bash
# SSH 접속
ssh deploy@<VM_IP>

# 저장소 클론 및 초기 설정
cd /home/deploy/app
git clone https://github.com/lian220/quant-jump-stock.git .

# 초기 설정 스크립트 실행
./quant-jump-stock-backend/deploy/setup-gce.sh
```

### 3. GitHub Actions로 자동 배포
```bash
# 로컬에서
git push origin main

# GitHub Actions가 자동으로:
# 1. Docker 이미지 빌드
# 2. Artifact Registry에 푸시
# 3. VM SSH 접속
# 4. 이미지 Pull & 재시작
```

## 🛠️ 운영 명령어

### 서비스 관리
```bash
cd /home/deploy/app

# 상태 확인
./quant-jump-stock-backend/deploy/deploy.sh status

# 로그 확인
./quant-jump-stock-backend/deploy/deploy.sh logs          # 전체 로그
./quant-jump-stock-backend/deploy/deploy.sh logs core    # Core API만
./quant-jump-stock-backend/deploy/deploy.sh logs nginx   # Nginx만

# 재시작
./quant-jump-stock-backend/deploy/deploy.sh restart

# 완전 재배포 (이미지 재다운로드)
./quant-jump-stock-backend/deploy/deploy.sh redeploy

# Health Check
./quant-jump-stock-backend/deploy/deploy.sh health

# 디스크 정리
./quant-jump-stock-backend/deploy/deploy.sh cleanup
```

### 환경 변수 업데이트
```bash
# 로컬에서 Secret Manager 업데이트
gcloud secrets versions add qjs-env-prod \
  --data-file=./quant-jump-stock-backend/.env.prod

# VM에서 재시작 (자동으로 새 환경변수 pull)
./quant-jump-stock-backend/deploy/deploy.sh restart
```

### SSL 인증서 설정
```bash
cd /home/deploy/app
sudo ./deploy/setup-ssl.sh
```

## 🔍 문제 해결

### 502 Bad Gateway 발생 시
```bash
# 1. 컨테이너 상태 확인
docker ps -a | grep qjs

# 2. Core 로그 확인
docker compose -f docker-compose.prod.yml logs core --tail=100

# 3. 환경 변수 확인
docker inspect qjs-core | grep -A 30 Env

# 4. 네트워크 확인
docker network ls
docker network inspect app_default

# 5. Health Check
curl http://localhost:10010/api/actuator/health  # Core 직접
curl http://localhost/api/actuator/health        # Nginx 경유
```

### Core가 시작되지 않을 때
```bash
# 메모리 확인
docker stats qjs-core

# 의존성 서비스 확인
docker ps | grep -E "kafka|zookeeper"

# 재시작 시도
docker compose -f docker-compose.prod.yml restart core

# 완전 재생성
docker compose -f docker-compose.prod.yml up -d --force-recreate core
```

### CORS 에러 발생 시
**SecurityConfig.kt 수정 필요:**
```kotlin
configuration.allowedOrigins = listOf(
    "https://alphafoundry.app",              // Frontend
    "https://backoffice.alphafoundry.app",   // Backoffice
    "https://qjs-frontend-*.run.app",        // Cloud Run (와일드카드)
    "https://qjs-backoffice-*.run.app"
)
```

수정 후:
```bash
git commit -am "fix: CORS 설정 수정"
git push origin main
# GitHub Actions가 자동 배포
```

## 📊 모니터링

### 리소스 사용량
```bash
# CPU/메모리 실시간 모니터링
docker stats

# 디스크 사용량
df -h
du -sh /var/lib/docker
```

### 로그 파일 위치
```bash
# Docker 로그
docker compose -f docker-compose.prod.yml logs

# Nginx 로그 (컨테이너 내부)
docker exec qjs-nginx cat /var/log/nginx/access.log
docker exec qjs-nginx cat /var/log/nginx/error.log
```

## 🔐 보안

### Secret Manager 환경 변수 등록
```bash
# 로컬에서 (최초 1회)
gcloud secrets create qjs-env-common --data-file=./quant-jump-stock-backend/.env.common
gcloud secrets create qjs-env-prod --data-file=./quant-jump-stock-backend/.env.prod

# 업데이트 시
gcloud secrets versions add qjs-env-common --data-file=./quant-jump-stock-backend/.env.common
gcloud secrets versions add qjs-env-prod --data-file=./quant-jump-stock-backend/.env.prod
```

### 방화벽 규칙
```bash
# Terraform이 자동 생성
# - 22 (SSH)
# - 80 (HTTP)
# - 443 (HTTPS)

# VM에서 확인
sudo ufw status
```

## 📈 성능 튜닝

### JVM 힙 메모리 조정
**docker-compose.prod.yml:**
```yaml
core:
  environment:
    - JAVA_OPTS=-Xmx1024m -Xms512m  # 현재 설정
```

### Kafka 메모리 조정
```yaml
kafka:
  environment:
    - KAFKA_HEAP_OPTS=-Xmx384M -Xms256M  # 현재 설정
```

## 🆘 긴급 대응

### 서비스 전체 중단
```bash
cd /home/deploy/app
./quant-jump-stock-backend/deploy/deploy.sh stop
```

### 롤백 (이전 버전으로)
```bash
# 1. 이전 이미지 태그 확인
gcloud artifacts docker images list \
  asia-northeast3-docker.pkg.dev/<PROJECT_ID>/qjs-docker/qjs-core

# 2. docker-compose.prod.yml 수정
# image: <AR_REPO>/qjs-core:latest → :previous-tag

# 3. 재시작
./quant-jump-stock-backend/deploy/deploy.sh restart
```

### 디버그 모드 실행
```bash
# Core 컨테이너 내부 접속
docker exec -it qjs-core /bin/bash

# 환경 변수 확인
env | grep -E "DB|KAFKA|MONGO"

# 네트워크 테스트
apt-get update && apt-get install -y curl
curl http://kafka:29092
curl http://postgres:5432
```

## 📞 연락처

문제 발생 시:
1. `DIAGNOSIS.md` 참고
2. GitHub Issues 등록
3. Slack 채널에 알림
