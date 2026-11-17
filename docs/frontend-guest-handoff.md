# 프론트 게스트 로그인 · Railway 연동 안내

기준: 백엔드 `dev`의 게스트 로그인 추가 버전. 이전 AWS ALB 인계 문서의 URL을 새 배포 주소로 사용하지 않는다.

## 프론트 환경변수

```dotenv
EXPO_PUBLIC_API_URL=https://<railway-public-domain>
EXPO_PUBLIC_USE_MOCK=false
```

`EXPO_PUBLIC_API_URL`은 끝의 `/`와 `/api/v1`을 뺀 HTTPS origin이다. 앱의 API 클라이언트가 `/api/v1`을 한 번만 붙이는지 확인한다. 기존에 공유된 AWS ALB HTTP 주소는 Railway 배포본이 아니며, 그 주소로는 새 게스트 API가 제공되지 않는다. Expo의 `EXPO_PUBLIC_` 값은 앱 빌드에 포함되므로 기존 APK의 주소를 바꾸려면 새 환경값으로 다시 빌드·배포해야 한다. 개발 서버도 `.env`를 바꾼 뒤 `npx expo start -c`로 다시 시작한다.

카카오 네이티브 앱 키·Google Web client ID는 소셜 로그인용이며 게스트 호출에는 필요하지 않다. 기존 앱에 소셜 네이티브 모듈이 포함돼 있다면 테스트 실행 방식은 프론트 프로젝트의 개발 빌드 설정을 따른다.

## 게스트 인증 흐름

1. 저장된 Access Token으로 `GET /api/v1/members/me`를 시도하고, 만료 시 Refresh Token으로 `POST /api/v1/auth/reissue`를 호출한다.
2. 저장된 세션이 없을 때만 `POST /api/v1/auth/guest`를 본문 없이 호출한다. 소셜 Provider 토큰이나 별도 게스트 ID는 보내지 않는다.
3. 성공 `data`의 `accessToken`, `refreshToken`을 기존 소셜 로그인과 같은 보안 저장소에 보관한다. 응답 필드는 [게스트 API 명세](api/guest-login.md)와 같다.
4. `GET /api/v1/members/me` → `GET /api/v1/babies` → 아기 등록·선택 → Home·기록 API를 실제 호출한다. 보호 API에는 Bearer Access Token을 헤더로 보낸다.
5. `401` 재발급은 기존 single-flight 처리와 두 토큰 동시 교체를 사용한다. 로그아웃 후 토큰을 지우면 같은 게스트 계정에 재로그인할 수 없다.

게스트는 일반 회원과 동일한 아기·기록 권한 규칙을 사용한다. `members/me`의 `email`은 `null`, `socialAccounts`는 빈 배열이다. 추후 소셜 로그인을 새로 하면 별도 회원으로 취급되며 데이터 자동 승계는 없다. 게스트 화면에서 이 점을 사용자에게 알린다.

## 배포 후 프론트 연동 점검

- Railway 서버 `GET /actuator/health`가 `200`과 `UP`을 반환한다.
- 게스트 로그인 응답의 `accessToken`으로 `GET /api/v1/members/me`가 `200`이다.
- 아기를 등록한 뒤 `GET /api/v1/babies`와 Home·기록 API가 같은 회원 데이터에 접근한다.
- Refresh Token 재발급 후 새 Access Token으로 보호 API가 성공하고, 이전 Refresh Token 재사용은 `401`이다.
- 실제 기기/에뮬레이터의 APK가 새 Railway HTTPS 주소를 바라보며 `EXPO_PUBLIC_USE_MOCK=false`로 동작한다.

현재 공유된 APK는 기존 ALB 주소로 빌드된 것으로 안내되어 있다. 새 Railway 주소와 게스트 로그인 UI·호출 코드를 포함한 프론트 새 빌드가 필요하다. 백엔드 저장소에는 프론트 소스가 없어 이 APK를 여기서 수정하거나 재빌드할 수 없다.
