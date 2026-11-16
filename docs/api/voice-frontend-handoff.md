# DearUs Voice API — 프론트엔드 인수인계

기준: 2026-10-09 DearUsBack **로컬 소스**의 Controller → Service → Care adapter → DB model, Security, 예외 처리, OpenAPI, MariaDB 테스트. 이 파일은 백엔드 저장소를 볼 수 없는 프론트엔드 개발자에게 전달할 수 있는 독립 문서다. 실제 AWS DEV에서 Google STT·Gemini가 성공했다는 뜻은 아니다.

## 1. 배포 사실과 코드 계약을 분리하기

- 코드에는 Voice API 4개, Google Speech-to-Text V2·Vertex AI Gemini adapter, 비동기 처리 및 MariaDB VoiceRequest 저장이 구현되어 있다. 로컬 OpenAPI 계약 테스트는 전체 45 operation 중 Voice 4개를 확인했다.
- 전달받은 **이전 AWS DEV 배포 정보**는 Region `ap-northeast-2`, ECS cluster `dearus-dev`, service `dearus-dev-api`, task definition `dearus-dev-api:7`, Voice/STT/LLM 활성화 및 Google 서비스 계정 Secret 주입 구성이다. 이 작업에서 ECS 현재 상태·배포 이미지와 로컬 소스의 동일성·실제 Google 호출 성공은 검증하지 않았다.
- 앱에는 Google 서비스 계정 JSON, Google STT/Gemini SDK 또는 서버 Secret을 넣지 않는다. 앱은 음성 파일을 DearUs API로 보내고 결과를 조회한다.

## 2. Base URL, 인증, 공통 응답

`API_BASE_URL`을 **`.../api/v1`까지 포함한 값**으로 정의한 뒤 아래 경로 `/babies/...`를 붙인다. 예: `https://<DEV_API_HOST>/api/v1` + `/babies/1/voice-commands`. 프로젝트의 기존 Base URL 규칙이 호스트까지만 담는다면 `/api/v1`을 한 번만 붙이도록 기존 API client 규칙을 따른다. 이 문서의 호스트·ID는 실제 배포 주소가 아니라 자리표시자다.

모든 Voice 호출에 DearUs Access JWT를 `Authorization: Bearer <JWT>`로 보낸다. `Accept: application/json`을 사용하면 좋지만 필수 요청 헤더로 검증하지는 않는다. 업로드는 `multipart/form-data`, confirm은 `application/json`, GET/cancel은 body가 없다. 보호 API라 JWT 누락·만료·무효는 401이다. 아기 OWNER/CAREGIVER 모두 업로드할 수 있지만, GET/confirm/cancel은 **해당 Voice 요청을 직접 업로드한 회원**이어야 한다. 다른 보호자·다른 아기·요청 ID는 404로 숨긴다. 탈퇴 회원은 403이다.

```ts
type ApiResponse<T> =
  | { success: true; data: T; message: null }
  | { success: false; data: null; message: string };

type VoiceAction = 'CREATE_COMPLETED_RECORD' | 'START_RECORD' | 'END_RECORD';
type VoiceResultStatus = 'AUTO_SAVED' | 'REVIEW_REQUIRED' |
  'CONFIRMATION_REQUIRED' | 'CONFIRMED' | 'FAILED' | 'CANCELED';

interface VoiceCommandResponse {
  voiceRequestId: number;
  status: VoiceResultStatus | null;
  pending: boolean;
  transcript: string | null;
  action: VoiceAction | null;
  draft: Record<string, unknown> | null;
  recordId: number | null;
  failureReason: string | null;
  createdAt: string; // UTC ISO 8601 Instant, 예: 2026-10-09T00:00:00Z
  updatedAt: string; // UTC ISO 8601 Instant
}

type ConfirmVoiceBody = Record<string, never> |
  { action: VoiceAction; draft: Record<string, unknown> };
```

서버 응답 DTO의 JSON 필드는 위 10개가 전부다. `babyId`, DB의 `processingStatus`, `idempotencyKey`는 응답에 없다. 실패 JSON에도 `errorCode` 필드는 없고 `message`만 있다. 프론트는 HTTP status와 `success`를 함께 검사한다. `draft`는 LLM 산출물이므로 특히 `REVIEW_REQUIRED`에서는 누락·불완전할 수 있다. 타입을 신뢰해 바로 저장하지 말고 UI에서 확인한다.

