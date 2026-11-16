# DearUs DEV API Stability Report

점검일: 2026-10-08 (Asia/Seoul)

범위: 현재 `dev` 체크아웃과 AWS DEV ALB의 공개 OpenAPI. Voice는 미구현이므로 제외.
주의: 이 문서는 **실제 호출 성공**, **로컬 자동화 통과**, **미실행**을 구분한다. 사용자가 제공한 DEV 배경 정보는 독립 검증 결과로 바꾸어 쓰지 않았다.

## Summary

- 구현 operation: **41개**. Controller mapping과 DEV `/v3/api-docs`의 method/path/operationId를 대조했다.
- 로컬 `clean test`: **269 tests, 0 failures, 0 errors**. Java 26 + 기존 Docker MariaDB Testcontainers 환경에서 성공.
- DEV 공개 smoke: health 200, Swagger UI 200, OpenAPI 200, 보호 API 무인증 401, 존재하지 않는 초대 토큰 404. 마지막 두 응답은 `success=false, data=null, message`이고 `errorCode`가 없다.
- DEV 인증 조회: **미실행**. 이번 셸에 `DEV_API_BASE_URL`, `DEARUS_ACCESS_TOKEN`이 제공되지 않았다. 로컬 `.env`에 Access Token은 없다. 기존 seed 데이터의 실제 조회나 보호 API의 실배포 성공은 이 점검에서 입증되지 않았다.
- DEV mutation: **미실행**. 실제 seed baby, 회원, 보호자·초대 관계를 변경하지 않았다.
- `scripts/dev-api-smoke.sh` 문법 검사 통과. **무효 placeholder token**으로 안전한 기동 경로를 시험했을 때 공개/무인증 검사 5건 PASS, 유효 JWT 조회 401(예상된 실패), 나머지 인증 조회 SKIP이었다. 이 시험을 유효 토큰 smoke PASS로 세지 않는다.
- 확정 실패 API: **0개**. 확인되지 않은 API를 PASS로 세지 않는다.
- 프론트 연동 안정성 최종 판정: **NOT_READY (검증 증거 부족)**. 인증된 DEV smoke가 없고, ALB 443 연결이 타임아웃되어 현재 확인된 접속은 HTTP다. 이는 41개 API가 고장 났다는 판정이 아니다.
- 검증/보안 blocker: **2개** — (1) 인증된 DEV smoke용 JWT 미제공, (2) 확인 가능한 HTTPS endpoint 부재.

분류: `A=LIVE_SAFE`(읽기/안전한 조회), `B=LIVE_ISOLATED_MUTATION`(별도 임시 Baby에서만 생성→검증→정리 가능), `C=LOCAL_INTEGRATION_ONLY`(실계정/관계 영향 때문에 DEV 금지), `D=MANUAL_EXTERNAL`(실제 provider token 필요).
결과: `DEV PASS`는 이번 점검의 실호출, `LOCAL PASS`는 전체 회귀 테스트만 통과, `SKIP`은 DEV 실호출 미실행이다. `A` 중 보호 조회의 테스트 방법은 `scripts/dev-api-smoke.sh`이며, 필요 데이터가 없는 상세 조회는 명시적으로 SKIP한다.

## API Matrix

