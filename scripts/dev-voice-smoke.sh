#!/usr/bin/env bash
# AWS DEV Voice smoke. Uses a new, uniquely named Baby; never edits or deletes seed resources.
# The temporary Baby/Care records are soft-deleted through existing APIs during cleanup.
# Optional DEARUS_VOICE_MEDICINE_AUDIO_FILE enables a separate isolated confirm-required case.
set +x
set -euo pipefail

for name in DEV_API_BASE_URL DEARUS_ACCESS_TOKEN DEARUS_VOICE_AUDIO_FILE; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Missing required environment variable: %s\n' "$name" >&2
    exit 2
  fi
done
for tool in curl jq uuidgen date wc tr sleep; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    printf 'Required tool is unavailable: %s\n' "$tool" >&2
    exit 2
  fi
done
BASE_URL="${DEV_API_BASE_URL%/}"
if [[ ! "$BASE_URL" =~ ^https?://([A-Za-z0-9.-]+)(:[0-9]+)?/api/v1$ ]] ||
    [[ "${BASH_REMATCH[1]:-}" != *dev* && "${BASH_REMATCH[1]:-}" != *DEV* ]]; then
  printf 'DEV_API_BASE_URL must be an AWS DEV http(s) URL ending in /api/v1\n' >&2
  exit 2
fi
if [[ ! "$DEARUS_ACCESS_TOKEN" =~ ^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$ ]]; then
  printf 'DEARUS_ACCESS_TOKEN must be a DearUs JWT, without a Bearer prefix\n' >&2
  exit 2
fi

mime_for() {
  local file="$1" size
  [[ -f "$file" && -r "$file" ]] || return 1
  size="$(wc -c < "$file")"
  (( size > 0 && size <= 5242880 )) || return 1
  case "$file" in
    *.[wW][aA][vV]) printf 'audio/wav' ;;
    *.[mM]4[aA]) printf 'audio/x-m4a' ;;
    *.[mM][pP]4) printf 'audio/mp4' ;;
    *) return 1 ;;
  esac
}
if ! MAIN_MIME="$(mime_for "$DEARUS_VOICE_AUDIO_FILE")"; then
  printf 'DEARUS_VOICE_AUDIO_FILE must be a readable, nonempty WAV/M4A/MP4 file up to 5 MiB\n' >&2
  exit 2
fi
if [[ -n "${DEARUS_VOICE_MEDICINE_AUDIO_FILE:-}" ]] &&
    ! MEDICINE_MIME="$(mime_for "$DEARUS_VOICE_MEDICINE_AUDIO_FILE")"; then
  printf 'DEARUS_VOICE_MEDICINE_AUDIO_FILE must be a readable WAV/M4A/MP4 file up to 5 MiB\n' >&2
  exit 2
fi

PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0
LAST_STATUS=''
LAST_BODY=''
TEMP_BABY_ID=''
TEMP_NAME="DearUsVoiceSmoke-$(date -u +%Y%m%d%H%M%S)-$$"
BASE_BABY_IDS='[]'
BASE_SELECTION=''
CREATE_ATTEMPTED=false
UPLOAD_UNCERTAIN=false
CLEANUP_OK=true
VOICE_IDS=()

pass() { printf 'PASS %s\n' "$1"; PASS_COUNT=$((PASS_COUNT + 1)); }
skip() { printf 'SKIP %s\n' "$1"; SKIP_COUNT=$((SKIP_COUNT + 1)); }
failure() {
  local label="$1" message voice_status
  message="$(jq -r 'if type == "object" then (.message // "") else "" end' <<< "$LAST_BODY" 2>/dev/null || true)"
  voice_status="$(jq -r 'if type == "object" then (.data.status // "none") else "none" end' <<< "$LAST_BODY" 2>/dev/null || true)"
  message="${message//"$DEARUS_ACCESS_TOKEN"/[REDACTED]}"
  message="${message//$'\n'/ }"
  printf 'FAIL %s: HTTP=%s message=%s voiceStatus=%s\n' "$label" "${LAST_STATUS:-none}" "${message:0:200}" "$voice_status" >&2
  FAIL_COUNT=$((FAIL_COUNT + 1))
}

