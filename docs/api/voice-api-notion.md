# DearUs Voice API — 프론트엔드 연동 명세

기준: 2026-10-09 현재 DearUsBack 소스의 Controller, Service, DTO, 예외 처리, 보안 설정 및 OpenAPI. 서버 Base URL은 환경별로 설정한다. 이 문서는 **구현된 HTTP 계약**이며, 현재 AWS DEV에 Voice 제공자 설정이 완료되었는지는 확인 필요하다. `DEARUS_VOICE_ENABLED` 기본값은 `false`다.

## 공통 계약

- 4개 API 모두 DearUs Access Token의 `Authorization: Bearer <JWT>` 헤더가 필요하다. Provider Token을 보내지 않는다.
- `babyId`, `voiceRequestId`는 양의 정수 ID다. 네 API 모두 query parameter는 없다.
- 활성 회원이며 해당 아기의 ACTIVE 보호자(OWNER 또는 CAREGIVER)여야 한다. 업로드는 두 역할 모두 가능하다. **조회·확정·취소는 그 Voice 요청을 올린 회원 본인만 가능**하다. 같은 아기의 다른 보호자라도 요청 ID로 접근하면 404다.
- 성공 본문은 `{"success":true,"data":...,"message":null}`, 실패 본문은 `{"success":false,"data":null,"message":"..."}`다. **외부 응답에는 `errorCode` 필드가 없다.** 아래 Error 표의 코드는 서버 내부 `ErrorCode` 식별자이며 클라이언트가 받을 수 있는 JSON 필드가 아니다.
- `VoiceCommandResponse`에는 DB 내부의 `processingStatus`가 없다. 프론트는 `pending`과 `status`를 기준으로 화면을 전환한다. 예시 ID·시각은 설명용이다.

### 공통 `data` 필드 (`VoiceCommandResponse`)

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| `voiceRequestId` | number (int64) | 음성 요청 ID |
| `status` | string 또는 null | 결과 상태. 처리 중에는 null |
| `pending` | boolean | `status == null`이면 true, 결과가 정해지면 false |
| `transcript` | string 또는 null | STT 전사 결과. 전사 전·전사 실패 시 null 가능 |
| `action` | string 또는 null | `CREATE_COMPLETED_RECORD`, `START_RECORD`, `END_RECORD` 중 하나. 해석하지 못한 경우 null 가능 |
| `draft` | object 또는 null | Care 명령 초안. 해석하지 못한 경우 null 가능 |
| `recordId` | number (int64) 또는 null | 실제 Care 기록 ID. Care 반영 전·실패·취소 시 null |
| `failureReason` | string 또는 null | `FAILED`의 안전한 사유 코드. Provider 원문 오류가 아님 |
| `createdAt` | string (UTC ISO 8601) | 음성 요청 생성 시각 |
| `updatedAt` | string (UTC ISO 8601) | 마지막 상태 변경 시각 |

### 결과 상태

| `status` | `pending` | 프론트 동작 |
| --- | --- | --- |
| `null` | `true` | 서버 처리 중. 동일 요청 ID로 GET 재조회 |
| `AUTO_SAVED` | `false` | Care 반영 완료. `recordId` 확인 후 필요한 화면 재조회 |
| `REVIEW_REQUIRED` | `false` | 초안 검토 필요. `action`/`draft`가 null일 수도 있음. 유효한 초안으로 confirm하거나 cancel |
| `CONFIRMATION_REQUIRED` | `false` | 주로 유효한 MEDICINE 명령. 초안 수정 없이 confirm 또는 cancel |
| `CONFIRMED` | `false` | 사용자가 확인하여 Care 반영 완료. `recordId` 확인 |
| `FAILED` | `false` | 처리 실패. `failureReason` 확인, 사용자에게 재녹음·새 요청 제안 |
| `CANCELED` | `false` | Voice 요청 취소 완료. Care 기록 미생성 |

서버 내부 `VoiceProcessingStatus`는 `RECEIVED → TRANSCRIBED → PARSED → COMPLETED` 또는 `FAILED`/`CANCELED`이지만 API 응답 필드가 아니다. 내부 `COMPLETED`라도 결과는 `REVIEW_REQUIRED` 또는 `CONFIRMATION_REQUIRED`일 수 있다.