| # | Method | Path | 인증 / 역할 | 분류 | 테스트 방법 | 이번 결과 |
|---:|---|---|---|:---:|---|---|
| 1 | POST | `/api/v1/auth/login` | 공개 / provider token | D | 실제 Google/Kakao/Naver token 수동 E2E; 로컬 Auth 통합 | LOCAL PASS, DEV SKIP |
| 2 | POST | `/api/v1/auth/reissue` | 공개 / refresh token | C | refresh rotation·동시성 로컬 통합 | LOCAL PASS, DEV SKIP |
| 3 | POST | `/api/v1/auth/logout` | 공개 / refresh token | C | 세션 revoke 로컬 통합 | LOCAL PASS, DEV SKIP |
| 4 | GET | `/api/v1/members/me` | Bearer / 본인 | A | 무인증 401, JWT 조회 | 401 DEV PASS, JWT SKIP |
| 5 | PATCH | `/api/v1/members/me` | Bearer / 본인 | C | multipart 수정·validation 로컬 통합 | LOCAL PASS, DEV SKIP |
| 6 | PATCH | `/api/v1/members/me/last-selected-baby` | Bearer / ACTIVE 보호자 | C | 선택/권한 로컬 통합 | LOCAL PASS, DEV SKIP |
| 7 | DELETE | `/api/v1/members/me` | Bearer / 본인 | C | 탈퇴·OWNER 충돌·세션 로컬 통합 | LOCAL PASS, DEV SKIP |
| 8 | GET | `/api/v1/babies` | Bearer / 본인 | A | JWT 목록, 소유 Baby 선택 | LOCAL PASS, DEV SKIP |
| 9 | POST | `/api/v1/babies` | Bearer / 활성 회원 | B | 임시 Baby multipart 생성 후 삭제 | LOCAL PASS, DEV SKIP |
| 10 | GET | `/api/v1/babies/{babyId}` | Bearer / ACTIVE 보호자 | A | JWT 상세, 미존재 Baby 404 | LOCAL PASS, DEV SKIP |
| 11 | PATCH | `/api/v1/babies/{babyId}` | Bearer / OWNER | B | 임시 Baby 수정·권한 fixture | LOCAL PASS, DEV SKIP |
| 12 | DELETE | `/api/v1/babies/{babyId}` | Bearer / OWNER | B | 임시 Baby soft delete·정리 | LOCAL PASS, DEV SKIP |
| 13 | GET | `/api/v1/babies/{babyId}/members` | Bearer / ACTIVE 보호자 | A | JWT 공동 보호자 목록 | LOCAL PASS, DEV SKIP |
| 14 | DELETE | `/api/v1/babies/{babyId}/members/{memberId}` | Bearer / OWNER | C | 제거·권한·rollback 로컬 통합 | LOCAL PASS, DEV SKIP |
| 15 | DELETE | `/api/v1/babies/{babyId}/members/me` | Bearer / CAREGIVER | C | 관계 종료·OWNER 거부 로컬 통합 | LOCAL PASS, DEV SKIP |
| 16 | PATCH | `/api/v1/babies/{babyId}/owner` | Bearer / OWNER | C | 역할 교환·동시성·rollback 로컬 통합 | LOCAL PASS, DEV SKIP |
| 17 | POST | `/api/v1/babies/{babyId}/invitations` | Bearer / OWNER | B | 임시 Baby 초대 생성·취소, URL 설정 필요 | LOCAL PASS, DEV SKIP |
| 18 | GET | `/api/v1/babies/{babyId}/invitations` | Bearer / OWNER | A | JWT OWNER 목록 | LOCAL PASS, DEV SKIP |
| 19 | DELETE | `/api/v1/babies/{babyId}/invitations/{invitationId}` | Bearer / OWNER | B | 임시 Baby PENDING 초대 취소 | LOCAL PASS, DEV SKIP |
| 20 | GET | `/api/v1/invitations/{token}` | 공개 / token 소지 | A | 없는 token 404, fixture 미리보기 | 404 DEV PASS, 유효 token SKIP |
| 21 | POST | `/api/v1/invitations/{token}/accept` | Bearer / 활성 회원 | C | one-use·동시성·관계 복구 로컬 통합 | LOCAL PASS, DEV SKIP |
| 22 | GET | `/api/v1/babies/{babyId}/records` | Bearer / ACTIVE 보호자 | A | JWT `date` 필수, 선택 `recordType`; 최근 10일 | LOCAL PASS, DEV SKIP |
| 23 | POST | `/api/v1/babies/{babyId}/records` | Bearer / ACTIVE 보호자 | B | 임시 Baby 완료 기록 생성 | LOCAL PASS, DEV SKIP |
| 24 | POST | `/api/v1/babies/{babyId}/records/start` | Bearer / ACTIVE 보호자 | B | 임시 Baby SLEEP/DIRECT_BREASTFEEDING 시작 | LOCAL PASS, DEV SKIP |
| 25 | GET | `/api/v1/babies/{babyId}/records/calendar` | Bearer / ACTIVE 보호자 | A | JWT `year`, `month` 필수 | LOCAL PASS, DEV SKIP |
| 26 | GET | `/api/v1/babies/{babyId}/records/{recordId}` | Bearer / ACTIVE 보호자 | A | JWT 완료 기록 상세 | LOCAL PASS, DEV SKIP |
| 27 | PATCH | `/api/v1/babies/{babyId}/records/{recordId}` | Bearer / ACTIVE 보호자 | B | 임시 기록 version 기반 수정 | LOCAL PASS, DEV SKIP |
| 28 | DELETE | `/api/v1/babies/{babyId}/records/{recordId}` | Bearer / ACTIVE 보호자 | B | 임시 기록 version 기반 soft delete | LOCAL PASS, DEV SKIP |
| 29 | GET | `/api/v1/babies/{babyId}/records/active` | Bearer / ACTIVE 보호자 | A | JWT 진행 기록 조회 | LOCAL PASS, DEV SKIP |
| 30 | PATCH | `/api/v1/babies/{babyId}/records/{recordId}/complete` | Bearer / ACTIVE 보호자 | B | 임시 진행 기록 완료 | LOCAL PASS, DEV SKIP |
| 31 | PATCH | `/api/v1/babies/{babyId}/records/{recordId}/cancel` | Bearer / ACTIVE 보호자 | B | 임시 진행 기록 취소 | LOCAL PASS, DEV SKIP |
| 32 | GET | `/api/v1/babies/{babyId}/home` | Bearer / ACTIVE 보호자 | A | JWT Home 조회 | LOCAL PASS, DEV SKIP |
| 33 | GET | `/api/v1/babies/{babyId}/stats/daily` | Bearer / ACTIVE 보호자 | A | JWT `date` 필수 | LOCAL PASS, DEV SKIP |
| 34 | GET | `/api/v1/babies/{babyId}/stats/routine` | Bearer / ACTIVE 보호자 | A | JWT `endDate` 필수 | LOCAL PASS, DEV SKIP |
| 35 | GET | `/api/v1/babies/{babyId}/stats/growth` | Bearer / ACTIVE 보호자 | A | JWT 성장 통계 | LOCAL PASS, DEV SKIP |
| 36 | GET | `/api/v1/babies/{babyId}/growth-records` | Bearer / ACTIVE 보호자 | A | JWT 성장 목록 | LOCAL PASS, DEV SKIP |
| 37 | POST | `/api/v1/babies/{babyId}/growth-records` | Bearer / ACTIVE 보호자 | B | 임시 Baby 성장 기록 생성 | LOCAL PASS, DEV SKIP |
| 38 | GET | `/api/v1/babies/{babyId}/growth-records/{growthRecordId}` | Bearer / ACTIVE 보호자 | A | JWT 성장 상세 | LOCAL PASS, DEV SKIP |
| 39 | PATCH | `/api/v1/babies/{babyId}/growth-records/{growthRecordId}` | Bearer / ACTIVE 보호자 | B | 임시 기록 version 기반 수정 | LOCAL PASS, DEV SKIP |
| 40 | DELETE | `/api/v1/babies/{babyId}/growth-records/{growthRecordId}` | Bearer / ACTIVE 보호자 | B | 임시 기록 version 기반 soft delete | LOCAL PASS, DEV SKIP |
| 41 | GET | `/api/v1/babies/{babyId}/events` | Bearer / ACTIVE 보호자 | A | JWT SSE 200 + `text/event-stream` handshake, 무인증 401 | LOCAL PASS, DEV SKIP |