| 응답 필드 | nullable | 해석 |
| --- | --- | --- |
| `voiceRequestId` | 아니오 | 업로드 202의 `data`에서 처음 얻음. 이후 GET/confirm/cancel에 재사용 |
| `status` | 예 | 처리 중 null, 결과 확정 후 아래 6개 enum 중 하나 |
| `pending` | 아니오 | 서버에서 `status == null`로 계산. true면 GET 재조회 |
| `transcript` | 예 | STT 전사 전 또는 STT 실패 시 null 가능 |
| `action` | 예 | 해석 실패·모호성으로 null 가능 |
| `draft` | 예 | 완전한 Care DTO라고 보장되지 않음 |
| `recordId` | 예 | Care 반영 완료 시 ID. `END_RECORD`면 새 기록이 아닌 기존 기록 ID |
| `failureReason` | 예 | `FAILED` 시 안전한 서버 사유 코드, Provider 원문 오류 아님 |
| `createdAt`, `updatedAt` | 아니오 | UTC offset을 가진 ISO 8601 시각 문자열 |

## 3. API 4개 전체 계약

네 API 모두 query parameter가 없다. Path의 `babyId`·`voiceRequestId`는 양의 int64 ID다.

### A. 업로드 — `POST /api/v1/babies/{babyId}/voice-commands`

| 위치 | 이름 | 타입 | 필수 | 검증/의미 |
| --- | --- | --- | --- | --- |
| Path | `babyId` | number | 예 | 양의 ID, ACTIVE 보호자 소속 |
| Header | `Authorization` | string | 예 | DearUs Bearer JWT |
| Header | `Idempotency-Key` | UUID string | 예 | 정규 UUID 형식. 동일 회원·키는 같은 아기/오디오/MIME일 때 기존 요청 반환; 다르면 409 |
| multipart file | `audio` | binary | 예 | 비어 있으면 안 됨. 정확한 part 이름은 `audio` |

허용 MIME: `audio/mp4`, `audio/x-m4a`, `audio/m4a`, `audio/wav`, `audio/x-wav`, `audio/wave`. 기본 최대 **5,242,880 bytes(5 MiB)**이며 `DEARUS_VOICE_MAX_AUDIO_BYTES`로 변경 가능하고 Spring multipart 제한도 있다. WAV는 `RIFF`와 `WAVE`, M4A/MP4는 `ftyp` 컨테이너 헤더를 확인한다. 파일 확장자나 MIME만 맞아도 실제 STT 인식이 보장되지는 않는다. 파일은 업로드 시 서버 메모리로 읽히며 재처리를 위한 원본 저장 API는 없다.

React Native 스타일의 요청 형상(정확한 구현은 현재 프론트 네트워크 라이브러리에 맞춘다):

```ts
const form = new FormData();
form.append('audio', { uri: recordingUri, name: 'recording.m4a', type: 'audio/x-m4a' } as any);
// POST `${API_BASE_URL}/babies/${babyId}/voice-commands`
// Authorization: Bearer <DearUs JWT>
// Idempotency-Key: <새 녹음에 대해 생성한 UUID>
// Content-Type: multipart/form-data; boundary=... 는 HTTP 클라이언트가 생성
```

성공: **202 Accepted**, `Location: /api/v1/babies/{babyId}/voice-commands/{voiceRequestId}`, `ApiResponse<VoiceCommandResponse>`.

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

202는 **접수**이지 분석 성공이 아니다. 같은 키로 같은 요청을 재전송하면 기존 진행/완료 상태가 202 본문에 올 수 있다.

### B. 결과 조회 — `GET /api/v1/babies/{babyId}/voice-commands/{voiceRequestId}`

| 위치 | 이름 | 타입 | 필수 | 의미 |
| --- | --- | --- | --- | --- |
| Path | `babyId` | number | 예 | 요청이 속한 아기 ID |
| Path | `voiceRequestId` | number | 예 | 업로드 202의 `data.voiceRequestId` |
| Header | `Authorization` | string | 예 | 요청자 본인의 DearUs Bearer JWT |

Request body 없음. 성공은 **200 OK**. `pending=true`이면 동일 URL로 다시 조회한다. 예시는 약물 명령이 명시적 확인을 기다리는 경우다.

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

GET의 HTTP 200은 조회 성공이다. `data.status="FAILED"`라면 분석은 실패했다. 예: `failureReason="PARSING_FAILED"`; `transcript`는 STT 성공 후 LLM 실패라면 남아 있을 수 있다.

