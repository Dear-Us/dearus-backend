package com.dearusback.support.presentation;

import com.dearusback.care.presentation.dto.CreateCareRecordRequest;
import com.dearusback.care.presentation.dto.EditCareRecordRequest;
import com.dearusback.care.presentation.dto.StartCareRecordRequest;
import com.dearusback.growth.presentation.dto.GrowthRecordRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired RequestMappingHandlerMapping mappings;

    @Test
    void generatedOpenApiDocumentsEveryImplementedOperationAndBearerSecurity() throws Exception {
        JsonNode api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(api.path("info").path("title").asText()).isEqualTo("DearUs API");
        assertThat(api.path("info").path("version").asText()).isEqualTo("v1");
        assertThat(api.path("components").path("securitySchemes").path("bearerAuth")
                .path("scheme").asText()).isEqualTo("bearer");
        assertThat(api.path("paths").has("/api/v1/auth/login")).isTrue();
        assertThat(api.path("paths").has("/api/v1/babies/{babyId}/records")).isTrue();
        assertThat(api.path("paths").has("/api/v1/babies/{babyId}/events")).isTrue();
        assertThat(api.path("paths").has("/api/v1/babies/{babyId}/growth-records")).isTrue();
        assertThat(api.path("paths").has("/api/v1/babies/{babyId}/voice-commands")).isFalse();

        Set<String> operationIds = new HashSet<>();
        int operationCount = 0;
        for (var path : api.path("paths").properties()) {
            for (var method : path.getValue().properties()) {
                JsonNode operation = method.getValue();
                String id = operation.path("operationId").asText();
                assertThat(id).isNotBlank();
                assertThat(operation.path("summary").asText()).isNotBlank();
                assertThat(operation.path("description").asText()).isNotBlank();
                assertThat(operation.path("tags").isEmpty()).isFalse();
                assertThat(operationIds.add(id)).as(id).isTrue();
                operationCount++;
            }
        }
        assertThat(operationCount).isEqualTo(41);
        Set<String> tags = new HashSet<>();
        api.path("tags").forEach(tag -> assertThat(tags.add(tag.path("name").asText())).isTrue());
        assertThat(tags).containsExactlyInAnyOrder("Auth", "Member", "Baby", "Baby Member", "Invitation",
                "Care Record", "Home", "Event", "Statistics", "Growth");
        assertThat(api.path("paths").path("/api/v1/auth/login").path("post")
                .path("security").isEmpty()).isTrue();
        assertThat(api.path("paths").path("/api/v1/members/me").path("get")
                .path("security").isEmpty()).isFalse();
        assertThat(api.path("paths").path("/api/v1/invitations/{token}").path("get")
                .path("security").isEmpty()).isTrue();
        assertThat(api.path("paths").path("/api/v1/invitations/{token}/accept").path("post")
                .path("security").isEmpty()).isFalse();
        assertThat(api.path("paths").path("/api/v1/auth/reissue").path("post")
                .path("responses").has("401")).isTrue();
        assertThat(api.path("paths").path("/api/v1/auth/reissue").path("post")
                .path("responses").has("409")).isFalse();

        int mapped = 0;
        for (var mapping : mappings.getHandlerMethods().keySet()) {
            for (String path : mapping.getPatternValues()) {
                if (!path.startsWith("/api/v1/")) continue;
                for (var method : mapping.getMethodsCondition().getMethods()) {
                    assertThat(api.path("paths").path(path).has(method.name().toLowerCase(Locale.ROOT)))
                            .as(method + " " + path).isTrue();
                    mapped++;
                }
            }
        }
        assertThat(mapped).isEqualTo(41);
    }

    @Test
    void generatedContractsDescribeMultipartCareDetailsSseAndAcceptedExamples() throws Exception {
        JsonNode api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode paths = api.path("paths");
        JsonNode babyCreate = paths.path("/api/v1/babies").path("post");
        JsonNode multipart = babyCreate.path("requestBody").path("content").path("multipart/form-data");
        assertThat(multipart.path("encoding").path("request").path("contentType").asText())
                .isEqualTo("application/json");
        assertThat(multipart.path("schema").path("required").toString()).contains("request");
        assertThat(multipart.path("schema").path("properties").path("profileImage")
                .path("format").asText()).isEqualTo("binary");
        assertThat(babyCreate.path("responses").has("201")).isTrue();
        JsonNode createBabyRequest = api.path("components").path("schemas").path("CreateBabyRequest");
        assertThat(createBabyRequest.path("required").toString())
                .contains("babyName", "birthDate", "gender");
        assertThat(createBabyRequest.path("properties").path("gender").path("enum").toString())
                .contains("MALE", "FEMALE");

        JsonNode careCreate = paths.path("/api/v1/babies/{babyId}/records").path("post");
        JsonNode listParameters = paths.path("/api/v1/babies/{babyId}/records").path("get").path("parameters");
        for (JsonNode parameter : listParameters) {
            if ("date".equals(parameter.path("name").asText())) {
                assertThat(parameter.path("required").asBoolean()).isTrue();
                assertThat(parameter.path("schema").path("example").asText()).isEqualTo("2026-10-05");
                assertThat(parameter.path("schema").has("minimum")).isFalse();
            }
            if ("recordType".equals(parameter.path("name").asText())) {
                assertThat(parameter.path("required").asBoolean()).isFalse();
                assertThat(parameter.path("schema").path("enum").size()).isEqualTo(6);
            }
        }
        JsonNode deleteParameters = paths.path("/api/v1/babies/{babyId}/records/{recordId}")
                .path("delete").path("parameters");
        assertThat(deleteParameters.toString()).contains("version");
        for (JsonNode parameter : deleteParameters) {
            if ("version".equals(parameter.path("name").asText())) {
                assertThat(parameter.path("required").asBoolean()).isTrue();
                assertThat(parameter.path("schema").path("minimum").asInt()).isZero();
            }
        }
        JsonNode careExamples = careCreate.path("requestBody").path("content").path("application/json")
                .path("examples");
        assertThat(careExamples.size()).isEqualTo(7);
        for (var example : careExamples.properties()) {
            JsonNode body = example.getValue().path("value");
            assertThat(body.isObject()).as(example.getKey()).isTrue();
            assertThat(CreateCareRecordRequest.toCommand(1L, 1L, body)).isNotNull();
        }
        assertThat(careCreate.path("responses").has("201")).isTrue();
        JsonNode detail = api.path("components").path("schemas").path("CareRecordResponse")
                .path("properties").path("detail").path("oneOf");
        assertThat(detail.size()).isEqualTo(7);
        assertThat(detail.get(6).path("type").asText()).isEqualTo("null");
        assertThat(api.path("components").path("schemas").path("ApiFailure")
                .path("properties").has("errorCode")).isFalse();
        assertThat(api.path("components").path("schemas").path("ApiResponseVoid")
                .path("properties").path("data").path("type").asText()).isEqualTo("null");
        assertThat(api.path("components").path("schemas").path("HomeResponse")
                .path("properties").path("activeRecord").path("oneOf").size()).isEqualTo(2);
        assertThat(api.path("components").path("schemas").path("Feeding")
                .path("properties").path("formulaAverageMl").path("type").toString())
                .contains("null");

        JsonNode careStart = paths.path("/api/v1/babies/{babyId}/records/start").path("post")
                .path("requestBody").path("content").path("application/json").path("examples");
        assertThat(careStart.size()).isEqualTo(2);
        for (var example : careStart.properties()) {
            assertThat(StartCareRecordRequest.toCommand(1L, 1L, example.getValue().path("value"))).isNotNull();
        }
        JsonNode careUpdate = paths.path("/api/v1/babies/{babyId}/records/{recordId}").path("patch")
                .path("requestBody").path("content").path("application/json").path("examples");
        for (var example : careUpdate.properties()) {
            assertThat(EditCareRecordRequest.toCommand(1L, 1L, 1L, example.getValue().path("value"))).isNotNull();
        }
        JsonNode growthCreate = paths.path("/api/v1/babies/{babyId}/growth-records").path("post")
                .path("requestBody").path("content").path("application/json").path("examples");
        for (var example : growthCreate.properties()) {
            assertThat(GrowthRecordRequest.create(1L, 1L, example.getValue().path("value"))).isNotNull();
        }
        JsonNode growthUpdate = paths.path("/api/v1/babies/{babyId}/growth-records/{growthRecordId}")
                .path("patch").path("requestBody").path("content").path("application/json").path("examples");
        for (var example : growthUpdate.properties()) {
            assertThat(GrowthRecordRequest.update(1L, 1L, 1L, example.getValue().path("value"))).isNotNull();
        }
        JsonNode sse = paths.path("/api/v1/babies/{babyId}/events").path("get").path("responses");
        assertThat(sse.path("200").path("content").has("text/event-stream")).isTrue();
        assertThat(sse.path("200").path("content").has("application/json")).isFalse();
        assertThat(sse.path("401").path("content").has("application/json")).isTrue();
        assertThat(sse.path("200").path("content").path("text/event-stream").path("example").asText())
                .contains("event: record-change", "RECORD_COMPLETED");
    }

    @Test
    void uiIsPublicWhileBusinessEndpointRemainsProtected() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/members/me")).andExpect(status().isUnauthorized());
    }
}