### 음성 파일과 분석

| 항목 | 실제 구현 |
| --- | --- |
| multipart part | `audio` 하나, 필수, 바이너리 파일 |
| 허용 MIME | `audio/mp4`, `audio/x-m4a`, `audio/m4a`, `audio/wav`, `audio/x-wav`, `audio/wave` |
| 크기 | 기본 최대 5,242,880바이트(5 MiB), 서버 설정으로 변경 가능. Spring multipart 제한도 적용 |
| 기본 검증 | 파일 비어 있음 여부, MIME, WAV의 `RIFF`/`WAVE` 또는 MP4 계열의 `ftyp` 헤더. 실제 코덱·음질과 STT 인식 가능 여부는 이 검증만으로 보장되지 않음 |
| 처리 방식 | 업로드 접수 후 **비동기** STT → Gemini 파싱 → Care 검증/반영. 최초 응답은 202이며 완료를 기다리지 않음 |
| 제공자 장애 | 접수 뒤 STT/LLM 오류·타임아웃은 보통 GET의 `status: "FAILED"`와 안전한 `failureReason`으로 확인. 업로드 시 기능 비활성/어댑터 부재는 503 |

`failureReason`에 현재 코드가 기록하는 값은 `PROCESSING_UNAVAILABLE`, `TRANSCRIPTION_FAILED`, `EMPTY_TRANSCRIPT`, `PARSING_FAILED`, `CARE_EXECUTION_FAILED`, `PROCESSING_INTERRUPTED`다. STT와 Gemini 각각 기본 네트워크 타임아웃은 30초(설정 가능)지만 **전체 처리 완료 시간·폴링 간격·최대 폴링 횟수는 API 계약으로 정해져 있지 않다.** 미완료 요청이 10분 넘게 정체되면 주기적 복구 작업이 `PROCESSING_INTERRUPTED`로 실패 처리한다. 앱은 자체 타임아웃을 표시하되 서버 결과를 임의로 성공 처리하지 않는다.

## 전체 연동 흐름

1. 앱에서 마이크 권한을 얻고 녹음을 종료하여 위 형식의 파일을 준비한다. Google 서비스 계정 자격증명이나 STT SDK를 앱에 넣지 않는다.
2. 새 녹음마다 UUID `Idempotency-Key`를 만들어 보관하고 `POST /voice-commands`에 파일을 보낸다. 네트워크 재시도라면 **같은 UUID·같은 아기·같은 파일·같은 MIME**을 재사용한다. 같은 키에 다른 내용은 409다. 같은 키 재전송은 기존 요청을 반환하며 재분석하지 않는다.
3. 202 본문의 `voiceRequestId` 또는 `Location`으로 GET을 반복한다. `pending=true` 동안 로딩을 보여 준다. 폴링 주기와 종료 정책은 앱에서 정한다.
4. `AUTO_SAVED`면 Care 기록이 이미 반영됐다. `recordId`를 사용하거나 Home/History/active record를 재조회한다. `START_RECORD`라면 기록은 `IN_PROGRESS`, `END_RECORD`라면 **기존** 진행 기록이 완료되고, `CREATE_COMPLETED_RECORD`라면 새 완료 기록이 생성된다.
5. `REVIEW_REQUIRED`면 전사·`action`·`draft`를 사용자에게 검토시킨다. 수정하려면 **완전한 유효 초안 전체**를 `action`과 함께 보낸다. 별도 초안 저장 API는 없다. `CONFIRMATION_REQUIRED`면 기존 초안 그대로 확인할 수 있으며 수정 요청은 400이다.
6. 사용자가 확인하면 confirm, 반영 전에 포기하면 cancel한다. 성공 후 Care 화면을 재조회한다. `AUTO_SAVED` 또는 `CONFIRMED` 이후 cancel은 409이고 Care 기록을 되돌리지 않는다.
7. `FAILED`라면 `failureReason`을 참고해 실패 UI를 보여 준다. 같은 키 재시도는 동일한 실패 요청을 돌려주므로 새로운 녹음/요청은 새 UUID로 시작한다.

