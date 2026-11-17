# 게스트 로그인 API

## 목적과 범위

소셜 SDK 설정이 끝나기 전에도 프론트가 실제 DearUs API로 아기·기록·홈 기능을 연동할 수 있도록 임시 회원 세션을 발급한다. 게스트도 일반 회원과 같은 JWT 권한 검사를 거치며 접근 가능한 아기 데이터는 자신의 회원 ID를 기준으로 제한된다.

## 요청

- `POST /api/v1/auth/guest`
- 인증 헤더와 요청 본문 없음. `Content-Type`도 필수가 아니다.
- 호출할 때마다 새 게스트 회원이 생성된다. 이미 토큰을 보유한 앱은 이 엔드포인트를 다시 호출하지 말고 기존 Access Token 또는 `POST /api/v1/auth/reissue`를 사용한다.

## 성공 응답

HTTP `200 OK`, 기존 소셜 로그인과 동일한 JSON 봉투·필드다. 아래 토큰은 예시 문자열이며 실제 값이 아니다.

```json
{
  "success": true,
  "data": {
    "accessToken": "<JWT>",
    "refreshToken": "<opaque-token>",
    "tokenType": "Bearer",
    "accessTokenExpiresInSeconds": 1800,
    "refreshTokenExpiresInSeconds": 2592000,
    "isNewMember": true,
    "member": {
      "memberId": 123,
      "memberName": "게스트",
      "profileImageUrl": null,
      "lastSelectedBabyId": null
    }
  },
  "message": null
}
```

회원은 `email=null`, `socialAccounts=[]`, 기본 timezone `Asia/Seoul`로 저장된다. `GET /api/v1/members/me`에서 이를 확인할 수 있다. `memberId`는 호출마다 달라진다. Refresh Token은 서버에 해시만 저장하고, 재발급 때 회전한다.

## 앱 연결

1. 앱에 저장된 Access Token이 있으면 `GET /api/v1/members/me`로 확인한다. 만료됐다면 저장된 Refresh Token으로 `/auth/reissue`를 호출한다.
2. 둘 다 없거나 Refresh Token이 무효일 때만 `/auth/guest`를 호출한다.
3. 반환된 두 토큰을 플랫폼 보안 저장소에 함께 저장한다. 앱 재시작마다 새 게스트를 만들면 이전 아기·기록에 접근할 수 없다.
4. 모든 보호 API에 `Authorization: Bearer <accessToken>`을 보낸다. 이후 아기 등록·조회와 기록 API는 소셜 로그인 회원과 동일하다.
5. 로그아웃은 `/auth/logout`에 Refresh Token을 보낸 뒤 로컬 토큰을 삭제한다. 토큰을 잃거나 삭제하면 기존 게스트 데이터로 다시 로그인할 수 없다.

게스트에서 소셜 계정으로의 자동 병합·데이터 승계는 이번 범위에 없다. 게스트 상태에서 소셜 로그인을 호출하면 별도 회원 세션이 생긴다. 이 동작을 프론트에 명확히 표시해야 한다.

## DB·배포

기존 `member.email`이 nullable이고 `social_account`가 별도 자식 테이블이므로 새 컬럼·마이그레이션은 필요하지 않다. 신규 회원 1행과 Refresh 세션 1행이 저장된다. `DEARUS_JWT_SECRET_BASE64`, DB 연결 등 기존 필수 환경변수 외에 게스트 로그인 전용 환경변수는 없다. Railway 배포 후 HTTPS URL에서 게스트 로그인→`members/me`→아기 API→토큰 재발급을 실호출한다.
