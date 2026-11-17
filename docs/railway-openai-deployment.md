# Railway 배포 환경 변수: OpenAI 음성 처리

Railway 백엔드 서비스의 **Variables**에 다음 값을 설정한다. 값에 실제 비밀을 넣고, 이 파일이나 Git에는 넣지 않는다. `PORT`는 Railway가 제공하며 애플리케이션은 `PORT`로 수신한다.

## 부팅 필수

| 변수 | 값 |
| --- | --- |
| `DB_URL` | Railway에서 접근 가능한 MariaDB JDBC URL. 예: `jdbc:mariadb://<host>:3306/<database>` |
| `DB_USERNAME` | DB 사용자명 |
| `DB_PASSWORD` | DB 비밀번호 |
| `DEARUS_JWT_SECRET_BASE64` | Base64 인코딩한 32바이트 이상 랜덤 비밀값 |
| `KAKAO_APP_ID` | 카카오 앱의 숫자 ID |

기존 `.env`의 DB URL은 사설 RDS 주소이므로 Railway에서 접근 가능한 DB 주소로 바꿔야 한다. DB에는 Flyway 마이그레이션을 실행할 권한이 필요하다. Railway MySQL 서비스를 사용할 경우 MariaDB JDBC 드라이버 및 현재 마이그레이션과의 호환성을 별도로 검증한다.

## OpenAI 음성 기능

| 변수 | 값 |
| --- | --- |
| `DEARUS_VOICE_ENABLED` | `true` |
| `DEARUS_STT_ENABLED` | `true` |
| `DEARUS_STT_PROVIDER` | `OPENAI` |
| `DEARUS_STT_MODEL` | `gpt-4o-mini-transcribe` |
| `DEARUS_LLM_ENABLED` | `true` |
| `DEARUS_LLM_PROVIDER` | `OPENAI` |
| `DEARUS_LLM_MODEL` | `gpt-4o-mini` |
| `OPENAI_API_KEY` | 새로 발급한 프로젝트 API 키. 채팅에 노출된 키는 폐기 |

`DEARUS_STT_TIMEOUT_MS`와 `DEARUS_LLM_TIMEOUT_MS`의 기본값은 각각 `30000`ms이다. `DEARUS_VOICE_MAX_AUDIO_BYTES` 기본값은 `5242880`(5 MiB)이다. 오디오 업로드는 기존 M4A/MP4/WAV API 계약을 유지한다.

## 사용하는 기능에 따라 설정

| 변수 | 언제 필요한가 |
| --- | --- |
| `GOOGLE_CLIENT_ID` | Google **로그인**을 사용할 때. Google STT 설정과 무관 |
| `DEARUS_INVITATION_BASE_URL` | 초대 링크를 생성할 때, 앱 링크용 HTTPS URL |
| `SWAGGER_ENABLED` | 운영에서 Swagger 공개를 끄려면 `false` |
| `DEARUS_OPENAPI_SERVER_URL` | Swagger에 배포 서버 URL을 추가하려면 HTTPS URL |
| `PROFILE_IMAGE_STORAGE_ENABLED` | 회원 사진을 S3에 저장할 때 `true` |
| `PROFILE_IMAGE_STORAGE_BUCKET` | 회원 사진 저장 활성화 시 버킷 |
| `PROFILE_IMAGE_STORAGE_REGION` | 회원 사진 저장 활성화 시 리전. 기본 `ap-northeast-2` |
| `PROFILE_IMAGE_STORAGE_PUBLIC_BASE_URL` | 회원 사진 저장 활성화 시 S3 객체 기준 URL |
| `BABY_IMAGE_STORAGE_ENABLED` | 아기 사진을 S3에 저장할 때 `true` |
| `BABY_IMAGE_STORAGE_BUCKET` | 아기 사진 저장 활성화 시 버킷 |
| `BABY_IMAGE_STORAGE_REGION` | 아기 사진 저장 활성화 시 리전. 기본 `ap-northeast-2` |
| `BABY_IMAGE_STORAGE_PUBLIC_BASE_URL` | 아기 사진 저장 활성화 시 S3 객체 기준 URL |
| `DEARUS_IMAGE_PRESIGNED_GET_TTL` | 이미지 읽기 URL 유효 기간. 기본 `PT1H` |

OpenAI 음성 처리에는 `DEARUS_GOOGLE_CREDENTIALS_JSON`, `GOOGLE_CLOUD_PROJECT`, `GOOGLE_CLOUD_LOCATION`, `DEARUS_STT_LOCATION`이 필요하지 않다. 배포 후 `/actuator/health`로 부팅 상태를 확인한다. 실제 음성 처리 성공은 새 키와 Railway 환경에서 업로드·결과 조회로 확인해야 한다.

게스트 로그인에는 새 환경변수나 DB 마이그레이션이 없다. 배포 후 Railway HTTPS 도메인에서 본문 없는 `POST /api/v1/auth/guest`를 호출하고, 발급된 JWT로 `GET /api/v1/members/me` 및 아기 API를 확인한다. 프론트의 `EXPO_PUBLIC_API_URL`은 이 HTTPS 도메인만 지정하며 끝에 `/api/v1`이나 `/`를 붙이지 않는다. 프론트 앱은 `EXPO_PUBLIC_USE_MOCK=false`로 설정한 뒤 Expo를 캐시 초기화해 다시 시작한다. 기존 AWS ALB 주소를 계속 사용하면 Railway 배포 코드는 호출되지 않는다.
