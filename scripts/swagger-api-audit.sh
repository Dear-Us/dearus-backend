#!/usr/bin/env bash
# Local-only OpenAPI inventory and non-mutating negative-path checks.
# A 401/400/404 guard PASS does NOT establish that an authenticated success path works.
set +x
set -euo pipefail

ROOT_URL="${LOCAL_API_ROOT_URL:-http://127.0.0.1:8080}"
ROOT_URL="${ROOT_URL%/}"
if [[ ! "$ROOT_URL" =~ ^http://(127\.0\.0\.1|localhost)(:[0-9]+)?$ ]]; then
  printf 'LOCAL_API_ROOT_URL must be a loopback HTTP origin; AWS DEV is deliberately blocked.\n' >&2
  exit 2
fi
for required in curl jq; do
  if ! command -v "$required" >/dev/null 2>&1; then
    printf 'Missing required tool: %s\n' "$required" >&2
    exit 2
  fi
done

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "${RUN_LOCAL_REGRESSION:-false}" == true ]]; then
  (cd "$repo_root" && ./gradlew clean test)
fi

if ! specification="$(curl -fsS --connect-timeout 3 --max-time 15 "$ROOT_URL/v3/api-docs")"; then
  printf 'Cannot read local /v3/api-docs. Start the backend on loopback first.\n' >&2
  exit 2
fi

operations='[.paths | to_entries[] as $path | $path.value | to_entries[]
  | select(.key | IN("get","post","patch","put","delete"))
  | {method:(.key | ascii_upcase), path:$path.key, id:.value.operationId,
     tag:.value.tags[0], secured:((.value.security // []) | length > 0),
     responses:(.value.responses | keys)}]'
if ! jq -e --argjson expected 45 "$operations as \$ops
  | (\$ops | length) == \$expected
    and ([\$ops[].id] | unique | length) == \$expected
    and ([\$ops[] | select(.tag == \"Voice\")] | length) == 4
    and ([\$ops[] | select(.secured)] | length) == 41
    and ([\$ops[] | select(.secured | not) | .id] | sort)
        == [\"getInvitation\",\"login\",\"logout\",\"reissueToken\"]
    and ([\$ops[] | select(.path | startswith(\"/api/v1/\") | not)] | length) == 0" \
    >/dev/null <<< "$specification"; then
  printf 'OpenAPI inventory mismatch: expected 45 unique operations, Voice 4, Bearer 41.\n' >&2
  exit 1
fi

printf 'INVENTORY 45 operations; Voice 4; Bearer 41\n'
printf 'TAG\tMETHOD\tPATH\tOPERATION_ID\tAUTH\n'
jq -r "$operations | sort_by(.tag, .path, .method)[]
  | [.tag,.method,.path,.id,(if .secured then \"Bearer\" else \"public\" end)] | @tsv" \
  <<< "$specification"

pass=0
fail=0
while IFS=$'\t' read -r method path operation_id secured expected; do
  # The local database is isolated and empty. Numeric placeholders cannot target a real fixture.
  path="${path//\{babyId\}/999999999}"
  path="${path//\{recordId\}/999999999}"
  path="${path//\{growthRecordId\}/999999999}"
  path="${path//\{voiceRequestId\}/999999999}"
  path="${path//\{memberId\}/999999999}"
  path="${path//\{invitationId\}/999999999}"
  path="${path//\{token\}/codex-audit-nonexistent-token}"
  if [[ "$secured" == true ]]; then
    expected=401
    request=(curl -sS --connect-timeout 3 --max-time 10 -X "$method"
      -H 'Accept: application/json')
  else
    request=(curl -sS --connect-timeout 3 --max-time 10 -X "$method"
      -H 'Accept: application/json')
    if [[ "$method" == POST ]]; then
      request+=(-H 'Content-Type: application/json' --data '{}')
    fi
  fi
  if ! raw="$("${request[@]}" -w $'\n%{http_code}' "$ROOT_URL$path")"; then
    printf 'FAIL %s %s (%s): network error\n' "$method" "$path" "$operation_id" >&2
    fail=$((fail + 1))
    continue
  fi
  status="${raw##*$'\n'}"
  body="${raw%$'\n'*}"
  if [[ "$status" == "$expected" ]] && jq -e \
      'type == "object" and .success == false and .data == null
       and (.message | type) == "string" and (keys | sort) == ["data","message","success"]' \
      >/dev/null 2>&1 <<< "$body"; then
    printf 'GUARD_PASS %s %s (%s): HTTP %s\n' "$method" "$path" "$operation_id" "$status"
    pass=$((pass + 1))
  else
    printf 'FAIL %s %s (%s): HTTP %s, expected %s or invalid ApiResponse\n' \
      "$method" "$path" "$operation_id" "$status" "$expected" >&2
    fail=$((fail + 1))
  fi
done < <(jq -r "$operations[] | [.method,.path,.id,.secured,
  (if .id == \"getInvitation\" then \"404\" else \"400\" end)] | @tsv" <<< "$specification")

printf 'SUMMARY route_guard_pass=%d route_guard_fail=%d authenticated_success_paths=NOT_RUN\n' "$pass" "$fail"
if [[ "$fail" -ne 0 || "$pass" -ne 45 ]]; then exit 1; fi

if [[ "${RUN_AUTHENTICATED_READS:-false}" == true && -n "${DEARUS_ACCESS_TOKEN:-}" ]]; then
  printf 'Authenticated read-only smoke follows; token value is never printed.\n'
  DEV_API_BASE_URL="$ROOT_URL/api/v1" DEARUS_ACCESS_TOKEN="$DEARUS_ACCESS_TOKEN" \
    bash "$repo_root/scripts/dev-api-smoke.sh"
else
  printf 'SKIP authenticated read-only smoke: requires RUN_AUTHENTICATED_READS=true and DEARUS_ACCESS_TOKEN.\n'
fi
