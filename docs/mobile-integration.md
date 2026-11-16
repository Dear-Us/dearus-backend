# DearUs 모바일 앱 연동 가이드

이 문서는 현재 `dev` 백엔드 구현을 기준으로 한다. iOS·Android의 네이티브 HTTP 클라이언트를 대상으로 하며 특정 앱 프레임워크에 의존하지 않는다. Voice API의 HTTP·상태 관리 흐름과 Google/OpenAI 제공자 코드는 구현되었지만 기능 플래그는 기본 비활성화이며 제공자 실호출·배포 검증은 별도다.

## 1. Environment / Base URL

- 모든 구현된 REST API 경로는 `/api/v1`로 시작한다. 운영 Base URL은 `https://{production-domain}/api/v1` 형태로 앱 환경별 설정에서 주입한다. 백엔드 코드에 Cloudtype 또는 특정 운영 도메인이 고정되어 있지 않다.
- 운영에서는 TLS를 제공하고 HTTP 요청이 HTTPS로 안전하게 연결되는지 배포 환경에서 확인한다. 이 저장소의 애플리케이션 설정만으로 TLS 종단이나 HTTP→HTTPS 리다이렉트를 보장하지 않는다.
- 서버가 반환하는 `profileImageUrl`은 이미지 저장소의 `*_PUBLIC_BASE_URL`, `invitationUrl`은 `DEARUS_INVITATION_BASE_URL`에 의존한다. 운영에서는 모두 HTTPS 공개 URL로 설정한다. 현 코드는 URL의 `http`도 허용하므로 배포 설정 검증이 필수다. 로컬 개발·테스트 URL을 운영에 사용하지 않는다.
- JSON REST 응답은 `{ "success": true, "data": ..., "message": null }`, 실패 응답은 `{ "success": false, "data": null, "message": "..." }`이다. 외부 `errorCode` 필드는 없다. SSE 스트림은 JSON 응답 봉투가 아니다.

## 2. Authentication

- 앱은 Google/Kakao/Naver SDK에서 로그인한 뒤 `POST /api/v1/auth/login`에 JSON `{ "provider": "GOOGLE|KAKAO|NAVER", "providerToken": "..." }`을 보낸다. Google은 ID token, Kakao·Naver는 access token을 백엔드에 전달한다. 앱은 DearUs JWT를 직접 생성하지 않는다.
- 성공 응답의 `data`에는 `accessToken`, `refreshToken`, `tokenType`(`Bearer`), 두 `*ExpiresInSeconds`, `isNewMember`, `member`가 있다. 일반 인증 요청은 `Authorization: Bearer {accessToken}` 헤더를 사용한다. URL query에 DearUs access token을 넣지 않는다.
- `POST /api/v1/auth/reissue`와 `POST /api/v1/auth/logout`은 각각 JSON `{ "refreshToken": "..." }`을 받으며 Access Token 없이 호출할 수 있다. 로그인·재발급은 Refresh Token 원문을 응답으로 돌려주지만 서버에는 해시만 저장한다.
- 로그아웃 성공 후 해당 기기의 로컬 토큰을 지운다. 서버는 요청한 Refresh 세션을 revoke한다. 이미 발급된 Access JWT의 즉시 무효화를 보장하는 blacklist는 없다.

## 3. Secure Token Storage

- Access·Refresh Token은 iOS Keychain 또는 Android Keystore 계열 보안 저장소에 보관하도록 권장한다. 특히 Refresh Token을 일반 평문 설정·로그·분석 이벤트·URL에 저장하지 않는다.
- 앱 저장 구현은 앱의 책임이며 서버가 특정 모바일 프레임워크나 보안 저장 라이브러리를 강제하지 않는다.

## 4. Refresh Flow

1. 보호된 REST 요청의 **401**을 받으면 현재 Refresh Token으로 AUTH-02를 호출한다.
2. 성공 응답의 Access·Refresh Token을 **함께 원자적으로 교체**한 뒤 원래 요청을 한 번만 재시도한다.
3. Refresh 실패 시 오래된 Refresh Token으로 반복 호출하지 말고 재로그인 흐름으로 이동한다.

Refresh Token은 매 재발급마다 rotation되며 이전 토큰은 다시 사용할 수 없다. 여러 요청이 동시에 401을 받으면 앱에서 재발급을 한 번만 수행하고 나머지는 그 결과를 기다리는 단일 진행 흐름으로 묶는다. 같은 구 Refresh Token의 동시 재발급은 서버에서 한 건만 성공한다. **409는 토큰 만료 신호가 아니며 로그아웃으로 연결하지 않는다.**

