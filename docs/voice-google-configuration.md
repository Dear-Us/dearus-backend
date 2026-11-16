# Google Voice provider 설정

Voice 처리는 기본 비활성화다. 아래 값은 백엔드 런타임에만 제공한다. 모바일 앱에 Google 서비스 계정 JSON, API 키 또는 서버 자격증명을 전달하지 않는다. `frontend/` 설정은 이 문서의 범위가 아니다.

| ENV | 기본값 | 비밀 여부 | 의미 |
| --- | --- | --- | --- |
| `DEARUS_VOICE_ENABLED` | `false` | 아니오 | Voice API 처리 활성화 |
| `DEARUS_VOICE_MAX_AUDIO_BYTES` | `5242880` | 아니오 | 업로드 오디오 최대 바이트 |
| `DEARUS_GOOGLE_CREDENTIALS_JSON` | 비어 있음 | **예** | 서비스 계정 JSON 문자열. 비어 있으면 ADC 사용 |
| `GOOGLE_CLOUD_PROJECT` | 비어 있음 | 아니오 | STT V2 및 Vertex AI 프로젝트 ID |
| `GOOGLE_CLOUD_LOCATION` | `global` | 아니오 | Vertex AI 위치 |
| `DEARUS_STT_ENABLED` | `false` | 아니오 | Google Speech-to-Text V2 어댑터 활성화 |
| `DEARUS_STT_LOCATION` | `global` | 아니오 | STT V2 recognizer 위치 |
| `DEARUS_STT_LANGUAGE_CODE` | `ko-KR` | 아니오 | 인식 언어 |
| `DEARUS_STT_MODEL` | `short` | 아니오 | 인식 모델 |
| `DEARUS_STT_TIMEOUT_MS` | `30000` | 아니오 | STT RPC 타임아웃, 허용 범위 1000–120000 ms |
| `DEARUS_LLM_ENABLED` | `false` | 아니오 | Vertex Gemini 파서 활성화 |
| `DEARUS_LLM_PROVIDER` | `VERTEX_GEMINI` | 아니오 | 현재 지원 제공자 |
| `DEARUS_LLM_MODEL` | `gemini-2.5-flash` | 아니오 | Gemini 모델 |
| `DEARUS_LLM_TIMEOUT_MS` | `30000` | 아니오 | Gemini HTTP 타임아웃, 허용 범위 1000–120000 ms |

실제 처리에는 Voice, STT, LLM 세 활성화 값을 모두 `true`로 설정한다. JSON 문자열은 AWS Secrets Manager에서 ECS secret environment variable로 주입할 계획이며, 소스·테스트 리소스·Git·로그에 저장하지 않는다. `GOOGLE_APPLICATION_CREDENTIALS`에 JSON 문자열을 넣지 않는다. 로컬 ADC 또는 향후 WIF를 쓰는 경우 `DEARUS_GOOGLE_CREDENTIALS_JSON`은 비워 둔다.

Google Cloud에서 Speech-to-Text API와 Vertex AI 접근, 서비스 계정의 Speech Client 및 Vertex AI User 권한, 프로젝트/위치의 모델 사용 가능 여부, 결제 및 할당량을 확인해야 한다. 현재 코드는 SDK 요청 구성과 가짜 gateway 테스트까지만 검증했으며 Google 실호출, ECS 주입, AWS DEV 배포는 별도 검증이 필요하다. 기능을 켜기 전 실제 한국어 WAV/M4A 짧은 녹음으로 end-to-end 테스트한다.
