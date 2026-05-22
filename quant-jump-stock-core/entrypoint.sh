#!/bin/sh
# ============================================================
# Cloud Run / VM 공용 엔트리포인트
# ============================================================
# Cloud Run: /secrets/*/env 에 마운트된 Secret Manager 시크릿을
#            환경변수로 로드한 뒤 앱 실행
# VM:        시크릿 파일 없으면 기존 환경변수(docker-compose env_file)로 실행
#
# 2026-05-22 강화 (사고 후속): SPRING_PROFILES_ACTIVE=prod 일 때 필수 secret 마운트 검증.
#   글로벌 규칙: SRE "fail fast and loudly" + 12-Factor "no default in code"
#   누락 시 즉시 exit 1 → Cloud Run startup probe 실패 → 새 revision rollout 차단
#
# NOTE: 값에 '&' 등 셸 특수문자가 포함될 수 있으므로
#       source(.) 대신 read + export 방식 사용

# ── Prod 환경 필수 secret 마운트 가드 ──────────────────────
if [ "${SPRING_PROFILES_ACTIVE}" = "prod" ]; then
    REQUIRED_SECRETS="/secrets/common/env /secrets/db-prod/env /secrets/prod/env"
    for required in $REQUIRED_SECRETS; do
        if [ ! -f "$required" ]; then
            echo "❌ FATAL: prod profile 필수 secret 마운트 누락: $required" >&2
            echo "    Cloud Run volumeMounts/volumes 정합성 또는 Secret Manager 권한 확인 필요" >&2
            exit 1
        fi
    done
    echo "✅ prod 필수 secret 마운트 검증 통과 (3/3)"
fi

# ── secret 파일을 환경변수로 로드 ──────────────────────────
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
