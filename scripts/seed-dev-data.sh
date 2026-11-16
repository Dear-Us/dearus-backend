#!/usr/bin/env bash
# One-time DEV fixture for the currently authenticated member.
# Re-running reuses one baby named 서아, but creates additional Care and Growth records.
# Use a dedicated test account: a real baby with the same name cannot be distinguished by this API.
# Bearer tokens sent to an HTTP endpoint are not encrypted; prefer HTTPS when available.
set -euo pipefail
set +x # Never echo the Bearer token, even when invoked with bash -x.

die() {
    printf 'Seed failed: %s\n' "$*" >&2
    exit 1
}

for tool in curl jq date; do
    command -v "$tool" >/dev/null 2>&1 || die "required command not found: $tool"
done

: "${API_BASE_URL:?Set API_BASE_URL to the DEV API URL ending in /api/v1}"
: "${DEARUS_ACCESS_TOKEN:?Set DEARUS_ACCESS_TOKEN to a DearUs Access JWT}"
API_BASE_URL="${API_BASE_URL%/}"
[[ "$API_BASE_URL" =~ ^https?://[^[:space:]]+/api/v1$ ]] || die 'API_BASE_URL must be an HTTP(S) URL ending in /api/v1'

response_file="$(mktemp)" || die 'could not create temporary response file'
trap 'rm -f "$response_file"' EXIT
API_RESPONSE=''

# call_api METHOD LABEL PATH EXPECTED_HTTP_STATUS [curl request options...]
call_api() {
    local method="$1" label="$2" path="$3" expected="$4" status message
    shift 4
    if ! status="$(curl --silent --show-error --connect-timeout 10 --max-time 45 \
        --request "$method" --header "Authorization: Bearer ${DEARUS_ACCESS_TOKEN}" \
        --header 'Accept: application/json' --output "$response_file" \
        --write-out '%{http_code}' "$@" "${API_BASE_URL}${path}")"; then
        die "$label ($method $path): network request failed"
    fi
    if [[ "$status" != "$expected" ]]; then
        message="$(jq -r '.message // "no API message"' "$response_file" 2>/dev/null || true)"
        die "$label ($method $path): HTTP $status, $message"
    fi
    if ! jq -e '.success == true' "$response_file" >/dev/null 2>&1; then
        message="$(jq -r '.message // "invalid or unsuccessful API response"' "$response_file" 2>/dev/null || true)"
        die "$label ($method $path): $message"
    fi
    API_RESPONSE="$(<"$response_file")"
}

# BSD date (macOS) and GNU date both produce dates in the member's timezone.
days_ago() {
    local count="$1"
    if TZ="$member_timezone" date -v-"$count"d '+%Y-%m-%d' 2>/dev/null; then
        return
    fi
    TZ="$member_timezone" date -d "$count days ago" '+%Y-%m-%d'
}

iso_at() {
    local day="$1" time="$2" raw zone_offset
    if raw="$(TZ="$member_timezone" date -j -f '%Y-%m-%d %H:%M:%S' \
        "$day $time" '+%Y-%m-%dT%H:%M:%S%z' 2>/dev/null)"; then
        :
    else
        raw="$(TZ="$member_timezone" date -d "$day $time" '+%Y-%m-%dT%H:%M:%S%z')"
    fi
    zone_offset="${raw: -5}"
    printf '%s%s:%s\n' "${raw:0:${#raw}-5}" "${zone_offset:0:3}" "${zone_offset:3:2}"
}

date_epoch_utc() {
    local day="$1"
    if TZ=UTC date -j -f '%Y-%m-%d %H:%M:%S' "$day 00:00:00" '+%s' 2>/dev/null; then
        return
    fi
    TZ=UTC date -d "$day 00:00:00" '+%s'
}

create_care() {
    local label="$1" payload="$2"
    call_api POST "$label" "/babies/$baby_id/records" 201 \
        --header 'Content-Type: application/json' --data "$payload"
    jq -e --arg type "$label" '.data.recordId | type == "number"' <<<"$API_RESPONSE" >/dev/null \
        || die "$label: create response did not contain recordId"
}

create_growth() {
    local label="$1" payload="$2"
    call_api POST "$label" "/babies/$baby_id/growth-records" 201 \
        --header 'Content-Type: application/json' --data "$payload"
    jq -e '.data.growthRecordId | type == "number"' <<<"$API_RESPONSE" >/dev/null \
        || die "$label: create response did not contain growthRecordId"
}

call_api GET 'Member profile' '/members/me' 200
member_id="$(jq -er '.data.memberId | select(type == "number" and . > 0)' <<<"$API_RESPONSE")" \
    || die 'Member profile did not contain a valid memberId'
member_timezone="$(jq -er '.data.timezone | select(type == "string" and length > 0)' <<<"$API_RESPONSE")" \
    || die 'Member profile did not contain a timezone'
today="$(days_ago 0)" || die "could not calculate today's date in $member_timezone"

call_api GET 'Baby list' '/babies' 200
jq -e '.data | type == "array"' <<<"$API_RESPONSE" >/dev/null \
    || die 'Baby list did not contain an array'
matches="$(jq -c '[.data[] | select(.babyName == "서아")]' <<<"$API_RESPONSE")" \
    || die 'Could not inspect the baby list'
match_count="$(jq 'length' <<<"$matches")"
if (( match_count > 1 )); then
    die 'More than one accessible baby is named 서아; refusing to seed an ambiguous baby'
fi
if (( match_count == 1 )); then
    baby_id="$(jq -er '.[0].babyId | select(type == "number" and . > 0)' <<<"$matches")" \
        || die 'Existing baby did not contain a valid babyId'
    birth_date="$(jq -er '.[0].birthDate | select(type == "string")' <<<"$matches")" \
        || die 'Existing baby did not contain birthDate'
else
    birth_date="$(days_ago 150)" # About five months old.
    baby_request="$(jq -nc --arg birthDate "$birth_date" \
        '{babyName:"서아", birthDate:$birthDate, gender:"FEMALE"}')"
    call_api POST 'Create baby' '/babies' 201 \
        --form "request=$baby_request;type=application/json"
    baby_id="$(jq -er '.data.babyId | select(type == "number" and . > 0)' <<<"$API_RESPONSE")" \
        || die 'Create baby response did not contain a valid babyId'
fi

today_epoch="$(date_epoch_utc "$today")" || die 'could not parse current date'
birth_epoch="$(date_epoch_utc "$birth_date")" || die 'could not parse baby birthDate'
age_days="$(((today_epoch - birth_epoch) / 86400))"
(( age_days >= 9 )) || die 'Existing 서아 is younger than the 10-day sample range; no records were created'

# The frontend opens the seeded baby even if the member had selected another baby.
selection="$(jq -nc --argjson babyId "$baby_id" '{babyId:$babyId}')"
call_api PATCH 'Select baby' '/members/me/last-selected-baby' 200 \
    --header 'Content-Type: application/json' --data "$selection"
jq -e --argjson babyId "$baby_id" '.data.lastSelectedBabyId == $babyId' \
    <<<"$API_RESPONSE" >/dev/null || die 'Baby selection did not match the seeded baby'

other_titles=('목욕' '산책' '터미타임')
for days_back in 9 8 7 6 5 4 3 2 1; do
    day="$(days_ago "$days_back")"

    for feeding_time in '07:30:00' '19:30:00'; do
        at="$(iso_at "$day" "$feeding_time")"
        payload="$(jq -nc --arg recordedAt "$at" \
            '{recordType:"FEEDING", recordedAt:$recordedAt, detail:{feedingType:"FORMULA", amountMl:120}}')"
        create_care 'FEEDING' "$payload"
    done
    if (( days_back % 3 == 0 )); then
        at="$(iso_at "$day" '02:15:00')"
        payload="$(jq -nc --arg recordedAt "$at" \
            '{recordType:"FEEDING", recordedAt:$recordedAt, detail:{feedingType:"DIRECT_BREASTFEEDING", breastSide:"LEFT", durationMinutes:15}}')"
        create_care 'FEEDING' "$payload"
    fi

    start="$(iso_at "$day" '13:00:00')"
    end="$(iso_at "$day" '14:30:00')"
    payload="$(jq -nc --arg startAt "$start" --arg endAt "$end" \
        '{recordType:"SLEEP", startAt:$startAt, endAt:$endAt}')"
    create_care 'SLEEP' "$payload"

    at="$(iso_at "$day" '10:20:00')"
    diaper_type='URINE'
    (( days_back % 3 == 0 )) && diaper_type='STOOL'
    payload="$(jq -nc --arg recordedAt "$at" --arg diaperType "$diaper_type" \
        '{recordType:"DIAPER", recordedAt:$recordedAt, detail:{diaperType:$diaperType}}')"
    create_care 'DIAPER' "$payload"

    if (( days_back % 2 == 1 )); then
        at="$(iso_at "$day" '09:10:00')"
        temperature='36.6'
        (( days_back == 3 )) && temperature='37.7'
        payload="$(jq -nc --arg recordedAt "$at" --argjson temperatureCelsius "$temperature" \
            '{recordType:"TEMPERATURE", recordedAt:$recordedAt, detail:{temperatureCelsius:$temperatureCelsius, measuredPart:"EAR"}}')"
        create_care 'TEMPERATURE' "$payload"
    fi

    if (( days_back == 7 || days_back == 4 || days_back == 1 )); then
        at="$(iso_at "$day" '08:15:00')"
        payload="$(jq -nc --arg recordedAt "$at" \
            '{recordType:"MEDICINE", recordedAt:$recordedAt, detail:{medicineName:"비타민D (샘플)", doseValue:1, doseUnit:"DROP"}}')"
        create_care 'MEDICINE' "$payload"
    fi

    at="$(iso_at "$day" '17:00:00')"
    title="${other_titles[$((days_back % 3))]}"
    payload="$(jq -nc --arg recordedAt "$at" --arg title "$title" \
        '{recordType:"OTHER", recordedAt:$recordedAt, detail:{title:$title}}')"
    create_care 'OTHER' "$payload"
done

# Keep at least one completed record on the member's current calendar day.
# 00:00 is always before the current local time; it also avoids a future timestamp near midnight.
today_at="$(iso_at "$today" '00:00:00')"
payload="$(jq -nc --arg recordedAt "$today_at" \
    '{recordType:"FEEDING", recordedAt:$recordedAt, detail:{feedingType:"FORMULA", amountMl:130}}')"
create_care 'FEEDING' "$payload"
payload="$(jq -nc --arg recordedAt "$today_at" \
    '{recordType:"DIAPER", recordedAt:$recordedAt, detail:{diaperType:"BOTH"}}')"
create_care 'DIAPER' "$payload"

# Five dates spread across the baby's lifetime; only supported Growth fields are sent.
growth_step="$((age_days / 5))"
(( growth_step > 0 )) || growth_step=1
growth_heights=('61.0' '62.5' '64.0' '65.3' '66.1')
growth_weights=('5.8' '6.2' '6.6' '6.9' '7.2')
for index in 0 1 2 3 4; do
    growth_day="$(days_ago "$(((4 - index) * growth_step))")"
    payload="$(jq -nc --arg measuredDate "$growth_day" \
        --argjson heightCm "${growth_heights[$index]}" \
        --argjson weightKg "${growth_weights[$index]}" \
        '{measuredDate:$measuredDate, heightCm:$heightCm, weightKg:$weightKg, memo:"DEV 화면 테스트 샘플"}')"
    create_growth 'Growth' "$payload"
done

# A run that crosses local midnight still leaves a record on the day being verified.
verification_day="$(days_ago 0)"
if [[ "$verification_day" != "$today" ]]; then
    today="$verification_day"
    today_at="$(iso_at "$today" '00:00:00')"
    payload="$(jq -nc --arg recordedAt "$today_at" \
        '{recordType:"FEEDING", recordedAt:$recordedAt, detail:{feedingType:"FORMULA", amountMl:130}}')"
    create_care 'FEEDING' "$payload"
fi

call_api GET 'Home verification' "/babies/$baby_id/home" 200
jq -e '.data.baby.babyId != null and .data.todaySummary.totalRecordCount > 0' \
    <<<"$API_RESPONSE" >/dev/null || die 'Home verification returned no current-day records'

call_api GET 'History verification' "/babies/$baby_id/records?date=$today" 200
jq -e '.data | type == "array" and length > 0' <<<"$API_RESPONSE" >/dev/null \
    || die 'History verification returned no records for today'

call_api GET 'Calendar verification' \
    "/babies/$baby_id/records/calendar?year=${today:0:4}&month=${today:5:2}" 200
jq -e '.data | type == "array" and length > 0' <<<"$API_RESPONSE" >/dev/null \
    || die 'Calendar verification returned no dated records'

call_api GET 'Daily stats verification' "/babies/$baby_id/stats/daily?date=$today" 200
jq -e '.data != null' <<<"$API_RESPONSE" >/dev/null || die 'Daily stats data is missing'
call_api GET 'Routine stats verification' "/babies/$baby_id/stats/routine?endDate=$today" 200
jq -e '.data != null' <<<"$API_RESPONSE" >/dev/null || die 'Routine stats data is missing'
call_api GET 'Growth stats verification' "/babies/$baby_id/stats/growth" 200
jq -e '.data != null' <<<"$API_RESPONSE" >/dev/null || die 'Growth stats data is missing'

call_api GET 'Growth verification' "/babies/$baby_id/growth-records" 200
jq -e '.data | type == "array" and length >= 5' <<<"$API_RESPONSE" >/dev/null \
    || die 'Growth verification returned fewer than five records'

printf 'babyId=%s\n' "$baby_id"
printf 'Home=PASS History=PASS Calendar=PASS DailyStats=PASS RoutineStats=PASS GrowthStats=PASS Growth=PASS\n'
