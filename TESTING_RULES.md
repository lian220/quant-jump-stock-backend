# Backend 테스트 규칙 (서비스 특화)

> **공통 규칙은 SSOT인 [../docs/testing/테스트_규칙.md](../docs/testing/테스트_규칙.md)를 참조한다.**
> 이 문서는 Backend(Core + Data Engine) 특화 사항만 기록한다.

## 실행 환경

- **항상 Docker로 실행** (Frontend/Backoffice와 달리 핫 리로드 없음)
- Core 수정 시: `./start.sh local --rebuild` (재빌드 필수, Gradle 빌드 → JAR)
- Data Engine 수정 시: 볼륨 마운트로 자동 반영 (재빌드 불필요)

## 단위 테스트

```bash
# Core 전체 테스트
cd quant-jump-stock-core && ./gradlew test

# 특정 테스트 클래스
./gradlew test --tests "com.quantjumpstock.core.SomeTest"

# Data Engine 테스트
cd quant-jump-stock-data-engine && python -m pytest tests/
```

## 서비스별 반영 방식

| 서비스 | 재빌드 필요 | 이유 |
|--------|:-----------:|------|
| **Core API** | O | Gradle 빌드 → JAR, 소스 볼륨 마운트 없음 |
| **Data Engine** | X | `./quant-jump-stock-data-engine:/app` 볼륨 마운트 |

## 로그 확인

```bash
# Core API 로그
docker logs -f qjs-core

# Data Engine 로그
docker logs -f qjs-data-engine

# 전체 로그
docker compose logs -f
```

## PR 전 필수

- 변경된 모듈의 테스트를 로컬에서 실행하고 통과 확인
- Core API: `./gradlew test`
- Data Engine: `python -m pytest tests/`
- 테스트 실패 시 원인 파악 → 수정 → 재실행 → 통과 확인 후 PR 생성