한 Voice 요청은 Care 기록을 최대 한 번 반영한다. 같은 요청의 동시 confirm은 DB 잠금으로 같은 `recordId`를 반환하고, confirm과 cancel이 경합하면 하나의 최종 상태만 유효하다. 아기별 `SLEEP`·`DIRECT_BREASTFEEDING` 진행 기록은 합쳐서 최대 1개이므로 START 충돌 시 Care 409가 가능하다.

## 1. 음성 파일 업로드 및 분석 요청

**기본 정보**

| 항목 | 값 |
| --- | --- |
| 기능 | 음성을 접수하고 백그라운드 분석 시작 |
| HTTP Method | `POST` |
| Endpoint | `/api/v1/babies/{babyId}/voice-commands` |
| 인증 | DearUs Bearer JWT 필수 |
| Content-Type | `multipart/form-data` |
| 성공 상태 | `202 Accepted`; `Location: /api/v1/babies/{babyId}/voice-commands/{voiceRequestId}` |

**Request**

| 위치 | 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| Path | `babyId` | number (int64) | 예 | ACTIVE 보호자로 연결된 아기 ID |
| Header | `Authorization` | string | 예 | `Bearer <DearUs Access Token>` |
| Header | `Idempotency-Key` | UUID string | 예 | 새 녹음마다 새 UUID; 동일 요청 재시도 시 재사용 |
| Multipart body | `audio` | binary file | 예 | 위 MIME/크기/컨테이너 조건을 만족하는 파일 |

Query parameter 없음. JSON request body 없음. multipart의 `Content-Type` boundary는 HTTP 클라이언트가 생성하게 둔다.

```bash
curl -X POST "$API_BASE_URL/api/v1/babies/1/voice-commands" \
  -H "Authorization: Bearer $DEARUS_ACCESS_TOKEN" \
  -H 'Idempotency-Key: 123e4567-e89b-42d3-a456-426614174000' \
  -F 'audio=@recording.wav;type=audio/wav'
```

**Response**

202 본문의 `data`는 위 공통 10개 필드를 모두 가진다. 신규 접수의 대표 예시는 다음과 같다. 같은 키의 재전송이라면 이미 갱신된 기존 요청 상태가 올 수도 있다.

```json
{
  "success": true,
  "data": {
    "voiceRequestId": 101,
    "status": null,
    "pending": true,
    "transcript": null,
    "action": null,
    "draft": null,
    "recordId": null,
    "failureReason": null,
    "createdAt": "2026-10-09T00:00:00Z",
    "updatedAt": "2026-10-09T00:00:00Z"
  },
  "message": null
}
```

주요 실패 응답 예시(503):

```json
{"success":false,"data":null,"message":"음성 기능을 사용할 수 없습니다."}
```

**Error** — 아래 코드는 내부 식별자이며 응답 JSON에 포함되지 않는다.

| HTTP | 내부 ErrorCode | 발생 조건 | 프론트 처리 |
| --- | --- | --- | --- |
| 400 | `INVALID_VOICE_REQUEST`, `INVALID_AUDIO`, `INVALID_REQUEST` | UUID·ID·MIME·파일/part 형식 오류 | 요청 구성을 확인하고 재녹음 유도 |
| 401 | `INVALID_AUTHENTICATION` | JWT 없음·만료·무효, 회원 없음 | 기존 토큰 재발급 흐름 후 제한된 재시도 |
| 403 | `WITHDRAWN_MEMBER` | 탈퇴 회원 | 로그인/계정 상태 안내 |
| 404 | `BABY_NOT_FOUND` | 아기 없음 또는 ACTIVE 보호자 아님 | 아기 목록·권한 재조회 |
| 409 | `VOICE_REQUEST_CONFLICT` | 동일 회원·UUID에 다른 아기/내용/MIME | 새 요청이면 새 UUID 사용; 단순 재시도면 원본 유지 |
| 413 | `AUDIO_TOO_LARGE`, `PAYLOAD_TOO_LARGE` | 서비스 설정 또는 multipart 용량 제한 초과 | 파일 줄여서 새로 업로드 |
| 503 | `VOICE_UNAVAILABLE` | Voice 비활성화 또는 STT/LLM 어댑터 없음 | 기능 사용 불가 안내 |
| 500 | `INTERNAL_SERVER_ERROR` 등 | 예상치 못한 서버 오류 | 성공으로 간주하지 말고 상태 확인/재시도 정책 적용 |