분류 합계: A 17, B 14, C 9, D 1. `B`는 **안전하게 분리할 수 있는 후보**일 뿐 이 점검에서 DEV 실행했다는 뜻이 아니다. 스크립트는 의도적으로 읽기 전용이며 기존 seed 리소스를 절대 수정하지 않는다.

### Local regression coverage ledger

| 요청된 범위 | 실행된 근거 |
|---|---|
| Unit / domain | `CareRecordTest`, `GrowthRecordTest`, `BabyInvitationTest`, `BabyMemberOwnershipTest` 등 |
| Application | `SocialLoginServiceTest`, `UpdateMyProfileServiceTest`, `GetBabyMembersServiceTest`, `CareStatisticsCalculatorTest` 등 |
| Controller / contract | `AuthControllerTest`, `ReissueTokenControllerTest`, `LogoutControllerTest`, `GlobalExceptionHandlerTest`, `OpenApiContractTest` |
| MariaDB Testcontainers | `IdentityMariaDbIntegrationTest`, `BabyMariaDbIntegrationTest`, `CareRecordMariaDbIntegrationTest`, `GrowthRecordMariaDbIntegrationTest`, `HomeMariaDbIntegrationTest`, `StatisticsMariaDbIntegrationTest`, `RecordEventMariaDbIntegrationTest` |
| Architecture | `ArchitectureTest` |
| Concurrency | Care start/complete/cancel/edit/delete, 초대 수락, 보호자 종료·OWNER 이전 등 MariaDB 동시성 테스트 |
| Authentication / authorization / validation | Identity·Baby·Care·Growth 통합 테스트의 401/403/404/400 계약 및 rollback 검사 |
| Stats / growth / SSE | `HomeMariaDbIntegrationTest`, `StatisticsMariaDbIntegrationTest`, `GrowthRecordMariaDbIntegrationTest`, `RecordEventMariaDbIntegrationTest` |