### C. 확정 — `POST /api/v1/babies/{babyId}/voice-commands/{voiceRequestId}/confirm`

| 위치 | 이름 | 타입 | 필수 | 의미 |
| --- | --- | --- | --- | --- |
| Path | `babyId` | number | 예 | 요청 아기 ID |
| Path | `voiceRequestId` | number | 예 | 확인할 Voice 요청 ID |
| Header | `Authorization` | string | 예 | 요청자 본인의 DearUs Bearer JWT |
| JSON body | 전체 | object | 아니오 | `Content-Type: application/json`; body 생략 또는 `{}`이면 기존 초안 사용 |
| JSON body | `action` | VoiceAction | 수정할 때 예 | `draft`와 **함께만** 허용 |
| JSON body | `draft` | object | 수정할 때 예 | action에 맞는 전체 Care 명령. 부분 수정 아님 |

본문은 생략하거나 `{}` 또는 **정확히** `{"action":"...","draft":{...}}`다. 알 수 없는 필드·한 필드만 전달·배열/문자열/null body는 400이다. `REVIEW_REQUIRED`는 유효한 기존 초안이면 `{}`로 확인하거나 완전한 초안을 수정해 보낼 수 있다. `action/draft`가 null 또는 불완전하면 `{}` 확인은 400이므로 UI에서 수정한다. `CONFIRMATION_REQUIRED`는 수정 불가: `{}` 또는 body 생략으로만 확인한다. Content-Type은 `application/json`으로 보낸다.

```json
{}
```

REVIEW_REQUIRED 수정 예시:

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

`action`별 전체 draft 형식:

| action | draft | 실제 Care 결과 |
| --- | --- | --- |
| `CREATE_COMPLETED_RECORD` | `recordType` + SLEEP이면 `startAt`,`endAt`(detail 없음), 그 외 `recordedAt`,`detail`; 선택 `memo` | 새 `COMPLETED` CareRecord, `inputType=VOICE` |
| `START_RECORD` | SLEEP: `{"recordType":"SLEEP"}`; 직접 수유: `{"recordType":"FEEDING","detail":{"feedingType":"DIRECT_BREASTFEEDING","breastSide":"LEFT"}}`; 선택 `memo` | 새 `IN_PROGRESS` CareRecord, `inputType=VOICE` |
| `END_RECORD` | `{"recordId":101}` | 해당 아기의 기존 진행 기록을 `COMPLETED`로 전이 |

완료형 `recordType`: FEEDING/SLEEP/DIAPER/TEMPERATURE/MEDICINE/OTHER. 비수면의 `recordedAt` 및 수면의 `startAt`·`endAt`은 offset을 포함한 ISO 8601이며 미래 완료 기록·종료 이전 시작 시각은 거부된다. 세부 `detail`은 실제 Care 계약을 따른다: FORMULA `{feedingType,amountMl}`, DIRECT_BREASTFEEDING 완료형 `{feedingType,breastSide,durationMinutes}`, DIAPER `{diaperType,condition?,color?}`, TEMPERATURE `{temperatureCelsius,measuredPart}`, MEDICINE `{medicineName,doseValue,doseUnit}`, OTHER `{title}`. `diaperType=URINE|STOOL|BOTH`, `breastSide=LEFT|RIGHT|BOTH`(START는 LEFT/RIGHT만), `measuredPart=EAR|FOREHEAD|ARMPIT|OTHER`, `doseUnit=ML|MG|DROP|TABLET|OTHER`다. Care 파서는 필드 누락·불필요한 필드를 거부한다.

성공은 **200 OK**, `status=CONFIRMED`, `pending=false`, `recordId`가 존재한다. 예:

```json
{
  "success": true,
  "data": {
    "voiceRequestId": 101,
    "status": "CONFIRMED",
    "pending": false,
    "transcript": "해열제 2.5밀리리터 먹였어",
    "action": "CREATE_COMPLETED_RECORD",
    "draft": {"recordType":"MEDICINE","recordedAt":"2026-10-09T08:30:00+09:00","detail":{"medicineName":"해열제","doseValue":2.5,"doseUnit":"ML"}},
    "recordId": 201,
    "failureReason": null,
    "createdAt": "2026-10-09T00:00:00Z",
    "updatedAt": "2026-10-09T00:00:02Z"
  },
  "message": null
}
```