**프론트엔드 연동 참고:** 업로드가 202로 접수됐다고 분석 성공은 아니다. `voiceRequestId`를 보관하고 GET을 시작한다. 네트워크 응답을 잃었다면 같은 파일·MIME·UUID로 재전송해 기존 요청을 찾는다. 토큰·음성 원문·Provider 응답을 로그에 기록하지 않는다.

## 2. 음성 분석 상태 및 결과 조회

**기본 정보**

| 항목 | 값 |
| --- | --- |
| 기능 | 요청 상태, STT 텍스트, Care 초안·결과 조회 |
| HTTP Method | `GET` |
| Endpoint | `/api/v1/babies/{babyId}/voice-commands/{voiceRequestId}` |
| 인증 | DearUs Bearer JWT 필수. 업로드한 회원 본인 + ACTIVE 보호자 |
| Content-Type | 요청 body 없음; 성공 응답 `application/json` |
| 성공 상태 | `200 OK` |

**Request**

| 위치 | 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| Path | `babyId` | number (int64) | 예 | 요청이 속한 아기 ID |
| Path | `voiceRequestId` | number (int64) | 예 | 업로드 응답의 ID |
| Header | `Authorization` | string | 예 | DearUs Bearer JWT |

Query parameter 및 request body 없음.

```http
GET /api/v1/babies/1/voice-commands/101
Authorization: Bearer <DearUs Access Token>
```

**Response**

`data`의 모든 필드와 타입은 공통 `VoiceCommandResponse` 표와 동일하다. 200은 **조회 성공**을 뜻하며 분석 성공과 다르다. 아래는 MEDICINE이 확인을 기다리는 예시다.

```json
{
  "success": true,
  "data": {
    "voiceRequestId": 101,
    "status": "CONFIRMATION_REQUIRED",
    "pending": false,
    "transcript": "해열제 2.5밀리리터 먹였어",
    "action": "CREATE_COMPLETED_RECORD",
    "draft": {
      "recordType": "MEDICINE",
      "recordedAt": "2026-10-09T08:30:00+09:00",
      "detail": {"medicineName":"해열제","doseValue":2.5,"doseUnit":"ML"}
    },
    "recordId": null,
    "failureReason": null,
    "createdAt": "2026-10-09T00:00:00Z",
    "updatedAt": "2026-10-09T00:00:02Z"
  },
  "message": null
}
```

FAILED 예시도 HTTP 200이며 `data.status`만 다르다:

```json
{"success":true,"data":{"voiceRequestId":101,"status":"FAILED","pending":false,"transcript":null,"action":null,"draft":null,"recordId":null,"failureReason":"TRANSCRIPTION_FAILED","createdAt":"2026-10-09T00:00:00Z","updatedAt":"2026-10-09T00:00:02Z"},"message":null}
```

주요 실패 응답 예시(404):

```json
{"success":false,"data":null,"message":"음성 요청을 찾을 수 없습니다."}
```

**Error**

| HTTP | 내부 ErrorCode | 발생 조건 | 프론트 처리 |
| --- | --- | --- | --- |
| 400 | `INVALID_VOICE_REQUEST`, `INVALID_REQUEST` | ID가 양수가 아니거나 path 형식 오류 | URL 구성 점검 |
| 401 | `INVALID_AUTHENTICATION` | JWT 없음·만료·무효 | 토큰 재발급 흐름 |
| 403 | `WITHDRAWN_MEMBER` | 탈퇴 회원 | 계정 상태 안내 |
| 404 | `BABY_NOT_FOUND`, `VOICE_REQUEST_NOT_FOUND` | 아기 접근 불가, 요청 없음, 요청자 아닌 회원, 다른 아기 ID | 현재 아기/요청 ID 확인; 타인 데이터로 취급하지 않음 |
| 500 | `INTERNAL_SERVER_ERROR` | 예상치 못한 서버 오류 | 재조회 가능 여부 판단 |

