# DearUs DEV runtime ENV 계약

기준: `src/main/resources/application.properties`와 현재 Spring configuration. 값이 비어 있을 때의 동작까지 구분한다. 실제 Secret 값은 문서·이미지·Git에 넣지 않는다.

| ENV | 분류 | 기본값/조건 |
| --- | --- | --- |
| `DB_URL` | REQUIRED_BOOT | 없음. MariaDB JDBC URL 필요 |
| `DB_USERNAME` | REQUIRED_BOOT | 없음 |
| `DB_PASSWORD` | REQUIRED_BOOT, SECRET | 없음 |
| `DEARUS_JWT_SECRET_BASE64` | REQUIRED_BOOT, SECRET | 없음. Base64 디코딩 후 최소 256비트 |
| `KAKAO_APP_ID` | REQUIRED_BOOT | 없음. 양의 숫자여야 함 |
| `GOOGLE_CLIENT_ID` | OPTIONAL | 빈 값이면 부팅은 가능하지만 Google 로그인 검증 불가 |
| `SWAGGER_ENABLED` | OPTIONAL | `true` |
| `DEARUS_OPENAPI_SERVER_URL` | OPTIONAL | 빈 값. 추가 OpenAPI 서버 URL |
| `DEARUS_INVITATION_BASE_URL` | OPTIONAL | 빈 값이면 부팅 가능하지만 초대 URL 생성 불가 |
| `PROFILE_IMAGE_STORAGE_ENABLED` | OPTIONAL | `false` |
| `PROFILE_IMAGE_STORAGE_BUCKET` | IMAGE_REQUIRED_IF_ENABLED | 프로필 이미지 저장 활성화 시 필수 |
| `PROFILE_IMAGE_STORAGE_REGION` | IMAGE_REQUIRED_IF_ENABLED | `ap-northeast-2` |
| `PROFILE_IMAGE_STORAGE_PUBLIC_BASE_URL` | IMAGE_REQUIRED_IF_ENABLED | 활성화 시 필수. DB에 저장할 관리 대상 S3 객체의 기준 URL; 버킷 공개 설정을 뜻하지 않음 |
| `PROFILE_IMAGE_STORAGE_ENDPOINT` | OPTIONAL | 빈 값. 별도 S3 endpoint가 필요할 때만 |
| `BABY_IMAGE_STORAGE_ENABLED` | OPTIONAL | `false` |
| `BABY_IMAGE_STORAGE_BUCKET` | IMAGE_REQUIRED_IF_ENABLED | 아기 이미지 저장 활성화 시 필수 |
| `BABY_IMAGE_STORAGE_REGION` | IMAGE_REQUIRED_IF_ENABLED | `ap-northeast-2` |
| `BABY_IMAGE_STORAGE_PUBLIC_BASE_URL` | IMAGE_REQUIRED_IF_ENABLED | 활성화 시 필수. DB에 저장할 관리 대상 S3 객체의 기준 URL; 버킷 공개 설정을 뜻하지 않음 |
| `BABY_IMAGE_STORAGE_ENDPOINT` | OPTIONAL | 빈 값. 별도 S3 endpoint가 필요할 때만 |
| `DEARUS_IMAGE_PRESIGNED_GET_TTL` | OPTIONAL | `PT1H`. 1초 이상~7일 이하 |
| `DEARUS_VOICE_ENABLED` | OPTIONAL | `false`. 실제 음성 처리는 STT와 LLM도 활성화 필요 |
| `DEARUS_VOICE_MAX_AUDIO_BYTES` | OPTIONAL | `5242880` (5 MiB) |
| `DEARUS_GOOGLE_CREDENTIALS_JSON` | SECRET, GOOGLE_VOICE_REQUIRED_IF_ENABLED | Google STT 또는 Vertex Gemini 사용 시 필요. 비어 있으면 ADC |
| `GOOGLE_CLOUD_PROJECT` | GOOGLE_VOICE_REQUIRED_IF_ENABLED | Google STT 또는 Vertex Gemini 사용 시 필수 |
| `GOOGLE_CLOUD_LOCATION` | OPTIONAL | `global`. Vertex AI 위치 |
| `DEARUS_STT_ENABLED` | VOICE_REQUIRED_IF_ENABLED | `false`; 실제 음성 처리에는 `true` |
| `DEARUS_STT_PROVIDER` | OPTIONAL | `GOOGLE_SPEECH`; Railway OpenAI 사용 시 `OPENAI` |
| `DEARUS_STT_LOCATION` | OPTIONAL | `global` |
| `DEARUS_STT_LANGUAGE_CODE` | OPTIONAL | `ko-KR` |
| `DEARUS_STT_MODEL` | OPTIONAL | `short` |
| `DEARUS_STT_TIMEOUT_MS` | OPTIONAL | `30000`, 1000~120000 ms |
| `DEARUS_LLM_ENABLED` | VOICE_REQUIRED_IF_ENABLED | `false`; 실제 음성 처리에는 `true` |
| `DEARUS_LLM_PROVIDER` | OPTIONAL | `VERTEX_GEMINI`; Railway OpenAI 사용 시 `OPENAI` |
| `OPENAI_API_KEY` | SECRET, REQUIRED_IF_OPENAI | OpenAI STT 또는 LLM 사용 시 필수 |
| `DEARUS_LLM_MODEL` | OPTIONAL | `gemini-2.5-flash` |
| `DEARUS_LLM_TIMEOUT_MS` | OPTIONAL | `30000`, 1000~120000 ms |

ECS에서는 DB/JWT/Google 자격증명 Secret을 이미지에 COPY하지 말고 task definition의 `secrets`로 주입한다. Secret 참조를 읽는 권한은 실행 역할에, 애플리케이션의 S3 접근 권한은 작업 역할에 둔다. 이미지 저장을 켜는 경우 두 bucket·region·base URL과 작업 역할의 S3 권한이 필요하다. Google Voice를 켜는 경우 서비스 계정 JSON 주입(또는 사용 가능한 ADC), 프로젝트·위치·권한·API 활성화를 배포 환경에서 확인해야 한다.
