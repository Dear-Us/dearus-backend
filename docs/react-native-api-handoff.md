# DearUs DEV → React Native API 연동 인계

> 2026-10-08 AWS DEV 기준의 이전 전달 문서다. Voice API 상태와 Railway/OpenAI 배포 설정은 현재 구현을 반영한 [모바일 연동 가이드](mobile-integration.md) 및 [Railway 설정](railway-openai-deployment.md)을 우선한다.

기준: 2026-10-08 `dev` 백엔드 Controller/DTO와 배포된 OpenAPI를 대조했다. 이 문서는 프론트엔드 Codex에게 **첨부해서 전달하는 작업 지시서**다. 실제 요청·응답 스키마의 최종 기준은 배포 OpenAPI이며, 배포 버전이 바뀌면 다시 확인한다.

## 프론트엔드 Codex에 그대로 보낼 프롬프트

> 첨부한 `react-native-api-handoff.md`를 끝까지 읽고 DearUs React Native 프론트엔드의 **실제 DEV API 연동**을 완료해 주세요. 먼저 프론트 저장소의 Expo/bare 여부, TypeScript 여부, 기존 HTTP 클라이언트·상태 관리·내비게이션·보안 저장소·소셜 SDK·화면 구조를 조사하고 기존 패턴을 재사용하세요. 배포된 OpenAPI `http://dearus-dev-alb-866733209.ap-northeast-2.elb.amazonaws.com/v3/api-docs`의 메서드·경로·스키마를 코드와 대조하고, 필드나 enum을 추측하지 마세요. Base URL은 환경별 설정으로만 주입하고 토큰·Provider 설정값을 소스나 로그에 하드코딩하지 마세요. 로그인/재발급/로그아웃, 아기 선택, Home/History/Care/Stats/Growth, 보호자·초대 중 **프론트에 존재하는 화면**을 기존 UI에 연결하고 로딩·빈 상태·오류·권한 상태를 처리하세요. 없는 화면을 임의로 신설하지 말고 누락 목록으로 보고하세요. 401 재발급은 single-flight로 구현하고 새 Access/Refresh를 함께 교체한 뒤 원 요청을 한 번만 재시도하세요. SSE는 Bearer **헤더**를 지원할 때만 연결하고 이벤트를 받으면 REST를 재조회하세요. Voice API는 미구현이므로 호출·가짜 성공 처리하지 마세요. 백엔드·DB·마이그레이션을 수정하지 말고, 프론트의 기존 테스트와 실제 기기/에뮬레이터 스모크 테스트를 실행해 결과를 보고하세요. DEV 서버가 현재 HTTP만 제공하므로 iOS/Android의 cleartext 정책과 HTTPS 제공 여부를 먼저 점검하고, 전역 보안 완화가 필요하면 임의 적용하지 말고 나에게 선택을 요청하세요. Google/Kakao/Naver 네이티브 앱 식별자·키 또는 초대 딥링크 도메인이 누락되었다면 정확한 누락 항목을 보고하고, 나머지 연동은 계속 진행하세요.

이 문서를 프론트 Codex에 파일로 첨부할 수 없다면, 프롬프트와 아래 계약을 함께 붙여 넣는다. 이 백엔드 저장소의 절대 경로가 프론트 Codex에서도 보인다고 가정하지 않는다.

## 1. 접속 주소와 현재 사용 가능 범위

| 항목 | DEV 값 |
| --- | --- |
| REST Base URL | `http://dearus-dev-alb-866733209.ap-northeast-2.elb.amazonaws.com/api/v1` |
| OpenAPI JSON | `http://dearus-dev-alb-866733209.ap-northeast-2.elb.amazonaws.com/v3/api-docs` |
| Swagger UI | `http://dearus-dev-alb-866733209.ap-northeast-2.elb.amazonaws.com/swagger-ui/index.html` |
| 인증 | 보호 API: `Authorization: Bearer {DearUs Access JWT}` |

