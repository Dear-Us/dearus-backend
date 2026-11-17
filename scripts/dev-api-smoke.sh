#!/usr/bin/env bash
# Read-only AWS DEV smoke test. Existing members, babies, invitations and records are never mutated.
# Required: DEV_API_BASE_URL (ending in /api/v1), DEARUS_ACCESS_TOKEN (DearUs Access JWT).
set -euo pipefail

if [[ -z "${DEV_API_BASE_URL:-}" || -z "${DEARUS_ACCESS_TOKEN:-}" ]]; then
  printf 'Required: DEV_API_BASE_URL and DEARUS_ACCESS_TOKEN\n' >&2
  exit 2
fi
for tool in curl jq date; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    printf 'Required tool is unavailable: %s\n' "$tool" >&2
    exit 2
  fi
done

BASE_URL="${DEV_API_BASE_URL%/}"
if [[ "$BASE_URL" != */api/v1 ]]; then
  printf 'DEV_API_BASE_URL must end in /api/v1\n' >&2
  exit 2
fi
ROOT_URL="${BASE_URL%/api/v1}"
PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0
LAST_BODY=''

pass() { printf 'PASS %s\n' "$1"; PASS_COUNT=$((PASS_COUNT + 1)); }
fail() { printf 'FAIL %s: %s\n' "$1" "$2"; FAIL_COUNT=$((FAIL_COUNT + 1)); }
skip() { printf 'SKIP %s: %s\n' "$1" "$2"; SKIP_COUNT=$((SKIP_COUNT + 1)); }

safe_message() {
  local message="$1"
  message="${message//"$DEARUS_ACCESS_TOKEN"/[REDACTED]}"
  printf '%s' "$message"
}

# check_json NAME METHOD PATH EXPECTED_STATUS EXPECTED_SUCCESS AUTH_MODE
check_json() {
  local name="$1" method="$2" endpoint="$3" expected_status="$4"
  local expected_success="$5" auth_mode="$6" raw body status message
  LAST_BODY=''
  if [[ "$auth_mode" == bearer ]]; then
    if ! raw="$(curl -sS --connect-timeout 5 --max-time 15 -X "$method" \
        -H "Authorization: Bearer ${DEARUS_ACCESS_TOKEN}" -H 'Accept: application/json' \
        -w $'\n%{http_code}' "${BASE_URL}${endpoint}")"; then
      fail "$name" 'network request failed'
      return 1
    fi
  elif ! raw="$(curl -sS --connect-timeout 5 --max-time 15 -X "$method" \
      -H 'Accept: application/json' -w $'\n%{http_code}' "${BASE_URL}${endpoint}")"; then
    fail "$name" 'network request failed'
    return 1
  fi
  status="${raw##*$'\n'}"
  body="${raw%$'\n'*}"
  if ! jq -e 'type == "object" and has("success") and has("data") and has("message") and (has("errorCode") | not)' \
      >/dev/null 2>&1 <<<"$body"; then
    fail "$name" "HTTP ${status}; invalid ApiResponse envelope"
    return 1
  fi
  message="$(jq -r '.message // ""' <<<"$body")"
  if [[ "$status" != "$expected_status" ]] ||
      ! jq -e --argjson expected "$expected_success" '.success == $expected' >/dev/null <<<"$body"; then
    fail "$name" "HTTP ${status} (expected ${expected_status}); message=$(safe_message "$message")"
    return 1
  fi
  if [[ "$expected_success" == false ]] &&
      ! jq -e '.data == null and (.message | type) == "string" and (keys | sort) == ["data", "message", "success"]' \
          >/dev/null <<<"$body"; then
    fail "$name" 'failure ApiResponse contract mismatch'
    return 1
  fi
  LAST_BODY="$body"
  pass "$name"
}

check_public_endpoint() {
  local name="$1" endpoint="$2" expected_status="$3" raw status
  if ! raw="$(curl -sS --connect-timeout 5 --max-time 15 -o /dev/null \
      -w '%{http_code}' "${ROOT_URL}${endpoint}")"; then
    fail "$name" 'network request failed'
    return 1
  fi
  status="$raw"
  if [[ "$status" == "$expected_status" ]]; then pass "$name"; else fail "$name" "HTTP ${status}"; return 1; fi
}

date_at_offset() {
  local offset="$1" result
  if result="$(TZ="$MEMBER_TIMEZONE" date -v-"${offset}"d +%F 2>/dev/null)"; then
    printf '%s' "$result"
  else
    TZ="$MEMBER_TIMEZONE" date -d "-${offset} days" +%F
  fi
}

check_sse() {
  local baby_id="$1" result rc status content_type
  rc=0
  result="$(curl -sS -N --connect-timeout 5 --max-time 4 -o /dev/null \
      -H "Authorization: Bearer ${DEARUS_ACCESS_TOKEN}" \
      -H 'Accept: text/event-stream' \
      -w '%{http_code}|%{content_type}' \
      "${BASE_URL}/babies/${baby_id}/events" 2>/dev/null)" || rc=$?
  status="${result%%|*}"
  content_type="${result#*|}"
  if [[ "$status" == 200 && "$content_type" == text/event-stream* && ( "$rc" == 0 || "$rc" == 28 ) ]]; then
    pass 'GET /babies/{babyId}/events (SSE handshake)'
  else
    fail 'GET /babies/{babyId}/events (SSE handshake)' "HTTP ${status}; content-type ${content_type}; curl ${rc}"
  fi
}

