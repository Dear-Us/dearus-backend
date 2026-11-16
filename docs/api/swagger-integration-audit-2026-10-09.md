# DearUsBack Swagger 전체 API 통합 점검 — 2026-10-09

## 판정 범위와 결과

이 보고서는 **현재 로컬 작업 트리의 소스**를 별도 로컬 MariaDB 11.4에 기동해 생성한 `/v3/api-docs`와 전체 테스트 결과를 기준으로 한다. 로컬 작업 트리에는 기존 미커밋 변경이 다수 있으므로, **AWS DEV에 배포된 이미지와 동일한 버전이라는 증거는 없다.** AWS DEV 호출·배포·인프라 변경은 하지 않았다. 로컬 기동 DB는 점검용 빈 DB이며 기존 회원·아기·기록을 건드리지 않았다.

| 검증 단위 | PASS | FAIL | SKIP | 해석 |
| --- | ---: | ---: | ---: | --- |
| 생성 OpenAPI의 operation 계약 | 45 | 0 | 0 | Controller 매핑 45개, 고유 operationId 45개, Voice 4개 |
| 로컬 실제 HTTP 오류 경계 | 45 | 0 | 0 | 보호 API 41개는 무인증 401; 공개 Auth 3개는 빈 JSON 400; 공개 초대 조회 1개는 미존재 토큰 404 |
| 각 API의 유효한 입력·권한으로 성공하는 HTTP 흐름 | 0 | 0 | 45 | 로컬 DearUs JWT/소셜 Provider 인증 및 안전한 테스트 계정이 없어 실행하지 않음 |
| 전체 로컬 JUnit 회귀 테스트 | 315 | 0 | 0 | MariaDB Testcontainers, MockMvc, ArchUnit, Voice/M4A 변환 포함. 테스트 케이스 수이며 API 수가 아님 |

따라서 **HTTP로 호출한 operation은 45개**이지만, 모두 비파괴적인 실패 경로다. 이를 “45개 API 정상 작동”으로 해석하면 안 된다. 유효한 Bearer 토큰으로 정상 응답을 받은 실제 HTTP operation은 **0개**다. `GET /actuator/health`와 Swagger UI는 각각 200이었으나 위 45개 API 수에는 포함하지 않았다.

## 생성 OpenAPI 기준 API별 체크리스트

표기: `G401`은 무인증 401, `G400`은 빈 JSON 400, `G404`는 존재하지 않는 초대 토큰 404를 실제 로컬 HTTP에서 확인했다는 뜻이다. 모든 행의 **정상 기능 흐름은 SKIP**이다. 성공 응답은 실제 OpenAPI의 `ApiResponse<T>`를 `T`로 줄여 적었다. `—`는 요청 body가 없다는 뜻이다.