- 점검 시 OpenAPI JSON은 HTTP 200이었고 41개 API가 노출되었다. 같은 ALB의 HTTPS 443 연결은 타임아웃되었다. 프론트 코드는 이 DEV 호스트를 상수로 박지 말고 프로젝트의 환경별 설정에 넣는다. 현재 HTTP는 개발용이며 **Bearer 토큰이 전송 중 TLS로 보호되지 않는다**. 실제 기기에서 플랫폼의 cleartext 제한이 걸릴 수 있다. 운영에는 HTTPS가 필요하다. iOS ATS/Android cleartext를 전역 허용하지 않는다.
- `VOICE-01~04` 음성 명령 API, Google STT/LLM 어댑터는 **미구현**이다. 마이크 UI를 만들더라도 현재 백엔드에 업로드하거나 성공한 것처럼 표시하지 않는다.
- 이미지 업로드 API는 존재하지만 저장소 설정 여부에 따라 503이 가능하다. 초대 링크의 실제 도메인도 배포 설정에 의존하므로 반환된 `invitationUrl`을 확인한다.
- 앱에 DB 비밀번호, JWT 서명 키, 서버용 Google credential을 넣지 않는다. Provider SDK용 앱 공개 식별자와 서버 Secret은 구분한다.

## 2. 공통 HTTP 계약

```json
{"success":true,"data":{},"message":null}
```

실패는 `{"success":false,"data":null,"message":"..."}`다. 외부 `errorCode` 필드는 없다. HTTP 상태와 `success`를 **둘 다** 검사한다. 삭제·로그아웃 등은 성공해도 `data:null`일 수 있다. SSE만 `text/event-stream`이며 이 JSON 봉투가 아니다. API 요청은 기본적으로 JSON이고, 아기·회원 사진 API만 아래 multipart 계약을 따른다.

| HTTP | 프론트 처리 |
| --- | --- |
| 400 | 입력·요청 형식 확인. 무조건 자동 재시도하지 않음 |
| 401 | 보호 요청만 Refresh 재발급 후 원 요청 1회 재시도; 재발급 실패 시 세션 종료/재로그인 |
| 403 | 탈퇴 회원 또는 명시적 OWNER/CAREGIVER 권한 문제. 토큰 재발급으로 해결된다고 가정하지 않음 |
| 404 | 삭제·접근 불가·다른 가족 데이터 가능. 리소스 존재 여부를 추측하지 않음 |
| 409 | 상태/낙관적 `version` 충돌. 최신 상세 재조회 후 사용자에게 선택을 요청; 로그아웃하지 않음 |
| 413 | 이미지 또는 multipart 크기 초과 |
| 503 | 소셜 제공자·이미지 저장소 등 현재 사용 불가 가능 |

날짜 필드는 `YYYY-MM-DD`, 시간 필드는 offset을 포함한 ISO 8601 (`2026-10-05T10:30:00+09:00` 또는 `Z`)이다. 날짜 경계는 `GET /members/me`의 IANA `timezone`을 기준으로 서버가 판단한다. 기기 timezone이나 문자열의 고정 길이/offset을 가정하지 않는다.

## 3. 인증·앱 시작 순서