**프론트엔드 연동 참고:** `pending=true` 동안 로딩 및 재조회. `pending=false`가 되면 상태별 UI로 전환한다. `REVIEW_REQUIRED`의 `action` 또는 `draft`가 null일 수 있으므로 자동 confirm하지 않는다. `AUTO_SAVED`/`CONFIRMED` 이후 Home·History·진행 기록 화면을 다시 조회한다. Provider 오류 본문은 반환되지 않는다.

## 3. 분석된 음성 기록 확정

**기본 정보**

| 항목 | 값 |
| --- | --- |
| 기능 | 확인이 필요한 Voice 초안을 Care에 한 번 반영 |
| HTTP Method | `POST` |
| Endpoint | `/api/v1/babies/{babyId}/voice-commands/{voiceRequestId}/confirm` |
| 인증 | DearUs Bearer JWT 필수. 요청자 본인 + ACTIVE 보호자 |
| Content-Type | `application/json` |
| 성공 상태 | `200 OK` |

**Request**

| 위치 | 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| Path | `babyId` | number (int64) | 예 | 요청이 속한 아기 ID |
| Path | `voiceRequestId` | number (int64) | 예 | 확인할 Voice 요청 ID |
| Header | `Authorization` | string | 예 | DearUs Bearer JWT |
| JSON body | 전체 | object 또는 생략 | 아니오 | `{}`면 저장된 초안 그대로 사용. `{action,draft}` 두 필드만 함께 보내 수정 가능 |
| JSON body | `action` | enum string | 수정 시 예 | `CREATE_COMPLETED_RECORD`, `START_RECORD`, `END_RECORD` |
| JSON body | `draft` | object | 수정 시 예 | 선택한 `action`에 맞는 **전체** Care 명령 초안. 부분 PATCH 아님 |

Query parameter 없음. 본문을 생략할 수도 있으나 `consumes=application/json`이므로 호출 시 해당 Content-Type을 유지하고, 보통 `{}`를 보낸다. 본문에 알 수 없는 필드, `action`만 또는 `draft`만 전달하면 400이다.

| 상태 | 허용 요청 |
| --- | --- |
| `REVIEW_REQUIRED` | 유효한 기존 초안이면 `{}` 또는 body 생략. 수정하려면 `action`과 `draft`를 **동시에** 전달 |
| `CONFIRMATION_REQUIRED` | `{}` 또는 body 생략만 가능. 수정 body는 400 |
| `CONFIRMED` | 반복 confirm은 같은 결과를 200으로 반환. 새로운 수정으로 해석되지 않음 |
| 그 외 | 409 충돌 |

저장된 초안 그대로 확인:

```json
{}
```

`REVIEW_REQUIRED`에서 완전한 초안으로 수정하는 예시:

```json
{
  "action": "CREATE_COMPLETED_RECORD",
  "draft": {
    "recordType": "OTHER",
    "recordedAt": "2026-10-09T08:30:00+09:00",
    "detail": {"title":"목욕"}
  }
}
```

`draft`는 action별로 다음 **실제 Care 파서**를 따른다. 서버는 추가 필드와 누락 필드를 거부한다.

| action | draft 핵심 형태 | 제약 |
| --- | --- | --- |
| `CREATE_COMPLETED_RECORD` | `recordType` + `recordedAt`/`detail` 또는 SLEEP의 `startAt`/`endAt`; 선택 `memo` | `recordType`: FEEDING/SLEEP/DIAPER/TEMPERATURE/MEDICINE/OTHER. 시각은 offset이 있는 ISO 8601; 미래 시각 금지 |
| `START_RECORD` | `{"recordType":"SLEEP"}` 또는 `{"recordType":"FEEDING","detail":{"feedingType":"DIRECT_BREASTFEEDING","breastSide":"LEFT"}}`; 선택 `memo` | FEEDING의 `breastSide`는 LEFT/RIGHT. 아기당 진행 기록 1개 제한 |
| `END_RECORD` | `{"recordId":101}` | 양의 Care 기록 ID. 해당 아기의 진행형 기록이어야 함 |

세부 `detail` 필드:

