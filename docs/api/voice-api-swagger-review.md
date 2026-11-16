# Voice Swagger / OpenAPI 검수 결과

검수 기준: 2026-10-09 현재 DearUsBack 소스. 비교 대상은 `VoiceCommandController`, `VoiceCommandResponse`, `VoiceWorkflowService`, `VoiceRequestTransactions`, Care 명령 파서/어댑터, `SecurityConfiguration`, `GlobalExceptionHandler`, `OpenApiContractCustomizer`, `/v3/api-docs`를 검사하는 `OpenApiContractTest`다. AWS 배포본의 OpenAPI가 최신 소스와 같은지는 이번 작업에서 확인하지 않았다.

## 실제 코드와 OpenAPI 대응

| operationId | 실제 Method·Path | 인증·권한 | 요청 | 정상 응답 | 검수 |
| --- | --- | --- | --- | --- | --- |
| `uploadVoiceCommand` | `POST /api/v1/babies/{babyId}/voice-commands` | Bearer JWT, ACTIVE 보호자 | `Idempotency-Key` UUID 헤더 + 필수 multipart `audio` 바이너리 | 202, `Location` 헤더, `ApiResponse<VoiceCommandResponse>` | 일치 |
| `getVoiceCommand` | `GET /api/v1/babies/{babyId}/voice-commands/{voiceRequestId}` | Bearer JWT, ACTIVE 보호자이면서 요청자 본인 | body/query 없음 | 200, 같은 응답 DTO | 일치 |
| `confirmVoiceCommand` | `POST /api/v1/babies/{babyId}/voice-commands/{voiceRequestId}/confirm` | 위와 동일 | `application/json`, body 생략/`{}` 또는 `action`+`draft` 쌍 | 200, 같은 응답 DTO | 일치 |
| `cancelVoiceCommand` | `POST /api/v1/babies/{babyId}/voice-commands/{voiceRequestId}/cancel` | 위와 동일 | body/query 없음 | 200, 같은 응답 DTO | 일치 |

Controller mapping과 사용자가 제시한 경로는 모두 같다. 네 API에 query parameter는 없다. OpenAPI의 전체 operation은 45개, Voice는 정확히 4개이며 operationId·Method/Path 중복 없이 실제 Controller mapping과 대응한다.

## 발견한 Swagger 불일치·누락 및 수정

| 항목 | 검수 전 상태 | 수정 내용 | 런타임 계약 변경 |
| --- | --- | --- | --- |
| Voice 실패 예시 | upload 503의 예시 메시지가 아기 프로필 이미지 저장 오류였음 | Voice 400/401/403/404/409/413/503/500의 실제 공통 실패 봉투·주요 메시지 예시를 작업별로 설정 | 없음 |
| 업로드 `Location` | Controller가 반환하지만 OpenAPI 202 응답 헤더에 명시되지 않음 | 조회 경로를 202 헤더 스키마·예시로 명시 | 없음 |
| 파일 요청 예시 | `audio` binary 스키마만 있고 요청 예시는 없음 | multipart 파일 part의 설명·예시 추가. 예시 문자열은 바이너리 자리표시자임을 표시 | 없음 |
| Path/Header 파라미터 | `voiceRequestId` 설명과 `Idempotency-Key`의 UUID 형식·재시도 의미 부족 | 양의 ID, UUID 형식·예시와 재사용 조건 설명 | 없음 |
| 성공 응답 예시 | Voice 네 API에 상태별 실제 JSON 예시 부족 | upload 처리 중, GET 처리 중/자동 저장/검토/실패, confirm 완료, cancel 완료 예시 추가 | 없음 |
| Response 필드 설명 | `status`, `pending`, `draft`, `recordId`, `failureReason` 등의 의미·null 가능성 부족 | 실제 DTO 10개 필드의 설명과 해당 nullable 형태 보강 | 없음 |

기존 문서 설정에서 이미 적용된 항목도 재검증했다: Voice 응답은 `application/json` 공통 ApiResponse 봉투, 업로드는 202, multipart `audio`는 필수 binary, confirm body는 빈 객체 또는 `action`·`draft`를 함께 갖는 객체이며 body 자체는 선택이다. Swagger의 confirm 초안은 action별 Care 스키마를 가리키지만, 시간·유형별 조건 등 세부 제약은 실제 Care 파서와 도메인 검증이 최종 기준이다.

## 오류와 상태의 정확한 해석

- 외부 실패 JSON은 `success`, `data`, `message` 세 필드만 가진다. 서버 내부 `VoiceErrorCode` (`INVALID_AUDIO`, `AUDIO_TOO_LARGE`, `INVALID_VOICE_REQUEST`, `VOICE_REQUEST_NOT_FOUND`, `VOICE_REQUEST_CONFLICT`, `VOICE_UNAVAILABLE`) 또는 Identity/Family/Care/Common ErrorCode를 **응답 필드로 추가하지 않았다**.
- Google STT·Gemini 오류/타임아웃은 보통 upload 요청 자체의 HTTP 오류가 아니라, 접수 후 GET 200의 `data.status=FAILED`, `failureReason`으로 드러난다. 원문 Provider 오류는 응답하지 않는다.
- `processingStatus`는 DB 내부 컬럼이다. HTTP DTO에는 `status`(nullable)와 `pending`이 있다. `REVIEW_REQUIRED`·`CONFIRMATION_REQUIRED`는 내부 처리 완료 상태지만 아직 Care 반영 전이다.
- confirm은 `CONFIRMED` 반복 요청 시 같은 결과를 200으로 반환한다. cancel은 `CANCELED` 반복 요청 시 같은 결과를 200으로 반환한다. 다른 종료 상태와 충돌하면 409다.
- GET/confirm/cancel은 같은 아기의 다른 보호자에게도 요청을 공개하지 않는다. 다른 요청자·다른 아기 ID는 404다.

## 검증과 남은 확인

- `OpenApiContractTest`는 `/v3/api-docs`의 45 operation, Voice 4 operation, Bearer 보안, 필수 multipart binary, confirm schema, 202·200 성공 봉투·예시, 202 `Location`, Voice 오류 봉투 및 DTO 필드 10개를 검사한다.
- 전체 테스트와 실행 결과는 이번 작업의 최종 보고에 기록한다.
- 라이브 AWS DEV의 Swagger 및 실제 Google STT/Gemini 호출은 이번 문서 작업 범위 밖이다. 소스 OpenAPI와 배포본의 일치, Provider 자격증명·기능 플래그 상태는 **확인 필요**다.
- `Idempotency-Key`를 반드시 보내야 한다. 필수 헤더가 누락된 경우의 최종 HTTP 매핑은 별도 런타임 테스트가 없어 **확인 필요**로 둔다. 이 작업에서는 예외 처리·비즈니스 동작을 변경하지 않았다.
- 기존 `docs/react-native-api-handoff.md`에는 Voice를 미구현이라고 적은 과거 문구가 있다. 프론트에 전달할 현재 Voice HTTP 계약은 새 [`voice-api-notion.md`](voice-api-notion.md)를 우선한다. 실제 배포에서 기능이 활성화되었는지는 별도로 확인해야 한다.
