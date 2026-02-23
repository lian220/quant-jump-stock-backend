#!/usr/bin/env bash
#
# 뉴스 수집 Cloud Function 빌드 스크립트
#
# src/에서 필요한 모듈만 .build/cf-news-collector/로 복사하여
# gcloud functions deploy에 사용할 소스 디렉토리를 구성합니다.
#
# 사용법: ./scripts/build_cf_news_collector.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_DIR="$PROJECT_ROOT/.build/cf-news-collector"
SRC_DIR="$PROJECT_ROOT/src"

echo "=== 뉴스 수집 Cloud Function 빌드 ==="
echo "프로젝트 루트: $PROJECT_ROOT"
echo "빌드 디렉토리: $BUILD_DIR"

# 기존 빌드 제거
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# ── Cloud Function 엔트리포인트 복사 ──────────────────────
cp "$PROJECT_ROOT/cloud_functions/news_collector/main.py" "$BUILD_DIR/main.py"

# ── 의존성 파일 복사 ──────────────────────────────────────
cp "$PROJECT_ROOT/requirements-cf-news.txt" "$BUILD_DIR/requirements.txt"

# ── 공유 모듈 복사 (디렉토리 구조 보존) ──────────────────
SHARED_MODULES=(
    # 도메인 모델
    "domain/__init__.py"
    "domain/news/__init__.py"
    "domain/news/models.py"

    # 어댑터 - 외부 API 클라이언트
    "adapter/__init__.py"
    "adapter/output/__init__.py"
    "adapter/output/external/__init__.py"
    "adapter/output/external/saveticker_client.py"
    "adapter/output/external/translator.py"

    # 어댑터 - MongoDB
    "adapter/output/mongodb/__init__.py"
    "adapter/output/mongodb/news_repository.py"

    # 어댑터 - PostgreSQL
    "adapter/output/postgresql/__init__.py"
    "adapter/output/postgresql/collector_state_repository.py"

    # 애플리케이션 서비스
    "application/__init__.py"
    "application/news/__init__.py"
    "application/news/news_collection_service.py"
)

echo ""
echo "공유 모듈 복사 중..."
for module in "${SHARED_MODULES[@]}"; do
    src_file="$SRC_DIR/$module"
    dest_file="$BUILD_DIR/$module"

    if [ ! -f "$src_file" ]; then
        echo "  [WARN] 파일 없음: $module"
        continue
    fi

    mkdir -p "$(dirname "$dest_file")"
    cp "$src_file" "$dest_file"
    echo "  [OK] $module"
done

# ── __init__.py 경량화 (불필요한 import 제거) ─────────────
# application/__init__.py는 원본에서 ports, strategy, backtest 등을
# 전부 import하므로 CF에서는 빈 파일로 교체
echo ""
echo "__init__.py 경량화 중..."
for init_file in \
    "application/__init__.py" \
    "adapter/output/mongodb/__init__.py" \
    "adapter/output/postgresql/__init__.py"; do

    pkg_name=$(dirname "$init_file" | tr '/' '.')
    cat > "$BUILD_DIR/$init_file" << PYEOF
"""${pkg_name} (Cloud Function 경량 버전)"""
PYEOF
    echo "  [OK] $init_file → 경량화"
done

# ── 빌드 결과 요약 ────────────────────────────────────────
echo ""
echo "=== 빌드 완료 ==="
FILE_COUNT=$(find "$BUILD_DIR" -type f | wc -l | tr -d ' ')
echo "총 파일 수: $FILE_COUNT"
echo ""
echo "디렉토리 구조:"
find "$BUILD_DIR" -type f | sort | sed "s|$BUILD_DIR/|  |"
echo ""
echo "빌드 경로: $BUILD_DIR"
echo ""
echo "다음 단계:"
echo "  1. import 검증: cd $BUILD_DIR && python -c 'from main import collect_news; print(\"OK\")'"
echo "  2. 로컬 테스트: cd $BUILD_DIR && functions-framework --target=collect_news --port=8080"
echo "  3. 배포: gcloud functions deploy news-collector --source=$BUILD_DIR ..."