# api METHOD PATH KIND PAYLOAD [MIME] [IDEMPOTENCY_KEY]. Never emit request headers or raw bodies.
api() {
  local method="$1" path="$2" kind="${3:-none}" payload="${4:-}" mime="${5:-}" key="${6:-}" raw
  LAST_STATUS='network'
  LAST_BODY=''
  case "$kind" in
    json)
      raw="$(curl -sS --connect-timeout 5 --max-time 45 -X "$method" \
        -H "Authorization: Bearer ${DEARUS_ACCESS_TOKEN}" -H 'Accept: application/json' \
        -H 'Content-Type: application/json' --data "$payload" \
        -w $'\n%{http_code}' "${BASE_URL}${path}")" || return 1 ;;
    baby)
      raw="$(curl -sS --connect-timeout 5 --max-time 45 -X "$method" \
        -H "Authorization: Bearer ${DEARUS_ACCESS_TOKEN}" -H 'Accept: application/json' \
        -F "request=${payload};type=application/json" \
        -w $'\n%{http_code}' "${BASE_URL}${path}")" || return 1 ;;
    voice)
      raw="$(curl -sS --connect-timeout 5 --max-time 45 -X "$method" \
        -H "Authorization: Bearer ${DEARUS_ACCESS_TOKEN}" -H 'Accept: application/json' \
        -H "Idempotency-Key: ${key}" -F "audio=@${payload};type=${mime}" \
        -w $'\n%{http_code}' "${BASE_URL}${path}")" || return 1 ;;
    none)
      raw="$(curl -sS --connect-timeout 5 --max-time 45 -X "$method" \
        -H "Authorization: Bearer ${DEARUS_ACCESS_TOKEN}" -H 'Accept: application/json' \
        -w $'\n%{http_code}' "${BASE_URL}${path}")" || return 1 ;;
    *) return 1 ;;
  esac
  LAST_STATUS="${raw##*$'\n'}"
  LAST_BODY="${raw%$'\n'*}"
}

expect_success() {
  local label="$1" expected="$2"
  if [[ "$LAST_STATUS" == "$expected" ]] &&
      jq -e 'type == "object" and .success == true and has("data") and has("message") and (has("errorCode") | not)' \
        >/dev/null 2>&1 <<< "$LAST_BODY"; then
    pass "$label"
  else
    failure "$label"
    return 1
  fi
}

valid_temp_id() {
  local id="$1"
  [[ "$id" =~ ^[1-9][0-9]*$ ]] &&
    jq -e --argjson id "$id" 'index($id) == null' >/dev/null <<< "$BASE_BABY_IDS"
}

recover_temp_baby() {
  local found
  [[ "$CREATE_ATTEMPTED" == true && -z "$TEMP_BABY_ID" ]] || return 0
  api GET '/babies' || return 1
  [[ "$LAST_STATUS" == 200 ]] || return 1
  found="$(jq -r --arg name "$TEMP_NAME" \
    '[.data[] | select(.babyName == $name and .myRole == "OWNER") | .babyId] | if length == 1 then .[0] else empty end' \
    <<< "$LAST_BODY")" || return 1
  valid_temp_id "$found" || return 1
  TEMP_BABY_ID="$found"
}

owned_temp_baby() {
  valid_temp_id "$TEMP_BABY_ID" || return 1
  api GET "/babies/${TEMP_BABY_ID}" || return 1
  [[ "$LAST_STATUS" == 200 ]] &&
    jq -e --arg name "$TEMP_NAME" --argjson id "$TEMP_BABY_ID" \
      '.success == true and .data.babyId == $id and .data.babyName == $name and .data.myRole == "OWNER"' \
      >/dev/null <<< "$LAST_BODY"
}

