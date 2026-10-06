package com.dearusback.support.infrastructure.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.tags.Tag;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class OpenApiContractCustomizer {

    private static final Set<String> PUBLIC = Set.of("login", "reissueToken", "logout", "getInvitation");
    private static final Set<String> CREATED = Set.of("createBaby", "createInvitation", "createCareRecord",
            "startCareRecord", "createGrowthRecord");
    private static final Set<String> CONFLICT = Set.of("login", "withdrawMember",
            "removeBabyCaregiver", "leaveBaby", "transferBabyOwnership",
            "cancelInvitation", "acceptInvitation", "startCareRecord", "completeCareRecord", "cancelCareRecord",
            "updateCareRecord", "deleteCareRecord", "updateGrowthRecord", "deleteGrowthRecord");
    private static final Set<String> NO_BAD_REQUEST = Set.of("listBabies", "getMyProfile", "withdrawMember",
            "getInvitation");

    @Bean
    OpenApiCustomizer actualContractCustomizer(ObjectMapper mapper) {
        return api -> {
            schemas(api.getComponents().getSchemas());
            Map<String, Tag> tags = new LinkedHashMap<>();
            api.getTags().forEach(tag -> tags.putIfAbsent(tag.getName(), tag));
            api.setTags(List.copyOf(tags.values()));
            api.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                String id = operation.getOperationId();
                parameters(operation);
                multipart(operation);
                request(operation, mapper);
                success(operation);
                if ("subscribeRecordEvents".equals(id)) {
                    sse(operation);
                }
                errors(path, operation, id);
            }));
        };
    }

    private void parameters(Operation operation) {
        if (operation.getParameters() == null) return;
        for (Parameter parameter : operation.getParameters()) {
            String name = parameter.getName();
            switch (name) {
                case "babyId" -> id(parameter, "아기 ID, 양의 정수", 1);
                case "memberId" -> id(parameter, "보호자 회원 ID, 양의 정수", 2);
                case "recordId" -> id(parameter, "육아 기록 ID, 양의 정수", 101);
                case "growthRecordId" -> id(parameter, "성장 기록 ID, 양의 정수", 101);
                case "invitationId" -> id(parameter, "초대 ID, 양의 정수", 10);
                case "token" -> parameter(parameter, "초대 URL에 포함된 일회성 원문 토큰", "invitation-token",
                        new StringSchema().example("invitation-token"));
                case "version" -> parameter(parameter, "현재 상세 응답의 낙관적 version, 0 이상", 0,
                        new IntegerSchema().format("int64").minimum(BigDecimal.ZERO).example(0));
                case "date" -> parameter(parameter, "조회 날짜 YYYY-MM-DD, 필수", "2026-10-05",
                        new StringSchema().pattern("[0-9]{4}-[0-9]{2}-[0-9]{2}").example("2026-10-05"));
                case "endDate" -> parameter(parameter, "7일 집계 마지막 날짜 YYYY-MM-DD, 필수", "2026-10-05",
                        new StringSchema().pattern("[0-9]{4}-[0-9]{2}-[0-9]{2}").example("2026-10-05"));
                case "year" -> parameter(parameter, "조회 연도, 4자리", "2026",
                        new StringSchema().pattern("[0-9]{4}").example("2026"));
                case "month" -> parameter(parameter, "조회 월, 1~12 (01~09도 허용)", "10",
                        new StringSchema().pattern("(?:0?[1-9]|1[0-2])").example("10"));
                case "recordType" -> parameter(parameter,
                        "선택 유형: FEEDING 수유 / SLEEP 수면 / DIAPER 기저귀 / TEMPERATURE 체온 / MEDICINE 투약 / OTHER 기타",
                        "FEEDING", choice("완료 기록 유형", "FEEDING", "SLEEP", "DIAPER", "TEMPERATURE", "MEDICINE", "OTHER"));
                default -> { }
            }
            if (Set.of("date", "endDate", "year", "month", "version").contains(name)) {
                parameter.setRequired(true);
            }
        }
    }

    private void id(Parameter parameter, String description, long example) {
        parameter(parameter, description, example,
                new IntegerSchema().format("int64").minimum(BigDecimal.ONE).example(example));
    }

    private void parameter(Parameter parameter, String description, Object example, Schema<?> schema) {
        parameter.setDescription(description);
        parameter.setExample(example);
        parameter.setSchema(schema);
    }

    private void multipart(Operation operation) {
        if (operation.getRequestBody() == null) return;
        MediaType multipart = operation.getRequestBody().getContent().get("multipart/form-data");
        if (multipart == null) return;
        multipart.addEncoding("request", new Encoding().contentType("application/json"));
        if (multipart.getSchema() != null && multipart.getSchema().getProperties() != null) {
            Schema<?> image = (Schema<?>) multipart.getSchema().getProperties().get("profileImage");
            if (image != null) image.setDescription("createBaby".equals(operation.getOperationId())
                    ? "선택 파일. JPEG/PNG/WebP, 최대 5 MiB."
                    : "선택 파일. JPEG/PNG/WebP, 최대 5 MiB. 제거 플래그와 동시 사용 불가.");
        }
    }

    private void request(Operation operation, ObjectMapper mapper) {
        if (operation.getRequestBody() == null) return;
        MediaType json = operation.getRequestBody().getContent().get("application/json");
        if (json == null) return;
        switch (operation.getOperationId()) {
            case "createCareRecord" -> {
                json.setSchema(ref("CareCreateRequest"));
                examples(json, mapper, Map.of(
                        "분유", "{\"recordType\":\"FEEDING\",\"recordedAt\":\"2026-10-05T18:30:00+09:00\",\"detail\":{\"feedingType\":\"FORMULA\",\"amountMl\":120}}",
                        "직접 수유", "{\"recordType\":\"FEEDING\",\"recordedAt\":\"2026-10-05T18:30:00+09:00\",\"detail\":{\"feedingType\":\"DIRECT_BREASTFEEDING\",\"breastSide\":\"LEFT\",\"durationMinutes\":15}}",
                        "수면", "{\"recordType\":\"SLEEP\",\"startAt\":\"2026-10-05T18:30:00+09:00\",\"endAt\":\"2026-10-05T19:30:00+09:00\"}",
                        "기저귀", "{\"recordType\":\"DIAPER\",\"recordedAt\":\"2026-10-05T18:30:00+09:00\",\"detail\":{\"diaperType\":\"URINE\"}}",
                        "체온", "{\"recordType\":\"TEMPERATURE\",\"recordedAt\":\"2026-10-05T18:30:00+09:00\",\"detail\":{\"temperatureCelsius\":36.5,\"measuredPart\":\"EAR\"}}",
                        "투약", "{\"recordType\":\"MEDICINE\",\"recordedAt\":\"2026-10-05T18:30:00+09:00\",\"detail\":{\"medicineName\":\"해열제\",\"doseValue\":2.5,\"doseUnit\":\"ML\"}}",
                        "기타", "{\"recordType\":\"OTHER\",\"recordedAt\":\"2026-10-05T18:30:00+09:00\",\"detail\":{\"title\":\"목욕\"}}"));
            }
            case "startCareRecord" -> {
                json.setSchema(ref("CareStartRequest"));
                examples(json, mapper, Map.of(
                        "수면 시작", "{\"recordType\":\"SLEEP\"}",
                        "직접 수유 시작", "{\"recordType\":\"FEEDING\",\"detail\":{\"feedingType\":\"DIRECT_BREASTFEEDING\",\"breastSide\":\"LEFT\"}}"));
            }
            case "updateCareRecord" -> {
                json.setSchema(ref("CareUpdateRequest"));
                examples(json, mapper, Map.of("메모 수정", "{\"version\":0,\"memo\":\"수정한 내용\"}"));
            }
            case "createGrowthRecord" -> {
                json.setSchema(ref("GrowthCreateRequest"));
                examples(json, mapper, Map.of("키와 몸무게", "{\"measuredDate\":\"2026-10-05\",\"heightCm\":62.5,\"weightKg\":6.8}"));
            }
            case "updateGrowthRecord" -> {
                json.setSchema(ref("GrowthUpdateRequest"));
                examples(json, mapper, Map.of("몸무게 수정", "{\"version\":0,\"weightKg\":6.9}"));
            }
            default -> { }
        }
    }

    private void examples(MediaType media, ObjectMapper mapper, Map<String, String> examples) {
        examples.forEach((name, value) -> media.addExamples(name,
                new Example().summary(name).value(mapper.readValue(value, Object.class))));
    }

    private void success(Operation operation) {
        if (!CREATED.contains(operation.getOperationId())) return;
        ApiResponse response = operation.getResponses().remove("200");
        if (response != null) operation.getResponses().addApiResponse("201", response.description("생성 완료"));
    }

    private void errors(String path, Operation operation, String id) {
        Map<Integer, String> statuses = new LinkedHashMap<>();
        if (!NO_BAD_REQUEST.contains(id)) statuses.put(400, "요청 형식·값이 올바르지 않음");
        if (!PUBLIC.contains(id)) {
            statuses.put(401, "Bearer JWT가 없거나 유효하지 않음");
            statuses.put(403, "탈퇴 회원 또는 해당 작업의 명시적 역할 권한 없음");
        }
        if ("login".equals(id)) statuses.put(401, "소셜 Provider 토큰이 유효하지 않음");
        if ("reissueToken".equals(id)) statuses.put(401, "Refresh Token이 만료·폐기·재사용되었거나 유효하지 않음");
        if (path.contains("{babyId}") || path.contains("{token}") || path.contains("{growthRecordId}")) {
            statuses.put(404, "현재 회원이 접근할 수 없거나 존재하지 않는 대상");
        }
        if ("changeLastSelectedBaby".equals(id)) statuses.put(404, "선택 가능한 Baby를 찾을 수 없음");
        if (CONFLICT.contains(id)) statuses.put(409, "현재 상태 또는 version과 요청이 충돌함");
        if (Set.of("updateMyProfile", "createBaby", "updateBaby").contains(id)) {
            statuses.put(413, "이미지 또는 multipart 요청 크기 제한 초과");
            statuses.put(503, "이미지 저장소를 사용할 수 없음");
        }
        if ("login".equals(id)) statuses.put(503, "소셜 제공자를 사용할 수 없음");
        statuses.forEach((code, description) -> {
            Map<String, Object> example = new LinkedHashMap<>();
            example.put("success", false);
            example.put("data", null);
            example.put("message", errorMessage(code, id));
            operation.getResponses().addApiResponse(code.toString(),
                    new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                            new MediaType().schema(ref("ApiFailure")).addExamples("실패", new Example().value(example)))));
        });
    }

    private String errorMessage(int code, String id) {
        return switch (code) {
            case 400 -> "잘못된 요청입니다.";
            case 401 -> "login".equals(id) ? "소셜 인증 정보가 유효하지 않습니다." : "인증 정보가 유효하지 않습니다.";
            case 403 -> "탈퇴한 회원입니다.";
            case 404 -> "getInvitation".equals(id) ? "초대 정보를 찾을 수 없습니다." : "아기 정보를 찾을 수 없습니다.";
            case 409 -> switch (id) {
                case "login" -> "소셜 계정 처리 중 충돌이 발생했습니다.";
                case "withdrawMember" -> "소유 중인 아기가 있어 탈퇴할 수 없습니다.";
                case "startCareRecord" -> "이미 진행 중인 기록이 있습니다.";
                default -> "요청이 다른 변경과 충돌했습니다. 최신 상태를 확인해 주세요.";
            };
            case 413 -> "요청 파일 크기가 허용 범위를 초과했습니다.";
            case 503 -> "login".equals(id) ? "소셜 로그인 제공자에 일시적으로 연결할 수 없습니다."
                    : "updateMyProfile".equals(id) ? "프로필 이미지를 처리할 수 없습니다."
                    : "아기 프로필 이미지를 처리할 수 없습니다.";
            default -> "서버 내부 오류가 발생했습니다.";
        };
    }

    private void sse(Operation operation) {
        operation.getResponses().clear();
        operation.getResponses().addApiResponse("200", new ApiResponse()
                .description("장기 SSE 스트림. heartbeat는 주석이며 business event가 아님. "
                        + "eventType은 RECORD_CREATED/RECORD_STARTED/RECORD_COMPLETED/"
                        + "RECORD_CANCELED/RECORD_UPDATED/RECORD_DELETED 중 하나")
                .content(new Content().addMediaType("text/event-stream", new MediaType()
                        .schema(new StringSchema().description("record-change 이벤트 스트림"))
                        .example("event: record-change\ndata: {\"eventType\":\"RECORD_COMPLETED\",\"babyId\":1,\"recordId\":101,\"recordType\":\"SLEEP\",\"occurredAt\":\"2026-10-05T09:30:00Z\"}\n\n"))));
    }

    private void schemas(Map<String, Schema> schemas) {
        schemas.put("ApiFailure", object("공통 실패 응답", Map.of(
                "success", new BooleanSchema().description("항상 false").example(false),
                "data", new Schema<>().types(Set.of("null")).description("항상 null"),
                "message", string("사용자에게 표시할 오류 메시지", "잘못된 요청입니다.")), "success", "data", "message"));

        schemas.put("CareFormulaDetail", object("분유 수유 상세", Map.of(
                "feedingType", choice("FORMULA 분유 수유", "FORMULA"), "amountMl", positive("분유량 mL", 120)), "feedingType", "amountMl"));
        schemas.put("CareDirectDetail", object("직접 수유 상세", Map.of(
                "feedingType", choice("DIRECT_BREASTFEEDING 직접 모유 수유", "DIRECT_BREASTFEEDING"),
                "breastSide", choice("LEFT 왼쪽 / RIGHT 오른쪽 / BOTH 양쪽", "LEFT", "RIGHT", "BOTH"),
                "durationMinutes", positive("수유 시간 분", 15)), "feedingType", "breastSide", "durationMinutes"));
        schemas.put("CareDiaperDetail", object("기저귀 상세", Map.of(
                "diaperType", choice("URINE 소변 / STOOL 대변 / BOTH 둘 다", "URINE", "STOOL", "BOTH"),
                "condition", nullableString("선택적 상태. 없으면 null", "보통"),
                "color", nullableString("선택적 색. 없으면 null", "노랑")), "diaperType"));
        schemas.put("CareTemperatureDetail", object("체온 상세", Map.of(
                "temperatureCelsius", number("섭씨 체온", 36.5),
                "measuredPart", choice("EAR 귀 / FOREHEAD 이마 / ARMPIT 겨드랑이 / OTHER 기타", "EAR", "FOREHEAD", "ARMPIT", "OTHER")), "temperatureCelsius", "measuredPart"));
        schemas.put("CareMedicineDetail", object("투약 상세", Map.of(
                "medicineName", string("약 이름", "해열제"), "doseValue", number("투여량", 2.5),
                "doseUnit", choice("ML 밀리리터 / MG 밀리그램 / DROP 방울 / TABLET 정 / OTHER 기타", "ML", "MG", "DROP", "TABLET", "OTHER")), "medicineName", "doseValue", "doseUnit"));
        schemas.put("CareOtherDetail", object("기타 상세", Map.of(
                "title", string("기록 제목", "목욕")), "title"));

        ComposedSchema detail = new ComposedSchema();
        detail.setDescription("recordType별 상세. FEEDING은 FORMULA/직접 수유 중 하나");
        for (String name : List.of("CareFormulaDetail", "CareDirectDetail", "CareDiaperDetail",
                "CareTemperatureDetail", "CareMedicineDetail", "CareOtherDetail")) {
            detail.addOneOfItem(ref(name));
        }
        Schema<?> response = schemas.get("CareRecordResponse");
        ComposedSchema responseDetail = new ComposedSchema();
        responseDetail.setDescription("recordType별 상세. SLEEP은 null이며 FEEDING은 FORMULA/직접 수유 중 하나");
        detail.getOneOf().forEach(responseDetail::addOneOfItem);
        responseDetail.addOneOfItem(new Schema<>().types(Set.of("null")));
        if (response != null) response.addProperty("detail", responseDetail);

        schemas.put("CareCreateRequest", object("완료 기록 생성. SLEEP은 startAt/endAt만, 다른 유형은 recordedAt/detail만 사용하며 여분 필드는 400", Map.of(
                "recordType", choice("기록 유형", "FEEDING", "SLEEP", "DIAPER", "TEMPERATURE", "MEDICINE", "OTHER"),
                "recordedAt", dateTime("SLEEP 외 유형에 필수"), "startAt", dateTime("SLEEP에 필수"),
                "endAt", dateTime("SLEEP에 필수"), "memo", nullableString("선택 메모. null은 없음", "메모"),
                "detail", detail), "recordType"));
        schemas.put("CareStartRequest", object("진행형 기록 시작. SLEEP 또는 직접 모유 수유만 가능", Map.of(
                "recordType", choice("SLEEP 또는 FEEDING", "SLEEP", "FEEDING"),
                "memo", nullableString("선택 메모. 없으면 null", "잠들었어요"),
                "detail", object("직접 수유에만 필요", Map.of(
                        "feedingType", choice("DIRECT_BREASTFEEDING만 가능", "DIRECT_BREASTFEEDING"),
                        "breastSide", choice("LEFT 또는 RIGHT", "LEFT", "RIGHT")), "feedingType", "breastSide")), "recordType"));
        schemas.put("CareUpdateRequest", object("완료 기록 PATCH. version 외 최소 한 필드 필요. 필드 미전달은 유지, memo:null은 삭제, detail 전달은 전체 교체", Map.of(
                "version", nonNegative("현재 version", 0), "recordedAt", dateTime("SLEEP 외 시간 변경"),
                "startAt", dateTime("SLEEP 시작 시간"), "endAt", dateTime("SLEEP 종료 시간"),
                "memo", nullableString("null이면 메모 제거", "수정한 메모"), "detail", detail), "version"));
        schemas.put("GrowthCreateRequest", object("키와 몸무게 중 최소 하나 필수. 같은 날짜 여러 기록 허용", Map.of(
                "measuredDate", date("측정 날짜"), "heightCm", nullableNumber("키 cm, null 가능", 62.5),
                "weightKg", nullableNumber("몸무게 kg, null 가능", 6.8),
                "memo", nullableString("선택 메모. 없으면 null", "측정")), "measuredDate"));
        schemas.put("GrowthUpdateRequest", object("version 외 최소 한 필드 필요. 생략은 유지, null은 해당 nullable 값 제거", Map.of(
                "version", nonNegative("현재 version", 0), "measuredDate", date("변경할 측정 날짜"),
                "heightCm", nullableNumber("키 cm, null이면 제거", 62.5),
                "weightKg", nullableNumber("몸무게 kg, null이면 제거", 6.8),
                "memo", nullableString("null이면 메모 제거", "수정")), "version"));

        describeResponseSchemas(schemas);
        schemas.forEach((name, schema) -> {
            if (name.startsWith("ApiResponse")) {
                Schema<?> success = (Schema<?>) schema.getProperties().get("success");
                if (success != null) success.setDescription("항상 true");
                Schema<?> message = (Schema<?>) schema.getProperties().get("message");
                if (message != null) nullable(message, "성공 응답에서는 항상 null");
                if (name.equals("ApiResponseVoid")) schema.addProperty("data", new Schema<>().types(Set.of("null"))
                        .description("응답 데이터 없음"));
            }
        });
    }

    private void describeResponseSchemas(Map<String, Schema> schemas) {
        field(schemas, "MyProfileResponse", "timezone", "회원 IANA timezone ID", "Asia/Seoul");
        field(schemas, "MyProfileResponse", "profileImageUrl", "사진이 없으면 null", null);
        field(schemas, "MyProfileResponse", "lastSelectedBabyId", "선택한 Baby가 없으면 null", null);
        field(schemas, "SocialAccountResponse", "provider", "GOOGLE/KAKAO/NAVER. 같은 이메일도 자동 병합하지 않음", "GOOGLE");
        field(schemas, "SocialLoginResponse", "accessToken", "Bearer 인증에 쓰는 단기 JWT. 로그·문서에 실제 값을 기록하지 말 것", null);
        field(schemas, "SocialLoginResponse", "refreshToken", "원문 불투명 토큰. 서버에는 해시만 저장되고 재발급 시 회전", null);
        field(schemas, "ReissueTokenResponse", "refreshToken", "이전 원문을 폐기하고 새로 발급한 불투명 토큰", null);
        field(schemas, "BabyResponse", "gender", "MALE 남아 / FEMALE 여아", "FEMALE");
        field(schemas, "BabyResponse", "myRole", "OWNER 소유자 / CAREGIVER 공동 보호자", "OWNER");
        field(schemas, "BabyResponse", "profileImageUrl", "이미지가 없으면 null", null);
        field(schemas, "BabyMemberResponse", "role", "OWNER 소유자 / CAREGIVER 공동 보호자", "CAREGIVER");
        for (String name : List.of("CreateBabyInvitationResponse", "InvitationSummaryResponse",
                "InvitationPreviewResponse", "AcceptedInvitationResponse")) {
            field(schemas, name, "status", "PENDING 대기 / ACCEPTED 수락 / EXPIRED 만료 / CANCELED 취소", null);
            field(schemas, name, "role", "초대/수락할 역할: CAREGIVER 공동 보호자", "CAREGIVER");
        }
        field(schemas, "InvitationSummaryResponse", "acceptedAt", "수락하지 않았으면 null", null);
        field(schemas, "InvitationSummaryResponse", "canceledAt", "취소하지 않았으면 null", null);
        field(schemas, "CareRecordResponse", "recordType", "FEEDING 수유 / SLEEP 수면 / DIAPER 기저귀 / TEMPERATURE 체온 / MEDICINE 투약 / OTHER 기타", "SLEEP");
        field(schemas, "CareRecordResponse", "inputType", "MANUAL 직접 입력 / VOICE 음성 입력(현재 Voice API 미구현)", "MANUAL");
        field(schemas, "CareRecordResponse", "status", "COMPLETED 완료 / IN_PROGRESS 진행 중 / CANCELED 취소", "COMPLETED");
        field(schemas, "CareRecordResponse", "recordedAt", "SLEEP 외 기록 시각. SLEEP에서는 null", null);
        field(schemas, "CareRecordResponse", "startAt", "SLEEP 시작. 다른 유형에서는 null", null);
        field(schemas, "CareRecordResponse", "endAt", "SLEEP 종료. 진행 중에는 null", null);
        field(schemas, "CareRecordResponse", "memo", "메모가 없으면 null", null);
        field(schemas, "CareRecordResponse", "version", "수정·삭제 낙관적 버전. 변경 시 증가", 0);
        field(schemas, "HomeResponse", "baby", "현재 Baby 정보. UI 문구는 앱에서 구성", null);
        field(schemas, "HomeResponse", "activeRecord", "현재 진행 중인 기록. 없으면 null", null);
        field(schemas, "HomeResponse", "todaySummary", "회원 timezone의 오늘 완료 기록 유형별 건수", null);
        field(schemas, "HomeResponse", "lastCompletedRecord", "최근 완료 기록. 없으면 null", null);
        field(schemas, "HomeResponse", "recentRecords", "최근 완료 기록 목록. 없으면 빈 배열", null);
        field(schemas, "Baby", "myRole", "OWNER 소유자 / CAREGIVER 공동 보호자", "OWNER");
        field(schemas, "Baby", "profileImageUrl", "이미지가 없으면 null", null);
        field(schemas, "TodaySummary", "date", "회원 timezone 기준 오늘 날짜", "2026-10-05");
        field(schemas, "TodaySummary", "totalRecordCount", "오늘 완료된 기록 총 건수. 없으면 0", 0);
        field(schemas, "GrowthRecordResponse", "heightCm", "키 cm. 입력하지 않았으면 null", 62.5);
        field(schemas, "GrowthRecordResponse", "weightKg", "몸무게 kg. 입력하지 않았으면 null", 6.8);
        field(schemas, "GrowthRecordResponse", "memo", "메모 없음은 null", null);
        field(schemas, "GrowthRecordResponse", "version", "PATCH/DELETE에 전달할 낙관적 버전", 0);
        field(schemas, "Feeding", "formulaTotalMl", "분유 기록이 없으면 0", 0);
        field(schemas, "Feeding", "totalCount", "수유 기록 건수. 없으면 0", 0);
        field(schemas, "Feeding", "formulaCount", "분유 기록 건수. 없으면 0", 0);
        field(schemas, "Feeding", "formulaAverageMl", "분유 기록이 없으면 null", null);
        field(schemas, "Feeding", "directBreastfeedingCount", "직접 수유 기록 건수. 없으면 0", 0);
        field(schemas, "Feeding", "directBreastfeedingTotalMinutes", "직접 수유가 없으면 0", 0);
        field(schemas, "Feeding", "directBreastfeedingAverageMinutes", "직접 수유가 없으면 null", null);
        field(schemas, "Sleep", "count", "기준 날짜에 속한 수면 기록 건수. 없으면 0", 0);
        field(schemas, "Sleep", "totalMinutes", "해당 날짜와 겹치는 수면 시간의 합. 없으면 0", 0);
        field(schemas, "Sleep", "averageMinutes", "겹치는 수면이 없으면 null", null);
        field(schemas, "Diaper", "totalCount", "기저귀 기록 총 건수. 없으면 0", 0);
        field(schemas, "Diaper", "urineCount", "소변 포함 기록 건수. BOTH도 포함", 0);
        field(schemas, "Diaper", "stoolCount", "대변 포함 기록 건수. BOTH도 포함", 0);
        field(schemas, "Temperature", "count", "체온 기록 건수. 없으면 0", 0);
        field(schemas, "Temperature", "latestTemperatureCelsius", "체온 기록이 없으면 null", null);
        field(schemas, "Temperature", "minTemperatureCelsius", "체온 기록이 없으면 null", null);
        field(schemas, "Temperature", "maxTemperatureCelsius", "체온 기록이 없으면 null", null);
        field(schemas, "Count", "count", "해당 유형 기록 건수. 없으면 0", 0);
        field(schemas, "RoutineStatistics", "startDate", "7일 집계 첫 날짜", "2026-09-29");
        field(schemas, "RoutineStatistics", "endDate", "7일 집계 마지막 날짜", "2026-10-05");
        field(schemas, "RoutineStatistics", "days", "날짜별 집계 7개. 기록이 없어도 0으로 채움", null);
        field(schemas, "RoutineStatistics", "averages", "7일 합계를 7로 나눈 일 평균", null);
        field(schemas, "Day", "sleepTotalMinutes", "해당 날짜와 겹치는 SLEEP 구간만 합산", 0);
        field(schemas, "Day", "formulaTotalMl", "분유 기록이 없으면 0", 0);
        field(schemas, "Averages", "sleepMinutesPerDay", "7일 전체를 분모로 한 하루 평균 수면 분", 0);
        field(schemas, "GrowthStatistics", "latest", "성장 기록이 없으면 null", null);
        field(schemas, "GrowthStatistics", "changeFromPrevious", "이전 동일 수치와 비교. 비교할 값이 없으면 해당 필드 null", null);
        field(schemas, "GrowthStatistics", "points", "삭제되지 않은 성장 측정 시계열. 없으면 빈 배열; 누락 수치 보간 없음", null);
        field(schemas, "Latest", "heightCm", "최신 키 기록이 없으면 null", null);
        field(schemas, "Latest", "weightKg", "최신 몸무게 기록이 없으면 null", null);
        field(schemas, "ChangeFromPrevious", "heightCm", "비교할 이전 키가 없으면 null", null);
        field(schemas, "ChangeFromPrevious", "weightKg", "비교할 이전 몸무게가 없으면 null", null);
        field(schemas, "GrowthMeasurement", "heightCm", "해당 측정 기록에 키가 없으면 null", null);
        field(schemas, "GrowthMeasurement", "weightKg", "해당 측정 기록에 몸무게가 없으면 null", null);
    }

    private void field(Map<String, Schema> schemas, String owner, String name, String description, Object example) {
        Schema<?> container = schemas.get(owner);
        if (container == null || container.getProperties() == null) return;
        Schema<?> property = (Schema<?>) container.getProperties().get(name);
        if (property == null) return;
        property.setDescription(description);
        if (example != null) property.setExample(example);
        if (description.contains("null")) nullable(property, description);
    }

    private void nullable(Schema<?> property, String description) {
        property.setDescription(description);
        if (property.get$ref() != null) {
            String reference = property.get$ref();
            property.set$ref(null);
            property.addOneOfItem(new Schema<>().$ref(reference));
            property.addOneOfItem(new Schema<>().types(Set.of("null")));
        } else if (property.getType() != null) {
            property.setTypes(Set.of(property.getType(), "null"));
        } else if (property.getTypes() != null && !property.getTypes().isEmpty()) {
            var types = new java.util.HashSet<>(property.getTypes());
            types.add("null");
            property.setTypes(types);
        }
    }

    private Schema<?> ref(String name) {
        return new Schema<>().$ref("#/components/schemas/" + name);
    }

    private Schema<?> object(String description, Map<String, Schema<?>> properties, String... required) {
        Schema<?> schema = new ObjectSchema().description(description);
        properties.forEach(schema::addProperty);
        schema.setRequired(List.of(required));
        return schema;
    }

    private Schema<?> string(String description, String example) {
        return new StringSchema().description(description).example(example);
    }

    private Schema<?> nullableString(String description, String example) {
        return new StringSchema().types(Set.of("string", "null")).description(description).example(example);
    }

    private Schema<?> choice(String description, String... values) {
        StringSchema schema = new StringSchema();
        schema.setDescription(description);
        schema.setEnum(List.of(values));
        schema.setExample(values[0]);
        return schema;
    }

    private Schema<?> date(String description) {
        return new StringSchema().format("date").description(description).example("2026-10-05");
    }

    private Schema<?> dateTime(String description) {
        return new StringSchema().format("date-time").description(description).example("2026-10-05T18:30:00+09:00");
    }

    private Schema<?> number(String description, double example) {
        return new NumberSchema().description(description).example(example);
    }

    private Schema<?> nullableNumber(String description, double example) {
        return new NumberSchema().types(Set.of("number", "null")).description(description).example(example);
    }

    private Schema<?> positive(String description, int example) {
        return new IntegerSchema().description(description).minimum(BigDecimal.ONE).example(example);
    }

    private Schema<?> nonNegative(String description, int example) {
        return new IntegerSchema().format("int64").description(description).minimum(BigDecimal.ZERO).example(example);
    }
}