동일 ID의 동시·반복 confirm은 Care 기록을 중복 생성하지 않고 이미 `CONFIRMED`라면 같은 결과를 200으로 반환한다. confirm 가능 상태가 아니면 409다. confirm/cancel 경합은 하나의 최종 상태만 남는다.

### D. 취소 — `POST /api/v1/babies/{babyId}/voice-commands/{voiceRequestId}/cancel`

| 위치 | 이름 | 타입 | 필수 | 의미 |
| --- | --- | --- | --- | --- |
| Path | `babyId` | number | 예 | 요청 아기 ID |
| Path | `voiceRequestId` | number | 예 | 취소할 Voice 요청 ID |
| Header | `Authorization` | string | 예 | 요청자 본인의 DearUs Bearer JWT |

Request body 없음, 별도 Content-Type 불필요. 성공은 **200 OK**. `status=CANCELED`, `pending=false`, `recordId=null`. 이미 STT/LLM을 거쳤다면 `transcript`/`action`/`draft`가 남아 있을 수도 있다. 예:

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

처리 중·REVIEW_REQUIRED·CONFIRMATION_REQUIRED에서 취소할 수 있다. 이미 `CANCELED`라면 반복 cancel은 200으로 같은 상태를 반환한다. 이미 `AUTO_SAVED`, `CONFIRMED`, `FAILED`면 409이고 Care 기록은 취소되지 않는다. 취소 뒤 GET은 `CANCELED`를 반환한다.

## 4. 상태 전이와 Polling

| 서버 단계 | 외부 응답 | 다음 단계 |
| --- | --- | --- |
| 업로드가 새 요청 저장 | `status=null`, `pending=true`, HTTP 202 | STT 백그라운드 실행 |
| STT 성공·LLM 처리 중 | 여전히 `status=null`, `pending=true`, `transcript`는 채워질 수 있음 | GET 재조회 |
| LLM 결과가 유효한 비투약 명령 | `AUTO_SAVED`, `recordId` 있음 | Care 화면 재조회 |
| 모호·다중 명령 또는 Care 검증 실패 | `REVIEW_REQUIRED`, `recordId=null` | 사용자 검토, confirm/cancel |
| 유효한 MEDICINE | `CONFIRMATION_REQUIRED`, `recordId=null` | 사용자 확인 또는 취소 |
| confirm 성공 | `CONFIRMED`, `recordId` 있음 | Care 화면 재조회 |
| cancel 성공 | `CANCELED`, `recordId=null` | 화면 종료. GET은 여전히 가능 |
| STT/LLM 실패·빈 전사·Care 처리 실패·처리 중단 | `FAILED`, `failureReason` 있음 | 오류 UI; 새 요청 필요 |

프론트는 202에서 받은 ID를 보관하고 GET을 `pending === false`까지 폴링한다. **서버가 정한 폴링 주기·클라이언트 최대 대기 시간은 없다**. 앱은 적절한 backoff와 로딩 상태를 구현하고 화면 이탈 시 타이머/요청을 정리한다. 앱 복귀 후 같은 ID로 GET할 수 있도록 필요하다면 ID와 `babyId`를 안전한 앱 상태에 보관한다. 현재 **Voice 요청 목록 API는 없다**. ID를 잃으면 임의 조회할 수 없다. 네트워크 timeout 때 이미 202가 처리됐을 수 있으므로 업로드는 같은 UUID/파일/MIME으로만 재시도한다.

서버의 STT와 Gemini 네트워크 타임아웃은 각각 기본 30초(설정 가능)지만 전체 Voice 완료 SLA는 없다. 처리 중 서버가 중단되면 10분 이상 정체된 요청을 주기적 복구가 `FAILED/PROCESSING_INTERRUPTED`로 전이한다. Google 실제 인증·권한·음질에 따른 동작은 AWS DEV 실호출 **미검증**이다.

## 5. 오류와 프론트 대응

실패 응답 형태(예: 404):

```json
{"success":false,"data":null,"message":"음성 요청을 찾을 수 없습니다."}
```

아래 `ErrorCode`는 내부 코드로서 **JSON에 나오지 않는다**. 상태와 메시지로 안전하게 처리하고, 특정 message 문자열의 영구 안정성은 가정하지 않는다.

