# 배포 문제 해결 보고서

**일시**: 2026년 2월 8일
**담당**: Claude Sonnet 4.5
**상태**: ✅ 해결 완료

---

## 📋 문제 요약

프론트엔드에서 백엔드 API 호출 시 500 Internal Server Error 발생

```
Request: https://qjs-frontend-449118661566.asia-northeast3.run.app/api/strategies
Response: 500 Internal Server Error (from service worker)
```

---

## 🔍 근본 원인 분석

### 1차 문제: 프론트엔드 rewrites 미설정
- **증상**: Frontend가 `/api/` 경로로 호출하지만 rewrites 없음
- **해결**: `next.config.ts`에 rewrites 추가하여 환경 변수로 API URL 설정
- **커밋**: `eb22185` - "fix: API rewrites 추가로 500 에러 해결"

### 2차 문제: nginx proxy_pass 경로 제거 오류
- **증상**: nginx가 `/api/` prefix를 제거하여 전달
  ```
  요청: /api/v1/marketplace/strategies
  nginx 전달: /v1/marketplace/strategies  (❌ /api/ 제거됨)
  백엔드 기대: /api/v1/marketplace/strategies
  ```
- **근본 원인**: `proxy_pass http://core_api/;` (trailing slash가 prefix 제거)
- **에러 로그**:
  ```
  NoResourceFoundException: No static resource v1/marketplace/strategies.
  ```

### 3차 문제: SSL 인증서 마운트 실패
- **증상**: nginx 컨테이너 재시작 반복
- **원인**: Docker가 심볼릭 링크(`/etc/letsencrypt/live/`)를 마운트할 수 없음
- **에러 로그**:
  ```
  cannot load certificate "/etc/nginx/ssl/fullchain.pem": No such file or directory
  ```

---

## ✅ 해결 내용

### 1. nginx 설정 수정 (`nginx/nginx.conf`)

**변경 전**:
```nginx
location /api/ {
    proxy_pass http://core_api/;  # ❌ trailing slash가 /api/ 제거
}
```

**변경 후**:
```nginx
location /api/ {
    proxy_pass http://core_api;   # ✅ trailing slash 제거로 /api/ 유지
}
```

**적용 블록**:
- HTTP 서버 (port 80): `/api/`, `/data-api/`
- HTTPS 서버 (port 443): `/api/`, `/data-api/`

**커밋**: `ef71c26` - "fix: nginx proxy_pass trailing slash 제거하여 /api/ prefix 유지"

### 2. SSL 인증서 경로 수정 (`docker-compose.prod.yml`)

**문제점**:
```yaml
volumes:
  - /etc/letsencrypt/live/api.alphafoundry.app:/etc/nginx/ssl:ro
# ❌ 심볼릭 링크를 Docker가 마운트할 수 없음
```

**해결**:
```bash
# VM에서 실제 인증서 파일 복사
sudo mkdir -p /home/deploy/app/ssl
sudo cp /etc/letsencrypt/archive/api.alphafoundry.app/fullchain1.pem /home/deploy/app/ssl/fullchain.pem
sudo cp /etc/letsencrypt/archive/api.alphafoundry.app/privkey1.pem /home/deploy/app/ssl/privkey.pem
sudo chown -R deploy:deploy /home/deploy/app/ssl
```

```yaml
volumes:
  - ./ssl:/etc/nginx/ssl:ro  # ✅ 상대 경로로 실제 파일 마운트
```

**커밋**: `8140702` - "fix: SSL 인증서 마운트 경로 수정"

### 3. VM 배포

```bash
# nginx.conf 복사
scp nginx/nginx.conf deploy@34.64.166.56:/home/deploy/app/nginx/nginx.conf

# docker-compose.prod.yml 복사
scp docker-compose.prod.yml deploy@34.64.166.56:/home/deploy/app/docker-compose.prod.yml

# nginx 컨테이너 재빌드 및 재시작
docker compose -f docker-compose.prod.yml up -d --build nginx
```

---

## 🧪 검증 결과

