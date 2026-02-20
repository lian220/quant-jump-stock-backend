# Backend 테스트 규칙

## 실행 환경
- **항상 Docker로 실행**
- Core 수정 시: `./start.sh local --rebuild` (재빌드 필수)
- Data Engine 수정 시: 볼륨 마운트로 자동 반영 (재빌드 불필요)
- 통합 시작: 루트에서 `./start.sh` (기본 local + build)

## 단위 테스트
```bash
# Core 전체 테스트
cd quant-jump-stock-core && ./gradlew test

# 특정 테스트 클래스
./gradlew test --tests "com.quantjumpstock.core.SomeTest"
```

## 통합 테스트 (요청 시에만)
사용자가 통합 테스트를 요청할 때만 아래 절차를 수행:
1. **테스트 플랜 작성**: `docs/testing/{기능명}-test-plan.md` 에 작성
2. **API 테스트**: curl로 엔드포인트 검증
3. **E2E 화면 테스트**: Frontend/Backoffice에서 실제 API 호출 결과 화면 검증 (Playwright MCP)
4. **테스트 결과 보고**: 완료 후 사용자에게 결과 보고 + `docs/testing/{기능명}-test-results.md` 작성

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