이 표는 해당 테스트 **파일들이 이번 전체 테스트에 포함되어 통과했다**는 뜻이지, 각 DEV API의 실배포 성공을 뜻하지 않는다.

## Authentication

- DEV에서 `GET /api/v1/members/me` 무인증은 401. 응답은 공통 실패 봉투 3필드이며 외부 `errorCode` 없음.
- 유효 DearUs JWT의 DEV 요청은 이번 점검에서 미실행. 사용자 배경에 있는 Google 실로그인 성공은 이번 점검에서 재현·독립 확인하지 않았다.
- Auth 로컬 테스트에는 로그인, provider client, refresh rotation, logout, 인증 거부가 포함된다. 재발급은 실사용 Refresh Token을 회전시키므로 DEV에서는 호출하지 않았다.

## Authorization

- Spring Security는 health/Swagger/OpenAPI, 초대 미리보기 GET, Auth 3개 POST만 공개하고 나머지 endpoint에 Bearer를 요구한다.
- OWNER only: 아기 수정·삭제, 초대 생성·목록·취소, caregiver 제거, OWNER 이전. CAREGIVER only: 본인의 관계 나가기. 일반 ACTIVE 보호자는 Baby/Care/Growth/Home/Stats/Event를 조회·기록할 수 있다.
- 다른 사용자의 Baby/Record 접근과 비활성 보호자 거부는 Baby/Care/Growth MariaDB fixture에서 검증된다. 이번 DEV에는 두 번째 회원 JWT가 없어서 실호출하지 않았다.

## Care invariants

- `CareRecordMariaDbIntegrationTest`가 SLEEP 및 DIRECT_BREASTFEEDING의 IN_PROGRESS, Baby당 단일 active record, 동시 시작 단일 승자, complete/cancel 경쟁, 종료 후 재시작을 검증한다. V6의 unique active constraint도 MariaDB에서 확인한다.
- 완료 기록 6종의 typed detail, OTHER title, MEDICINE/숫자/시간 validation, 수정·삭제 version 충돌, soft delete, timezone 기준 목록·캘린더를 검증한다.
- 부족한 부분: 이 점검에서 DEV의 동시성 부하나 장기 SSE replay는 수행하지 않았다. SSE는 best-effort이고 replay가 없는 설계다.

## Family / Invitation stability