| recordType | `detail` |
| --- | --- |
| FEEDING / FORMULA | `{"feedingType":"FORMULA","amountMl":120}` |
| FEEDING / DIRECT_BREASTFEEDING | `{"feedingType":"DIRECT_BREASTFEEDING","breastSide":"LEFT","durationMinutes":15}` (완료형) |
| DIAPER | `{"diaperType":"URINE"}`; `diaperType`은 URINE/STOOL/BOTH, 선택 `condition`·`color` |
| TEMPERATURE | `{"temperatureCelsius":36.5,"measuredPart":"EAR"}`; 위치는 EAR/FOREHEAD/ARMPIT/OTHER |
| MEDICINE | `{"medicineName":"해열제","doseValue":2.5,"doseUnit":"ML"}`; 단위 ML/MG/DROP/TABLET/OTHER |
| OTHER | `{"title":"목욕"}` |
| SLEEP | `detail` 없음; `startAt`·`endAt` 필수 |

**Response**

`data`는 공통 `VoiceCommandResponse` 10개 필드다. 성공 시 `status=CONFIRMED`, `pending=false`, `recordId`는 실제 Care ID다. `START_RECORD`는 새 IN_PROGRESS 기록을 만들고, `END_RECORD`는 기존 기록을 COMPLETED로 변경하며, 완료형은 새 COMPLETED 기록을 만든다. MEDICINE은 confirm 전에는 생성되지 않는다.

```json
{
  "success": true,
  "data": {
    "voiceRequestId": 101,
    "status": "CONFIRMED",
    "pending": false,
    "transcript": "해열제 2.5밀리리터 먹였어",
    "action": "CREATE_COMPLETED_RECORD",
    "draft": {
      "recordType": "MEDICINE",
      "recordedAt": "2026-10-09T08:30:00+09:00",
      "detail": {"medicineName":"해열제","doseValue":2.5,"doseUnit":"ML"}
    },
    "recordId": 201,
    "failureReason": null,
    "createdAt": "2026-10-09T00:00:00Z",
    "updatedAt": "2026-10-09T00:00:02Z"
  },
  "message": null
}
```

주요 실패 응답 예시(409):

```json
{"success":false,"data":null,"message":"음성 요청 상태가 충돌합니다."}
```

**Error**

| HTTP | 내부 ErrorCode | 발생 조건 | 프론트 처리 |
| --- | --- | --- | --- |
| 400 | `INVALID_VOICE_REQUEST`, `INVALID_RECORD`, `INVALID_REQUEST` | body 형태·action·draft 오류, 저장된 초안도 유효하지 않음 | 초안 전체를 수정하거나 재녹음 |
| 401 | `INVALID_AUTHENTICATION` | JWT 없음·만료·무효 | 토큰 재발급 흐름 |
| 403 | `WITHDRAWN_MEMBER` | 탈퇴 회원 | 계정 상태 안내 |
| 404 | `BABY_NOT_FOUND`, `VOICE_REQUEST_NOT_FOUND`, `RECORD_NOT_FOUND` | 아기/Voice 접근 불가 또는 END_RECORD 대상 없음 | 해당 아기·진행 기록 재조회 |
| 409 | `VOICE_REQUEST_CONFLICT`, `ACTIVE_RECORD_EXISTS`, `RECORD_ALREADY_FINISHED`, `CONCURRENT_REQUEST` | 상태 충돌, confirm/cancel 경합, 진행 기록 제한·종료 경합 | GET 최신 Voice/Care 상태 재조회. 동일 요청의 confirm 반복은 동일 recordId 가능 |
| 500 | `INTERNAL_SERVER_ERROR` | 예상치 못한 서버 오류 | GET으로 반영 여부 확인 후 재시도 판단 |

**프론트엔드 연동 참고:** 확인 버튼 중복 탭을 막되 네트워크 응답 손실 후 **동일 Voice ID confirm 재호출은 멱등**이다. `CONFIRMED`에서 다시 호출해도 같은 `recordId`를 반환한다. confirm/cancel이 동시에 경합하면 하나만 성공하므로 409 시 GET으로 최종 상태를 확인한다. 성공 후 Home/History 또는 진행 기록을 재조회한다.

## 4. 음성 요청 취소

**기본 정보**