cleanup() {
  local original_exit="$1" id state record_id version record_state
  trap - EXIT
  set +e
  if [[ "$CREATE_ATTEMPTED" == true && -z "$TEMP_BABY_ID" ]]; then
    recover_temp_baby || { failure 'cleanup recover temporary Baby'; CLEANUP_OK=false; }
  fi
  if [[ -n "$TEMP_BABY_ID" ]]; then
    if ! owned_temp_baby; then
      failure 'cleanup ownership guard'
      CLEANUP_OK=false
    elif [[ "$UPLOAD_UNCERTAIN" == true ]]; then
      failure 'cleanup uncertain upload; temporary Baby preserved for manual review'
      CLEANUP_OK=false
    else
      if (( ${#VOICE_IDS[@]} > 0 )); then
        for id in "${VOICE_IDS[@]}"; do
        if ! api GET "/babies/${TEMP_BABY_ID}/voice-commands/${id}" || [[ "$LAST_STATUS" != 200 ]]; then
          failure 'cleanup Voice GET'; CLEANUP_OK=false; continue
        fi
        state="$(jq -r '.data.status // "PENDING"' <<< "$LAST_BODY")"
        if [[ "$state" == PENDING || "$state" == REVIEW_REQUIRED || "$state" == CONFIRMATION_REQUIRED ]]; then
          if api POST "/babies/${TEMP_BABY_ID}/voice-commands/${id}/cancel" &&
              [[ "$LAST_STATUS" == 200 ]] &&
              jq -e '.success == true and .data.status == "CANCELED"' >/dev/null <<< "$LAST_BODY"; then
            pass 'isolated Voice cancel/cleanup'
          else
            failure 'cleanup Voice cancel'; CLEANUP_OK=false
          fi
          continue
        fi
        if [[ "$state" == AUTO_SAVED || "$state" == CONFIRMED ]]; then
          record_id="$(jq -r '.data.recordId // empty' <<< "$LAST_BODY")"
          if [[ ! "$record_id" =~ ^[1-9][0-9]*$ ]] ||
              ! api GET "/babies/${TEMP_BABY_ID}/records/${record_id}" || [[ "$LAST_STATUS" != 200 ]]; then
            failure 'cleanup created Care lookup'; CLEANUP_OK=false; continue
          fi
          record_state="$(jq -r '.data.status // empty' <<< "$LAST_BODY")"
          version="$(jq -r '.data.version // empty' <<< "$LAST_BODY")"
          if [[ "$record_state" == IN_PROGRESS ]]; then
            api PATCH "/babies/${TEMP_BABY_ID}/records/${record_id}/cancel"
          elif [[ "$record_state" == COMPLETED && "$version" =~ ^[0-9]+$ ]]; then
            api DELETE "/babies/${TEMP_BABY_ID}/records/${record_id}?version=${version}"
          else
            LAST_STATUS='unexpected-record-state'
          fi
          if [[ "$LAST_STATUS" == 200 ]] && jq -e '.success == true' >/dev/null <<< "$LAST_BODY"; then
            pass 'cleanup isolated Care record'
          else
            failure 'cleanup isolated Care record'; CLEANUP_OK=false
          fi
        elif [[ "$state" != FAILED && "$state" != CANCELED ]]; then
          failure 'cleanup unknown Voice status'; CLEANUP_OK=false
        fi
        done
      fi
      if [[ "$CLEANUP_OK" == true ]]; then
        if owned_temp_baby && api DELETE "/babies/${TEMP_BABY_ID}" &&
            [[ "$LAST_STATUS" == 200 ]] && api GET "/babies/${TEMP_BABY_ID}" &&
            [[ "$LAST_STATUS" == 404 ]]; then
          pass 'cleanup temporary Baby'
          if api GET '/members/me' && [[ "$LAST_STATUS" == 200 ]] &&
              [[ "$(jq -r '.data.lastSelectedBabyId // "null"' <<< "$LAST_BODY")" == "$BASE_SELECTION" ]]; then
            pass 'member selection restored'
          else
            failure 'member selection after cleanup'; CLEANUP_OK=false
          fi
        else
          failure 'cleanup temporary Baby'; CLEANUP_OK=false
        fi
      fi
    fi
  fi
  printf 'SUMMARY pass=%s fail=%s skip=%s cleanup=%s\n' \
    "$PASS_COUNT" "$FAIL_COUNT" "$SKIP_COUNT" "$CLEANUP_OK"
  if (( original_exit != 0 || FAIL_COUNT > 0 )) || [[ "$CLEANUP_OK" != true ]]; then exit 1; fi
}

api GET '/members/me' || { failure 'GET /members/me'; exit 1; }
expect_success 'GET /members/me' 200 || exit 1
BASE_SELECTION="$(jq -r '.data.lastSelectedBabyId // "null"' <<< "$LAST_BODY")"
api GET '/babies' || { failure 'GET /babies'; exit 1; }
expect_success 'GET /babies' 200 || exit 1
if ! jq -e '.data | type == "array"' >/dev/null <<< "$LAST_BODY"; then
  failure 'GET /babies data array'; exit 1
fi
BASE_BABY_IDS="$(jq -c '[.data[].babyId]' <<< "$LAST_BODY")"

trap 'cleanup $?' EXIT
CREATE_ATTEMPTED=true
baby_payload="$(jq -nc --arg name "$TEMP_NAME" \
  '{babyName:$name,birthDate:"2020-01-01",gender:"FEMALE"}')"
api POST '/babies' baby "$baby_payload" || { failure 'POST /babies temporary'; exit 1; }
if [[ "$LAST_STATUS" != 201 ]]; then CREATE_ATTEMPTED=false; fi
expect_success 'POST /babies temporary' 201 || exit 1
TEMP_BABY_ID="$(jq -r '.data.babyId // empty' <<< "$LAST_BODY")"
if ! owned_temp_baby; then failure 'temporary Baby identity'; exit 1; fi
pass 'temporary Baby ownership verified'

upload_and_poll() {
  local label="$1" file="$2" mime="$3" id key attempt
  key="$(uuidgen | tr '[:upper:]' '[:lower:]')"
  UPLOAD_UNCERTAIN=true
  if ! api POST "/babies/${TEMP_BABY_ID}/voice-commands" voice "$file" "$mime" "$key"; then
    failure "$label upload"; return 1
  fi
  if [[ "$LAST_STATUS" != 202 ]]; then UPLOAD_UNCERTAIN=false; fi
  expect_success "$label upload" 202 || return 1
  id="$(jq -r '.data.voiceRequestId // empty' <<< "$LAST_BODY")"
  if [[ ! "$id" =~ ^[1-9][0-9]*$ ]]; then failure "$label voiceRequestId"; return 1; fi
  VOICE_IDS+=("$id")
  UPLOAD_UNCERTAIN=false
  for ((attempt=0; attempt<60; attempt++)); do
    if ! api GET "/babies/${TEMP_BABY_ID}/voice-commands/${id}"; then
      failure "$label GET"; return 1
    fi
    if [[ "$LAST_STATUS" != 200 ]] ||
        ! jq -e --argjson id "$id" '.success == true and .data.voiceRequestId == $id' \
          >/dev/null 2>&1 <<< "$LAST_BODY"; then
      failure "$label GET"; return 1
    fi
    if jq -e '.data.pending == false' >/dev/null <<< "$LAST_BODY"; then
      pass "$label terminal GET"
      return 0
    fi
    sleep 2
  done
  LAST_STATUS='poll-timeout'
  failure "$label terminal GET"
  return 1
}

upload_and_poll 'Voice' "$DEARUS_VOICE_AUDIO_FILE" "$MAIN_MIME" || exit 1
VOICE_STATUS="$(jq -r '.data.status // empty' <<< "$LAST_BODY")"
TRANSCRIPT="$(jq -r '.data.transcript // ""' <<< "$LAST_BODY")"
if [[ -z "$TRANSCRIPT" || "$VOICE_STATUS" == FAILED || "$VOICE_STATUS" == CANCELED ]]; then
  failure 'Voice status/transcript'; exit 1
fi
if [[ -n "${DEARUS_VOICE_EXPECTED_TEXT:-}" && "$TRANSCRIPT" != *"$DEARUS_VOICE_EXPECTED_TEXT"* ]]; then
  failure 'Voice expected transcript'; exit 1
fi
pass 'Voice transcript present'
case "$VOICE_STATUS" in
  AUTO_SAVED)
    record_id="$(jq -r '.data.recordId // empty' <<< "$LAST_BODY")"
    if [[ ! "$record_id" =~ ^[1-9][0-9]*$ ]]; then failure 'AUTO_SAVED recordId'; exit 1; fi
    api GET "/babies/${TEMP_BABY_ID}/records/${record_id}" || { failure 'AUTO_SAVED Care GET'; exit 1; }
    expect_success 'AUTO_SAVED Care GET' 200 || exit 1 ;;
  REVIEW_REQUIRED|CONFIRMATION_REQUIRED)
    pass "Voice ${VOICE_STATUS} requires user review"
    if [[ "$VOICE_STATUS" == CONFIRMATION_REQUIRED && -z "${DEARUS_VOICE_MEDICINE_AUDIO_FILE:-}" ]]; then
      skip 'medicine confirm requires a separate explicit fixture'
    fi ;;
  *) failure 'unexpected Voice status'; exit 1 ;;
