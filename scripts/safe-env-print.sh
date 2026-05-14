#!/bin/bash
# safe-env-print.sh
#
# 컨테이너 환경변수를 안전하게 출력 (시크릿 자동 마스킹).
#
# 배경:
#   2026-05-14 진단 작업 중 `docker compose config`, `docker exec ... env`,
#   `docker exec ... cut -c1-60` 같은 명령에서 DB_PASSWORD / MONGODB_URI 평문이
#   stdout으로 흘러 LLM API 컨텍스트에 전송된 사고. 본 wrapper로 재발 차단.
#
# 사용:
#   ./scripts/safe-env-print.sh [container_name]
#   ./scripts/safe-env-print.sh qjs-data-engine
#   ./scripts/safe-env-print.sh qjs-core
#
# 마스킹 대상 키 패턴:
#   PASSWORD, TOKEN, SECRET, URI, KEY, CREDENTIAL, WEBHOOK
#   (대소문자 무관, 부분 일치)
#
# 출력 형태:
#   DB_PASSWORD=***
#   MONGODB_URI=mongodb+srv://test:***@cluster-test.2dkjwjs.mongodb.net/...
#   OPENAI_API_KEY=***

set -euo pipefail

CONTAINER="${1:-}"
if [ -z "$CONTAINER" ]; then
    echo "Usage: $0 <container_name>" >&2
    echo "Example: $0 qjs-data-engine" >&2
    exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
    echo "Error: container '${CONTAINER}' not running" >&2
    exit 2
fi

# 1) env 출력 → 시크릿 키는 전체 값 마스킹
# 2) URI 내부 password 부분 (user:password@host) 도 별도 마스킹
docker exec "$CONTAINER" env 2>/dev/null | \
    sed -E 's/(PASSWORD|TOKEN|SECRET|KEY|CREDENTIAL|WEBHOOK)=[^[:space:]]+/\1=***/gI' | \
    sed -E 's|(://[^:]+:)[^@]+(@)|\1***\2|g' | \
    sort
