# 프론트엔드 Codex에 그대로 전달할 프롬프트 — DearUs Voice 연동

아래 내용 전체를 프론트엔드 Codex에 사용자 메시지로 복사해 전달하세요. **이 파일만 전달해도 필수 백엔드 Voice 계약은 아래에 포함되어 있습니다.** 별도로 `voice-frontend-handoff.md`를 첨부할 수 있으면 상세 참고자료로 제공하되, 백엔드 저장소에 접근할 수 있다고 가정하지 마세요.

---

DearUs React Native 프론트엔드에 **DearUs Backend의 Voice API 4개를 실제로 연결**해 주세요. 먼저 이 프론트엔드 저장소를 조사한 뒤 기존 Voice 화면이 있다면 재사용하고, 기존 API client·인증/토큰 재발급·상태 관리·녹음/파일 처리·내비게이션·테스트 패턴을 재사용하세요. Expo인지 bare React Native인지, TypeScript인지, iOS/Android의 녹음 파일 URI와 MIME이 무엇인지 **실제 코드로 확인**하세요. 없는 화면이나 라이브러리를 임의로 전제하지 마세요. 백엔드 코드는 수정하지 말고, 불필요한 새 라이브러리를 설치하지 마세요.

## 확정된 백엔드 HTTP 계약

`API_BASE_URL`을 프로젝트 기존 규칙에 맞게 정의하세요. 아래 설명에서는 `API_BASE_URL`이 `https://<API_HOST>/api/v1`처럼 **`/api/v1`까지 포함**한다고 가정하며, 함수는 `/babies/...`만 붙입니다. 기존 client가 호스트까지만 보관한다면 `/api/v1`을 딱 한 번 붙이세요. 실제 주소를 소스에 하드코딩하지 마세요. 모든 Voice API는 DearUs **Access JWT**를 `Authorization: Bearer <JWT>`로 요구합니다. Provider Token이나 Google 서비스 계정 JSON을 보내지 않습니다. 성공/실패 응답은 다음과 같습니다.

```ts
type ApiResponse<T> =
  | { success: true; data: T; message: null }
  | { success: false; data: null; message: string };

type VoiceAction = 'CREATE_COMPLETED_RECORD' | 'START_RECORD' | 'END_RECORD';
type VoiceResultStatus = 'AUTO_SAVED' | 'REVIEW_REQUIRED' |
  'CONFIRMATION_REQUIRED' | 'CONFIRMED' | 'FAILED' | 'CANCELED';

interface VoiceCommandResponse {
  voiceRequestId: number;
  status: VoiceResultStatus | null; // 서버 처리 중 null
  pending: boolean;                // status === null일 때 true
  transcript: string | null;
  action: VoiceAction | null;
  draft: Record<string, unknown> | null;
  recordId: number | null;
  failureReason: string | null;
  createdAt: string; // UTC ISO 8601 Instant
  updatedAt: string; // UTC ISO 8601 Instant
}

type ConfirmVoiceBody = Record<string, never> |
  { action: VoiceAction; draft: Record<string, unknown> };
```

`data`의 필드는 정확히 위 10개입니다. `babyId`, DB 내부 `processingStatus`, 외부 `errorCode` 필드는 응답에 없습니다. 실패 응답 예시: `{"success":false,"data":null,"message":"음성 요청을 찾을 수 없습니다."}`. HTTP 상태와 `success`를 모두 검사하세요. `draft`는 불완전할 수 있으므로 화면에서 런타임 검증/널 처리가 필요합니다. 시간 필드는 UTC ISO 8601 문자열이고 `draft.recordedAt/startAt/endAt`은 offset을 포함한 ISO 8601입니다.

| API | Method·Path (`API_BASE_URL` 뒤) | 요청 | 정상 응답 |
| --- | --- | --- | --- |
| 업로드 | `POST /babies/{babyId}/voice-commands` | `Idempotency-Key: <UUID>` 필수 헤더 + `multipart/form-data`의 필수 바이너리 `audio` part | **202 Accepted**, `Location: /api/v1/babies/{babyId}/voice-commands/{voiceRequestId}`, `ApiResponse<VoiceCommandResponse>` |
| 결과 조회 | `GET /babies/{babyId}/voice-commands/{voiceRequestId}` | body/query 없음 | **200 OK**, 같은 DTO |
| 확정 | `POST /babies/{babyId}/voice-commands/{voiceRequestId}/confirm` | `Content-Type: application/json`, body 생략/`{}` 또는 `action`+`draft` **두 필드만** 함께 | **200 OK**, 같은 DTO |
| 취소 | `POST /babies/{babyId}/voice-commands/{voiceRequestId}/cancel` | body/query 없음 | **200 OK**, 같은 DTO |

