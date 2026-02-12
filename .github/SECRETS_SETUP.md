# GitHub Secrets 설정 - Backend

Backend 배포를 위해 필요한 GitHub Secrets 설정 가이드입니다.

## 필수 GitHub Secrets

> **설정 위치**: GitHub Repository → Settings → Secrets and variables → Actions

### 1. GCP 인증

| Secret 이름 | 설명 | 확인 방법 |
|-------------|------|-----------|
| **GCP_PROJECT_ID** | GCP 프로젝트 ID | 루트 프로젝트의 `terraform` 디렉토리에서:<br>`terraform output -raw github_actions_sa_email \| cut -d'@' -f2 \| cut -d'.' -f1` |
| **GCP_SA_KEY** | Service Account JSON 키 | `terraform output -raw github_actions_sa_key \| base64 -d` |

### 2. VM 접속 정보

| Secret 이름 | 설명 | 확인 방법 |
|-------------|------|-----------|
| **GCE_HOST** | VM 외부 IP 주소 | `terraform output -raw vm_ip` |
| **GCE_USERNAME** | SSH 사용자명 | 기본값: `deploy` |
| **GCE_SSH_PRIVATE_KEY** | SSH 개인키 | VM 생성 시 사용한 키<br>`cat ~/.ssh/id_rsa` |

## Secret Manager 설정 (필수)

GitHub Actions는 배포 시 VM에서 Secret Manager로부터 환경변수를 가져옵니다.

### 환경변수 업로드

```bash
# 프로젝트 루트에서 실행
cd /path/to/quant-jump-stock

# .env.common 업로드
gcloud secrets versions add qjs-env-common \
  --data-file=quant-jump-stock-backend/.env.common

# .env.prod 업로드 (중요!)
gcloud secrets versions add qjs-env-prod \
  --data-file=quant-jump-stock-backend/.env.prod
```

### 업로드 확인

```bash
# 버전 목록
gcloud secrets versions list qjs-env-prod

# 최신 내용 확인
gcloud secrets versions access latest --secret=qjs-env-prod | head -10

# 특정 변수 확인
gcloud secrets versions access latest --secret=qjs-env-prod | grep NAVER_REDIRECT_URI
```

### 로컬과 비교

```bash
# 프로젝트 루트에서
./scripts/compare-env-secrets.sh
```

## 설정 방법

### 자동 설정 (권장)

```bash
cd /path/to/quant-jump-stock
./scripts/setup-github-secrets.sh
```

### 수동 설정

1. **Terraform 정보 추출**
```bash
cd terraform
terraform output vm_ip                    # → GCE_HOST
terraform output -raw github_actions_sa_key | base64 -d > /tmp/sa-key.json
```

2. **GitHub 웹사이트에서 등록**
   - https://github.com/YOUR_USERNAME/quant-jump-stock/settings/secrets/actions
   - 위 표의 각 Secret 등록

3. **임시 파일 삭제**
```bash
rm /tmp/sa-key.json
```

## 배포 흐름

```
git push origin main
    ↓
GitHub Actions
    ↓
VM에 SSH 접속
    ↓
Secret Manager에서 .env 다운로드  ← ⚠️ 여기서 환경변수 가져옴
    ↓
Docker Compose로 컨테이너 재시작
```

**중요**: `.env.prod`를 수정했다면 반드시 Secret Manager에 재업로드해야 합니다!

## Vertex AI 키 등록 (필수 - Vertex AI 사용 시)

Vertex AI Service Account 키(`vertex-ai-key.json`)는 별도 Secret으로 관리합니다.

### 최초 등록

```bash
# credentials 디렉토리의 키 파일을 Secret Manager에 등록
gcloud secrets create qjs-vertex-ai-key \
  --data-file=./credentials/vertex-ai-key.json \
  --replication-policy=automatic

# 등록 확인
gcloud secrets versions list qjs-vertex-ai-key
```

### 키 갱신

```bash
# 새 버전 추가
gcloud secrets versions add qjs-vertex-ai-key \
  --data-file=./credentials/vertex-ai-key.json

# 확인
gcloud secrets versions list qjs-vertex-ai-key
```

### 배포 시 동작

배포 시 `deploy.sh`와 GitHub Actions 워크플로우가 자동으로:
1. Secret Manager에서 `qjs-vertex-ai-key` 다운로드
2. `./credentials/vertex-ai-key.json`에 저장
3. `docker-compose.prod.yml`이 컨테이너에 볼륨 마운트

Secret이 없으면 경고만 출력하고 배포는 계속 진행됩니다 (Vertex AI 기능만 비활성화).

## 확인 방법

```bash
# GitHub Secrets 확인
gh secret list --repo YOUR_USERNAME/quant-jump-stock

# Secret Manager 확인
gcloud secrets versions list qjs-env-prod

# 배포 후 VM 상태 확인
ssh deploy@$(cd terraform && terraform output -raw vm_ip)
docker ps
docker logs qjs-core --tail 50
```

## 보안 주의사항

- ⚠️ VM IP 주소는 공개 저장소에 커밋하지 마세요
- ⚠️ SSH 키는 절대 코드에 포함하지 마세요
- ⚠️ Service Account 키는 최소 권한만 부여하세요
- ⚠️ Secret Manager 환경변수는 정기적으로 로테이션하세요
- ⚠️ 임시로 생성한 키 파일(`/tmp/*.json`)은 즉시 삭제하세요

## 문제 해결

### Secret Manager 접근 불가
```bash
# 프로젝트 확인
gcloud config get-value project

# 올바른 프로젝트로 변경
gcloud config set project YOUR_PROJECT_ID

# Secret Manager API 활성화
gcloud services enable secretmanager.googleapis.com
```

### GitHub Actions 배포 실패
```bash
# 로그 확인
# GitHub → Actions → 실패한 workflow 클릭 → 상세 로그 확인

# 일반적인 원인:
# 1. GCE_HOST가 잘못됨 → terraform output vm_ip로 재확인
# 2. SSH 키가 잘못됨 → VM 생성 시 사용한 키 확인
# 3. Secret Manager가 비어있음 → gcloud secrets 명령어로 업로드
```

## 관련 문서

- 전체 배포 가이드: `/docs/technical/implemented/gcp-deployment.md`
- Frontend 설정: `quant-jump-stock-frontend/.github/SECRETS_SETUP.md`
- 환경변수 예시: `.env.prod` (프로젝트 내부)