## 5. Baby Selection

- `GET /api/v1/members/me`의 `lastSelectedBabyId`는 `null`일 수 있다. 신규 계정, Baby 삭제, 보호자 탈퇴·내보내기 후를 정상 상태로 처리한다.
- `null`이면 Home에 임의 ID나 `null`을 넣어 호출하지 않고 Baby 목록/등록·선택 화면으로 보낸다. Baby 목록은 `GET /api/v1/babies`, 선택 변경은 `PATCH /api/v1/members/me/last-selected-baby`의 JSON `{ "babyId": 123 }`을 사용한다.
- 선택 Baby가 바뀌면 이전 Baby의 SSE를 종료한 뒤 새 Baby의 Home을 조회하고 SSE를 연결한다.

## 6. Multipart Image

| API | HTTP 및 JSON part | 이미지 part | 동작 |
| --- | --- | --- | --- |
| MEMBER-02 `PATCH /api/v1/members/me` | `multipart/form-data`, 필수 `request` (`application/json`; `memberName?`, `removeProfileImage?`) | 선택 `profileImage` | 이름·사진 수정 또는 사진 제거 |
| BABY-02 `POST /api/v1/babies` | `multipart/form-data`, 필수 `request` (`application/json`; `babyName`, `birthDate`, `gender`) | 선택 `profileImage` | Baby 생성; 사진 생략 가능 |
| BABY-04 `PATCH /api/v1/babies/{babyId}` | `multipart/form-data`, 필수 `request` (`application/json`; `babyName?`, `birthDate?`, `gender?`, `removeProfileImage?`) | 선택 `profileImage` | OWNER만 수정 가능 |

- `profileImage` 허용 MIME은 `image/jpeg`, `image/png`, `image/webp`뿐이다. MIME과 파일 시작 바이트를 함께 확인한다. 이미지당 최대 **5 MiB**이며 서버의 전체 multipart 요청 제한은 **6 MB**이다. 초과 시 413, 빈 파일·형식 불일치는 400이다.
- `request` part는 문자열 필드 집합이 아니라 `Content-Type: application/json`인 JSON part여야 한다. 앱의 로컬 URI를 읽어 파일 part의 바이트로 전송한다. 서버에 로컬 절대 경로를 보내지 않으며, 원본 파일명 자체는 검증·저장 조건이 아니다.
- PATCH에서 이미지 part를 생략하고 `removeProfileImage`가 `true`가 아니면 이미지는 유지된다. `removeProfileImage: true`이면 사진을 제거한다. 제거 플래그와 이미지 part를 동시에 보내면 400이다. MEMBER-02/BABY-04에서 변경할 필드가 전혀 없으면 400이다. BABY-02에는 제거 플래그가 없다.
- 실제 이미지 저장 기능은 해당 저장소 어댑터가 운영 설정으로 활성화되어 있어야 한다. 미설정 상태에서 사진 업로드를 시도하면 503이 가능하다.

## 7. Voice Audio — 제공자 실호출 검증 전

- `POST /api/v1/babies/{babyId}/voice-commands`는 필수 `audio` multipart part와 `Idempotency-Key` UUID 헤더를 받는다. MIME은 M4A/MP4(`audio/mp4`, `audio/x-m4a`, `audio/m4a`) 및 WAV(`audio/wav`, `audio/x-wav`, `audio/wave`)이며 기본 최대 크기는 5 MiB다. 접수 시 202와 `Location` 헤더, `voiceRequestId`를 반환한다.
- `GET /api/v1/babies/{babyId}/voice-commands/{voiceRequestId}`로 결과를 폴링한다. 응답 `data`는 `status`, `pending`, `transcript`, `action`, `draft`, `recordId`, `failureReason`, `createdAt`, `updatedAt`를 포함한다. 상태가 `REVIEW_REQUIRED`면 `POST .../confirm`에 수정한 `action`과 `draft`를 함께 보내고, `CONFIRMATION_REQUIRED`면 `{}`로 확인한다. `POST .../cancel`은 반영 전 요청만 취소한다.
- 앱은 마이크 권한, 녹음 시작·중지, 파일 업로드, 로딩 상태, 사용자 주도 재시도를 담당한다. 재시도할 때는 같은 UUID와 같은 파일을 사용한다. Google credential이나 OpenAI API 키를 앱에 넣지 않는다. 서버 기능 플래그는 기본 비활성화이며 그 상태의 업로드는 503을 반환한다. 실제 음성 처리에는 선택한 제공자의 서버 설정·배포·실호출 검증이 필요하다.