| 항목 | 값 |
| --- | --- |
| 기능 | Care 반영 전 Voice 요청을 취소 |
| HTTP Method | `POST` |
| Endpoint | `/api/v1/babies/{babyId}/voice-commands/{voiceRequestId}/cancel` |
| 인증 | DearUs Bearer JWT 필수. 요청자 본인 + ACTIVE 보호자 |
| Content-Type | 요청 body 없음; 성공 응답 `application/json` |
| 성공 상태 | `200 OK` |

**Request**

| 위치 | 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- | --- |
| Path | `babyId` | number (int64) | 예 | 요청이 속한 아기 ID |
| Path | `voiceRequestId` | number (int64) | 예 | 취소할 Voice 요청 ID |
| Header | `Authorization` | string | 예 | DearUs Bearer JWT |

Query parameter 및 request body 없음.

```http
POST /api/v1/babies/1/voice-commands/101/cancel
Authorization: Bearer <DearUs Access Token>
```

**Response**

`data`는 공통 `VoiceCommandResponse` 10개 필드다. 취소 성공 시 `status=CANCELED`, `pending=false`, `recordId=null`. 전사나 초안이 이미 만들어졌다면 `transcript`/`action`/`draft`는 남아 있을 수 있으므로 아래 null 예시를 모든 취소의 고정값으로 가정하지 않는다.

```json
{
  "success": true,
  "data": {
    "voiceRequestId": 101,
    "status": "CANCELED",
    "pending": false,
    "transcript": null,
    "action": null,
    "draft": null,
    "recordId": null,
    "failureReason": null,
    "createdAt": "2026-10-09T00:00:00Z",
    "updatedAt": "2026-10-09T00:00:01Z"
  },
  "message": null
}
```

주요 실패 응답 예시(409):

```json
{"success":false,"data":null,"message":"음성 요청 상태가 충돌합니다."}
```

**Error**

| HTTP | 내부 ErrorCode | 발생 조건 | 프론트 처리 |
| --- | --- | --- | --- |
| 400 | `INVALID_VOICE_REQUEST`, `INVALID_REQUEST` | ID가 양수가 아니거나 path 형식 오류 | URL 구성 점검 |
| 401 | `INVALID_AUTHENTICATION` | JWT 없음·만료·무효 | 토큰 재발급 흐름 |
| 403 | `WITHDRAWN_MEMBER` | 탈퇴 회원 | 계정 상태 안내 |
| 404 | `BABY_NOT_FOUND`, `VOICE_REQUEST_NOT_FOUND` | 아기/Voice 접근 불가, 요청자 아님 | 아기·요청 ID 확인 |
| 409 | `VOICE_REQUEST_CONFLICT`, `CONCURRENT_REQUEST` | 이미 AUTO_SAVED/CONFIRMED/FAILED이거나 confirm 경합 | GET으로 최종 상태 확인. Care 반영 후 취소 불가 |
| 500 | `INTERNAL_SERVER_ERROR` | 예상치 못한 서버 오류 | GET으로 최종 상태 확인 |

**프론트엔드 연동 참고:** 접수 직후 처리 중(`pending=true`)과 REVIEW/CONFIRMATION 단계에서는 취소 가능하다. 이미 `CANCELED`인 요청의 반복 cancel은 200으로 같은 상태를 반환한다. cancel은 Care DELETE가 아니며 이미 저장된 기록을 제거하지 않는다. 사용자가 취소한 뒤에도 GET에서는 요청이 `CANCELED`로 보인다.

## 구현·배포 확인이 필요한 항목

- 현재 AWS DEV에서 `DEARUS_VOICE_ENABLED`, STT/LLM 활성화, Google 프로젝트·권한·자격증명이 실제로 설정됐는지: **확인 필요**. 로컬 계약 테스트 통과만으로 Google 실호출 성공을 보장하지 않는다.
- 실제 모바일 오디오 코덱별 STT 인식 품질·전체 처리 시간: **확인 필요**. 서버는 MIME/컨테이너 헤더까지만 사전 검증한다.
- 앱 폴링 간격, 화면 대기 타임아웃, 실패 후 사용자 재시도 UX: 백엔드 계약에 고정값 없음. 프론트 정책으로 결정한다.
