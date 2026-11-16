#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

mode="${1:-run}"
if [[ "$mode" != "run" && "$mode" != "--check" ]] || (( $# > 1 )); then
    printf 'Usage: %s [--check]\n' "$0" >&2
    exit 2
fi

fail() {
    printf 'Local runtime check failed: %s\n' "$1" >&2
    exit 1
}

[[ -x /usr/libexec/java_home ]] || fail 'This launcher requires macOS. Configure Java 26 manually on another OS.'
java_26_home="$(/usr/libexec/java_home -v 26 2>/dev/null)" || fail 'Java 26 is not installed.'
export JAVA_HOME="$java_26_home"
export PATH="$JAVA_HOME/bin:$PATH"

[[ -f .env && -r .env ]] || fail 'Create a readable .env from .env.example.'

config_value() {
    awk -v key="$1" 'index($0, key "=") == 1 {print substr($0, length(key) + 2); exit}' .env
}

for key in DB_URL DB_USERNAME DB_PASSWORD DEARUS_JWT_SECRET_BASE64 KAKAO_APP_ID; do
    value="$(config_value "$key")"
    if [[ -z "$value" || "$value" == *'{'*'}'* || "$value" == '<'*'>' ]]; then
        fail "$key must be filled in .env."
    fi
done

db_url="$(config_value DB_URL)"
if [[ ! "$db_url" =~ ^jdbc:mariadb://([^:/?]+):([0-9]+)/([^?]+)(\?.*)?$ ]]; then
    fail 'DB_URL must be jdbc:mariadb://HOST:PORT/DATABASE?sslMode=verify-full.'
fi
db_host="${BASH_REMATCH[1]}"
db_port="${BASH_REMATCH[2]}"
if [[ ! "$db_url" =~ (^|[?&])sslMode=verify-full(&|$) ]]; then
    fail 'DB_URL must enable certificate and hostname verification with sslMode=verify-full.'
fi

[[ "$(config_value KAKAO_APP_ID)" =~ ^[0-9]+$ ]] || fail 'KAKAO_APP_ID must be numeric.'

jwt_secret="$(config_value DEARUS_JWT_SECRET_BASE64)"
if ! jwt_bytes="$(printf '%s' "$jwt_secret" | openssl base64 -d -A | wc -c | tr -d '[:space:]')"; then
    fail 'DEARUS_JWT_SECRET_BASE64 is not valid Base64.'
fi
(( jwt_bytes >= 32 )) || fail 'DEARUS_JWT_SECRET_BASE64 must decode to at least 32 bytes.'
unset jwt_secret

printf 'Java 26: ready\n.env required values: ready\n'
google_client_id="$(config_value GOOGLE_CLIENT_ID)"
if [[ -z "$google_client_id" || "$google_client_id" == '<'*'>' || "$google_client_id" == *'{'*'}'* ]]; then
    printf 'Google login: GOOGLE_CLIENT_ID is not configured yet.\n'
fi

command -v nc >/dev/null 2>&1 || fail 'netcat (nc) is required for the database connectivity check.'
if ! dscacheutil -q host -a name "$db_host" | awk '/ip_address:/ {found = 1} END {exit !found}'; then
    fail 'The MariaDB hostname does not resolve from this Mac. Verify the RDS Endpoint and DNS/VPC access.'
fi
if ! nc -G 3 -z "$db_host" "$db_port" >/dev/null 2>&1; then
    fail 'MariaDB TCP is unreachable from this Mac. Use an approved VPC connection or run inside the VPC.'
fi
printf 'MariaDB TCP: reachable\n'

if [[ "$mode" == "--check" ]]; then
    exit 0
fi

export SPRING_CONFIG_IMPORT='file:.env[.properties]'
exec ./gradlew bootRun