## 8. SSE

- `GET /api/v1/babies/{babyId}/events`는 활성 공동 보호자에게만 열리며 `Authorization: Bearer {accessToken}` 헤더가 필요하다. `Accept: text/event-stream`을 사용하고, Authorization 헤더를 설정할 수 있는 모바일 SSE 클라이언트를 선택한다. 브라우저 기본 `EventSource` 동작을 전제로 하지 않는다.
- Care 변경 시 SSE event 이름은 `record-change`이며 데이터에는 `eventType`, `babyId`, `recordId`, `recordType`, `occurredAt`이 있다. 현재 유형은 `RECORD_CREATED`, `RECORD_STARTED`, `RECORD_COMPLETED`, `RECORD_CANCELED`, `RECORD_UPDATED`, `RECORD_DELETED`이다.
- SSE는 **변경 알림**이고 화면의 최종 데이터는 REST+MariaDB가 권위다. 이벤트를 받으면 `HOME-01 GET /api/v1/babies/{babyId}/home`을 재조회하고 필요하면 History를 다시 조회한다. payload만으로 전체 화면 상태를 확정하지 않는다.
- 연결 직후 및 약 30초 간격으로 오는 `:heartbeat`는 SSE comment이며 business event가 아니다. 이를 이유로 Home을 재조회할 필요는 없다. 서버 연결 제한은 약 1시간이며 호스팅·네트워크 상황에 따라 더 일찍 끊길 수 있다.
- 앱 foreground 복귀, 네트워크 재연결, 서버 배포·timeout 후 자동 재연결하고 **연결 직후 Home을 재조회**한다. MVP에는 `Last-Event-ID` replay가 없으므로 끊긴 동안의 이벤트는 REST 재조회로 복구한다. Access Token 만료로 401이면 위 Refresh Flow 후 새 헤더로 연결한다.

## 9. Invitation Deep Link

1. OWNER가 `POST /api/v1/babies/{babyId}/invitations`를 호출하면 `data.invitationUrl`을 받는다. 필요하면 앱의 공유 기능으로 Kakao 등에 전달한다.
2. URL은 설정된 `DEARUS_INVITATION_BASE_URL` 뒤에 `?token={opaqueToken}`을 붙인 형태다. 운영 설정 예시는 `https://{app-link-domain}/invite`이며 실제 도메인은 저장소에 하드코딩하지 않는다.
3. 초대받은 앱은 URL에서 토큰을 추출해 공개 `GET /api/v1/invitations/{token}`으로 미리보기를 조회한다. 로그인 상태가 아니면 로그인한다. 수락은 인증된 `POST /api/v1/invitations/{token}/accept`를 호출한다.

토큰이 포함된 링크를 로그·분석 도구에 남기지 않는다. iOS Universal Links, Android App Links, custom URI scheme fallback의 사용 여부와 도메인 소유권/연결 파일은 **앱·배포 담당자가 결정**한다. 이 백엔드는 해당 플랫폼 연결 파일을 생성하지 않는다. 초대 수락은 상태 충돌로 409가 가능하므로 실패 시 무조건 반복하지 말고 미리보기를 다시 조회한다.

## 10. Date / Timezone

- 날짜만 있는 필드(`birthDate`, `measuredDate`, 통계·History 날짜)는 `YYYY-MM-DD`를 사용한다. 시간 필드는 ISO 8601의 offset 포함 형태로 파싱한다(예: `2026-10-05T10:30:00+09:00`, UTC이면 `Z`). 클라이언트는 offset을 보존하거나 instant로 변환하되, 문자열의 고정 길이·고정 offset을 가정하지 않는다.
- 회원 `timezone`은 `Asia/Seoul` 같은 **IANA timezone ID**다. `GMT+9` 같은 단순 고정 offset으로 바꾸면 DST와 날짜 경계가 달라질 수 있다. Home/History/Stats의 일자 경계는 서버가 회원 timezone으로 계산한 결과를 따른다.
- 성장 기록의 미래 날짜와 Baby 생일 검증도 서버의 회원 timezone을 기준으로 한다. 앱 기기 timezone의 오늘 날짜를 서버 판정으로 가정하지 않는다.

## 11. Error Handling

