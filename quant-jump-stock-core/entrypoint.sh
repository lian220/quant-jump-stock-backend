#!/bin/sh
# ============================================================
# Cloud Run / VM 공용 엔트리포인트
# ============================================================
# Cloud Run: /secrets/*/env 에 마운트된 Secret Manager 시크릿을
#            환경변수로 로드한 뒤 앱 실행
# VM:        시크릿 파일 없으면 기존 환경변수(docker-compose env_file)로 실행

for f in /secrets/*/env; do
    if [ -f "$f" ]; then
        set -a
        . "$f"
        set +a
    fi
done

exec ./app