1. 프론트의 Google/Kakao/Naver SDK가 Provider 인증을 완료한다. `POST /auth/login` JSON은 `{"provider":"GOOGLE","providerToken":"..."}`. Google은 **ID Token**, Kakao/Naver는 **Access Token**을 보낸다. 백엔드는 `(provider, providerUserId)`로 회원을 찾고, 이메일이 같아도 자동 병합하지 않는다.
2. 로그인 `data`: `accessToken`, `refreshToken`, `tokenType`(`Bearer`), `accessTokenExpiresInSeconds`, `refreshTokenExpiresInSeconds`, `isNewMember`, `member`(`memberId`, `memberName`, `profileImageUrl`, `lastSelectedBabyId`). Refresh 원문은 이 응답과 재발급 응답에서만 받을 수 있다.
3. Access/Refresh를 iOS Keychain 또는 Android Keystore 계열의 **보안 저장소**에 저장한다. 일반 AsyncStorage, 소스 파일, 로그, 분석 이벤트, URL에 토큰을 넣지 않는다.
4. 앱 시작 시 보관 토큰으로 `GET /members/me`. `data`는 `memberId`, `email`, `memberName`, `profileImageUrl`, `timezone`, `lastSelectedBabyId`, `socialAccounts[]`이다. `lastSelectedBabyId:null`이면 `GET /babies` 후 아기 선택/등록 화면으로 이동한다. 임의 babyId로 Home을 호출하지 않는다.
5. 선택 변경: `PATCH /members/me/last-selected-baby` JSON `{"babyId":123}` → 이전 SSE 종료 → 새 아기 Home 조회 → 새 SSE 연결. 아기 삭제/공동 보호자 관계 종료로 선택 ID가 `null`이 될 수 있다.
6. 보호 요청의 401을 만나면 `POST /auth/reissue` JSON `{"refreshToken":"..."}`를 **단 한 번** 수행한다. 동시 401은 하나의 Promise/작업으로 묶는다. 성공 시 새 Access와 Refresh를 **같이** 보안 저장소에 교체하고 대기 중인 요청을 새 Access로 1회만 재시도한다. 옛 Refresh는 재사용할 수 없다. 재발급 요청 자체에 401 재시도를 걸지 않는다.
7. 로그아웃은 `POST /auth/logout` JSON `{"refreshToken":"..."}`. Access 헤더 불필요. 성공 또는 더 이상 유효하지 않은 세션이면 SSE를 닫고 로컬 두 토큰을 지운다. 서버는 그 Refresh 세션만 폐기하며 이미 발급된 Access JWT를 즉시 blacklist하지 않는다.

공개 API는 로그인·재발급·로그아웃과 초대 미리보기 `GET /invitations/{token}`다. 나머지는 Bearer가 필요하다. SDK client ID/키, Android package/SHA-1/Kakao key hash, iOS bundle ID/URL scheme은 프론트 프로젝트·Provider 콘솔에서 확인해야 한다. 모르면 만들거나 추측하지 말고 사용자에게 누락값을 요청한다.

## 4. 화면별 API 연결 순서

| 화면/흐름 | 호출 및 캐시 갱신 |
| --- | --- |
| 로그인/부트 | Provider SDK → `POST /auth/login` → `GET /members/me` → `GET /babies` 또는 선택 Baby Home |
| 아기 선택·설정 | `GET /babies`, `GET /babies/{babyId}`, `PATCH /members/me/last-selected-baby`; 변경 후 이전 SSE 해제 |
| Home | `GET /babies/{babyId}/home`; `baby`, `activeRecord`, `todaySummary`, `lastCompletedRecord`, `recentRecords` 사용. `activeRecord`·`lastCompletedRecord`는 null 가능 |
| History | `GET /babies/{babyId}/records?date=YYYY-MM-DD[&recordType=...]`, `GET /babies/{babyId}/records/calendar?year=YYYY&month=M`, `GET /babies/{babyId}/records/{recordId}`. `date`, `year`, `month` 필수 |
| Care 작성/타이머 | 완료 기록 `POST /records`; 진행형 `POST /records/start`, `GET /records/active`, `PATCH /records/{recordId}/complete|cancel`. 성공 후 Home/History/Stats 재조회 |
| Care 편집/삭제 | `PATCH /records/{recordId}` body에 현재 `version` 필수; `DELETE /records/{recordId}?version=N`. 409 시 상세 재조회 |
| Stats | `GET /stats/daily?date=YYYY-MM-DD`, `GET /stats/routine?endDate=YYYY-MM-DD`, `GET /stats/growth`. 데이터가 없으면 0 또는 null 가능 |
| Growth | `GET /growth-records`, `POST /growth-records`, `GET /growth-records/{growthRecordId}`, `PATCH /growth-records/{growthRecordId}`, `DELETE /growth-records/{growthRecordId}?version=N`. 성장 통계 캐시도 갱신 |
| 보호자·초대 | OWNER만 초대 생성/목록/취소·아기 수정/삭제·CAREGIVER 내보내기·OWNER 이전. CAREGIVER는 본인 나가기 가능. 역할은 Baby 응답의 `myRole`로 판단 |
| 초대 딥링크 | `invitationUrl` 공유 → 토큰 추출 → 공개 `GET /invitations/{token}` → 필요 시 로그인 → 인증 `POST /invitations/{token}/accept` → Baby 목록/선택 갱신 |