| 영역 | Method | Endpoint | 요청 body / 주요 입력 | 성공 응답 data / HTTP | 로컬 오류 경계 |
| --- | --- | --- | --- | --- | --- |
| Auth | POST | `/api/v1/auth/login` | `SocialLoginRequest` | `SocialLoginResponse` / 200 | G400 |
| Auth | POST | `/api/v1/auth/reissue` | `ReissueTokenRequest` | `ReissueTokenResponse` / 200 | G400 |
| Auth | POST | `/api/v1/auth/logout` | `LogoutRequest` | `null` / 200 | G400 |
| Member | GET | `/api/v1/members/me` | — | `MyProfileResponse` / 200 | G401 |
| Member | PATCH | `/api/v1/members/me` | multipart `request: UpdateMyProfileRequest`, `profileImage?` | `MyProfileResponse` / 200 | G401 |
| Member | PATCH | `/api/v1/members/me/last-selected-baby` | `ChangeLastSelectedBabyRequest` | `LastSelectedBabyResponse` / 200 | G401 |
| Member | DELETE | `/api/v1/members/me` | — | `null` / 200 | G401 |
| Baby | GET | `/api/v1/babies` | — | `BabyResponse[]` / 200 | G401 |
| Baby | POST | `/api/v1/babies` | multipart `request: CreateBabyRequest`, `profileImage?` | `BabyResponse` / 201 | G401 |
| Baby | GET | `/api/v1/babies/{babyId}` | `babyId` | `BabyResponse` / 200 | G401 |
| Baby | PATCH | `/api/v1/babies/{babyId}` | `babyId`, multipart `request: UpdateBabyRequest`, `profileImage?` | `BabyResponse` / 200 | G401 |
| Baby | DELETE | `/api/v1/babies/{babyId}` | `babyId` | `null` / 200 | G401 |
| Baby Member | GET | `/api/v1/babies/{babyId}/members` | `babyId` | `BabyMemberResponse[]` / 200 | G401 |
| Baby Member | DELETE | `/api/v1/babies/{babyId}/members/{memberId}` | `babyId`, `memberId` | `null` / 200 | G401 |
| Baby Member | DELETE | `/api/v1/babies/{babyId}/members/me` | `babyId` | `null` / 200 | G401 |
| Baby Member | PATCH | `/api/v1/babies/{babyId}/owner` | `babyId`, `TransferBabyOwnershipRequest` | `TransferBabyOwnershipResponse` / 200 | G401 |
| Invitation | POST | `/api/v1/babies/{babyId}/invitations` | `babyId` | `CreateBabyInvitationResponse` / 201 | G401 |
| Invitation | GET | `/api/v1/babies/{babyId}/invitations` | `babyId` | `InvitationSummaryResponse[]` / 200 | G401 |
| Invitation | DELETE | `/api/v1/babies/{babyId}/invitations/{invitationId}` | `babyId`, `invitationId` | `null` / 200 | G401 |
| Invitation | GET | `/api/v1/invitations/{token}` | `token`; 공개 API | `InvitationPreviewResponse` / 200 | G404 |
| Invitation | POST | `/api/v1/invitations/{token}/accept` | `token`; 로그인 필수 | `AcceptedInvitationResponse` / 200 | G401 |
| Care Record | GET | `/api/v1/babies/{babyId}/records` | `babyId`, 필수 `date`, 선택 `recordType` | `CareRecordResponse[]` / 200 | G401 |
| Care Record | GET | `/api/v1/babies/{babyId}/records/calendar` | `babyId`, 필수 `year`,`month` | `CalendarDayCount[]` / 200 | G401 |
| Care Record | GET | `/api/v1/babies/{babyId}/records/{recordId}` | `babyId`, `recordId` | `CareRecordResponse` / 200 | G401 |
| Care Record | GET | `/api/v1/babies/{babyId}/records/active` | `babyId` | `CareRecordResponse` 또는 `null` / 200 | G401 |
| Care Record | POST | `/api/v1/babies/{babyId}/records` | `babyId`, `CareCreateRequest` | `CareRecordResponse` / 201 | G401 |
| Care Record | POST | `/api/v1/babies/{babyId}/records/start` | `babyId`, `CareStartRequest` | `CareRecordResponse` / 201 | G401 |
| Care Record | PATCH | `/api/v1/babies/{babyId}/records/{recordId}/complete` | `babyId`, `recordId` | `CareRecordResponse` / 200 | G401 |
| Care Record | PATCH | `/api/v1/babies/{babyId}/records/{recordId}/cancel` | `babyId`, `recordId` | `null` / 200 | G401 |
| Care Record | PATCH | `/api/v1/babies/{babyId}/records/{recordId}` | `babyId`, `recordId`, `CareUpdateRequest` | `CareRecordResponse` / 200 | G401 |
| Care Record | DELETE | `/api/v1/babies/{babyId}/records/{recordId}` | `babyId`, `recordId`, 필수 query `version` | `null` / 200 | G401 |
| Home | GET | `/api/v1/babies/{babyId}/home` | `babyId` | `HomeResponse` / 200 | G401 |
| Event | GET | `/api/v1/babies/{babyId}/events` | `babyId`, Bearer 헤더 | `text/event-stream` / 200 | G401 |
| Statistics | GET | `/api/v1/babies/{babyId}/stats/daily` | `babyId`, 필수 `date` | `DailyStatistics` / 200 | G401 |
| Statistics | GET | `/api/v1/babies/{babyId}/stats/routine` | `babyId`, 필수 `endDate` | `RoutineStatistics` / 200 | G401 |
| Statistics | GET | `/api/v1/babies/{babyId}/stats/growth` | `babyId` | `GrowthStatistics` / 200 | G401 |
| Growth | GET | `/api/v1/babies/{babyId}/growth-records` | `babyId` | `GrowthRecordResponse[]` / 200 | G401 |
| Growth | POST | `/api/v1/babies/{babyId}/growth-records` | `babyId`, `GrowthCreateRequest` | `GrowthRecordResponse` / 201 | G401 |
| Growth | GET | `/api/v1/babies/{babyId}/growth-records/{growthRecordId}` | `babyId`, `growthRecordId` | `GrowthRecordResponse` / 200 | G401 |
| Growth | PATCH | `/api/v1/babies/{babyId}/growth-records/{growthRecordId}` | `babyId`, `growthRecordId`, `GrowthUpdateRequest` | `GrowthRecordResponse` / 200 | G401 |
| Growth | DELETE | `/api/v1/babies/{babyId}/growth-records/{growthRecordId}` | `babyId`, `growthRecordId`, 필수 query `version` | `null` / 200 | G401 |
| Voice | POST | `/api/v1/babies/{babyId}/voice-commands` | `babyId`, UUID `Idempotency-Key`, multipart `audio` | `VoiceCommandResponse` / 202 | G401 |
| Voice | GET | `/api/v1/babies/{babyId}/voice-commands/{voiceRequestId}` | `babyId`, `voiceRequestId` | `VoiceCommandResponse` / 200 | G401 |
| Voice | POST | `/api/v1/babies/{babyId}/voice-commands/{voiceRequestId}/confirm` | `babyId`, `voiceRequestId`, `VoiceConfirmRequest` | `VoiceCommandResponse` / 200 | G401 |
| Voice | POST | `/api/v1/babies/{babyId}/voice-commands/{voiceRequestId}/cancel` | `babyId`, `voiceRequestId` | `VoiceCommandResponse` / 200 | G401 |

