#!/usr/bin/env bash
# Isolated AWS DEV mutation smoke test. Only a newly created, uniquely named Baby is mutated.
# Cleanup is API-level soft deletion: rows may remain in the DEV database as deleted history.
set +x
set -euo pipefail

if [[ -z "${DEV_API_BASE_URL:-}" || -z "${DEARUS_ACCESS_TOKEN:-}" ]]; then
  printf 'Required: DEV_API_BASE_URL and DEARUS_ACCESS_TOKEN\n' >&2
  exit 2
fi
for required_tool in curl jq date; do
  if ! command -v "$required_tool" >/dev/null 2>&1; then
    printf 'Required tool is unavailable: %s\n' "$required_tool" >&2
    exit 2
  fi
done

BASE_URL="${DEV_API_BASE_URL%/}"
if [[ ! "$BASE_URL" =~ ^https?://([^/:]+)(:[0-9]+)?/api/v1$ ]]; then
  printf 'DEV_API_BASE_URL must be an http(s) URL ending in /api/v1\n' >&2
  exit 2
fi
DEV_HOST="${BASH_REMATCH[1]}"
ROOT_URL="${BASE_URL%/api/v1}"
if [[ "$DEV_HOST" != *dev* && "$DEV_HOST" != *DEV* ]]; then
  printf 'Refusing mutations: API host does not contain dev\n' >&2
  exit 2
fi

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0
LAST_BODY=''
LAST_STATUS=''
TEMP_BABY_ID=''
ACTIVE_RECORD_ID=''
INVITATION_ID=''
CREATE_ATTEMPTED=false
CLEANUP_OK=true
BASE_MEMBER_ID=''
BASE_SELECTION=''
BASE_BABY_IDS='[]'
BASELINE_READY=false
TEMP_NAME="CodexSmoke-$(date -u +%Y%m%d%H%M%S)-$$"
declare -a CARE_IDS=()
declare -a GROWTH_IDS=()

pass() { printf 'PASS %s\n' "$1"; PASS_COUNT=$((PASS_COUNT + 1)); }
fail() { printf 'FAIL %s: %s\n' "$1" "$2" >&2; FAIL_COUNT=$((FAIL_COUNT + 1)); }
skip() { printf 'SKIP %s: %s\n' "$1" "$2"; SKIP_COUNT=$((SKIP_COUNT + 1)); }

# api METHOD PATH [json|multipart|none] [PAYLOAD]; never print a response body or bearer token.
api() {
  local method="$1" endpoint="$2" content_kind="${3:-none}" payload="${4:-}"
  local raw
  LAST_BODY=''
  LAST_STATUS=''
  if [[ "$content_kind" == json ]]; then
    raw="$(curl -sS --connect-timeout 5 --max-time 25 -X "$method" \
        -H "Authorization: Bearer ${DEARUS_ACCESS_TOKEN}" -H 'Accept: application/json' \
        -H 'Content-Type: application/json' --data "$payload" \
        -w $'\n%{http_code}' "${BASE_URL}${endpoint}")" || return 1
  elif [[ "$content_kind" == multipart ]]; then
    raw="$(curl -sS --connect-timeout 5 --max-time 25 -X "$method" \
        -H "Authorization: Bearer ${DEARUS_ACCESS_TOKEN}" -H 'Accept: application/json' \
        -F "request=${payload};type=application/json" \
        -w $'\n%{http_code}' "${BASE_URL}${endpoint}")" || return 1
  else
    raw="$(curl -sS --connect-timeout 5 --max-time 25 -X "$method" \
        -H "Authorization: Bearer ${DEARUS_ACCESS_TOKEN}" -H 'Accept: application/json' \
        -w $'\n%{http_code}' "${BASE_URL}${endpoint}")" || return 1
  fi
  LAST_STATUS="${raw##*$'\n'}"
  LAST_BODY="${raw%$'\n'*}"
}

expect_success() {
  local label="$1" expected_status="$2" response_success
  if [[ "$LAST_STATUS" != "$expected_status" ]] ||
      ! jq -e 'type == "object" and .success == true and has("data") and has("message") and (has("errorCode") | not)' \
          >/dev/null 2>&1 <<<"$LAST_BODY"; then
    response_success="$(jq -r 'if .success == true then "true" elif .success == false then "false" else "invalid" end' <<<"$LAST_BODY" 2>/dev/null || printf 'invalid')"
    fail "$label" "HTTP ${LAST_STATUS:-none} (expected ${expected_status}); ApiResponse.success=${response_success}"
    return 1
  fi
  pass "$label"
}

expect_not_found() {
  local label="$1" response_success
  if [[ "$LAST_STATUS" != 404 ]] ||
      ! jq -e 'type == "object" and .success == false and .data == null and (.message | type) == "string" and (keys | sort) == ["data", "message", "success"]' \
          >/dev/null 2>&1 <<<"$LAST_BODY"; then
    response_success="$(jq -r 'if .success == true then "true" elif .success == false then "false" else "invalid" end' <<<"$LAST_BODY" 2>/dev/null || printf 'invalid')"
    fail "$label" "HTTP ${LAST_STATUS:-none} (expected 404); ApiResponse.success=${response_success}"
    return 1
  fi
  pass "$label"
}

assert_data() {
  local label="$1" filter="$2"
  if ! jq -e "$filter" >/dev/null 2>&1 <<<"$LAST_BODY"; then
    fail "$label" 'response data did not match the expected contract'
    return 1
  fi
  pass "$label"
}

valid_new_id() {
  local candidate="$1"
  [[ "$candidate" =~ ^[0-9]+$ ]] && (( candidate > 1 )) &&
    jq -e --argjson id "$candidate" 'index($id) == null' >/dev/null <<<"$BASE_BABY_IDS"
}

hours_ago() {
  local amount="$1"
  if date -u -v-"${amount}"H +'%Y-%m-%dT%H:%M:%SZ' 2>/dev/null; then return; fi
  date -u -d "${amount} hours ago" +'%Y-%m-%dT%H:%M:%SZ'
}

days_ago() {
  local amount="$1"
  if TZ="$MEMBER_TIMEZONE" date -v-"${amount}"d +%F 2>/dev/null; then return; fi
  TZ="$MEMBER_TIMEZONE" date -d "${amount} days ago" +%F
}

is_owned_temp_baby() {
  local id="$1"
  valid_new_id "$id" || return 1
  api GET "/babies/${id}" || return 1
  [[ "$LAST_STATUS" == 200 ]] || return 1
  jq -e --arg name "$TEMP_NAME" --argjson id "$id" \
      '.success == true and .data.babyId == $id and (.data.babyName == $name or .data.babyName == ($name + "-updated")) and .data.myRole == "OWNER"' \
      >/dev/null <<<"$LAST_BODY"
}

recover_temp_id() {
  local candidates recovered
  [[ "$CREATE_ATTEMPTED" == true && -z "$TEMP_BABY_ID" ]] || return 0
  api GET '/babies' || return 1
  [[ "$LAST_STATUS" == 200 ]] || return 1
  candidates="$(jq -c --arg name "$TEMP_NAME" \
      '[.data[] | select(.babyName == $name and .myRole == "OWNER") | .babyId]' <<<"$LAST_BODY")" || return 1
  [[ "$(jq 'length' <<<"$candidates")" == 1 ]] || return 1
  recovered="$(jq -r '.[0]' <<<"$candidates")"
  valid_new_id "$recovered" || return 1
  TEMP_BABY_ID="$recovered"
  printf 'CLEANUP recovered temporary babyId=%s\n' "$TEMP_BABY_ID"
}

cleanup() {
  local original_exit="$1" id version current_ids current_selection current_member
  trap - EXIT
  set +e
  if [[ -z "$TEMP_BABY_ID" && "$CREATE_ATTEMPTED" == true ]]; then
    recover_temp_id || { fail 'cleanup recovery' "cannot locate temporary Baby by unique name ${TEMP_NAME}"; CLEANUP_OK=false; }
  fi
  if [[ -n "$TEMP_BABY_ID" ]]; then
    if is_owned_temp_baby "$TEMP_BABY_ID"; then
      if [[ -n "$ACTIVE_RECORD_ID" ]]; then
        if api PATCH "/babies/${TEMP_BABY_ID}/records/${ACTIVE_RECORD_ID}/cancel" && [[ "$LAST_STATUS" == 200 ]]; then
          pass 'cleanup active record cancel'
          ACTIVE_RECORD_ID=''
        else
          fail 'cleanup active record cancel' "recordId=${ACTIVE_RECORD_ID}, babyId=${TEMP_BABY_ID}"
          CLEANUP_OK=false
        fi
      fi
      if [[ -n "$INVITATION_ID" ]]; then
        if api DELETE "/babies/${TEMP_BABY_ID}/invitations/${INVITATION_ID}" && [[ "$LAST_STATUS" == 200 ]]; then
          pass 'cleanup invitation cancel'
          INVITATION_ID=''
        else
          fail 'cleanup invitation cancel' "invitationId=${INVITATION_ID}, babyId=${TEMP_BABY_ID}"
          CLEANUP_OK=false
        fi
      fi
      if (( ${#CARE_IDS[@]} > 0 )); then
        for id in "${CARE_IDS[@]}"; do
          if api GET "/babies/${TEMP_BABY_ID}/records/${id}"; then
            if [[ "$LAST_STATUS" == 200 ]]; then
              version="$(jq -r '.data.version // empty' <<<"$LAST_BODY")"
              if [[ "$version" =~ ^[0-9]+$ ]] && api DELETE "/babies/${TEMP_BABY_ID}/records/${id}?version=${version}" && [[ "$LAST_STATUS" == 200 ]]; then
                pass "cleanup care recordId=${id}"
              else
                fail 'cleanup care record' "recordId=${id}, babyId=${TEMP_BABY_ID}"
                CLEANUP_OK=false
              fi
            elif [[ "$LAST_STATUS" != 404 ]]; then
              fail 'cleanup care detail' "recordId=${id}, HTTP ${LAST_STATUS}"
              CLEANUP_OK=false
            fi
          else
            fail 'cleanup care detail' "recordId=${id}, request failed"
            CLEANUP_OK=false
          fi
        done
      fi
      if (( ${#GROWTH_IDS[@]} > 0 )); then
        for id in "${GROWTH_IDS[@]}"; do
          if api GET "/babies/${TEMP_BABY_ID}/growth-records/${id}"; then
            if [[ "$LAST_STATUS" == 200 ]]; then
              version="$(jq -r '.data.version // empty' <<<"$LAST_BODY")"
              if [[ "$version" =~ ^[0-9]+$ ]] && api DELETE "/babies/${TEMP_BABY_ID}/growth-records/${id}?version=${version}" && [[ "$LAST_STATUS" == 200 ]]; then
                pass "cleanup growth recordId=${id}"
              else
                fail 'cleanup growth record' "growthRecordId=${id}, babyId=${TEMP_BABY_ID}"
                CLEANUP_OK=false
              fi
            elif [[ "$LAST_STATUS" != 404 ]]; then
              fail 'cleanup growth detail' "growthRecordId=${id}, HTTP ${LAST_STATUS}"
              CLEANUP_OK=false
            fi
          else
            fail 'cleanup growth detail' "growthRecordId=${id}, request failed"
            CLEANUP_OK=false
          fi
        done
      fi
      if api DELETE "/babies/${TEMP_BABY_ID}" && [[ "$LAST_STATUS" == 200 ]] &&
          api GET "/babies/${TEMP_BABY_ID}" && [[ "$LAST_STATUS" == 404 ]]; then
        pass "cleanup temporary babyId=${TEMP_BABY_ID} (soft delete + 404)"
      else
        fail 'cleanup temporary Baby' "babyId=${TEMP_BABY_ID} may remain; manual review required"
        CLEANUP_OK=false
      fi
    else
      fail 'cleanup safety guard' "babyId=${TEMP_BABY_ID} identity/ownership mismatch; refused deletion"
      CLEANUP_OK=false
    fi
  fi
  if [[ -n "$BASE_MEMBER_ID" ]]; then
    if api GET '/members/me' && [[ "$LAST_STATUS" == 200 ]]; then
      current_member="$(jq -r '.data.memberId // empty' <<<"$LAST_BODY")"
      current_selection="$(jq -r '.data.lastSelectedBabyId // empty' <<<"$LAST_BODY")"
      if [[ "$current_member" == "$BASE_MEMBER_ID" && "$current_selection" == "$BASE_SELECTION" ]]; then
        pass 'cleanup member selection unchanged'
      else
        fail 'cleanup member selection' 'memberId or lastSelectedBabyId changed'
        CLEANUP_OK=false
      fi
    else
      fail 'cleanup member verification' 'profile request failed'
      CLEANUP_OK=false
    fi
    if [[ "$BASELINE_READY" == true ]] && api GET '/babies' && [[ "$LAST_STATUS" == 200 ]]; then
      current_ids="$(jq -c '[.data[].babyId] | sort' <<<"$LAST_BODY")"
      if [[ "$current_ids" == "$BASE_BABY_IDS" ]]; then
        pass 'cleanup original Baby list unchanged'
      else
        fail 'cleanup Baby list' "temporary babyId=${TEMP_BABY_ID:-unknown} or baseline list differs"
        CLEANUP_OK=false
      fi
    elif [[ "$BASELINE_READY" == true ]]; then
      fail 'cleanup Baby list verification' 'list request failed'
      CLEANUP_OK=false
    fi
  fi
  printf 'SUMMARY pass=%s fail=%s skip=%s cleanup=%s temporaryBabyId=%s\n' \
      "$PASS_COUNT" "$FAIL_COUNT" "$SKIP_COUNT" "$CLEANUP_OK" "${TEMP_BABY_ID:-none}"
  if [[ "$original_exit" != 0 || "$FAIL_COUNT" != 0 || "$CLEANUP_OK" != true ]]; then exit 1; fi
}
trap 'cleanup $?' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Read-only preflight: never let creation initialize an existing member's null selection.
if ! openapi_doc="$(curl -fsS --connect-timeout 5 --max-time 25 "${ROOT_URL}/v3/api-docs")"; then
  fail 'preflight deployed OpenAPI' 'OpenAPI is unavailable; refusing mutations'
  exit 1
fi
if ! jq -e '
    .paths["/api/v1/babies"].post.operationId == "createBaby" and
    .paths["/api/v1/babies/{babyId}"].patch.operationId == "updateBaby" and
    .paths["/api/v1/babies/{babyId}"].delete.operationId == "deleteBaby" and
    .paths["/api/v1/babies/{babyId}/records"].post.operationId == "createCareRecord" and
    .paths["/api/v1/babies/{babyId}/records/start"].post.operationId == "startCareRecord" and
    .paths["/api/v1/babies/{babyId}/records/{recordId}"].patch.operationId == "updateCareRecord" and
    .paths["/api/v1/babies/{babyId}/records/{recordId}"].delete.operationId == "deleteCareRecord" and
    .paths["/api/v1/babies/{babyId}/records/{recordId}/complete"].patch.operationId == "completeCareRecord" and
    .paths["/api/v1/babies/{babyId}/records/{recordId}/cancel"].patch.operationId == "cancelCareRecord" and
    .paths["/api/v1/babies/{babyId}/growth-records"].post.operationId == "createGrowthRecord" and
    .paths["/api/v1/babies/{babyId}/growth-records/{growthRecordId}"].patch.operationId == "updateGrowthRecord" and
    .paths["/api/v1/babies/{babyId}/growth-records/{growthRecordId}"].delete.operationId == "deleteGrowthRecord"
    ' >/dev/null <<<"$openapi_doc"; then
  fail 'preflight deployed OpenAPI' 'mutation operation contract differs; refusing mutations'
  exit 1
fi
pass 'preflight deployed OpenAPI mutation paths'

api GET '/members/me'
expect_success 'preflight member profile' 200
BASE_MEMBER_ID="$(jq -r '.data.memberId // empty' <<<"$LAST_BODY")"
BASE_SELECTION="$(jq -r '.data.lastSelectedBabyId // empty' <<<"$LAST_BODY")"
MEMBER_TIMEZONE="$(jq -r '.data.timezone // "Asia/Seoul"' <<<"$LAST_BODY")"
if [[ ! "$BASE_MEMBER_ID" =~ ^[0-9]+$ || ! "$BASE_SELECTION" =~ ^[0-9]+$ ]]; then
  skip 'isolated mutations' 'BLOCKED_BY_PRECONDITION: active member or lastSelectedBabyId unavailable'
  exit 2
fi
api GET '/babies'
expect_success 'preflight original Baby list' 200
BASE_BABY_IDS="$(jq -c '[.data[].babyId] | sort' <<<"$LAST_BODY")"
BASELINE_READY=true
if ! jq -e --argjson id "$BASE_SELECTION" 'index($id) != null' >/dev/null <<<"$BASE_BABY_IDS"; then
  skip 'isolated mutations' 'BLOCKED_BY_PRECONDITION: selected Baby is not in accessible list'
  exit 2
fi
if jq -e --arg name "$TEMP_NAME" '[.data[] | select(.babyName == $name)] | length > 0' >/dev/null <<<"$LAST_BODY"; then
  fail 'preflight unique name' 'temporary Baby name already exists'
  exit 1
fi

birth_date="$(days_ago 100)"
baby_request="$(jq -nc --arg name "$TEMP_NAME" --arg date "$birth_date" \
    '{babyName:$name,birthDate:$date,gender:"FEMALE"}')"
CREATE_ATTEMPTED=true
api POST '/babies' multipart "$baby_request"
if [[ "$LAST_STATUS" == 201 ]]; then
  TEMP_BABY_ID="$(jq -r '.data.babyId // empty' <<<"$LAST_BODY")"
fi
expect_success 'POST /babies temporary create' 201
if ! valid_new_id "$TEMP_BABY_ID"; then
  fail 'temporary Baby ID guard' 'response ID is invalid, 1, or present in baseline'
  exit 1
fi
assert_data 'temporary Baby owner/name verification' \
  ".data.babyName == \"${TEMP_NAME}\" and .data.myRole == \"OWNER\""
api GET "/babies/${TEMP_BABY_ID}"
expect_success 'GET /babies/{babyId} after create' 200
assert_data 'created Baby persisted' ".data.babyId == ${TEMP_BABY_ID} and .data.babyName == \"${TEMP_NAME}\""

updated_name="${TEMP_NAME}-updated"
update_baby_request="$(jq -nc --arg name "$updated_name" '{babyName:$name}')"
api PATCH "/babies/${TEMP_BABY_ID}" multipart "$update_baby_request"
expect_success 'PATCH /babies/{babyId}' 200
api GET "/babies/${TEMP_BABY_ID}"
expect_success 'GET /babies/{babyId} after update' 200
assert_data 'updated Baby persisted' ".data.babyName == \"${updated_name}\""

recorded_at="$(hours_ago 1)"
sleep_start="$(hours_ago 3)"
sleep_end="$(hours_ago 2)"

create_completed_record() {
  local type="$1" detail="$2" payload id
  if [[ "$type" == SLEEP ]]; then
    payload="$(jq -nc --arg start "$sleep_start" --arg end "$sleep_end" \
        '{recordType:"SLEEP",startAt:$start,endAt:$end}')"
  else
    payload="$(jq -nc --arg type "$type" --arg at "$recorded_at" --argjson detail "$detail" \
        '{recordType:$type,recordedAt:$at,detail:$detail}')"
  fi
  api POST "/babies/${TEMP_BABY_ID}/records" json "$payload"
  if [[ "$LAST_STATUS" == 201 ]]; then
    id="$(jq -r '.data.recordId // empty' <<<"$LAST_BODY")"
    if [[ "$id" =~ ^[1-9][0-9]*$ ]]; then CARE_IDS+=("$id"); fi
  fi
  expect_success "POST /records ${type}" 201
  [[ "$id" =~ ^[1-9][0-9]*$ ]] || { fail "POST /records ${type}" 'recordId missing'; return 1; }
  api GET "/babies/${TEMP_BABY_ID}/records/${id}"
  expect_success "GET /records/${id} ${type}" 200
  if [[ "$type" == SLEEP ]]; then
    assert_data "SLEEP detail persisted ${id}" ".data.recordId == ${id} and .data.recordType == \"SLEEP\" and .data.status == \"COMPLETED\" and .data.detail == null"
  else
    assert_data "${type} detail persisted ${id}" ".data.recordId == ${id} and .data.recordType == \"${type}\" and .data.status == \"COMPLETED\" and .data.detail != null"
  fi
  CREATED_RECORD_ID="$id"
}

create_completed_record FEEDING '{"feedingType":"FORMULA","amountMl":120}'
assert_data 'FEEDING formula detail' '.data.detail.feedingType == "FORMULA" and .data.detail.amountMl == 120'
create_completed_record SLEEP null
create_completed_record DIAPER '{"diaperType":"URINE","condition":"normal","color":"yellow"}'
assert_data 'DIAPER detail' '.data.detail.diaperType == "URINE"'
create_completed_record TEMPERATURE '{"temperatureCelsius":36.7,"measuredPart":"EAR"}'
assert_data 'TEMPERATURE detail' '.data.detail.measuredPart == "EAR" and .data.detail.temperatureCelsius == 36.7'
create_completed_record MEDICINE '{"medicineName":"Smoke test","doseValue":2.5,"doseUnit":"ML"}'
assert_data 'MEDICINE detail' '.data.detail.medicineName == "Smoke test" and .data.detail.doseUnit == "ML"'
create_completed_record OTHER '{"title":"Smoke test"}'
other_id="$CREATED_RECORD_ID"
assert_data 'OTHER title' '.data.detail.title == "Smoke test"'

other_version="$(jq -r '.data.version' <<<"$LAST_BODY")"
edit_other="$(jq -nc --argjson version "$other_version" \
    '{version:$version,memo:"smoke updated"}')"
api PATCH "/babies/${TEMP_BABY_ID}/records/${other_id}" json "$edit_other"
expect_success 'PATCH /records/{recordId} OTHER' 200
api GET "/babies/${TEMP_BABY_ID}/records/${other_id}"
expect_success 'GET /records/{recordId} after update' 200
assert_data 'updated Care persisted' '.data.memo == "smoke updated" and .data.version > 0'

start_sleep='{"recordType":"SLEEP"}'
api POST "/babies/${TEMP_BABY_ID}/records/start" json "$start_sleep"
if [[ "$LAST_STATUS" == 201 ]]; then ACTIVE_RECORD_ID="$(jq -r '.data.recordId // empty' <<<"$LAST_BODY")"; fi
expect_success 'POST /records/start SLEEP' 201
if [[ ! "$ACTIVE_RECORD_ID" =~ ^[1-9][0-9]*$ ]]; then
  fail 'active SLEEP ID' 'recordId missing or invalid'
  exit 1
fi
assert_data 'SLEEP IN_PROGRESS' '.data.recordType == "SLEEP" and .data.status == "IN_PROGRESS"'
api GET "/babies/${TEMP_BABY_ID}/records/active"
expect_success 'GET /records/active SLEEP' 200
assert_data 'active SLEEP is the only active record' ".data.recordId == ${ACTIVE_RECORD_ID}"
api PATCH "/babies/${TEMP_BABY_ID}/records/${ACTIVE_RECORD_ID}/complete"
if [[ "$LAST_STATUS" == 200 ]]; then CARE_IDS+=("$ACTIVE_RECORD_ID"); ACTIVE_RECORD_ID=''; fi
expect_success 'PATCH /records/{recordId}/complete' 200
assert_data 'completed active SLEEP' '.data.status == "COMPLETED"'

start_direct='{"recordType":"FEEDING","detail":{"feedingType":"DIRECT_BREASTFEEDING","breastSide":"LEFT"}}'
api POST "/babies/${TEMP_BABY_ID}/records/start" json "$start_direct"
if [[ "$LAST_STATUS" == 201 ]]; then ACTIVE_RECORD_ID="$(jq -r '.data.recordId // empty' <<<"$LAST_BODY")"; fi
expect_success 'POST /records/start DIRECT_BREASTFEEDING' 201
if [[ ! "$ACTIVE_RECORD_ID" =~ ^[1-9][0-9]*$ ]]; then
  fail 'active feeding ID' 'recordId missing or invalid'
  exit 1
fi
assert_data 'DIRECT_BREASTFEEDING IN_PROGRESS' '.data.recordType == "FEEDING" and .data.detail.feedingType == "DIRECT_BREASTFEEDING" and .data.status == "IN_PROGRESS"'
api PATCH "/babies/${TEMP_BABY_ID}/records/${ACTIVE_RECORD_ID}/cancel"
if [[ "$LAST_STATUS" == 200 ]]; then ACTIVE_RECORD_ID=''; fi
expect_success 'PATCH /records/{recordId}/cancel' 200
api GET "/babies/${TEMP_BABY_ID}/records/active"
expect_success 'GET /records/active after cancel' 200
assert_data 'no active record after cancellation' '.data == null'

growth_date="$(days_ago 7)"
growth_payload="$(jq -nc --arg date "$growth_date" \
    '{measuredDate:$date,heightCm:62.0,weightKg:6.2,memo:"smoke create"}')"
api POST "/babies/${TEMP_BABY_ID}/growth-records" json "$growth_payload"
if [[ "$LAST_STATUS" == 201 ]]; then
  growth_id="$(jq -r '.data.growthRecordId // empty' <<<"$LAST_BODY")"
  if [[ "$growth_id" =~ ^[1-9][0-9]*$ ]]; then GROWTH_IDS+=("$growth_id"); fi
fi
expect_success 'POST /growth-records' 201
[[ "$growth_id" =~ ^[1-9][0-9]*$ ]] || { fail 'growth ID' 'growthRecordId missing'; exit 1; }
api GET "/babies/${TEMP_BABY_ID}/growth-records/${growth_id}"
expect_success 'GET /growth-records/{growthRecordId} after create' 200
assert_data 'Growth create persisted' '.data.heightCm == 62.0 and .data.weightKg == 6.2'
growth_version="$(jq -r '.data.version' <<<"$LAST_BODY")"
growth_update="$(jq -nc --argjson version "$growth_version" \
    '{version:$version,weightKg:6.3,memo:"smoke update"}')"
api PATCH "/babies/${TEMP_BABY_ID}/growth-records/${growth_id}" json "$growth_update"
expect_success 'PATCH /growth-records/{growthRecordId}' 200
api GET "/babies/${TEMP_BABY_ID}/growth-records/${growth_id}"
expect_success 'GET /growth-records/{growthRecordId} after update' 200
assert_data 'Growth update persisted' '.data.weightKg == 6.3 and .data.memo == "smoke update"'
growth_version="$(jq -r '.data.version' <<<"$LAST_BODY")"
api DELETE "/babies/${TEMP_BABY_ID}/growth-records/${growth_id}?version=${growth_version}"
expect_success 'DELETE /growth-records/{growthRecordId}' 200
api GET "/babies/${TEMP_BABY_ID}/growth-records/${growth_id}"
expect_not_found 'GET deleted Growth returns 404'

if [[ -z "${DEARUS_INVITATION_BASE_URL:-}" ]]; then
  skip 'POST/DELETE /invitations' 'BLOCKED_BY_ENV: DEARUS_INVITATION_BASE_URL not provided; ECS value not inferred'
else
  api POST "/babies/${TEMP_BABY_ID}/invitations"
  if [[ "$LAST_STATUS" == 201 ]]; then INVITATION_ID="$(jq -r '.data.invitationId // empty' <<<"$LAST_BODY")"; fi
  expect_success 'POST /invitations temporary Baby' 201
  if [[ ! "$INVITATION_ID" =~ ^[1-9][0-9]*$ ]]; then
    fail 'temporary Invitation ID' 'invitationId missing or invalid'
    exit 1
  fi
  assert_data 'Invitation URL and role' '.data.invitationId > 0 and .data.role == "CAREGIVER" and .data.status == "PENDING" and (.data.invitationUrl | type) == "string"'
  api GET "/babies/${TEMP_BABY_ID}/invitations"
  expect_success 'GET /invitations after create' 200
  assert_data 'temporary Invitation PENDING persisted' ".data | any(.invitationId == ${INVITATION_ID} and .status == \"PENDING\")"
  canceled_invitation_id="$INVITATION_ID"
  api DELETE "/babies/${TEMP_BABY_ID}/invitations/${INVITATION_ID}"
  if [[ "$LAST_STATUS" == 200 ]]; then INVITATION_ID=''; fi
  expect_success 'DELETE /invitations/{invitationId} temporary Baby' 200
  api GET "/babies/${TEMP_BABY_ID}/invitations"
  expect_success 'GET /invitations after cancel' 200
  assert_data 'temporary Invitation CANCELED persisted' ".data | any(.invitationId == ${canceled_invitation_id} and .status == \"CANCELED\")"
fi

# Explicitly verify record soft deletion; the EXIT trap also cleans up if any step above fails.
for care_id in "${CARE_IDS[@]}"; do
  api GET "/babies/${TEMP_BABY_ID}/records/${care_id}"
  expect_success "GET /records/${care_id} before delete" 200
  care_version="$(jq -r '.data.version' <<<"$LAST_BODY")"
  api DELETE "/babies/${TEMP_BABY_ID}/records/${care_id}?version=${care_version}"
  expect_success "DELETE /records/${care_id}" 200
  api GET "/babies/${TEMP_BABY_ID}/records/${care_id}"
  expect_not_found "GET deleted recordId=${care_id} returns 404"
done

pass 'isolated mutations completed; EXIT trap will delete temporary Baby'