| HTTP / 결과 | 실제 원인·내부 코드 예 | 프론트 대응 |
| --- | --- | --- |
| 400 | `INVALID_VOICE_REQUEST`: 잘못된 ID/UUID/body; `INVALID_AUDIO`: MIME·파일 내용·크기 0; `INVALID_REQUEST`: 누락된 multipart part 등 | 파일 part 이름·UUID·요청 body 확인. 무작정 재시도하지 않음 |
| 401 | `INVALID_AUTHENTICATION`: JWT 누락/만료/무효 또는 회원 없음 | 기존 앱의 단일-flight 재발급·원 요청 제한 재시도 사용. 신규 업로드 재시도는 같은 UUID/파일 유지 |
| 403 | `WITHDRAWN_MEMBER`: 탈퇴 회원 | 계정 상태 안내, Voice 화면 중지 |
| 404 | `BABY_NOT_FOUND`, `VOICE_REQUEST_NOT_FOUND`: 아기 접근 불가, 다른 요청자·아기 ID, 존재하지 않는 요청 | 현재 아기·보호자 관계·저장한 Voice ID 재확인; 타인 요청을 노출하지 않음 |
| 409 | `VOICE_REQUEST_CONFLICT`: 중복 UUID에 다른 내용, 확정/취소 상태 충돌; Care의 `ACTIVE_RECORD_EXISTS` 등 | 같은 녹음 재시도면 원본 유지. confirm/cancel 경합이면 GET으로 최종 상태 확인. 아기 진행형 기록은 하나만 허용 |
| 413 | `AUDIO_TOO_LARGE` 또는 공통 `PAYLOAD_TOO_LARGE` | 5 MiB 기본 제한 및 multipart 크기 확인, 파일 줄이기 |
| 503 | `VOICE_UNAVAILABLE`: Voice 비활성·STT/LLM adapter 부재 | 녹음 업로드 기능 비활성/일시 사용 불가 안내 |
| 500 | 공통 `INTERNAL_SERVER_ERROR`: 예상치 못한 서버 오류 | 업로드 응답 손실이면 같은 키로 재시도, confirm/cancel은 GET으로 상태 확인 |
| GET 200 + `data.status=FAILED` | `PROCESSING_UNAVAILABLE`, `TRANSCRIPTION_FAILED`, `EMPTY_TRANSCRIPT`, `PARSING_FAILED`, `CARE_EXECUTION_FAILED`, `PROCESSING_INTERRUPTED` | 분석 실패 UI. 같은 키는 실패 요청을 재반환하므로 사용자가 새 요청을 시작할 때 새 UUID 사용 |
| 415 | Controller에 `consumes`가 있어 잘못된 Content-Type은 거부 대상이나, GlobalExceptionHandler의 최종 HTTP 변환은 실측 **미검증** | 올바른 Content-Type 사용; 415를 확정 계약으로 가정하지 말 것 |

비정상 path 타입, 필수 `Idempotency-Key` 헤더 누락, React Native multipart 라이브러리별 boundary 오류의 정확한 최종 HTTP 코드는 별도 런타임 테스트가 없어 **확인 필요**다. 서버의 일반 예외 handler가 500으로 포장할 가능성이 있으므로 UI는 예상 외 5xx도 안전하게 처리한다. 인증 오류 후 토큰이나 Provider 응답을 로그에 출력하지 않는다.

## 6. React Native / Expo 구현 가이드

프론트 저장소는 별도이므로 폴더 구조, Expo/bare 여부, 녹음 라이브러리, HTTP client, 토큰 저장소, 상태 관리 도구를 이 문서에서 확정하지 않는다. 먼저 실제 프로젝트를 조사하고 기존 방식을 재사용한다.