### API 정상 작동 확인
```bash
curl -i "https://api.alphafoundry.app/api/v1/marketplace/strategies?sortBy=subscribers&page=0&size=3"

HTTP/1.1 200 ✅
Content-Type: application/json
{
  "strategies": [
    {"id":1,"name":"골든크로스",...},
    {"id":2,"name":"RSI 과매도 반등",...},
    {"id":3,"name":"MACD 크로스오버",...}
  ],
  "pagination": {
    "currentPage":0,
    "totalElements":20,
    "totalPages":7
  }
}
```

### nginx 컨테이너 상태
```
CONTAINER ID   STATUS          PORTS
ff84d5b93c15   Up 13 seconds   0.0.0.0:80->80/tcp, 0.0.0.0:443->443/tcp ✅
```

### nginx 로그
```
HTTP/HTTPS 요청 정상 처리 중
에러 없음 ✅
```

---

## 📊 영향 범위

### 수정된 파일
1. **`nginx/nginx.conf`** - proxy_pass trailing slash 제거 (HTTP + HTTPS)
2. **`docker-compose.prod.yml`** - SSL 인증서 마운트 경로 변경
3. **프론트엔드 `next.config.ts`** - rewrites 추가 (별도 커밋)

### 배포 대상
- ✅ **nginx 컨테이너**: VM에서 재빌드 및 재시작 완료
- ✅ **프론트엔드**: Cloud Run 자동 배포 완료 (`eb22185`)
- ℹ️ **백엔드**: 코드 변경 없음 (설정만 수정)

---

## 🔑 핵심 교훈

### 1. nginx proxy_pass의 trailing slash 의미
- `proxy_pass http://backend/;` → 경로에서 location prefix 제거
- `proxy_pass http://backend;` → 전체 경로 그대로 전달

### 2. Docker 볼륨 마운트의 심볼릭 링크 처리
- Docker는 심볼릭 링크를 마운트할 수 없음
- Let's Encrypt 인증서는 심볼릭 링크 구조 사용
- **해결**: 실제 파일을 Docker 접근 가능한 위치에 복사

### 3. 백엔드 API 경로 설계
- Spring Boot `@RequestMapping`에 `/api/` prefix 포함
- nginx에서 prefix를 제거하면 안 됨
- 프론트엔드도 `/api/v1/...` 전체 경로로 호출 필요

---

## 🚀 향후 개선 사항

### 1. SSL 인증서 자동 갱신 대응
현재 `/home/deploy/app/ssl`에 복사된 인증서는 Let's Encrypt 갱신 시 수동 업데이트 필요

**개선 방안**:
```bash
# cron job으로 인증서 갱신 후 자동 복사
0 0 * * * certbot renew && \
  cp /etc/letsencrypt/archive/api.alphafoundry.app/fullchain1.pem /home/deploy/app/ssl/fullchain.pem && \
  cp /etc/letsencrypt/archive/api.alphafoundry.app/privkey1.pem /home/deploy/app/ssl/privkey.pem && \
  docker compose -f /home/deploy/app/docker-compose.prod.yml restart nginx
```

### 2. 배포 자동화 개선
- nginx 설정 변경 시 GitHub Actions로 자동 배포
- SSL 인증서 경로를 환경 변수로 관리

### 3. Health Check 강화
- nginx 재시작 후 API 엔드포인트 자동 검증
- 배포 실패 시 롤백 자동화

---

## 📝 관련 커밋

| 커밋 | 내용 | 파일 |
|------|------|------|
| `eb22185` | fix: API rewrites 추가로 500 에러 해결 | `next.config.ts` |
| `ef71c26` | fix: nginx proxy_pass trailing slash 제거 | `nginx/nginx.conf` |
| `8140702` | fix: SSL 인증서 마운트 경로 수정 | `docker-compose.prod.yml` |

---

## ✅ 최종 상태

- **프론트엔드**: ✅ 정상 배포 및 작동
- **백엔드 API**: ✅ HTTPS 정상 응답
- **nginx**: ✅ HTTP/HTTPS 모두 정상 프록시
- **SSL 인증서**: ✅ 정상 로드 및 적용

**모든 시스템 정상 작동 중** 🎉