모든 성공 JSON은 `{ "success": true, "data": ..., "message": null }` 봉투를 사용한다. 로컬에서 본 실패 JSON은 `{ "success": false, "data": null, "message": "..." }`였으며 외부 `errorCode` 필드는 없었다. SSE 성공 응답만 JSON 봉투가 아닌 이벤트 스트림이다.

## 인증·필수값·선행 조건

- 공개 operation은 `POST /auth/login`, `/auth/reissue`, `/auth/logout`, `GET /invitations/{token}` 네 개다. 나머지 41개는 DearUs Access JWT를 `Authorization: Bearer` 헤더에 넣어야 한다. 이번 점검에는 유효 토큰이 없어 정상 인증·권한 경로를 호출하지 않았다.
- `CreateBabyRequest`는 `babyName`, `birthDate`(`YYYY-MM-DD`), `gender`(`MALE|FEMALE`)를 가진 JSON `request` part가 필수다. 아기·회원 이미지의 `profileImage` part는 선택이며 없으면 생략한다. JPEG/PNG/WebP, 5 MiB 제한을 가진다. 생성·수정 multipart에서 `request` part의 Content-Type은 `application/json`이다.
- Baby 조회·Care·Growth·Home·Stats·Event·Voice는 해당 Baby의 ACTIVE 보호자 관계가 선행한다. Baby 수정·삭제, 초대 생성·목록·취소, 보호자 내보내기 및 OWNER 이전은 OWNER 권한이 필요하다. OWNER 이전·회원 탈퇴·기존 보호자 관계 변경은 자동 실호출 대상에서 제외했다.
- Care 완료 기록은 `FEEDING|SLEEP|DIAPER|TEMPERATURE|MEDICINE|OTHER`별 detail 형태가 다르다. SLEEP 생성은 `startAt/endAt`을 사용하고 detail을 생략한다. 진행형 시작은 SLEEP 또는 직접 수유만 가능하며 Baby당 합계 최대 1개다. Care/Growth 수정·삭제는 `version` 기반 충돌(409)을 처리해야 한다.
- Growth 생성은 `measuredDate`가 필수이며 `heightCm` 또는 `weightKg` 중 적어도 하나가 필요하다. 조회·통계의 날짜 기준은 회원 timezone이다.
- Voice 업로드는 M4A/MP4 또는 WAV, 기본 5 MiB, `audio` part와 UUID `Idempotency-Key`가 필수다. 202는 접수이며 결과 완료가 아니다. GET으로 `AUTO_SAVED|REVIEW_REQUIRED|CONFIRMATION_REQUIRED|FAILED|CANCELED` 등을 확인한다. `REVIEW_REQUIRED`는 수정된 action/draft 확인이 가능하고 약물 기록은 확인 필수다. STT/LLM/FFmpeg 설정과 실제 음성 파일이 있어야 정상 흐름이 가능하다.
- 초대 링크 생성은 `DEARUS_INVITATION_BASE_URL` 설정에 의존한다. 실수락은 별도 회원/초대 관계가 필요하다. SSE는 Bearer **헤더** 인증이며 query token 방식은 없다. 재연결 replay가 없으므로 Home 재조회가 필요하다.