네 API 모두 `babyId`와 필요한 경우 `voiceRequestId`가 양의 int64 path ID이며 query parameter는 없습니다. 아기의 ACTIVE OWNER·CAREGIVER 모두 업로드할 수 있습니다. GET/confirm/cancel은 **업로드 요청자 본인 + 현재 ACTIVE 보호자**만 허용됩니다. 같은 아기의 다른 보호자가 요청 ID를 알아도 404입니다. JWT 누락/만료는 401, 탈퇴 회원은 403, 아기 접근 불가·요청 ID 없음은 404입니다.

### 업로드의 정확한 파일·재시도 계약

- Multipart 파일 필드명은 반드시 `audio`입니다. 별도 JSON request part가 없습니다. 허용 MIME은 `audio/mp4`, `audio/x-m4a`, `audio/m4a`, `audio/wav`, `audio/x-wav`, `audio/wave`입니다. 기본 최대 **5,242,880 bytes(5 MiB)**이며 서버 설정과 Spring multipart 제한이 추가 적용됩니다. 서버는 파일 비어 있음, MIME, WAV `RIFF`/`WAVE` 또는 M4A/MP4 `ftyp` 헤더를 검사합니다. 실제 오디오 코덱 인식은 STT 실호출 전까지 보장할 수 없습니다.
- 새 녹음마다 **정규 UUID** `Idempotency-Key`를 한 번 생성해 보관하세요. 네트워크 실패·401 재발급 후 같은 녹음을 재시도할 때 **같은 UUID, 같은 babyId, 같은 파일 바이트, 같은 MIME**을 보내세요. 같은 회원/키에 다른 아기·파일·MIME이면 409입니다. 같은 키의 재전송은 기존 요청을 반환하고 다시 분석하지 않습니다.
- React Native에서 `FormData`의 `audio`에 실제 파일 `{uri,name,type}`를 넣고, 사용하는 HTTP client가 multipart boundary를 생성하도록 하세요. `Content-Type: multipart/form-data`를 수동 고정해 boundary를 잃지 마세요. iOS `file://`, Android `content://`/`file://`, Expo 파일 객체는 실제 사용 중인 녹음 라이브러리에서 확인하세요.
- `Accept: application/json`을 설정할 수 있지만 필수 서버 검증 헤더는 아닙니다. Access JWT·오디오 원문·Google credential을 콘솔/네트워크 로그에 출력하지 마세요.

업로드 요청 형상:

```http
POST /api/v1/babies/1/voice-commands
Authorization: Bearer <DearUs Access JWT>
Idempotency-Key: 123e4567-e89b-42d3-a456-426614174000
Content-Type: multipart/form-data; boundary=<client-generated>

audio=<binary recording.wav, MIME audio/wav>
```

신규 업로드 202의 전체 응답 예시(같은 키 재호출은 이미 바뀐 상태일 수 있음):

```json
{"success":true,"data":{"voiceRequestId":101,"status":null,"pending":true,"transcript":null,"action":null,"draft":null,"recordId":null,"failureReason":null,"createdAt":"2026-10-09T00:00:00Z","updatedAt":"2026-10-09T00:00:00Z"},"message":null}
```

### 상태 조회와 사용자 확인

업로드는 **비동기**입니다. 202의 `data.voiceRequestId`를 보관하고 GET을 반복해 `pending === false`까지 기다리세요. 백엔드는 폴링 주기/최대 대기 시간을 계약하지 않았으므로 프로젝트에 맞는 backoff·로딩·사용자 취소를 구현하세요. 화면 이탈 시 타이머/요청을 정리하고, 복귀 시 저장한 `babyId`/`voiceRequestId`로 GET하세요. **Voice 요청 목록 API는 없습니다.** ID를 잃으면 기존 요청을 자동 탐색할 수 없습니다.