### 주 화면에서 사용하는 응답 필드

- `BabyResponse`: `babyId`, `babyName`, `birthDate`, `gender`(`MALE|FEMALE`), `profileImageUrl|null`, `myRole`(`OWNER|CAREGIVER`).
- `HomeResponse`: `baby`, `activeRecord|null`, `todaySummary`(`date`, `totalRecordCount`, `feedingCount`, `sleepCount`, `diaperCount`, `temperatureCount`, `medicineCount`, `otherCount`), `lastCompletedRecord|null`, `recentRecords[]`.
- `CareRecordResponse`: `recordId`, `babyId`, `recordType`, `inputType`, `status`, `recordedAt`, `startAt|null`, `endAt|null`, `memo|null`, 유형별 `detail|null`, `version`. `SLEEP`의 `detail`은 null. `GET /records/active`는 진행 중 기록이 없으면 `data:null`.
- Calendar 항목: `{date, recordCount}`. History 목록은 **해당 날짜의 완료·미삭제 기록만** 반환한다.
- Daily Stats: `{date, feeding, sleep, diaper, temperature, medicine, other}`. Routine: `{startDate, endDate, days[], averages}`. Growth Stats: `{latest, changeFromPrevious, points[]}`. 세부 수치·nullable은 OpenAPI 스키마를 그대로 따른다.
- `GrowthRecordResponse`: `growthRecordId`, `babyId`, `measuredDate`, `heightCm|null`, `weightKg|null`, `memo|null`, `version`. **머리둘레 필드는 없다.**

## 5. 요청 형식에서 자주 틀리는 부분

### 사진 multipart

`PATCH /members/me`, `POST /babies`, `PATCH /babies/{babyId}`는 `multipart/form-data`다. 필수 part 이름은 `request`, 이 part의 **Content-Type은 `application/json`**이며 내용은 JSON이다. 선택 파일 part 이름은 `profileImage`. 지원 MIME은 JPEG/PNG/WebP, 파일당 최대 5 MiB, 전체 multipart 최대 6 MB. React Native 로컬 파일 URI를 **파일 바이트**로 전송한다. `Content-Type: multipart/form-data`의 boundary는 클라이언트 라이브러리에 맡기며 직접 고정하지 않는다.

| API | `request` JSON |
| --- | --- |
| 아기 생성 | `{"babyName":"서아","birthDate":"2026-05-01","gender":"FEMALE"}` |
| 아기 수정 | 변경할 `babyName?`, `birthDate?`, `gender?`, `removeProfileImage?` 중 최소 하나 |
| 내 정보 수정 | 변경할 `memberName?`, `removeProfileImage?` 중 최소 하나 |

`removeProfileImage:true`와 `profileImage` 파일은 동시에 보낼 수 없다. 수정에서 생략한 필드는 유지된다. 이미지 저장소가 비활성화된 DEV에서는 파일 없는 등록/수정을 우선 테스트한다.

### 완료 Care Record 생성

`POST /babies/{babyId}/records`는 JSON, 성공 201. 서버가 `inputType:MANUAL`, `status:COMPLETED`를 설정한다. **SLEEP만** `startAt/endAt`을 보내고 `recordedAt/detail`은 보내지 않는다. 나머지는 `recordedAt/detail`을 보낸다. 서버 현재 시간보다 미래인 기록은 거절한다.

```json
{"recordType":"FEEDING","recordedAt":"2026-10-05T08:00:00+09:00","detail":{"feedingType":"FORMULA","amountMl":120}}
{"recordType":"FEEDING","recordedAt":"2026-10-05T08:00:00+09:00","detail":{"feedingType":"DIRECT_BREASTFEEDING","breastSide":"LEFT","durationMinutes":15}}
{"recordType":"SLEEP","startAt":"2026-10-05T13:00:00+09:00","endAt":"2026-10-05T14:00:00+09:00"}
{"recordType":"DIAPER","recordedAt":"2026-10-05T10:00:00+09:00","detail":{"diaperType":"URINE"}}
{"recordType":"TEMPERATURE","recordedAt":"2026-10-05T10:00:00+09:00","detail":{"temperatureCelsius":36.5,"measuredPart":"EAR"}}
{"recordType":"MEDICINE","recordedAt":"2026-10-05T10:00:00+09:00","detail":{"medicineName":"비타민D","doseValue":1,"doseUnit":"DROP"}}
{"recordType":"OTHER","recordedAt":"2026-10-05T10:00:00+09:00","detail":{"title":"목욕"}}
```

