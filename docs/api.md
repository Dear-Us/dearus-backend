# DearUs API / Swagger

현재 `dev`에 구현된 46개 API의 실제 계약을 OpenAPI 3.1로 제공한다. 게스트 로그인과 VOICE-01~04의 HTTP 계약, Google Speech-to-Text V2 / Vertex AI Gemini 및 OpenAI 어댑터 코드가 포함된다. `DEARUS_VOICE_ENABLED=false`가 기본이며 제공자 설정과 배포·실호출 검증 전에는 음성 처리 성공을 보장하지 않는다. 앱 연동 방법은 [모바일 연동 가이드](mobile-integration.md)에 설명되어 있다.

- 로컬 Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- 로컬 OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- 배포 환경: 실제 HTTPS API 호스트 뒤에 각각 `/swagger-ui/index.html`, `/v3/api-docs`를 붙인다. 소스에는 운영 도메인을 고정하지 않는다. OpenAPI `servers`의 첫 항목은 현재 접속 호스트를 쓰는 `/`이고, 필요하면 `DEARUS_OPENAPI_SERVER_URL` 환경변수로 추가 절대 URL을 지정한다.
- 문서 활성화: 기본값 `SWAGGER_ENABLED=true`; 운영에서도 협업용으로 열려 있다. 비활성화하려면 `false`로 설정한다.

Swagger UI 오른쪽 상단 **Authorize**에 소셜·게스트 로그인 또는 재발급 응답의 `accessToken`을 입력한다. HTTP Bearer 스키마이므로 UI가 `Authorization: Bearer {JWT}` 헤더를 보낸다. Auth의 로그인·게스트 로그인·재발급·로그아웃과 초대 토큰 미리보기는 공개 API이며 그 외 보호 API는 JWT가 필요하다. 문서 경로가 공개되어도 보호 API의 인증은 해제되지 않는다. Refresh Token, 소셜 Provider Token, 초대 원문 Token은 URL·콘솔 로그·공유 스크린샷에 남기지 않는다. [게스트 로그인 상세 계약](api/guest-login.md)을 참고한다.

Multipart API(MEMBER-02, BABY-02/04)는 `request` part를 `Content-Type: application/json`으로 보내고, 선택적인 `profileImage` part에는 JPEG/PNG/WebP 파일을 붙인다. Baby 등록의 JSON 예시는 `{"babyName":"하늘","birthDate":"2026-01-01","gender":"FEMALE"}`이다. 사진 제거와 파일 업로드를 함께 요청할 수 없다. Swagger의 multipart 스키마에는 `request`의 JSON encoding과 이미지 binary 필드가 명시되어 있다.

VOICE-01은 `POST /api/v1/babies/{babyId}/voice-commands`에 `Idempotency-Key` UUID 헤더와 필수 `audio` multipart part를 보낸다. 접수되면 202와 `Location`이 반환되며 GET으로 상태를 폴링한다. 확인이 필요한 초안은 `/confirm`, 미완료 요청은 `/cancel`로 처리한다. 필드·상태·오류와 프론트 연동 흐름은 [Voice API 상세 명세](api/voice-api-notion.md)를 기준으로 한다. 기본 비활성 상태의 업로드는 503이며, Railway에서 OpenAI를 사용할 때는 [Railway 설정](railway-openai-deployment.md)을 적용한다.

Care 생성 요청에는 수유·수면·기저귀·체온·투약·기타의 실제 수락 가능한 예시가 있고, `detail`은 유형에 따라 `oneOf`로 표시된다. Care/Growth 수정·삭제의 409는 단순 토큰 만료가 아니라 상태 또는 version 충돌이므로 상세 재조회 후 처리한다. EVENT-01은 JSON 응답이 아닌 `text/event-stream` 장기 연결이다. Swagger UI가 SSE를 끝까지 소비하지 못할 수 있으므로 모바일에서는 Bearer 헤더를 지원하는 SSE 클라이언트를 사용하고 재연결 후 HOME-01을 조회한다.