## 실패 응답 및 원인 분석

이번 로컬 호출은 45/45가 예상한 실패 상태와 `ApiResponse` 구조를 반환했다. **예상 밖 FAIL은 0개이며 재현할 결함은 발견되지 않았다.** 다만 이는 정상 입력 처리·DB 쓰기·외부 Provider 성공을 증명하지 않는다.

| HTTP | 확인 또는 코드상 조건 | 프론트 대응 |
| --- | --- | --- |
| 400 | 빈/잘못된 요청, 날짜·타입·UUID·MIME·detail 검증 실패 | 현재 DTO와 multipart part 이름·형식 확인 |
| 401 | Bearer 누락/만료/잘못된 JWT; 공개 Auth의 경우 Refresh Token 재발급 실패도 가능 | 토큰 재발급은 한 번만 회전시키고 동시 401을 묶어 처리 |
| 403/404 | OWNER가 아닌 변경 또는 Baby/기록/요청 접근 불가. Voice는 타 회원 요청을 404로 숨김 | 선택 Baby와 ACTIVE 관계 재조회; 타인 데이터를 노출하지 않음 |
| 409 | 낙관적 version 충돌, 이미 진행 중인 기록, 초대/Voice 상태 충돌 등 | 최신 상세 조회 후 UI 상태 갱신 |
| 413 | multipart 또는 오디오/이미지 크기 제한 | 파일 크기 확인 후 재선택 |
| 503 | Voice·이미지 저장 비활성/외부 저장 불가, Provider 설정 부족 등 해당 기능별 조건 | 일시 사용 불가로 표시; 무한 재시도 금지 |

OpenAPI에는 endpoint별 가능한 HTTP 코드가 포함되어 있으나, 위 표의 **각 조건별 실측은 이번에 하지 않았다.** 현재 로컬 작업 트리와 AWS DEV가 불일치할 수 있으므로 프론트 장애를 백엔드 결함·클라이언트 요청 오류·배포 설정 문제 중 하나로 단정할 근거는 아직 없다.

## 로컬 회귀 테스트와 외부 서비스 구분

