#!/bin/bash
# check-bash-secrets.sh
#
# Claude Code PreToolUse hook for Bash tool.
# stdin으로 들어온 명령에서 "시크릿을 stdout으로 출력하는 패턴"을 검출하면
# exit 2로 차단 (Claude에게 stderr 메시지 전달).
#
# 배경:
#   2026-05-14 진단 작업 중 `docker compose config`, `docker exec ... env`,
#   `echo $MONGODB_URI | cut -c1-60` 같은 명령에서 DB/Atlas password 평문이
#   stdout으로 흘러 LLM API 컨텍스트에 전송된 사고. 재발 차단 하네스.
#
# stdin 형식: {"tool_input": {"command": "<bash 명령>"}, ...}
# exit code:
#   0 = 통과
#   2 = 차단 (stderr 메시지가 Claude에게 전달됨)
#
# 우회: 명령을 'BASH_SECRET_HOOK_BYPASS=1' 환경변수 prefix로 시작
#       예) BASH_SECRET_HOOK_BYPASS=1 docker compose config

set -euo pipefail

input=$(cat)

# jq가 없으면 통과 (개발 환경 호환성)
if ! command -v jq >/dev/null 2>&1; then
    exit 0
fi

cmd=$(echo "$input" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")
[ -z "$cmd" ] && exit 0

# 명시적 우회
if echo "$cmd" | grep -qE '(^|[;&]|^[ \t]*)[ \t]*BASH_SECRET_HOOK_BYPASS=1\b'; then
    exit 0
fi

# 위험 패턴 (각 패턴은 자체적으로 grep -E 으로 매치)
patterns=(
    # 1. docker compose config (시크릿 마운트 평문 출력)
    'docker[- ]compose[ \t]+config(\b|$)'

    # 2. 컨테이너 안에서 env / printenv 직접 출력 (파이프 마스킹 없이)
    'docker[ \t]+exec[^|]*\b(env|printenv)\b[^|]*$'

    # 3. .env 파일 직접 cat / less / more (.env.example, .env.sample 제외)
    'cat[ \t]+([^|;&]*[/ ])?\.env\.(prod|local|common|db\.[a-z]+|production|secret)'
    '(less|more|bat)[ \t]+([^|;&]*[/ ])?\.env\.(prod|local|common|db\.[a-z]+|production|secret)'

    # 4. Secret Manager 평문 출력 (파이프 없이 stdout 직접)
    'gcloud[ \t]+secrets[ \t]+versions[ \t]+access[^|]*$'

    # 5. echo/printf로 시크릿 환경변수 평문 출력
    '(echo|printf)[ \t][^|]*\$\{?(PASSWORD|TOKEN|SECRET|MONGODB_URI|DB_PASSWORD|API_KEY|CREDENTIAL|WEBHOOK|APP_KEY|ENCRYPTION_KEY|AWS_SECRET|GCP_CREDENTIALS_ENCODED)'

    # 6. base64 -d 로 인코딩 시크릿 평문 디코드 출력 (파이프나 리다이렉트 없이)
    'base64[ \t]+(-d|--decode)[ \t]*[^|>]*$'

    # 7. cut/head/tail로 URI 잘라내기 (이번 사고 패턴 — 60자 안에 password 들어감)
    '(echo|printf)[ \t][^|]*\$\{?MONGODB_URI[^|]*\|[ \t]*cut'
    'cut[ \t]+-c[ \t]*[0-9]+[^|]*\bMONGODB_URI\b'

    # 8. grep으로 .env 파일에서 값 부분까지 출력 (키 이름만이 아니라 = 뒤까지)
    'grep[ \t]+[^|]*[A-Z_]+=.*\.env\.(prod|local|common|db)'

    # 9. Python 코드 형태 시크릿 inject 블록 출력 — 2026-05-19 사고 패턴
    #    Vertex AI task.py 안의 os.environ.setdefault('DB_PASSWORD', '...') 같은 줄을
    #    grep / sed / awk / cat 로 출력 시 비번이 stdout 노출됨.
    'os\.environ\.setdefault\([^)]*(PASSWORD|SECRET|TOKEN|MONGODB_URI|API_KEY|CREDENTIAL|WEBHOOK|APP_KEY|ENCRYPTION_KEY|AWS_SECRET|GCP_CREDENTIALS_ENCODED)'

    # 10. MongoDB URI 평문 (cluster credentials 가 URI 안에 박힘)
    #     mongodb+srv://user:password@host  — :password@ 구간이 grep 출력에 노출되면 차단
    'mongodb(\+srv)?://[^[:space:]/@]+:[^[:space:]/@]+@'

    # 11. tar 압축 archive 의 내용을 cat/grep/head/tail 로 출력 (z/j 옵션 포함)
    'tar[ \t]+[xtvzjf]*[ \t]+[^|]*\.(tar|tar\.gz|tgz|tar\.bz2)[^|]*\|[ \t]*(cat|grep|head|tail|less|more)'

    # 12a. ML 패키지 task.py 직접 출력 — cat/less/more/bat/head/tail
    #     실제 사고: `cat task.py`, `head task.py` 가 DB_PASSWORD 평문 노출
    '(cat|less|more|bat|head|tail)[ \t]+[^|;&]*\btask\.py\b'

    # 12b. grep task.py — 옵션 유무 상관 없이 차단
    'grep[ \t]+[^|;&]*\btask\.py\b'

    # 13. tar 로 task.py 만 stdout 추출 (`tar xzOf pkg.tar.gz task.py`)
    'tar[ \t]+[xtvzjOf]*O[xtvzjOf]*[ \t]+[^|]*\.(tar|tar\.gz|tgz)[ \t]+[^|]*task\.py'
)

for p in "${patterns[@]}"; do
    if echo "$cmd" | grep -qE "$p"; then
        cat >&2 <<EOF
🚨 [secret-stdout-hook] 시크릿이 stdout으로 출력될 위험 — 명령 차단

매치 패턴: ${p}
원본 명령: ${cmd}

대안:
  • 환경변수 점검: scripts/safe-env-print.sh <container>  (자동 마스킹)
  • host만 필요: python3 -c "import os; from urllib.parse import urlparse; print(urlparse(os.environ['MONGODB_URI']).hostname)"
  • .env 파일 키 목록만: grep -E "^[A-Z_]+=" .env.prod | sed 's/=.*//'
  • 시크릿 길이만: echo "len=\${#PASSWORD}"
  • DB 검증은 connect 성공/실패만: psql ... -c 'SELECT 1' (결과만)

이번 한 번만 우회 필요시:
  BASH_SECRET_HOOK_BYPASS=1 <원본 명령>
EOF
        exit 2
    fi
done

exit 0