`recordType`: `FEEDING|SLEEP|DIAPER|TEMPERATURE|MEDICINE|OTHER`. 세부 enum: `feedingType=FORMULA|DIRECT_BREASTFEEDING`, `breastSide=LEFT|RIGHT|BOTH`(완료 직접 수유), `diaperType=URINE|STOOL|BOTH`, `measuredPart=EAR|FOREHEAD|ARMPIT|OTHER`, `doseUnit=ML|MG|DROP|TABLET|OTHER`. 정확한 추가/선택 필드와 `oneOf`는 OpenAPI를 읽는다. 요청 DTO는 여분 필드를 거절한다.

### 진행형 Care와 Growth

- `POST /records/start`는 `{"recordType":"SLEEP"}` 또는 `{"recordType":"FEEDING","detail":{"feedingType":"DIRECT_BREASTFEEDING","breastSide":"LEFT"}}`; 선택 `memo` 가능. `breastSide:BOTH`는 시작 시 불가. Baby당 IN_PROGRESS 최대 1건이라 충돌은 409. 완료/취소는 시작 응답의 `recordId`로 PATCH한다. 완료 기록 생성과 별개의 흐름이다.
- Care 수정은 `{"version":0,"memo":"변경"}`처럼 `version` 외 최소 한 필드가 필요하다. `memo:null`은 메모 삭제. `detail` 교체 시 해당 유형의 상세를 **전체** 보내야 한다. 삭제는 body가 아니라 `?version=0`.
- Growth 생성은 `{"measuredDate":"2026-10-05","heightCm":62.5,"weightKg":6.8,"memo":"측정"}`. `heightCm`/`weightKg` 중 최소 하나가 필요하며 머리둘레는 받지 않는다. 미래 날짜 불가. 수정은 `version` 외 최소 한 필드, 삭제는 `?version=N`; 같은 날짜 여러 측정 허용.

## 6. 전체 엔드포인트 목록 (41개)

아래 경로는 전부 `/api/v1` 뒤에 붙인다. `B`는 `{babyId}`, `R`은 `{recordId}`, `G`는 `{growthRecordId}`. 초대 토큰 URL 이외에는 Access/Refresh를 URL query에 넣지 않는다.

| 영역 | Method + path |
| --- | --- |
| Auth (3) | `POST /auth/login`, `POST /auth/reissue`, `POST /auth/logout` |
| Member (4) | `GET /members/me`, `PATCH /members/me`, `PATCH /members/me/last-selected-baby`, `DELETE /members/me` |
| Baby (5) | `GET /babies`, `POST /babies`, `GET /babies/B`, `PATCH /babies/B`, `DELETE /babies/B` |
| Baby Member (4) | `GET /babies/B/members`, `DELETE /babies/B/members/{memberId}`, `DELETE /babies/B/members/me`, `PATCH /babies/B/owner` (`{"memberId":2}`) |
| Invitation (5) | `POST /babies/B/invitations`, `GET /babies/B/invitations`, `DELETE /babies/B/invitations/{invitationId}`, `GET /invitations/{token}`, `POST /invitations/{token}/accept` |
| Care (10) | `GET /babies/B/records`, `GET /babies/B/records/calendar`, `GET /babies/B/records/R`, `GET /babies/B/records/active`, `POST /babies/B/records`, `POST /babies/B/records/start`, `PATCH /babies/B/records/R`, `PATCH /babies/B/records/R/complete`, `PATCH /babies/B/records/R/cancel`, `DELETE /babies/B/records/R?version=N` |
| Home (1) | `GET /babies/B/home` |
| Stats (3) | `GET /babies/B/stats/daily?date=YYYY-MM-DD`, `GET /babies/B/stats/routine?endDate=YYYY-MM-DD`, `GET /babies/B/stats/growth` |
| Growth (5) | `GET /babies/B/growth-records`, `POST /babies/B/growth-records`, `GET /babies/B/growth-records/G`, `PATCH /babies/B/growth-records/G`, `DELETE /babies/B/growth-records/G?version=N` |
| Event (1) | `GET /babies/B/events` (`Accept: text/event-stream`) |