- `./gradlew clean test`: **315 tests, failures 0, skipped 0**. MariaDB Testcontainers의 인증/가족/Care/Growth/Home/Stats/Voice 저장·조회·동시성 테스트, MockMvc 보안/Controller, ArchUnit, 이미지 어댑터, M4A→WAV 변환 테스트가 포함된다.
- 실제 이미지 저장(S3), 실제 Google/Kakao/Naver 소셜 인증, Google STT V2, Vertex Gemini, AWS ECS/RDS 접속은 이 로컬 테스트에서 **모의 또는 미호출**이다. 테스트 클래스 이름이나 Mockito 성공을 AWS 실연동 성공으로 간주하지 않는다.
- Voice의 “아기에게 분유 120ml 먹였어”는 모의 STT/Gemini와 MariaDB 테스트에서 `FEEDING / FORMULA / amountMl=120` 저장 및 Care 상세 조회가 통과했다. 실제 Expo M4A 원본의 STT→Gemini→AUTO_SAVED→Care를 이번에 실호출하지 않았다. M4A→PCM WAV(16-bit·16kHz·모노)는 로컬 FFmpeg 통합 테스트로 검증했다.
- 로컬 HTTP 서버의 DB는 Flyway로 초기화된 별도 빈 MariaDB였다. 유효한 테스트 JWT를 임의 생성해 실제 회원으로 가장하지 않았고, API 호출로 만드는 CRUD 테스트 데이터도 생성하지 않았다. 따라서 API 성공 경로의 DB 영속성은 기존 Testcontainers 테스트 증거와 구분한다.

## 남은 검증과 우선순위

1. **P0 — 배포 버전 확인:** 승인된 AWS DEV에서 `/v3/api-docs`의 operationId/스키마를 현재 로컬 45개와 비교한다. 현재 미커밋 변경이 배포됐는지 확인 전에는 프론트 계약 일치를 보증할 수 없다.
2. **P0 — 승인된 격리 계정으로 정상 흐름:** DearUs Access Token과 명시적 AWS DEV 실호출 승인을 받은 뒤 새 임시 Baby만 만들어 생성→조회→수정→정리한다. 기존 회원·아기·기록은 수정/삭제하지 않는다. OWNER 이전·회원 탈퇴·기존 관계 파괴는 자동 실행하지 않는다.
3. **P1 — Voice 실연동:** 별도 승인/자격증명 및 실제 Expo M4A 파일로 upload→poll→AUTO_SAVED→Care detail을 확인한다. 실패 시 HTTP/Voice 상태, 안전한 Provider 오류 분류, ECS의 FFmpeg 포함 여부를 구분한다.
4. **P1 — 이미지·초대:** private S3 권한/만료 URL 재조회, 초대 base URL, 두 번째 테스트 회원의 수락 흐름은 환경 의존이므로 별도 격리 시나리오로 확인한다.
5. **P2 — 추가 오류 경계:** 권한 없는 보호자, 파일 초과/손상, 동시 버전 충돌, SSE 재연결을 승인된 격리 리소스에서 확인한다.

**현재 판정:** 로컬 Swagger 라우팅·공통 실패 계약과 전체 자동화 회귀는 PASS. AWS DEV에서 프론트가 모든 API의 정상 기능을 사용할 수 있는지는 **미검증**이다. 확정된 코드 결함은 없고, 우선 필요한 것은 배포 버전 대조와 승인된 격리 계정의 정상 경로 테스트다.

## 재실행

로컬 백엔드와 로컬 MariaDB를 기동한 뒤:

```bash
LOCAL_API_ROOT_URL=http://127.0.0.1:8080 ./scripts/swagger-api-audit.sh
```

이 스크립트는 loopback URL만 허용한다. 45개 operation의 OpenAPI 개수·고유 ID·인증 선언과 45개 비파괴적 오류 경계를 재확인한다. 유효한 로컬 테스트 토큰이 있을 때만 `RUN_AUTHENTICATED_READS=true`와 `DEARUS_ACCESS_TOKEN`으로 기존 읽기 전용 smoke를 추가할 수 있다. 전체 JUnit은 `RUN_LOCAL_REGRESSION=true`로 선택 실행할 수 있다. **이 스크립트는 성공 CRUD·실제 Google STT·AWS DEV 호출을 자동 수행하지 않는다.** AWS DEV에서 기존 `dev-api-smoke.sh`, `dev-mutation-smoke.sh`, `dev-voice-smoke.sh`를 실행하려면 사전 승인과 전용 격리 계정/리소스가 별도로 필요하다.
