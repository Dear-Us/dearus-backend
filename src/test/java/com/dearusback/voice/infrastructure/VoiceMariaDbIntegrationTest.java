package com.dearusback.voice.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.DearUsBackApplication;
import com.dearusback.care.domain.exception.CareException;
import com.dearusback.voice.application.port.out.VoiceCommandParsingPort;
import com.dearusback.voice.application.port.out.VoiceRequestRepository;
import com.dearusback.voice.application.port.out.VoiceTranscriptionPort;
import com.dearusback.voice.application.service.VoiceWorkflowService;
import com.dearusback.voice.application.service.VoiceRequestTransactions;
import com.dearusback.voice.domain.exception.VoiceException;
import com.dearusback.voice.domain.model.ParsedVoiceCommand;
import com.dearusback.voice.domain.model.VoiceAction;
import com.dearusback.voice.domain.model.VoiceClientStatus;
import com.dearusback.voice.domain.model.VoiceProcessingStatus;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(classes = DearUsBackApplication.class)
@AutoConfigureMockMvc
@Import(VoiceMariaDbIntegrationTest.FakeConfig.class)
class VoiceMariaDbIntegrationTest {
    @Container static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.4")
            .withDatabaseName("dearus_voice_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MARIADB::getJdbcUrl);
        properties.add("spring.datasource.username", MARIADB::getUsername);
        properties.add("spring.datasource.password", MARIADB::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        properties.add("dearus.voice.enabled", () -> "true");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired FakeProviders fake;
    @Autowired VoiceWorkflowService workflow;
    @Autowired VoiceRequestTransactions voiceTransactions;
    @MockitoSpyBean VoiceRequestRepository requests;
    @Autowired TransactionTemplate transactions;

    @Test
    void flywayV8AndAutoSaveAndIdempotentUploadWorkOnMariaDb() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '8' "
                + "AND success = 1", Integer.class)).isEqualTo(1);
        long member = member(); long baby = baby(member); membership(baby, member);
        fake.command = completed("OTHER", "{\"title\":\"목욕\"}");
        String key = UUID.randomUUID().toString();
        JsonNode accepted = upload(baby, member, key, audio(), "audio/wav", 202);
        long id = accepted.path("data").path("voiceRequestId").asLong();
        JsonNode done = await(baby, member, id, "AUTO_SAVED");
        long record = done.path("data").path("recordId").asLong();
        assertThat(record).isPositive();
        assertThat(jdbc.queryForMap("SELECT input_type, record_type FROM care_record WHERE record_id = ?", record))
                .containsEntry("input_type", "VOICE").containsEntry("record_type", "OTHER");
        assertThat(jdbc.queryForMap("SELECT processing_status, result_status, care_record_id FROM voice_request "
                + "WHERE voice_request_id = ?", id))
                .containsEntry("processing_status", "COMPLETED").containsEntry("result_status", "AUTO_SAVED")
                .containsEntry("care_record_id", record);
        assertThat(upload(baby, member, key, audio(), "audio/wav", 202)
                .path("data").path("voiceRequestId").asLong()).isEqualTo(id);
        assertThat(count(baby)).isEqualTo(1);
        voiceTransactions.finish(id, fake.command);
        voiceTransactions.finish(id, fake.command);
        assertThat(count(baby)).isEqualTo(1);
        assertThat(fake.transcribeInsideTransaction).isFalse();
        assertThat(fake.parseInsideTransaction).isFalse();
        assertThat(upload(baby, member, key, wav("other"), "audio/wav", 409).path("success").asBoolean())
                .isFalse();
        assertThat(upload(baby, member, UUID.randomUUID().toString(), audio(), "audio/mp4", 400)
                .path("success").asBoolean()).isFalse();
        assertThat(upload(baby, member, UUID.randomUUID().toString(), audio(), "text/plain", 400)
                .path("success").asBoolean()).isFalse();
        assertThat(upload(baby, member, UUID.randomUUID().toString(), new byte[0], "audio/wav", 400)
                .path("success").asBoolean()).isFalse();
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(audio(), 0, oversized, 0, audio().length);
        assertThatThrownBy(() -> workflow.upload(member, baby, UUID.randomUUID().toString(), oversized, "audio/wav"))
                .isInstanceOf(VoiceException.class)
                .extracting("errorCode.code").isEqualTo("AUDIO_TOO_LARGE");
    }

    @Test
    void formulaVoiceAutoSaveIsVisibleThroughCareDetail() throws Exception {
        long member = member(); long baby = baby(member); membership(baby, member);
        fake.transcript = "아기에게 분유 120ml 먹였어";
        fake.command = completed("FEEDING", "{\"feedingType\":\"FORMULA\",\"amountMl\":120}");
        long id = requestId(upload(baby, member, UUID.randomUUID().toString(), m4a(), "audio/x-m4a", 202));
        JsonNode done = await(baby, member, id, "AUTO_SAVED");
        assertThat(done.path("data").path("transcript").asText()).isEqualTo(fake.transcript);
        long recordId = done.path("data").path("recordId").asLong();
        JsonNode care = mapper.readTree(mvc.perform(get("/api/v1/babies/{babyId}/records/{recordId}", baby, recordId)
                .header("Authorization", "Bearer " + token(member)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(care.path("data").path("recordType").asText()).isEqualTo("FEEDING");
        assertThat(care.path("data").path("inputType").asText()).isEqualTo("VOICE");
        assertThat(care.path("data").path("detail").path("feedingType").asText()).isEqualTo("FORMULA");
        assertThat(care.path("data").path("detail").path("amountMl").asInt()).isEqualTo(120);
        assertThat(count(baby)).isEqualTo(1);
    }

    @Test
    void medicineNeedsConfirmationAndConcurrentConfirmCreatesOneRecord() throws Exception {
        long member = member(); long baby = baby(member); membership(baby, member);
        fake.command = completed("MEDICINE", "{\"medicineName\":\"해열제\",\"doseValue\":2.5,\"doseUnit\":\"ML\"}");
        long id = requestId(upload(baby, member, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        assertThat(await(baby, member, id, "CONFIRMATION_REQUIRED").path("data").path("recordId").isNull())
                .isTrue();
        assertThat(count(baby)).isZero();
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch gate = new CountDownLatch(1);
            var first = pool.submit(() -> { gate.await(); return workflow.confirm(member, baby, id, null, null); });
            var second = pool.submit(() -> { gate.await(); return workflow.confirm(member, baby, id, null, null); });
            gate.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).recordId())
                    .isEqualTo(second.get(10, TimeUnit.SECONDS).recordId());
        }
        assertThat(count(baby)).isEqualTo(1);
        JsonNode confirmed = confirm(baby, member, id, "{}", 200);
        assertThat(confirmed.path("data").path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(count(baby)).isEqualTo(1);
        assertThat(confirm(baby, member, id, "{\"action\":\"CREATE_COMPLETED_RECORD\",\"draft\":{}}", 200)
                .path("data").path("recordId").asLong()).isEqualTo(confirmed.path("data").path("recordId").asLong());
        assertThat(cancel(baby, member, id, 409).path("success").asBoolean()).isFalse();
    }

    @Test
    void concurrentConfirmAndCancelHaveOneTerminalOutcome() throws Exception {
        long member = member(); long baby = baby(member); membership(baby, member);
        fake.command = completed("MEDICINE", "{\"medicineName\":\"해열제\",\"doseValue\":2.5,\"doseUnit\":\"ML\"}");
        long id = requestId(upload(baby, member, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        await(baby, member, id, "CONFIRMATION_REQUIRED");
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch gate = new CountDownLatch(1);
            var confirm = pool.submit(() -> {
                gate.await();
                try { return workflow.confirm(member, baby, id, null, null).status().name(); }
                catch (VoiceException exception) { return exception.errorCode().code(); }
            });
            var cancel = pool.submit(() -> {
                gate.await();
                try { return workflow.cancel(member, baby, id).status().name(); }
                catch (VoiceException exception) { return exception.errorCode().code(); }
            });
            gate.countDown();
            String confirmResult = confirm.get(10, TimeUnit.SECONDS);
            String cancelResult = cancel.get(10, TimeUnit.SECONDS);
            String finalStatus = getVoice(baby, member, id, 200).path("data").path("status").asText();
            if ("CONFIRMED".equals(finalStatus)) {
                assertThat(List.of(confirmResult, cancelResult))
                        .containsExactlyInAnyOrder("CONFIRMED", "VOICE_REQUEST_CONFLICT");
                assertThat(count(baby)).isEqualTo(1);
            } else {
                assertThat(finalStatus).isEqualTo("CANCELED");
                assertThat(List.of(confirmResult, cancelResult))
                        .containsExactlyInAnyOrder("CANCELED", "VOICE_REQUEST_CONFLICT");
                assertThat(count(baby)).isZero();
            }
            assertThat(jdbc.queryForMap("SELECT processing_status, result_status, care_record_id "
                    + "FROM voice_request WHERE voice_request_id = ?", id))
                    .containsEntry("result_status", finalStatus)
                    .containsEntry("care_record_id", "CONFIRMED".equals(finalStatus)
                            ? getVoice(baby, member, id, 200).path("data").path("recordId").asLong() : null);
        }
    }

    @Test
    void concurrentVoiceStartsByDifferentCaregiversPreserveSingleActiveRecord() throws Exception {
        long owner = member(); long caregiver = member(); long baby = baby(owner);
        membership(baby, owner); membership(baby, caregiver);
        fake.command = new ParsedVoiceCommand(VoiceAction.START_RECORD,
                "{\"recordType\":\"SLEEP\"}", true, false);
        long firstId = requestId(upload(baby, owner, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        await(baby, owner, firstId, "REVIEW_REQUIRED");
        fake.command = new ParsedVoiceCommand(VoiceAction.START_RECORD,
                "{\"recordType\":\"FEEDING\",\"detail\":{\"feedingType\":\"DIRECT_BREASTFEEDING\","
                        + "\"breastSide\":\"LEFT\"}}", true, false);
        long secondId = requestId(upload(baby, caregiver, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        await(baby, caregiver, secondId, "REVIEW_REQUIRED");
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch gate = new CountDownLatch(1);
            var first = pool.submit(() -> {
                gate.await();
                try { return workflow.confirm(owner, baby, firstId, null, null).status().name(); }
                catch (CareException exception) { return exception.errorCode().code(); }
            });
            var second = pool.submit(() -> {
                gate.await();
                try { return workflow.confirm(caregiver, baby, secondId, null, null).status().name(); }
                catch (CareException exception) { return exception.errorCode().code(); }
            });
            gate.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CONFIRMED", "ACTIVE_RECORD_EXISTS");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM care_record WHERE baby_id = ? "
                + "AND status = 'IN_PROGRESS' AND is_deleted = FALSE", Integer.class, baby)).isEqualTo(1);
        assertThat(count(baby)).isEqualTo(1);
    }

    @Test
    void careInsertRollsBackWhenFinalVoiceStateWriteFails() {
        long member = member(); long baby = baby(member); membership(baby, member);
        ParsedVoiceCommand command = completed("OTHER", "{\"title\":\"목욕\"}");
        long id = voiceTransactions.register(member, baby, UUID.randomUUID().toString(), "a".repeat(64))
                .request().id();
        assertThat(voiceTransactions.transcribed(id, "목욕했어")).isTrue();
        assertThat(voiceTransactions.parsed(id, command)).isTrue();
        doThrow(new IllegalStateException("simulated final state write failure"))
                .when(requests).save(argThat(request -> request != null
                        && request.status() == VoiceClientStatus.AUTO_SAVED));
        try {
            assertThatThrownBy(() -> voiceTransactions.finish(id, command))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("simulated final state write failure");
        } finally {
            reset(requests);
        }
        assertThat(count(baby)).isZero();
        assertThat(jdbc.queryForMap("SELECT processing_status, result_status, care_record_id "
                + "FROM voice_request WHERE voice_request_id = ?", id))
                .containsEntry("processing_status", VoiceProcessingStatus.PARSED.name())
                .containsEntry("result_status", null).containsEntry("care_record_id", null);
    }

    @Test
    void concurrentUploadWithSameKeyCreatesOneRequestAndOneRecord() throws Exception {
        long member = member(); long baby = baby(member); membership(baby, member);
        fake.command = completed("OTHER", "{\"title\":\"터미타임\"}");
        String key = UUID.randomUUID().toString();
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch gate = new CountDownLatch(1);
            var first = pool.submit(() -> { gate.await(); return workflow.upload(member, baby, key, audio(), "audio/wav"); });
            var second = pool.submit(() -> { gate.await(); return workflow.upload(member, baby, key, audio(), "audio/wav"); });
            gate.countDown();
            long firstId = first.get(10, TimeUnit.SECONDS).id();
            assertThat(second.get(10, TimeUnit.SECONDS).id()).isEqualTo(firstId);
            await(baby, member, firstId, "AUTO_SAVED");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM voice_request "
                + "WHERE requester_member_id = ? AND idempotency_key = ?", Integer.class, member, key))
                .isEqualTo(1);
        assertThat(count(baby)).isEqualTo(1);
    }

    @Test
    void staleInFlightRequestBecomesFailedWithoutReprocessingAudio() throws Exception {
        long member = member(); long baby = baby(member); membership(baby, member);
        String key = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO voice_request (baby_id, requester_member_id, idempotency_key, payload_hash, "
                        + "processing_status, created_at, updated_at) VALUES (?, ?, ?, ?, 'RECEIVED', ?, ?)",
                baby, member, key, "a".repeat(64),
                Timestamp.valueOf(LocalDateTime.ofInstant(Instant.now().minusSeconds(1200), ZoneOffset.UTC)),
                Timestamp.valueOf(LocalDateTime.ofInstant(Instant.now().minusSeconds(1200), ZoneOffset.UTC)));
        Integer recovered = transactions.execute(status -> requests.failStaleProcessing(
                Instant.now().minusSeconds(600), Instant.now()));
        assertThat(recovered).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT processing_status, result_status, failure_reason "
                + "FROM voice_request WHERE requester_member_id = ? AND idempotency_key = ?", member, key))
                .containsEntry("processing_status", "FAILED").containsEntry("result_status", "FAILED")
                .containsEntry("failure_reason", "PROCESSING_INTERRUPTED");
    }

    @Test
    void reviewMultipleAndCorrectedDraftDoNotCreateMoreThanOneCareRecord() throws Exception {
        long member = member(); long baby = baby(member); membership(baby, member);
        fake.command = new ParsedVoiceCommand(VoiceAction.CREATE_COMPLETED_RECORD, "{}", true, true);
        long id = requestId(upload(baby, member, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        await(baby, member, id, "REVIEW_REQUIRED");
        assertThat(count(baby)).isZero();
        String corrected = "{\"action\":\"CREATE_COMPLETED_RECORD\",\"draft\":"
                + completed("OTHER", "{\"title\":\"산책\"}").draftJson() + "}";
        assertThat(confirm(baby, member, id, corrected, 200).path("data").path("status").asText())
                .isEqualTo("CONFIRMED");
        assertThat(count(baby)).isEqualTo(1);
        fake.command = new ParsedVoiceCommand(VoiceAction.CREATE_COMPLETED_RECORD,
                "{\"recordType\":\"FEEDING\",\"recordedAt\":\""
                        + Instant.now().minusSeconds(300) + "\",\"detail\":{\"feedingType\":\"FORMULA\"}}",
                false, false);
        long missing = requestId(upload(baby, member, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        await(baby, member, missing, "REVIEW_REQUIRED");
        assertThat(count(baby)).isEqualTo(1);
    }

    @Test
    void startEndAndActiveInvariantUseCareApplicationRules() throws Exception {
        long member = member(); long baby = baby(member); membership(baby, member);
        fake.command = new ParsedVoiceCommand(VoiceAction.START_RECORD, "{\"recordType\":\"SLEEP\"}", false, false);
        long start = requestId(upload(baby, member, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        long record = await(baby, member, start, "AUTO_SAVED").path("data").path("recordId").asLong();
        assertThat(jdbc.queryForObject("SELECT status FROM care_record WHERE record_id = ?", String.class, record))
                .isEqualTo("IN_PROGRESS");
        long duplicate = requestId(upload(baby, member, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        await(baby, member, duplicate, "FAILED");
        assertThat(count(baby)).isEqualTo(1);
        fake.command = new ParsedVoiceCommand(VoiceAction.END_RECORD, "{\"recordId\":" + record + "}", false, false);
        long end = requestId(upload(baby, member, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        await(baby, member, end, "AUTO_SAVED");
        assertThat(jdbc.queryForObject("SELECT status FROM care_record WHERE record_id = ?", String.class, record))
                .isEqualTo("COMPLETED");
        fake.command = new ParsedVoiceCommand(VoiceAction.START_RECORD,
                "{\"recordType\":\"FEEDING\",\"detail\":{\"feedingType\":\"DIRECT_BREASTFEEDING\",\"breastSide\":\"LEFT\"}}",
                false, false);
        long feedStart = requestId(upload(baby, member, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        long feedRecord = await(baby, member, feedStart, "AUTO_SAVED").path("data").path("recordId").asLong();
        assertThat(jdbc.queryForMap("SELECT record_type, input_type, status FROM care_record WHERE record_id = ?", feedRecord))
                .containsEntry("record_type", "FEEDING").containsEntry("input_type", "VOICE")
                .containsEntry("status", "IN_PROGRESS");
    }

    @Test
    void authorizationCancelAndProviderFailuresAreSafe() throws Exception {
        long owner = member(); long other = member(); long outsider = member();
        long baby = baby(owner); membership(baby, owner); membership(baby, other);
        fake.command = completed("OTHER", "{\"title\":\"목욕\"}");
        mvc.perform(multipart("/api/v1/babies/{babyId}/voice-commands", baby)
                .file(new MockMultipartFile("audio", "a.wav", "audio/wav", audio()))
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
        assertThat(upload(baby, outsider, UUID.randomUUID().toString(), audio(), "audio/wav", 404)
                .path("success").asBoolean()).isFalse();
        assertThat(upload(baby, outsider, UUID.randomUUID().toString(), new byte[0], "audio/wav", 404)
                .path("success").asBoolean()).isFalse();
        fake.block = new CountDownLatch(1);
        long canceled = requestId(upload(baby, owner, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        assertThat(cancel(baby, owner, canceled, 200).path("data").path("status").asText()).isEqualTo("CANCELED");
        assertThat(cancel(baby, owner, canceled, 200).path("data").path("status").asText()).isEqualTo("CANCELED");
        fake.block.countDown(); fake.block = null;
        assertThat(getVoice(baby, owner, canceled, 200).path("data").path("status").asText())
                .isEqualTo("CANCELED");
        assertThat(getVoice(baby, other, canceled, 404).path("success").asBoolean()).isFalse();
        assertThat(getVoice(baby, outsider, canceled, 404).path("success").asBoolean()).isFalse();
        assertThat(confirm(baby, owner, canceled, "{}", 409).path("success").asBoolean()).isFalse();
        fake.transcript = "";
        long empty = requestId(upload(baby, owner, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        await(baby, owner, empty, "FAILED");
        fake.transcript = "recorded";
        fake.parseFailure = true;
        long failed = requestId(upload(baby, owner, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        assertThat(await(baby, owner, failed, "FAILED").path("data").path("failureReason").asText())
                .isEqualTo("PARSING_FAILED");
        assertThat(jdbc.queryForMap("SELECT processing_status, result_status, care_record_id FROM voice_request "
                + "WHERE voice_request_id = ?", failed))
                .containsEntry("processing_status", "FAILED").containsEntry("result_status", "FAILED")
                .containsEntry("care_record_id", null);
        assertThat(count(baby)).isZero();
        fake.parseFailure = false;
        fake.transcribeFailure = true;
        long failedStt = requestId(upload(baby, owner, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        assertThat(await(baby, owner, failedStt, "FAILED").path("data").path("failureReason").asText())
                .isEqualTo("TRANSCRIPTION_FAILED");
        fake.transcribeFailure = false;
        assertThat(count(baby)).isZero();
    }

    @Test
    void ownerCaregiverAndWrongBabyRequestBoundariesAreEnforced() throws Exception {
        long owner = member(); long caregiver = member(); long unrelated = member();
        long baby = baby(owner); long otherBaby = baby(owner);
        membership(baby, owner); membership(baby, caregiver); membership(otherBaby, owner);
        fake.command = new ParsedVoiceCommand(VoiceAction.CREATE_COMPLETED_RECORD,
                completed("OTHER", "{\"title\":\"산책\"}").draftJson(), true, false);
        long ownerRequest = requestId(upload(baby, owner, UUID.randomUUID().toString(), audio(), "audio/wav", 202));
        await(baby, owner, ownerRequest, "REVIEW_REQUIRED");
        long caregiverRequest = requestId(upload(baby, caregiver, UUID.randomUUID().toString(),
                audio(), "audio/wav", 202));
        await(baby, caregiver, caregiverRequest, "REVIEW_REQUIRED");
        assertThat(getVoice(baby, owner, ownerRequest, 200).path("success").asBoolean()).isTrue();
        assertThat(getVoice(baby, caregiver, caregiverRequest, 200).path("success").asBoolean()).isTrue();
        assertThat(getVoice(baby, caregiver, ownerRequest, 404).path("success").asBoolean()).isFalse();
        assertThat(confirm(baby, caregiver, ownerRequest, "{}", 404).path("success").asBoolean()).isFalse();
        assertThat(cancel(baby, caregiver, ownerRequest, 404).path("success").asBoolean()).isFalse();
        assertThat(getVoice(baby, unrelated, ownerRequest, 404).path("success").asBoolean()).isFalse();
        assertThat(getVoice(otherBaby, owner, ownerRequest, 404).path("success").asBoolean()).isFalse();
        assertThat(confirm(otherBaby, owner, ownerRequest, "{}", 404).path("success").asBoolean()).isFalse();
        assertThat(cancel(otherBaby, owner, ownerRequest, 404).path("success").asBoolean()).isFalse();
        assertThat(getVoice(baby, owner, ownerRequest, 200).path("data").path("status").asText())
                .isEqualTo("REVIEW_REQUIRED");
        assertThat(count(baby)).isZero();
    }

    private ParsedVoiceCommand completed(String type, String detail) {
        return new ParsedVoiceCommand(VoiceAction.CREATE_COMPLETED_RECORD,
                "{\"recordType\":\"" + type + "\",\"recordedAt\":\""
                        + Instant.now().minusSeconds(300) + "\",\"detail\":" + detail + "}", false, false);
    }

    private JsonNode upload(long baby, long member, String key, byte[] bytes, String type, int expected)
            throws Exception {
        String json = mvc.perform(multipart("/api/v1/babies/{babyId}/voice-commands", baby)
                .file(new MockMultipartFile("audio", "ignored.filename", type, bytes))
                .header("Authorization", "Bearer " + token(member)).header("Idempotency-Key", key))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private JsonNode getVoice(long baby, long member, long id, int expected) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/babies/{babyId}/voice-commands/{id}", baby, id)
                .header("Authorization", "Bearer " + token(member)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode confirm(long baby, long member, long id, String body, int expected) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/babies/{babyId}/voice-commands/{id}/confirm", baby, id)
                .header("Authorization", "Bearer " + token(member))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode cancel(long baby, long member, long id, int expected) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/babies/{babyId}/voice-commands/{id}/cancel", baby, id)
                .header("Authorization", "Bearer " + token(member)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode await(long baby, long member, long id, String status) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            JsonNode value = getVoice(baby, member, id, 200);
            if (status.equals(value.path("data").path("status").asText())) return value;
            Thread.sleep(30);
        }
        throw new AssertionError("Voice request did not reach " + status);
    }

    private long requestId(JsonNode response) { return response.path("data").path("voiceRequestId").asLong(); }
    private int count(long baby) {
        return jdbc.queryForObject("SELECT count(*) FROM care_record WHERE baby_id = ?", Integer.class, baby);
    }
    private byte[] audio() { return wav("payload"); }
    private byte[] m4a() {
        return new byte[]{0, 0, 0, 16, 'f', 't', 'y', 'p', 'M', '4', 'A', ' ', 0, 0, 0, 0};
    }
    private byte[] wav(String text) {
        byte[] suffix = text.getBytes(StandardCharsets.US_ASCII);
        byte[] bytes = new byte[12 + suffix.length];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 4);
        System.arraycopy(suffix, 0, bytes, 12, suffix.length);
        return bytes;
    }

    private long member() {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO member "
                    + "(member_name, timezone, is_deleted, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    new String[]{"member_id"});
            statement.setString(1, "보호자"); statement.setString(2, "Asia/Seoul"); statement.setBoolean(3, false);
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.setTimestamp(5, Timestamp.from(Instant.now()));
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
    private long baby(long member) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO baby "
                    + "(created_by_member_id, baby_name, birth_date, gender, is_deleted, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)", new String[]{"baby_id"});
            statement.setLong(1, member); statement.setString(2, "테스트");
            statement.setDate(3, Date.valueOf("2026-01-01")); statement.setString(4, "FEMALE");
            statement.setBoolean(5, false); statement.setTimestamp(6, Timestamp.from(Instant.now()));
            statement.setTimestamp(7, Timestamp.from(Instant.now()));
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
    private void membership(long baby, long member) {
        jdbc.update("INSERT INTO baby_member (baby_id, member_id, role, status, joined_at) "
                + "VALUES (?, ?, ?, ?, ?)", baby, member, "CAREGIVER", "ACTIVE", Timestamp.from(Instant.now()));
    }
    private String token(long member) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("dearus").subject(Long.toString(member))
                .issuedAt(now.minusSeconds(1)).expiresAt(now.plusSeconds(300)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims)).getTokenValue();
    }

    @TestConfiguration
    static class FakeConfig {
        @Bean FakeProviders fakeProviders() { return new FakeProviders(); }
    }

    static class FakeProviders implements VoiceTranscriptionPort, VoiceCommandParsingPort {
        volatile String transcript = "recorded";
        volatile ParsedVoiceCommand command;
        volatile boolean transcribeFailure;
        volatile boolean parseFailure;
        volatile CountDownLatch block;
        volatile Boolean transcribeInsideTransaction;
        volatile Boolean parseInsideTransaction;

        @Override public String transcribe(byte[] bytes, String type) {
            transcribeInsideTransaction = TransactionSynchronizationManager.isActualTransactionActive();
            CountDownLatch wait = block;
            if (wait != null) {
                try { wait.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(); }
            }
            if (transcribeFailure) throw new IllegalStateException("fake STT failure");
            return transcript;
        }
        @Override public ParsedVoiceCommand parse(String text, String timezone, Instant requestedAt,
                                                   Long babyId, Long memberId) {
            parseInsideTransaction = TransactionSynchronizationManager.isActualTransactionActive();
            if (parseFailure) throw new IllegalStateException("fake parser failure");
            return command;
        }
    }
}