| `status` | 의미와 화면 처리 |
| --- | --- |
| `null`, `pending=true` | RECEIVED/TRANSCRIBED/PARSED 처리 중. 로딩 후 GET 재조회. transcript가 중간에 생길 수 있음 |
| `AUTO_SAVED` | Care 기록 이미 반영. `recordId` 있음. Home/History/active record를 재조회 |
| `REVIEW_REQUIRED` | 모호·다중 요청 또는 Care 검증 실패. `action`/`draft`가 null·불완전할 수 있음. 사용자에게 검토/수정 또는 취소 제공 |
| `CONFIRMATION_REQUIRED` | 유효한 MEDICINE 명령은 자동 저장하지 않음. 초안 수정 없이 `{}`로 확인 또는 취소 |
| `CONFIRMED` | confirm으로 Care 반영. `recordId` 있음. 화면 재조회 |
| `FAILED` | 안전한 `failureReason` 확인, 재녹음/새 UUID 요청 제안. GET 자체는 HTTP 200 |
| `CANCELED` | Care 반영 전 취소 완료. GET으로 재조회해도 CANCELED |

내부 `processingStatus`는 `RECEIVED`, `TRANSCRIBED`, `PARSED`, `COMPLETED`, `FAILED`, `CANCELED`이나 API에는 노출되지 않습니다. 프론트에서 이 필드를 읽거나 추가하지 마세요. GET 200 + `status=FAILED`를 네트워크 성공으로만 처리하지 마세요. 실패 사유 현재 값: `PROCESSING_UNAVAILABLE`, `TRANSCRIPTION_FAILED`, `EMPTY_TRANSCRIPT`, `PARSING_FAILED`, `CARE_EXECUTION_FAILED`, `PROCESSING_INTERRUPTED`. 원본 Google 오류는 노출되지 않습니다. STT·Gemini 네트워크 타임아웃 기본값은 각각 30초(서버 설정 가능)지만 전체 완료 시간은 보장되지 않습니다. 서버 중단 등으로 10분 넘게 정체되면 주기적 복구가 FAILED로 전환합니다.

GET 200 전체 응답 예시 — 확인 대기 중인 MEDICINE:

```json
{"success":true,"data":{"voiceRequestId":101,"status":"CONFIRMATION_REQUIRED","pending":false,"transcript":"해열제 2.5밀리리터 먹였어","action":"CREATE_COMPLETED_RECORD","draft":{"recordType":"MEDICINE","recordedAt":"2026-10-09T08:30:00+09:00","detail":{"medicineName":"해열제","doseValue":2.5,"doseUnit":"ML"}},"recordId":null,"failureReason":null,"createdAt":"2026-10-09T00:00:00Z","updatedAt":"2026-10-09T00:00:02Z"},"message":null}
```

confirm body는 빈 객체 `{}` 또는 `action`과 **완전한** `draft`를 함께 담은 객체입니다. `REVIEW_REQUIRED`는 유효한 기존 초안이라면 `{}`, 수정이 필요하면 `{action,draft}` 전체를 보냅니다. `CONFIRMATION_REQUIRED`는 `{}` 또는 body 생략만 가능하고 수정 body는 400입니다. body를 생략하더라도 `Content-Type: application/json`으로 호출하세요. 부분 draft PATCH, `action`만, `draft`만, 추가 필드, JSON null은 400입니다.

```json
{"action":"CREATE_COMPLETED_RECORD","draft":{"recordType":"OTHER","recordedAt":"2026-10-09T08:30:00+09:00","detail":{"title":"목욕"}}}
```

`VoiceAction`과 Care 처리:

- `CREATE_COMPLETED_RECORD`: 완료형 Care 생성, `inputType=VOICE`, 상태 `COMPLETED`. `draft`는 `recordType` FEEDING/SLEEP/DIAPER/TEMPERATURE/MEDICINE/OTHER 중 하나. SLEEP은 `startAt`,`endAt`만(상세 없음), 나머지는 `recordedAt`,`detail`; 선택 `memo`. 시간은 offset 포함 ISO 8601이며 미래 완료 기록은 불가.
- `START_RECORD`: 새 진행형 Care 생성, `inputType=VOICE`, 상태 `IN_PROGRESS`. `draft`는 `{"recordType":"SLEEP"}` 또는 `{"recordType":"FEEDING","detail":{"feedingType":"DIRECT_BREASTFEEDING","breastSide":"LEFT"}}`(LEFT/RIGHT); 선택 `memo`. Baby당 SLEEP+직접 수유 진행 기록 합계 최대 1개.
- `END_RECORD`: `draft`는 기존 진행 Care ID 하나만 담은 `{"recordId":101}`. 새 Care 기록을 만들지 않고 기존 기록을 `COMPLETED`로 전이합니다.
- 완료형 `detail`의 실제 형태: FORMULA `{feedingType:"FORMULA",amountMl:120}`, 직접 수유 `{feedingType:"DIRECT_BREASTFEEDING",breastSide:"LEFT",durationMinutes:15}`, DIAPER `{diaperType:"URINE",condition?:string|null,color?:string|null}`, TEMPERATURE `{temperatureCelsius:36.5,measuredPart:"EAR"}`, MEDICINE `{medicineName:"해열제",doseValue:2.5,doseUnit:"ML"}`, OTHER `{title:"목욕"}`. `diaperType=URINE|STOOL|BOTH`, `measuredPart=EAR|FOREHEAD|ARMPIT|OTHER`, `doseUnit=ML|MG|DROP|TABLET|OTHER`.