| HTTP | 현재 모바일 처리 원칙 |
| --- | --- |
| 400 | 요청 형식·입력값 확인. 동일 요청 자동 반복 금지 |
| 401 | Access Token 재발급 흐름. Refresh도 실패하면 재로그인 |
| 403 | 탈퇴 회원·명시적 역할/권한 문제. 토큰 재발급으로 해결된다고 가정하지 않음 |
| 404 | 존재하지 않거나 현재 사용자에게 접근 불가한 리소스. 타 가족 리소스 존재 여부를 추측하지 않음 |
| 409 | 버전 경합 또는 비즈니스 상태 충돌. 최신 상태 재조회; 자동 로그아웃 금지 |
| 413 | 이미지 업로드 크기 제한 초과 |
| 503 | 현재 외부 소셜 제공자 또는 이미지 저장소 이용 불가 가능. 상황에 맞춰 사용자에게 안내 |

Voice Provider 장애는 Voice 요청 상태 조회의 안전한 실패 사유로 확인해야 하며, **STT/LLM 원본 오류 본문은 외부에 전달되지 않는다.** 모든 현재 REST 오류의 본문은 공통 실패 봉투이며 내부 `errorCode`는 노출되지 않는다. 네트워크 timeout 후 `Record create`, `Invitation accept`, `Voice POST`를 결과 확인 없이 무한 자동 재시도하지 않는다.

## 12. Version Conflict

- Care/ Growth 상세 조회 응답의 `version`을 저장한다. 수정 `PATCH`는 JSON body의 `version`에 그 값을 넣고, 삭제 `DELETE`는 `?version={값}` query parameter로 전달한다. 이는 Access Token query 전달 허용과 무관하다.
- 409를 받으면 PATCH/DELETE를 무조건 재전송하지 말고 최신 상세를 GET으로 다시 조회해 사용자에게 현재 상태를 보여준다. 수정 성공 후 응답의 새 `version`을 사용한다.
- `409`는 이미 진행 중인 Care 기록, complete/cancel 경합, 초대 상태 충돌 등 버전 이외에도 발생할 수 있다. Voice confirm/cancel 경합은 하나의 최종 상태만 유효하며 409를 받은 앱은 Voice GET으로 결과를 재조회한다. 세부 상태와 요청 형식은 [Voice API 상세 명세](api/voice-api-notion.md)를 따른다.

## 13. App Lifecycle

- **App launch:** 저장 토큰 상태를 확인하고 `GET /members/me`로 회원·`lastSelectedBabyId`를 조회한다. 선택 Baby가 있으면 Home을 읽고 해당 Baby SSE를 연결한다. 없으면 Baby 선택/등록 화면으로 이동한다.
- **Foreground resume / network reconnect:** 이전 SSE를 정리하고 새 연결을 만든 뒤 Home을 재조회한다. 이벤트 replay에 의존하지 않는다.
- **Baby 변경:** 기존 SSE disconnect → `PATCH /members/me/last-selected-baby` → 새 Baby Home 조회 → 새 Baby SSE connect.
- **Logout:** SSE disconnect → AUTH-03 logout → 로컬 보안 저장소의 두 토큰 삭제. 기기에서 이미 사용 중인 Access JWT는 만료 전까지 서버 blacklist 대상이 아니다.

## 14. Production Checklist

- [ ] 운영 API HTTPS 도메인과 TLS 종단·리다이렉트 확인. 앱에는 환경별 Base URL 주입.
- [ ] `DEARUS_INVITATION_BASE_URL`에 앱 링크용 HTTPS URL 주입, 실제 기기에서 링크 열기·토큰 추출 검증.
- [ ] 이미지 저장을 사용할 경우 두 `*_STORAGE_ENABLED` 및 bucket/region/HTTPS `*_PUBLIC_BASE_URL`을 설정하고 실제 이미지 접근 확인.
- [ ] JWT secret, DB 암호, OAuth 설정을 환경변수로 제공. 앱에는 백엔드 secret·Google STT credential을 넣지 않음.
- [ ] 동시 401 단일 재발급, Refresh Rotation 저장, 409 별도 처리, 앱 복귀·네트워크 변경 시 SSE 재연결 검증.
- [ ] Voice API는 선택한 제공자의 설정·실호출·배포 검증이 완료되기 전까지 앱에서 비활성화.
- [ ] 현재 백엔드에는 별도 CORS 허용 설정이 없다. 네이티브 모바일 HTTP에는 일반적인 브라우저 CORS 허용이 필요하지 않다. Web/Swagger 클라이언트가 추가되면 그 출처를 별도 확정해야 하며 운영에서 `*`를 허용하지 않는다.