- Baby MariaDB integration이 OWNER/CAREGIVER 권한, 제거/나가기, OWNER 이전의 단일 트랜잭션·동시성, 초대 단일 사용·만료·수락 rollback을 검증한다.
- 초대 유효기간은 코드상 72시간이다. V4 `baby_invitation.token_hash`는 CHAR(64) UNIQUE이며 토큰 원문 대신 SHA-256 hash를 저장한다. 생성 응답의 invitation URL에는 원문 토큰이 들어간다. 토큰 또는 URL을 로그·보고서에 기록하지 않는다.
- 로컬 `DEARUS_INVITATION_BASE_URL`이 비어 있다. 생성 시 `InvitationUrlAdapter`가 `IllegalStateException`을 던지므로, **배포에서도 비어 있다면 초대 생성이 500**이 된다. ECS 실주입값은 미확인이다. 목록·미리보기·수락의 기존 토큰 처리와는 별개의 설정이다.
- DEV에서 기존 초대/관계 파괴 테스트는 하지 않았다.

## DB / Flyway

- 소스 migration은 V1~V7이고 `spring.flyway.enabled=true`, `spring.jpa.hibernate.ddl-auto=validate`다. 이번 로컬 MariaDB Testcontainers 회귀 테스트는 성공했다.
- 사용자 제공 배경에는 DEV RDS V1~V7 적용과 seed 데이터 생성이 명시되어 있다. 그러나 이번 점검에서 RDS의 `flyway_schema_history` 또는 row count를 직접 조회하지 않았고, 인증된 DEV seed 조회도 수행하지 못했다. 따라서 DEV DB 검증 결과로 다시 표시하지 않는다.
- DEV의 존재하지 않는 초대 토큰 404는 배포 초대 조회 경로가 응답했다는 증거지만, 전체 schema와 seed 데이터 존재의 증명은 아니다.

## External dependencies

- Google 실로그인 성공은 사용자 제공 배경. 이번 점검에는 provider token이 없어 재현하지 않았다. Kakao/Naver 실토큰 검증도 미실행.
- 프로필/아기 이미지 저장은 `*_ENABLED=false`일 때 별도 S3 값 없이 일반 API가 동작할 수 있지만 업로드 기능은 비활성화된다. DEV의 활성화·Bucket·IAM 상태는 미확인.
- Voice/STT/LLM adapter 및 VOICE-01~04 endpoint는 없다. 41개 분모에 포함하지 않는다.

## Environment contract

| 그룹 | 변수 | 실제 코드 기준 의미 |
|---|---|---|
| REQUIRED_BOOT | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MariaDB datasource 및 Flyway/JPA 검증에 필요 |
| REQUIRED_BOOT | `DEARUS_JWT_SECRET_BASE64` | Access JWT 서명/검증에 필요 |
| REQUIRED_BOOT | `KAKAO_APP_ID` | 기본값 없는 property; 실제 Kakao 검증에도 필요 |
| REQUIRED_FEATURE | `GOOGLE_CLIENT_ID` | Google ID Token audience 검증. 미설정이면 Google login 불가 |
| REQUIRED_FEATURE | `DEARUS_INVITATION_BASE_URL` | 초대 링크 생성. 미설정이면 해당 요청 실패 |
| REQUIRED_FEATURE | `PROFILE_IMAGE_STORAGE_BUCKET`, `PROFILE_IMAGE_STORAGE_PUBLIC_BASE_URL`, `PROFILE_IMAGE_STORAGE_REGION` | `PROFILE_IMAGE_STORAGE_ENABLED=true`이고 이미지 업로드를 쓸 때 필요; S3 IAM 권한도 필요 |
| REQUIRED_FEATURE | `BABY_IMAGE_STORAGE_BUCKET`, `BABY_IMAGE_STORAGE_PUBLIC_BASE_URL`, `BABY_IMAGE_STORAGE_REGION` | `BABY_IMAGE_STORAGE_ENABLED=true`이고 이미지 업로드를 쓸 때 필요; S3 IAM 권한도 필요 |
| OPTIONAL | `SWAGGER_ENABLED` | 기본 `true`; DEV Swagger는 이번 점검에서 200 |
| OPTIONAL | `DEARUS_OPENAPI_SERVER_URL` | 빈 값 가능; OpenAPI server URL 표시용 |
| OPTIONAL | `PROFILE_IMAGE_STORAGE_ENABLED`, `BABY_IMAGE_STORAGE_ENABLED` | 기본 `false` |
| OPTIONAL | `PROFILE_IMAGE_STORAGE_ENDPOINT`, `BABY_IMAGE_STORAGE_ENDPOINT` | 표준 AWS S3 사용 시 빈 값 가능; 호환 스토리지용 |

