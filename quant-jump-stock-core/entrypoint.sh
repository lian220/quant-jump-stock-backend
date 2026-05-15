#!/bin/sh
# ============================================================
# Cloud Run / VM 공용 엔트리포인트
# ============================================================
# Cloud Run: /secrets/*/env 에 마운트된 Secret Manager 시크릿을
#            환경변수로 로드한 뒤 앱 실행
# VM:        시크릿 파일 없으면 기존 환경변수(docker-compose env_file)로 실행
#
# NOTE: 값에 '&' 등 셸 특수문자가 포함될 수 있으므로
#       source(.) 대신 read + export 방식 사용

for f in /secrets/*/env; do
    if [ -f "$f" ]; then
        while IFS= read -r line || [ -n "$line" ]; do
            case "$line" in \#*|'') continue ;; esac
            key="${line%%=*}"
            val="${line#*=}"
            # strip surrounding single or double quotes (dotenv convention)
            case "$val" in
                \"*\") val="${val#\"}" ; val="${val%\"}" ;;
                \'*\') val="${val#\'}" ; val="${val%\'}" ;;
            esac
            export "${key}=${val}"
        done < "$f"
    fi
done

exec ./app