check_public_endpoint 'GET /actuator/health' '/actuator/health' 200 || true
check_public_endpoint 'GET /v3/api-docs' '/v3/api-docs' 200 || true
check_public_endpoint 'GET /swagger-ui/index.html' '/swagger-ui/index.html' 200 || true
check_json 'GET /members/me without Bearer' GET '/members/me' 401 false none || true
check_json 'GET /invitations/{token} nonexistent' GET '/invitations/codex-smoke-nonexistent-token' 404 false none || true

if ! check_json 'GET /members/me with Bearer' GET '/members/me' 200 true bearer; then
  skip 'authenticated resource reads' 'profile request failed; no baby ID available'
  printf 'SUMMARY pass=%s fail=%s skip=%s\n' "$PASS_COUNT" "$FAIL_COUNT" "$SKIP_COUNT"
  exit 1
fi
MEMBER_TIMEZONE="$(jq -r '.data.timezone // "Asia/Seoul"' <<<"$LAST_BODY")"

if ! check_json 'GET /babies' GET '/babies' 200 true bearer; then
  skip 'baby-scoped reads' 'baby list request failed'
  printf 'SUMMARY pass=%s fail=%s skip=%s\n' "$PASS_COUNT" "$FAIL_COUNT" "$SKIP_COUNT"
  exit 1
fi
babies_json="$LAST_BODY"
if [[ -n "${DEV_SMOKE_BABY_ID:-}" ]]; then
  if [[ ! "$DEV_SMOKE_BABY_ID" =~ ^[1-9][0-9]*$ ]]; then
    printf 'DEV_SMOKE_BABY_ID must be a positive integer\n' >&2
    exit 2
  fi
  baby_id="$(jq -r --argjson id "$DEV_SMOKE_BABY_ID" \
      '[.data[] | select(.babyId == $id)] | .[0].babyId // empty' <<<"$babies_json")"
else
  baby_id="$(jq -r '([.data[] | select(.myRole == "OWNER")] + .data) | .[0].babyId // empty' \
      <<<"$babies_json")"
fi

if [[ -z "$baby_id" ]]; then
  skip 'baby-scoped reads' 'no accessible baby (or selected DEV_SMOKE_BABY_ID is inaccessible)'
else
  baby_role="$(jq -r --argjson id "$baby_id" '.data[] | select(.babyId == $id) | .myRole' <<<"$babies_json")"
  check_json 'GET /babies/{babyId}' GET "/babies/${baby_id}" 200 true bearer || true
  check_json 'GET /babies/{babyId}/members' GET "/babies/${baby_id}/members" 200 true bearer || true
  check_json 'GET /babies/{babyId}/home' GET "/babies/${baby_id}/home" 200 true bearer || true
  check_json 'GET /babies/{babyId}/records/active' GET "/babies/${baby_id}/records/active" 200 true bearer || true
  if [[ "$baby_role" == OWNER ]]; then
    check_json 'GET /babies/{babyId}/invitations' GET "/babies/${baby_id}/invitations" 200 true bearer || true
  else
    skip 'GET /babies/{babyId}/invitations' 'requires OWNER'
  fi

  today="$(date_at_offset 0)"
  year="${today:0:4}"
  month="${today:5:2}"
  check_json 'GET /babies/{babyId}/records/calendar' GET \
      "/babies/${baby_id}/records/calendar?year=${year}&month=${month}" 200 true bearer || true
  check_json 'GET /babies/{babyId}/stats/daily' GET \
      "/babies/${baby_id}/stats/daily?date=${today}" 200 true bearer || true
  check_json 'GET /babies/{babyId}/stats/routine' GET \
      "/babies/${baby_id}/stats/routine?endDate=${today}" 200 true bearer || true
  check_json 'GET /babies/{babyId}/stats/growth' GET \
      "/babies/${baby_id}/stats/growth" 200 true bearer || true
  check_json 'GET /babies/{babyId}/growth-records' GET \
      "/babies/${baby_id}/growth-records" 200 true bearer || true
  if [[ -n "$LAST_BODY" ]]; then
    growth_id="$(jq -r '.data[0].growthRecordId // empty' <<<"$LAST_BODY")"
    if [[ -n "$growth_id" ]]; then
      check_json 'GET /babies/{babyId}/growth-records/{growthRecordId}' GET \
          "/babies/${baby_id}/growth-records/${growth_id}" 200 true bearer || true
    else
      skip 'GET /babies/{babyId}/growth-records/{growthRecordId}' 'no growth record in selected baby'
    fi
  fi

  record_id=''
  for offset in 0 1 2 3 4 5 6 7 8 9; do
    care_date="$(date_at_offset "$offset")"
    if check_json "GET /babies/{babyId}/records?date=${care_date}" GET \
        "/babies/${baby_id}/records?date=${care_date}" 200 true bearer; then
      if [[ -z "$record_id" ]]; then
        record_id="$(jq -r '.data[0].recordId // empty' <<<"$LAST_BODY")"
      fi
    fi
  done
  if [[ -n "$record_id" ]]; then
    check_json 'GET /babies/{babyId}/records/{recordId}' GET \
        "/babies/${baby_id}/records/${record_id}" 200 true bearer || true
  else
    skip 'GET /babies/{babyId}/records/{recordId}' 'no completed record in last 10 days'
  fi
  check_json 'GET /records invalid date' GET "/babies/${baby_id}/records?date=invalid" 400 false bearer || true
  check_sse "$baby_id"
fi

check_json 'GET /babies/{babyId} nonexistent' GET '/babies/9223372036854775807' 404 false bearer || true
printf 'SUMMARY pass=%s fail=%s skip=%s\n' "$PASS_COUNT" "$FAIL_COUNT" "$SKIP_COUNT"
if (( FAIL_COUNT > 0 )); then exit 1; fi