로컬 `.env`의 값과 ECS task/Secrets Manager 실주입값은 동일하다고 단정할 수 없다. 이번 작업은 AWS 설정을 변경하지 않았다. 이 보고서에는 실제 secret·JWT·DB 비밀번호가 없다.

## Known risks

1. **P0 — HTTPS 미확인:** 현재 DEV ALB 443 접속이 타임아웃이며 HTTP는 응답한다. HTTP에서 Bearer JWT를 전송하는 앱 연동은 권장할 수 없다. HTTPS listener/인증서/도메인과 모바일 네트워크 정책 확인이 필요하다.
2. **P0 — 인증 DEV smoke 증거 없음:** `DEARUS_ACCESS_TOKEN` 미제공. 41개 API 노출·로컬 통합 테스트만으로 실제 배포 보호 API를 모두 PASS라고 판정할 수 없다.
3. **P1 — 초대 base URL 실주입 미확인:** 배포가 빈 값이면 링크 생성이 실패한다. 설정을 확인하고 임시 Baby에서 안전하게 검증해야 한다.
4. **P1 — 배포 아티팩트와 로컬 작업 트리의 일치 미확인:** `dev`에 기존 미커밋 변경이 있다. DEV ECS image digest/task revision과 체크아웃 commit 대응을 확인해야 한다.
5. **P2 — 이미지 업로드 외부 의존성 미검증:** S3 설정/권한 및 실제 업로드는 로컬 adapter 테스트 외 DEV 실검증이 없다.

## Voice excluded

`voice`는 패키지 표시 외 구현이 없고 OpenAPI에도 Voice operation이 없다. 예정된 VOICE-01~04는 이번 41개 점검 대상에서 제외했다.

## Recommended next actions

1. DEV HTTPS endpoint를 확정하고 443 연결, TLS, 모바일 인증서 신뢰를 확인한다.
2. 실제 회원의 **DearUs Access JWT를 셸 환경변수로만** 제공해 `scripts/dev-api-smoke.sh`를 실행한다. 토큰·secret을 문서/로그/명령 이력에 남기지 않는다. 선택적으로 `DEV_SMOKE_BABY_ID`로 본인 Baby를 지정한다.
3. 결과의 인증 조회 17개를 확인하고, 부족한 fixture는 **새 임시 Baby**만 사용해 별도 검증한다. 기존 seed Baby/회원/초대는 건드리지 않는다. cleanup 실패 시 새 리소스 ID를 보고한다.
4. ECS의 `DEARUS_INVITATION_BASE_URL`과 이미지 저장 flag/S3/IAM을 읽기 전용으로 확인한다. RDS V1~V7과 seed row count도 권한이 있을 때 조회한다.
5. DEV task의 image digest/commit을 기록해 로컬 통과 테스트가 실제 배포 이미지와 연결되는지 확인한다.

재현 명령(값은 환경변수로만 전달):

```bash
bash -n scripts/dev-api-smoke.sh
JAVA_HOME="$(/usr/libexec/java_home -v 26)" DOCKER_HOST='unix:///Users/soeun/.docker/run/docker.sock' ./gradlew clean test
DEV_API_BASE_URL='https://<DEV_HOST>/api/v1' DEARUS_ACCESS_TOKEN='<JWT>' ./scripts/dev-api-smoke.sh
```

현재 확인된 DEV URL은 HTTP뿐이다. 위 `https` 예시는 TLS가 준비된 후 사용한다. 실토큰을 명령에 직접 적으면 shell history에 남을 수 있으므로 실제 실행에서는 비공개 환경변수 주입을 사용한다.
