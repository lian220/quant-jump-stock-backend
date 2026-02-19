# Quant Jump Stock Data Engine

Python 기반 데이터 수집 및 분석 엔진

## 아키텍처

Hexagonal Architecture (Ports & Adapters) 패턴을 적용한 구조입니다.

```
src/
├── adapter/           # 어댑터 레이어 (외부 연동)
│   ├── input/         # 인바운드 (Kafka, REST)
│   └── output/        # 아웃바운드 (MongoDB, GCP, Slack)
├── application/       # 애플리케이션 레이어 (유스케이스)
│   ├── analysis/      # 분석 서비스
│   ├── ml/            # ML 예측 서비스
│   └── strategy/      # 전략 실행 서비스
├── domain/            # 도메인 레이어 (비즈니스 로직)
│   └── strategy/      # Strategy DSL (models, interpreter)
├── config/            # 설정
└── ml/                # ML 스크립트 (Vertex AI용)
```

## 설치

```bash
# Poetry로 의존성 설치
poetry install

# 또는 pip
pip install -r requirements.txt
```

## 실행

```bash
# 개발 서버
python src/main.py

# 또는
uvicorn src.main:app --reload --port 10020
```

## 테스트

```bash
# 전체 테스트 실행
pytest tests/ -v

# 커버리지 포함
pytest tests/ -v --cov=src
```

### 테스트 현황
- `tests/test_domain_models.py`: Strategy DSL 도메인 모델 (21개)
- `tests/test_prediction_service.py`: ML 예측 서비스 (15개)
- **Total: 36 tests**

## 환경변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `MONGODB_URI` | MongoDB 연결 URI | - |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 서버 | `localhost:9092` |
| `GCP_PROJECT_ID` | GCP 프로젝트 ID | - |
| `GCP_BUCKET_NAME` | GCS 버킷 이름 | - |

### Vertex AI 환경변수 (Job 실행 시)
| 변수 | 설명 |
|------|------|
| `VERTEX_AI_DB_HOST` | PostgreSQL 호스트 (외부 접근 가능) |
| `VERTEX_AI_DB_PORT` | PostgreSQL 포트 |
| `VERTEX_AI_DB_NAME` | PostgreSQL 데이터베이스 |
| `VERTEX_AI_DB_USER` | PostgreSQL 사용자 |
| `VERTEX_AI_DB_PASSWORD` | PostgreSQL 비밀번호 |
| `VERTEX_AI_MONGODB_URI` | MongoDB Atlas URI |
| `VERTEX_AI_MONGODB_DATABASE` | MongoDB 데이터베이스 |

## API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/health` | 헬스체크 |
| POST | `/api/ml/upload` | ML 패키지 업로드 |
| POST | `/api/ml/predict` | Vertex AI Job 실행 |
| GET | `/api/ml/status` | 패키지 상태 조회 |

## 참고 문서

- [아키텍처 계획](./refactorplan/아키텍처.md)
- [리팩토링 계획](./refactorplan/REFACTOR_PLAN.md)
