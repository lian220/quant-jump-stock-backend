#!/bin/bash
# predict_optimized.py 로컬 테스트용 스크립트
# .env.prod 환경변수 로드 후 실행

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="$BACKEND_DIR/.env.prod"

echo "=== predict_optimized.py 로컬 테스트 ==="
echo "환경변수 파일: $ENV_FILE"
echo ""

# .env.prod 파일 존재 확인
if [ ! -f "$ENV_FILE" ]; then
    echo "❌ 오류: $ENV_FILE 파일을 찾을 수 없습니다."
    exit 1
fi
echo "✅ .env.prod 파일 발견"

# 기존 환경변수 클리어
unset MONGODB_URI MONGODB_DB_NAME
unset DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD

# .env.prod에서 환경변수 로드
echo ""
echo "=== 환경변수 로드 ==="
set -a
source "$ENV_FILE"
set +a

echo "[MongoDB]"
[ -n "$MONGODB_URI" ] && echo "  MONGODB_URI: ****" || echo "  MONGODB_URI: ❌ 없음"
[ -n "$MONGODB_DB_NAME" ] && echo "  MONGODB_DB_NAME: $MONGODB_DB_NAME" || echo "  MONGODB_DB_NAME: ❌ 없음"
echo ""
echo "[PostgreSQL]"
[ -n "$DB_HOST" ] && echo "  DB_HOST: $DB_HOST" || echo "  DB_HOST: ❌ 없음"
[ -n "$DB_PORT" ] && echo "  DB_PORT: $DB_PORT" || echo "  DB_PORT: ❌ 없음"
[ -n "$DB_NAME" ] && echo "  DB_NAME: $DB_NAME" || echo "  DB_NAME: ❌ 없음"
[ -n "$DB_USER" ] && echo "  DB_USER: $DB_USER" || echo "  DB_USER: ❌ 없음"
[ -n "$DB_PASSWORD" ] && echo "  DB_PASSWORD: ****" || echo "  DB_PASSWORD: ❌ 없음"
echo ""

# Python 실행
echo "=== Python 스크립트 실행 ==="
cd "$SCRIPT_DIR"
python3 predict_optimized.py "$@"