esac

if [[ -n "${DEARUS_VOICE_MEDICINE_AUDIO_FILE:-}" ]]; then
  upload_and_poll 'Medicine Voice' "$DEARUS_VOICE_MEDICINE_AUDIO_FILE" "$MEDICINE_MIME" || exit 1
  if ! jq -e '.data.status == "CONFIRMATION_REQUIRED" and .data.recordId == null' \
      >/dev/null <<< "$LAST_BODY"; then
    failure 'Medicine CONFIRMATION_REQUIRED'; exit 1
  fi
  pass 'Medicine CONFIRMATION_REQUIRED, no Care record yet'
  medicine_id="${VOICE_IDS[${#VOICE_IDS[@]}-1]}"
  api POST "/babies/${TEMP_BABY_ID}/voice-commands/${medicine_id}/confirm" json '{}' || {
    failure 'Medicine confirm'; exit 1;
  }
  expect_success 'Medicine confirm' 200 || exit 1
  if ! jq -e '.data.status == "CONFIRMED" and (.data.recordId | type) == "number" and .data.recordId > 0' \
      >/dev/null <<< "$LAST_BODY"; then
    failure 'Medicine CONFIRMED recordId'; exit 1
  fi
  pass 'Medicine CONFIRMED with one Care record'
else
  skip 'dedicated medicine confirm fixture not supplied'
fi