생성 성공 201: Baby, Invitation, 완료 Care, 진행형 Care 시작, Growth. 다른 일반 성공은 200이다. 초대 생성은 body가 없으며 OWNER 전용. 목록 응답은 원문 토큰/URL을 다시 주지 않는다. OWNER 이전은 기존 OWNER를 CAREGIVER로, 대상 ACTIVE CAREGIVER를 OWNER로 원자적으로 바꾼다.

## 7. SSE·딥링크·비동기 상태

- SSE 경로는 `GET /babies/{babyId}/events`; `Authorization: Bearer ...`와 `Accept: text/event-stream`을 **헤더**로 보내는 React Native 클라이언트가 필요하다. JWT query parameter는 지원하지 않는다. 헤더를 못 넣는 기본 EventSource만 있다면 억지로 우회하지 말고 해당 제약을 보고한다.
- 이벤트 이름 `record-change`, payload `{eventType,babyId,recordId,recordType,occurredAt}`. `eventType`: `RECORD_CREATED|RECORD_STARTED|RECORD_COMPLETED|RECORD_CANCELED|RECORD_UPDATED|RECORD_DELETED`. 이벤트는 best-effort 알림이며 데이터 원본이 아니다. 이벤트 후 Home/History/Stats를 재조회한다. 약 30초 heartbeat는 주석이며 business event가 아니다. `Last-Event-ID` replay는 없다. 앱 복귀·재연결 시 Home을 재조회한다.
- 초대 생성 응답 `invitationUrl`은 한 번만 제공되는 원문 토큰 포함 링크다. 목록은 상태만 제공한다. iOS Universal Links/Android App Links/URL scheme, 앱 링크 도메인은 프론트와 배포 설정의 별도 결정 사항이다. 토큰 URL을 로그·분석 이벤트에 남기지 않는다.
- `GET /babies/B/members`는 `memberId`, `memberName`, `profileImageUrl`, `role`을 반환한다. 초대 목록은 `PENDING|ACCEPTED|CANCELED|EXPIRED` 상태를 반환한다. 삭제/탈퇴·초대 수락·OWNER 이전 후 관련 Baby/Member/Home 캐시를 갱신한다.

## 8. 프론트 Codex 완료 기준

- 기존 화면의 mock/하드코딩 데이터가 실제 DEV API 응답으로 교체되었다. 새 백엔드 API나 VOICE 경로를 가정하지 않았다.
- Provider SDK → DearUs 로그인, 앱 재실행 후 세션 복원, 동시 401 single-flight rotation, 재발급 실패, 로그아웃을 테스트했다.
- Baby 없음/선택 없음, Home 빈 상태, History 날짜·유형, Stats 0/null, Growth 빈 목록과 차트, OWNER/CAREGIVER 권한에 따른 UI를 확인했다.
- Care/Growth 생성·수정·삭제와 409 최신 상세 재조회, multipart의 `request` part Content-Type, 이미지 미설정 503, 초대 딥링크의 토큰 취급을 확인했다.
- SSE가 헤더 Bearer로 실제 연결된다면 이벤트 후 REST 재조회와 재연결을 확인했다. 연결할 수 없다면 미완료로 정확히 보고했다.
- 실제 기기/에뮬레이터에서 DEV HTTP 정책을 확인했다. 보안 정책 완화나 Provider 콘솔 등록이 필요한 경우 사용자의 결정을 요청했다. 토큰·Secret·실제 회원 개인정보를 커밋/로그에 남기지 않았다.

테스트 데이터가 필요하면 백엔드의 `scripts/seed-dev-data.sh`를 **별도 테스트 계정의 DearUs JWT로** 실행할 수 있다. 이 스크립트는 실제 DEV API를 호출하고 재실행 시 기록이 중복될 수 있으므로 프론트 Codex가 사용자 허가 없이 실행하지 않는다.