confirm 200 전체 응답 예시:

```json
{"success":true,"data":{"voiceRequestId":101,"status":"CONFIRMED","pending":false,"transcript":"해열제 2.5밀리리터 먹였어","action":"CREATE_COMPLETED_RECORD","draft":{"recordType":"MEDICINE","recordedAt":"2026-10-09T08:30:00+09:00","detail":{"medicineName":"해열제","doseValue":2.5,"doseUnit":"ML"}},"recordId":201,"failureReason":null,"createdAt":"2026-10-09T00:00:00Z","updatedAt":"2026-10-09T00:00:02Z"},"message":null}
```

confirm은 이미 CONFIRMED인 같은 요청에 반복 호출해도 같은 결과/recordId를 200으로 돌려주며 두 Care 기록을 만들지 않습니다. confirm/cancel이 경합하면 하나만 성공하고 다른 요청은 409일 수 있으므로 GET으로 최종 상태를 다시 읽으세요. 분석 완료 이전 confirm이나 FAILED/CANCELED/AUTO_SAVED confirm은 409입니다.

cancel은 처리 중 또는 검토/확인 대기 중인 요청만 취소합니다. body 없음. 이미 CANCELED라면 반복 호출 200, 이미 AUTO_SAVED/CONFIRMED/FAILED면 409입니다. cancel은 **Care DELETE가 아니므로 이미 저장된 기록을 되돌리지 않습니다.**

cancel 200 전체 응답 예시(전사/초안이 이미 생성된 요청에서는 이 필드들이 null이 아닐 수 있음):

```json
{"success":true,"data":{"voiceRequestId":101,"status":"CANCELED","pending":false,"transcript":null,"action":null,"draft":null,"recordId":null,"failureReason":null,"createdAt":"2026-10-09T00:00:00Z","updatedAt":"2026-10-09T00:00:01Z"},"message":null}
```

### 오류를 반드시 분리해서 처리

서버 실패 JSON은 항상 `{"success":false,"data":null,"message":"..."}` 형태이고 **`errorCode`가 없습니다.** 다음 내부 코드 이름은 원인 설명용이지 응답 필드가 아닙니다.

| HTTP / 상태 | 코드상 원인·예 | 구현 지시 |
| --- | --- | --- |
| 400 | `INVALID_VOICE_REQUEST`(ID·UUID·body), `INVALID_AUDIO`(MIME/컨테이너/빈 파일), 공통 `INVALID_REQUEST`(누락 multipart part 등) | 입력·파일 URI/part/header 점검. 수정 없이 자동 반복 금지 |
| 401 | `INVALID_AUTHENTICATION` JWT 없음/만료/무효 | 기존 앱의 Access/Refresh 재발급 흐름 재사용. 업로드 재시도 시 UUID/파일 유지 |
| 403 | `WITHDRAWN_MEMBER` 탈퇴 회원 | 계정 상태 안내 |
| 404 | `BABY_NOT_FOUND`, `VOICE_REQUEST_NOT_FOUND` 아기 권한 없음·요청 없음·다른 요청자/아기 ID | 현재 아기·요청 ID/보호자 상태 재확인 |
| 409 | `VOICE_REQUEST_CONFLICT`, Care `ACTIVE_RECORD_EXISTS` 등: 중복 UUID 다른 payload, confirm/cancel 경합, 진행 기록 제한 | GET으로 최종 Voice/Care 상태 확인. 새 녹음에만 새 UUID |
| 413 | `AUDIO_TOO_LARGE`, `PAYLOAD_TOO_LARGE` | 파일 크기/서버 multipart 제한 확인 |
| 503 | `VOICE_UNAVAILABLE` 기능 비활성 또는 제공자 adapter 없음 | Voice 업로드 일시 사용 불가 안내 |
| 500 | `INTERNAL_SERVER_ERROR` 등 예상치 못한 서버 오류 | 기존 요청이 반영됐을 수 있으므로 같은 UUID 또는 GET으로 확인 |
| GET 200 + FAILED | `failureReason`으로 안전한 STT/LLM/Care 실패 표시 | 새 사용자 시도라면 새 UUID. 동일 키는 실패 요청 재반환 |
| 415 | Content-Type 불일치는 MVC의 `consumes` 대상이지만 **현재 최종 HTTP status 실측 미검증** | 415 확정 계약에 의존하지 말고 예상 외 4xx/5xx fallback 구현 |

