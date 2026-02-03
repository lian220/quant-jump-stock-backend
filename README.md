# quant-jump-stock-backend

Quant Jump Stock Backend - AI 기반 주식 예측 및 분석 서비스

## 구성

| 서비스 | 설명 | 포트 |
|--------|------|------|
| **quant-jump-stock-core** | Spring Boot + Kotlin 메인 API 서버 | 10010 |
| **quant-jump-stock-data-engine** | Python + FastAPI 데이터 수집 엔진 | 10020 |

## 실행 방법

### Docker Compose (권장)

```bash
# 로컬 개발 (PostgreSQL, MongoDB 포함)
docker compose --profile local up -d

# 프로덕션 (외부 DB 사용)
docker compose up -d
```

### 개별 실행

```bash
# Core API
cd quant-jump-stock-core
./gradlew bootRun

# Data Engine
cd quant-jump-stock-data-engine
poetry install
python src/main.py
```

## 환경 변수 설정

### .env.local (로컬 개발용)

```bash
# Database (로컬)
DB_HOST=localhost
DB_PORT=5433
DB_NAME=quantiq
DB_USER=quantiq_user
DB_PASSWORD=quantiq_password

# MongoDB
MONGODB_URI=mongodb://quantiq_user:quantiq_password@localhost:27017/stock_trading?authSource=admin

# Slack
SLACK_BOT_TOKEN=xoxb-xxx
SLACK_CHANNEL=C0A1XASTLH2

# GCP
GCP_ENABLED=true
GCP_PROJECT_ID=your-project-id
GCP_REGION=us-central1
```

### Vertex AI 설정

Vertex AI는 GCP에서 실행되므로 **외부 접근 가능한** PostgreSQL 설정이 필요합니다.

```bash
# .env.local에 추가 (Supabase 등 외부 DB 사용)
VERTEX_AI_DB_HOST=your-db-host.supabase.com
VERTEX_AI_DB_PORT=5432
VERTEX_AI_DB_NAME=quantiq
VERTEX_AI_DB_USER=your-user
VERTEX_AI_DB_PASSWORD=your-password
```

> **주의**: `DB_HOST=localhost` 또는 `postgresql`은 Docker 내부 호스트명이므로 Vertex AI에서 접근할 수 없습니다.

## Vertex AI API

### ML 패키지 업로드
```bash
POST /api/v1/ml-packages/upload-package
```

### 예측 실행
```bash
POST /api/v1/vertex-ai/predict
```

### Job 상태 조회
```bash
GET /api/v1/vertex-ai/jobs/{jobId}/status
```

## ML 스크립트

`scripts/ml/predict_optimized.py` - 주가 예측 모델

### 환경변수 (Vertex AI에서 사용)
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` - PostgreSQL
- `MONGO_URL`, `MONGO_USER`, `MONGO_PASSWORD` - MongoDB
- `SLACK_BOT_TOKEN`, `SLACK_CHANNEL`, `SLACK_THREAD_TS` - Slack 알림
- `REQUEST_ID` - 작업 추적용 ID

### 에러 핸들링
- PostgreSQL 연결 실패 시 자동 Slack 알림 후 종료
- 전역 에러 핸들러로 처리되지 않은 예외 Slack 알림

## 문서

- [Architecture](./docs/architecture/README.md)
- [Database](./docs/database/README.md)
- [Features](./docs/features/README.md)
- [Setup](./docs/setup/README.md)