1. 기존 인증-aware API client에 `uploadVoice`, `getVoice`, `confirmVoice`, `cancelVoice` 네 함수를 추가한다. Base URL의 `/api/v1` 중복을 테스트한다. 필요하면 Voice 전용 서비스 파일로 묶되 기존 네트워크·토큰 처리 코드를 복제하지 않는다.
2. 네이티브 녹음 결과의 `uri`, 파일명, MIME을 실제 파일과 일치시킨다. iOS `file://`, Android `content://`/`file://`, Expo 파일 객체 처리는 선택된 라이브러리의 실제 API를 확인한다. FormData에 `audio`만 파일로 넣고 **multipart boundary를 직접 하드코딩하지 않는다**.
3. 녹음당 UUID를 한 번 생성해 업로드 성공 또는 확실한 실패까지 유지한다. 중복 탭·네트워크 재시도로 새 UUID를 남발하지 않는다. 토큰 재발급 후 업로드를 다시 시도할 때도 같은 UUID와 파일을 사용한다.
4. 업로드 중·분석 중·사용자 검토·확인 중·완료·실패·취소를 명시적 UI 상태로 다룬다. GET은 `pending`이 false가 되면 멈춘다. 컴포넌트 unmount/화면 이탈 시 폴링 타이머와 미완료 요청을 정리하고, 복귀 시 저장한 ID가 있으면 GET으로 복구한다.
5. `REVIEW_REQUIRED`에서 `action`/`draft` null·불완전 상태를 허용한다. 서버가 반환한 초안을 무조건 confirm하지 않는다. `CONFIRMATION_REQUIRED`는 수정 없이 확인 또는 취소만 제공한다.
6. `AUTO_SAVED`/`CONFIRMED`는 결과 `action`에 따라 `GET /api/v1/babies/{babyId}/home`, 완료 기록 조회 `GET /api/v1/babies/{babyId}/records?date=YYYY-MM-DD`, 진행 기록 조회 `GET /api/v1/babies/{babyId}/records/active` 등 **기존** 조회 경로를 재사용한다. 완료 기록 상세는 `GET /api/v1/babies/{babyId}/records/{recordId}`이지만 `START_RECORD`의 IN_PROGRESS 기록에는 쓰지 않는다. 날짜는 회원 timezone 기준으로 기존 앱 로직을 따른다.
7. `FAILED`나 409를 HTTP 성공/실패 하나로 뭉개지 않는다. GET 200 + FAILED는 분석 실패이고, 409는 최신 Voice/Care 상태를 GET으로 확인한다. 401은 기존 앱 토큰 재발급 정책에 맡긴다.

## 7. 프론트엔드 테스트 체크리스트

- [ ] Base URL에 `/api/v1`이 정확히 한 번만 붙고 네 호출의 Method/Path가 위 표와 같다.
- [ ] Bearer JWT가 자동 첨부되며 토큰/오디오/Provider 응답은 로그에 찍히지 않는다.
- [ ] `audio` part 이름·MIME·파일 URI·boundary가 실제 기기와 Expo/bare 환경에서 유효하다.
- [ ] 202의 ID를 보관하고 GET `pending=true` 동안 로딩, false에서 종료한다.
- [ ] `AUTO_SAVED`, `REVIEW_REQUIRED`, `CONFIRMATION_REQUIRED`, `CONFIRMED`, `FAILED`, `CANCELED` 및 status null을 모두 렌더링한다.
- [ ] REVIEW 초안 null/불완전, MEDICINE 확인, 수정 초안 전체 전송, cancel·중복 confirm을 다룬다.
- [ ] 네트워크 끊김 후 같은 UUID·파일로 재시도하고 화면 이탈 시 폴링을 정리한다.
- [ ] 401/403/404/409/413/503/5xx 및 GET 200 + FAILED를 구분한다.
- [ ] Voice 성공 후 Home/History/active record를 실제 상태에 맞게 무효화·재조회한다.
- [ ] 로컬 타입 검사·빌드·관련 UI/네트워크 테스트를 통과하고, **실제 DEV 호출을 하지 않았다면 실서버 성공이라고 보고하지 않는다**.

## 8. 남은 검증·위험 요소

- AWS DEV `dearus-dev-api:7`이 **현재** 실행 중인 버전인지, 실제 `/v3/api-docs`가 이 소스의 45 operation과 같은지, Google 자격증명·권한·STT/Gemini 실호출이 성공하는지는 **미검증**이다. 제공된 배포 정보와 코드 분석을 혼동하지 않는다.
- 실제 기기별 녹음 코덱과 provider 전사 품질, 백그라운드/앱 종료 시 네이티브 파일 URI의 수명은 프론트 실행 환경에서 확인 필요.
- 415 및 필수 헤더 누락의 최종 HTTP 매핑은 실측 미검증. 문서상 예상 status만 보고 분기하지 말고 일반적인 4xx/5xx fallback을 둔다.
- 서버는 Voice 요청 목록 API와 원본 오디오 재처리 API를 제공하지 않는다. 앱이 진행 중 요청 ID를 잃으면 해당 요청을 자동 재발견할 수 없다.