필수 `Idempotency-Key` 헤더 누락과 일부 미디어 타입 오류의 최종 HTTP 매핑은 실측 **미검증**입니다. 예외 handler가 예상 외 500을 반환할 가능성도 있으니 올바른 요청을 만들고 일반 5xx fallback을 구현하세요. 실제 오류 응답에 임의의 `errorCode` 필드를 기대하지 마세요.

## 구현해야 할 프론트엔드 작업

1. 실제 저장소를 분석해 기존 Voice UI, 녹음 권한/파일 URI, API client, JWT 보안 저장소, refresh single-flight, 화면 상태 관리, React Query 등 기존 캐시 방식(존재한다면)을 파악하고 짧게 계획을 세우세요. 프론트 구조를 보지 않고 파일 경로·라이브러리·Expo API를 가정하지 마세요.
2. 기존 client에 업로드/조회/confirm/cancel 서비스 함수를 추가하고 위 TypeScript 타입을 실제 응답에 맞게 정의하세요. Bearer 헤더를 기존 인증 코드에서 첨부하고 `/api/v1` 중복을 방지하세요.
3. 녹음 파일 FormData 업로드를 구현하세요. 동일 녹음의 중복 탭과 네트워크 재시도에는 같은 UUID를 유지하세요. 파일 URI·MIME·multipart boundary를 실제 iOS/Android 환경에서 검증하세요.
4. 업로드 202 → Voice ID 저장 → GET polling → 여섯 결과 상태 분기 → REVIEW 편집 또는 MEDICINE 확인 → confirm/cancel → Care/Home 갱신까지 연결하세요. 화면 이탈·복귀와 네트워크 단절, GET 200+FAILED, 401/403/404/409/413/503/5xx를 처리하세요. 분석 상태를 가짜로 완료 처리하지 마세요.
5. Care 갱신에 이미 있는 조회/캐시 무효화 구조를 사용하세요. 코드에 존재하는 경로는 `GET /api/v1/babies/{babyId}/home`, `GET /api/v1/babies/{babyId}/records?date=YYYY-MM-DD`(완료 목록, date 필수), `GET /api/v1/babies/{babyId}/records/active`(진행 중), `GET /api/v1/babies/{babyId}/records/{recordId}`(완료 상세)입니다. `START_RECORD`는 active 조회, `CREATE_COMPLETED_RECORD`/`END_RECORD`는 Home·완료 기록을 갱신하세요. 기존 Voice 화면이 없다면 현재 앱의 UI·내비게이션 패턴 안에서 연동에 필요한 최소 흐름을 구현하고, 제품 디자인 결정이 필요한 부분은 분리해 보고하세요.
6. 타입 검사, 프론트 빌드, 관련 단위/UI 테스트, 가능한 실제 기기/에뮬레이터 검증을 수행하세요. 기존 기능을 훼손하지 말고 불필요한 의존성을 추가하지 마세요. 실제 AWS DEV Voice 실호출을 하지 않았다면 **실서버 성공이라고 주장하지 마세요**.
7. 완료 보고에 수정 파일, 재사용한 기존 인프라, API 4개 연동 상태, 상태별 UI·폴링·재시도 처리, 타입 검사/빌드/테스트 결과, 실기기/DEV 테스트 여부, 남은 blocker를 구분해 적으세요.

## 미검증 배포 정보

이전 전달 정보에 따르면 AWS DEV는 Region `ap-northeast-2`, ECS cluster `dearus-dev`, service `dearus-dev-api`, task definition `dearus-dev-api:7`이며 Voice/STT/LLM 활성화와 Google Secret 주입이 구성되었다고 합니다. **현재 실행 중인 revision, 로컬 소스와 배포 OpenAPI 일치, Google STT/Gemini 실호출 성공은 확인되지 않았습니다.** 사용 가능한 DEV URL/토큰/테스트 음성 파일이 있다면 안전한 실제 호출로 검증하고, 없으면 미검증으로 보고하세요. 토큰과 Secret을 로그·커밋·공유 문서에 넣지 마세요.
