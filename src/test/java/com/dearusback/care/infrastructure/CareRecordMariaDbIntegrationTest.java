package com.dearusback.care.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.DearUsBackApplication;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(classes = DearUsBackApplication.class)
class CareRecordMariaDbIntegrationTest {

    @Container
    static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.4")
            .withDatabaseName("dearus_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MARIADB::getJdbcUrl);
        properties.add("spring.datasource.username", MARIADB::getUsername);
        properties.add("spring.datasource.password", MARIADB::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        properties.add("dearus.invitation.base-url", () -> "https://app.example.com/invite");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean NamedParameterJdbcTemplate named;

    @Test
    void activeSleepFlowAndUniqueConstraintWorkOnMariaDb() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history "
                + "WHERE version = '6' AND success = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.statistics "
                + "WHERE table_schema = database() AND table_name = 'care_record' "
                + "AND index_name = 'uk_care_record_active_baby'", Integer.class)).isPositive();
        long owner = insertMember(false);
        long caregiver = insertMember(false);
        long baby = insertBaby(owner, false);
        long otherBaby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        insertMembership(otherBaby, owner, "OWNER", "ACTIVE");
        assertSuccess(active(baby, token(owner), 200));
        assertThat(active(baby, token(owner), 200).path("data").isNull()).isTrue();

        JsonNode started = start(baby, token(caregiver), "{\"recordType\":\"SLEEP\",\"memo\":null}", 201);
        assertSuccess(started);
        JsonNode data = started.path("data");
        long id = data.path("recordId").asLong();
        assertThat(data.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(data.path("inputType").asText()).isEqualTo("MANUAL");
        assertThat(data.path("recordedAt").asText()).isEqualTo(data.path("startAt").asText());
        assertThat(data.path("endAt").isNull()).isTrue();
        assertThat(data.path("detail").isNull()).isTrue();
        assertThat(active(baby, token(owner), 200).path("data").path("recordId").asLong()).isEqualTo(id);
        assertThat(active(otherBaby, token(owner), 200).path("data").isNull()).isTrue();
        assertFailure(start(baby, token(owner), "{\"recordType\":\"SLEEP\"}", 409));
        assertFailure(start(baby, token(owner), direct("LEFT"), 409));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO care_record "
                + "(baby_id, record_type, input_type, status, recorded_at, start_at, memo, "
                + "created_by_member_id, last_updated_by_member_id, version, is_deleted, created_at, updated_at) "
                + "SELECT baby_id, record_type, input_type, status, recorded_at, start_at, memo, "
                + "created_by_member_id, last_updated_by_member_id, version, is_deleted, created_at, updated_at "
                + "FROM care_record WHERE record_id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);

        JsonNode completed = transition(baby, id, token(owner), "complete", 200);
        assertSuccess(completed);
        assertThat(completed.path("data").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("data").path("startAt").asText()).isEqualTo(data.path("startAt").asText());
        assertThat(completed.path("data").path("recordedAt").asText()).isEqualTo(data.path("recordedAt").asText());
        assertThat(completed.path("data").path("endAt").isNull()).isFalse();
        assertThat(completed.path("data").path("version").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT created_by_member_id, completed_by_member_id, "
                + "last_updated_by_member_id FROM care_record WHERE record_id = ?", id))
                .containsEntry("created_by_member_id", caregiver)
                .containsEntry("completed_by_member_id", owner)
                .containsEntry("last_updated_by_member_id", owner);
        assertThat(active(baby, token(owner), 200).path("data").isNull()).isTrue();
        String date = OffsetDateTime.parse(data.path("startAt").asText()).toLocalDate().toString();
        assertThat(list(baby, token(owner), date, null, 200).path("data").toString()).contains("\"recordId\":" + id);
        assertFailure(transition(baby, id, token(owner), "complete", 409));
        assertFailure(transition(baby, id, token(owner), "cancel", 409));
        assertThat(start(baby, token(owner), direct("RIGHT"), 201).path("data").path("recordId").asLong())
                .isPositive();
    }

    @Test
    void activeBreastfeedingCancelAndCompletionPreserveDetailsAndAudit() throws Exception {
        long owner = insertMember(false);
        long caregiver = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        JsonNode left = start(baby, token(owner), direct("LEFT"), 201).path("data");
        long leftId = left.path("recordId").asLong();
        assertThat(left.path("detail").path("durationMinutes").isNull()).isTrue();
        assertThat(active(baby, token(caregiver), 200).path("data").path("detail")
                .path("breastSide").asText()).isEqualTo("LEFT");
        assertThat(jdbc.queryForMap("SELECT amount_ml, duration_minutes FROM feeding_detail WHERE record_id = ?", leftId))
                .containsEntry("amount_ml", null).containsEntry("duration_minutes", null);
        assertFailure(start(baby, token(owner), direct("RIGHT"), 409));
        assertFailure(start(baby, token(owner), "{\"recordType\":\"SLEEP\"}", 409));

        assertSuccess(transition(baby, leftId, token(caregiver), "cancel", 200));
        assertThat(jdbc.queryForMap("SELECT status, end_at, completed_by_member_id, last_updated_by_member_id "
                + "FROM care_record WHERE record_id = ?", leftId))
                .containsEntry("status", "CANCELED").containsEntry("end_at", null)
                .containsEntry("completed_by_member_id", null).containsEntry("last_updated_by_member_id", caregiver);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feeding_detail WHERE record_id = ?",
                Integer.class, leftId)).isEqualTo(1);
        assertThat(active(baby, token(owner), 200).path("data").isNull()).isTrue();
        String date = OffsetDateTime.parse(left.path("startAt").asText()).toLocalDate().toString();
        assertThat(list(baby, token(owner), date, null, 200).path("data").toString())
                .doesNotContain("\"recordId\":" + leftId);
        assertFailure(transition(baby, leftId, token(owner), "complete", 409));
        assertFailure(transition(baby, leftId, token(owner), "cancel", 409));

        JsonNode right = start(baby, token(caregiver), direct("RIGHT"), 201).path("data");
        long rightId = right.path("recordId").asLong();
        jdbc.update("UPDATE care_record SET start_at = DATE_SUB(start_at, INTERVAL 61 SECOND) WHERE record_id = ?",
                rightId);
        JsonNode completed = transition(baby, rightId, token(owner), "complete", 200).path("data");
        assertThat(completed.path("detail").path("breastSide").asText()).isEqualTo("RIGHT");
        assertThat(completed.path("detail").path("durationMinutes").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT duration_minutes FROM feeding_detail WHERE record_id = ?",
                Integer.class, rightId)).isEqualTo(completed.path("detail").path("durationMinutes").asInt());
        assertThat(jdbc.queryForMap("SELECT created_by_member_id, completed_by_member_id "
                + "FROM care_record WHERE record_id = ?", rightId))
                .containsEntry("created_by_member_id", caregiver).containsEntry("completed_by_member_id", owner);
    }

    @Test
    void longRunningActiveRecordIsNotAutomaticallyClosed() throws Exception {
        long owner = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        long id = start(baby, token(owner), "{\"recordType\":\"SLEEP\"}", 201)
                .path("data").path("recordId").asLong();
        jdbc.update("UPDATE care_record SET recorded_at = DATE_SUB(recorded_at, INTERVAL 15 HOUR), "
                + "start_at = DATE_SUB(start_at, INTERVAL 15 HOUR) WHERE record_id = ?", id);
        JsonNode active = active(baby, token(owner), 200).path("data");
        assertThat(active.path("recordId").asLong()).isEqualTo(id);
        assertThat(active.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(active.path("endAt").isNull()).isTrue();
    }

    @Test
    void activeRequestsRejectInvalidInputAndUnauthorizedAccess() throws Exception {
        long owner = insertMember(false);
        long outsider = insertMember(false);
        long left = insertMember(false);
        long removed = insertMember(false);
        long baby = insertBaby(owner, false);
        long otherBaby = insertBaby(owner, false);
        long deleted = insertBaby(owner, true);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(otherBaby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, left, "CAREGIVER", "LEFT");
        insertMembership(baby, removed, "CAREGIVER", "REMOVED");
        insertMembership(deleted, owner, "OWNER", "ACTIVE");
        for (String body : List.of("{\"recordType\":\"FEEDING\",\"detail\":{\"feedingType\":\"FORMULA\",\"breastSide\":\"LEFT\"}}",
                direct("BOTH"), "{\"recordType\":\"DIAPER\"}",
                "{\"recordType\":\"SLEEP\",\"startAt\":\"2020-01-01T00:00:00Z\"}",
                "{\"recordType\":\"SLEEP\",\"detail\":null}",
                "{\"recordType\":\"FEEDING\",\"detail\":{\"feedingType\":\"DIRECT_BREASTFEEDING\"}}")) {
            assertFailure(start(baby, token(owner), body, 400));
        }
        JsonNode started = start(baby, token(owner), "{\"recordType\":\"SLEEP\"}", 201).path("data");
        long id = started.path("recordId").asLong();
        for (long member : List.of(outsider, left, removed)) {
            assertFailure(active(baby, token(member), 404));
            assertFailure(start(baby, token(member), direct("LEFT"), 404));
            assertFailure(transition(baby, id, token(member), "complete", 404));
            assertFailure(transition(baby, id, token(member), "cancel", 404));
        }
        assertFailure(active(deleted, token(owner), 404));
        assertFailure(active(Long.MAX_VALUE, token(owner), 404));
        assertFailure(transition(baby, Long.MAX_VALUE, token(owner), "complete", 404));
        assertFailure(transition(baby, Long.MAX_VALUE, token(owner), "cancel", 404));
        assertFailure(transition(otherBaby, id, token(owner), "complete", 404));
        assertFailure(transition(otherBaby, id, token(owner), "cancel", 404));
        assertFailure(active(0, token(owner), 400));
        assertFailure(start(0, token(owner), "{\"recordType\":\"SLEEP\"}", 400));
        assertFailure(transition(baby, 0, token(owner), "complete", 400));
        assertFailure(transition(baby, 0, token(owner), "cancel", 400));
        assertFailure(transition(baby, id, null, "complete", 401));
        assertFailure(active(baby, null, 401));
        assertFailure(start(baby, null, direct("LEFT"), 401));
        assertFailure(active(baby, token(Long.MAX_VALUE), 401));
        String withdrawn = token(insertMember(true));
        assertFailure(active(baby, withdrawn, 403));
        assertFailure(start(baby, withdrawn, "{\"recordType\":\"SLEEP\"}", 403));
        assertFailure(transition(baby, id, withdrawn, "complete", 403));
        assertFailure(transition(baby, id, withdrawn, "cancel", 403));
    }

    @Test
    void concurrentStartsAndCompleteCancelHaveExactlyOneWinner() throws Exception {
        long owner = insertMember(false);
        long caregiver = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        List<Integer> starts = concurrently(
                () -> startAnyStatus(baby, token(owner), "{\"recordType\":\"SLEEP\"}"),
                () -> startAnyStatus(baby, token(caregiver), direct("LEFT")));
        assertThat(starts).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM care_record WHERE baby_id = ? "
                + "AND status = 'IN_PROGRESS' AND is_deleted = FALSE", Integer.class, baby)).isEqualTo(1);
        long id = active(baby, token(owner), 200).path("data").path("recordId").asLong();
        List<Integer> endings = concurrently(
                () -> transitionAnyStatus(baby, id, token(owner), "complete"),
                () -> transitionAnyStatus(baby, id, token(caregiver), "cancel"));
        assertThat(endings).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT status FROM care_record WHERE record_id = ?", String.class, id))
                .isIn("COMPLETED", "CANCELED");
        assertThat(start(baby, token(owner), "{\"recordType\":\"SLEEP\"}", 201).path("data")
                .path("status").asText()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void migrationAndAllSixTypesPersistWithTypedDetailsAndAudit() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history "
                + "WHERE version = '5' AND success = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = database() "
                + "AND table_name IN ('care_record','feeding_detail','diaper_detail','temperature_detail',"
                + "'medicine_detail','other_detail')", Integer.class)).isEqualTo(6);
        long owner = insertMember(false);
        long caregiver = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        String at = "2026-10-04T10:00:00+09:00";
        JsonNode formula = created(baby, owner, "{\"recordType\":\"FEEDING\",\"recordedAt\":\"" + at
                + "\",\"detail\":{\"feedingType\":\"FORMULA\",\"amountMl\":120}}", "FEEDING");
        long formulaId = formula.path("recordId").asLong();
        assertThat(formula.path("recordedAt").asText()).isEqualTo(at);
        assertThat(jdbc.queryForObject("SELECT recorded_at FROM care_record WHERE record_id = ?",
                LocalDateTime.class, formulaId).toInstant(ZoneOffset.UTC))
                .isEqualTo(OffsetDateTime.parse(at).toInstant());
        assertThat(formula.path("detail").path("feedingType").asText()).isEqualTo("FORMULA");
        assertThat(formula.path("detail").path("amountMl").asInt()).isEqualTo(120);
        assertThat(formula.path("detail").size()).isEqualTo(2);
        assertThat(formula.path("startAt").isNull()).isTrue();
        assertThat(formula.path("endAt").isNull()).isTrue();
        assertThat(jdbc.queryForMap("SELECT feeding_type, amount_ml, breast_side, duration_minutes "
                + "FROM feeding_detail WHERE record_id = ?", formulaId))
                .containsEntry("feeding_type", "FORMULA").containsEntry("amount_ml", 120)
                .containsEntry("breast_side", null).containsEntry("duration_minutes", null);

        JsonNode direct = created(baby, caregiver, "{\"recordType\":\"FEEDING\",\"recordedAt\":\"" + at
                + "\",\"detail\":{\"feedingType\":\"DIRECT_BREASTFEEDING\",\"breastSide\":\"BOTH\","
                + "\"durationMinutes\":20}}", "FEEDING");
        assertThat(jdbc.queryForMap("SELECT feeding_type, amount_ml, breast_side, duration_minutes "
                + "FROM feeding_detail WHERE record_id = ?", direct.path("recordId").asLong()))
                .containsEntry("feeding_type", "DIRECT_BREASTFEEDING")
                .containsEntry("amount_ml", null).containsEntry("breast_side", "BOTH")
                .containsEntry("duration_minutes", 20);

        String start = Instant.now().minusSeconds(7200).toString();
        String end = Instant.now().minusSeconds(3600).toString();
        JsonNode sleep = created(baby, owner, "{\"recordType\":\"SLEEP\",\"startAt\":\"" + start
                + "\",\"endAt\":\"" + end + "\"}", "SLEEP");
        assertThat(OffsetDateTime.parse(sleep.path("recordedAt").asText()).toInstant())
                .isEqualTo(Instant.parse(start));
        assertThat(OffsetDateTime.parse(sleep.path("startAt").asText()).toInstant())
                .isEqualTo(Instant.parse(start));
        assertThat(OffsetDateTime.parse(sleep.path("endAt").asText()).toInstant())
                .isEqualTo(Instant.parse(end));
        assertThat(sleep.path("detail").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT start_at IS NOT NULL AND end_at IS NOT NULL FROM care_record "
                + "WHERE record_id = ?", Boolean.class, sleep.path("recordId").asLong())).isTrue();
        for (String table : List.of("feeding_detail", "diaper_detail", "temperature_detail",
                "medicine_detail", "other_detail")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE record_id = ?",
                    Integer.class, sleep.path("recordId").asLong())).isZero();
        }

        JsonNode diaper = created(baby, caregiver, "{\"recordType\":\"DIAPER\",\"recordedAt\":\"" + at
                + "\",\"detail\":{\"diaperType\":\"BOTH\",\"condition\":\" \" ,\"color\":\"노란색\"}}",
                "DIAPER");
        assertThat(jdbc.queryForMap("SELECT diaper_type, `condition`, color FROM diaper_detail "
                + "WHERE record_id = ?", diaper.path("recordId").asLong()))
                .containsEntry("diaper_type", "BOTH").containsEntry("condition", null)
                .containsEntry("color", "노란색");

        JsonNode temperature = created(baby, owner, "{\"recordType\":\"TEMPERATURE\",\"recordedAt\":\""
                + at + "\",\"detail\":{\"temperatureCelsius\":37.2,\"measuredPart\":\"EAR\"}}",
                "TEMPERATURE");
        assertThat(jdbc.queryForObject("SELECT temperature_celsius FROM temperature_detail WHERE record_id = ?",
                java.math.BigDecimal.class, temperature.path("recordId").asLong()))
                .isEqualByComparingTo("37.2");

        JsonNode medicine = created(baby, caregiver, "{\"recordType\":\"MEDICINE\",\"recordedAt\":\""
                + at + "\",\"detail\":{\"medicineName\":\" 해열제 \",\"doseValue\":2.5,"
                + "\"doseUnit\":\"ML\"}}", "MEDICINE");
        assertThat(medicine.path("detail").path("medicineName").asText()).isEqualTo("해열제");
        assertThat(jdbc.queryForMap("SELECT medicine_name, dose_unit FROM medicine_detail WHERE record_id = ?",
                medicine.path("recordId").asLong()))
                .containsEntry("medicine_name", "해열제").containsEntry("dose_unit", "ML");

        JsonNode other = created(baby, owner, "{\"recordType\":\"OTHER\",\"recordedAt\":\"" + at
                + "\",\"memo\":\"B형간염 2차 접종\",\"detail\":{\"title\":\" 예방접종 \"}}", "OTHER");
        assertThat(other.path("memo").asText()).isEqualTo("B형간염 2차 접종");
        assertThat(jdbc.queryForObject("SELECT title FROM other_detail WHERE record_id = ?", String.class,
                other.path("recordId").asLong())).isEqualTo("예방접종");
        JsonNode noMemo = created(baby, owner, "{\"recordType\":\"OTHER\",\"recordedAt\":\"" + at
                + "\",\"detail\":{\"title\":\"일상\"}}", "OTHER");
        assertThat(noMemo.path("memo").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_schema = database() AND table_name = 'care_record' AND column_name = 'duration'",
                Integer.class)).isZero();
    }

    @Test
    void supportedEnumVariantsAndPositiveUnboundedMedicalValuesSave() throws Exception {
        long owner = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        String at = past();
        for (String side : List.of("LEFT", "RIGHT", "BOTH")) {
            JsonNode record = created(baby, owner, "{\"recordType\":\"FEEDING\",\"recordedAt\":\"" + at
                    + "\",\"detail\":{\"feedingType\":\"DIRECT_BREASTFEEDING\",\"breastSide\":\""
                    + side + "\",\"durationMinutes\":20}}", "FEEDING");
            assertThat(record.path("detail").path("breastSide").asText()).isEqualTo(side);
        }
        for (String kind : List.of("URINE", "STOOL", "BOTH")) {
            created(baby, owner, "{\"recordType\":\"DIAPER\",\"recordedAt\":\"" + at
                    + "\",\"detail\":{\"diaperType\":\"" + kind + "\"}}", "DIAPER");
        }
        for (String part : List.of("EAR", "FOREHEAD", "ARMPIT", "OTHER")) {
            created(baby, owner, "{\"recordType\":\"TEMPERATURE\",\"recordedAt\":\"" + at
                    + "\",\"detail\":{\"temperatureCelsius\":1000.5,\"measuredPart\":\"" + part
                    + "\"}}", "TEMPERATURE");
        }
        for (String unit : List.of("ML", "MG", "DROP", "TABLET", "OTHER")) {
            created(baby, owner, "{\"recordType\":\"MEDICINE\",\"recordedAt\":\"" + at
                    + "\",\"detail\":{\"medicineName\":\"약\",\"doseValue\":2.5,\"doseUnit\":\""
                    + unit + "\"}}", "MEDICINE");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM care_record WHERE baby_id = ?", Integer.class, baby))
                .isEqualTo(15);
        clearInvocations(named);
        String date = LocalDate.ofInstant(Instant.parse(at), ZoneId.of("Asia/Seoul")).toString();
        assertThat(list(baby, token(owner), date, null, 200).path("data").size()).isEqualTo(15);
        verify(named, times(4)).query(anyString(), anyMap(), any(RowCallbackHandler.class));
    }

    @Test
    void malformedDetailsTimesAndNumbersReturn400WithoutWrites() throws Exception {
        long owner = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        int before = countRecords();
        String at = past();
        String later = Instant.now().minusSeconds(60).toString();
        String future = Instant.now().plusSeconds(3600).toString();
        for (String body : List.of(
                "{\"recordType\":\"UNKNOWN\",\"recordedAt\":\"" + at + "\",\"detail\":{}}",
                "{\"recordType\":\"FEEDING\",\"recordedAt\":\"" + at
                        + "\",\"detail\":{\"feedingType\":\"FORMULA\"}}",
                "{\"recordType\":\"FEEDING\",\"recordedAt\":\"" + at
                        + "\",\"detail\":{\"feedingType\":\"FORMULA\",\"amountMl\":120,"
                        + "\"breastSide\":\"LEFT\"}}",
                "{\"recordType\":\"FEEDING\",\"recordedAt\":\"" + at
                        + "\",\"detail\":{\"temperatureCelsius\":37.2,\"measuredPart\":\"EAR\"}}",
                "{\"recordType\":\"FEEDING\",\"recordedAt\":\"" + at
                        + "\",\"detail\":{\"feedingType\":\"DIRECT_BREASTFEEDING\","
                        + "\"breastSide\":\"LEFT\"}}",
                "{\"recordType\":\"DIAPER\",\"recordedAt\":\"" + at
                        + "\",\"detail\":{\"diaperType\":\"UNKNOWN\"}}",
                "{\"recordType\":\"SLEEP\",\"startAt\":\"" + at + "\",\"endAt\":\"" + at + "\"}",
                "{\"recordType\":\"SLEEP\",\"startAt\":\"" + at + "\",\"endAt\":\"" + future + "\"}",
                "{\"recordType\":\"SLEEP\",\"startAt\":\"" + at + "\",\"endAt\":\"" + at
                        + "\",\"detail\":null}",
                "{\"recordType\":\"SLEEP\",\"recordedAt\":\"" + at + "\",\"startAt\":\""
                        + at + "\",\"endAt\":\"" + later + "\"}",
                "{\"recordType\":\"TEMPERATURE\",\"recordedAt\":\"" + at
                        + "\",\"detail\":{\"temperatureCelsius\":0,\"measuredPart\":\"EAR\"}}",
                "{\"recordType\":\"TEMPERATURE\",\"recordedAt\":\"" + at
                        + "\",\"detail\":{\"temperatureCelsius\":-1,\"measuredPart\":\"EAR\"}}",
                "{\"recordType\":\"TEMPERATURE\",\"recordedAt\":\"" + at
                        + "\",\"detail\":{\"temperatureCelsius\":37.2,\"measuredPart\":\"UNKNOWN\"}}",
                "{\"recordType\":\"MEDICINE\",\"recordedAt\":\"" + at
                        + "\",\"detail\":{\"medicineName\":\"   \",\"doseValue\":2.5,\"doseUnit\":\"ML\"}}",
                "{\"recordType\":\"MEDICINE\",\"recordedAt\":\"" + at
                        + "\",\"detail\":{\"medicineName\":\"약\",\"doseValue\":-1,\"doseUnit\":\"ML\"}}",
                "{\"recordType\":\"MEDICINE\",\"recordedAt\":\"" + at
                        + "\",\"detail\":{\"medicineName\":\"약\",\"doseValue\":0,\"doseUnit\":\"ML\"}}",
                "{\"recordType\":\"MEDICINE\",\"recordedAt\":\"" + at
                        + "\",\"detail\":{\"medicineName\":\"약\",\"doseValue\":2.5,\"doseUnit\":\"UNKNOWN\"}}",
                "{\"recordType\":\"OTHER\",\"recordedAt\":\"" + at
                        + "\",\"detail\":{\"title\":\"  \"}}",
                "{\"recordType\":\"OTHER\",\"recordedAt\":\"" + future
                        + "\",\"detail\":{\"title\":\"예방접종\"}}",
                "{\"recordType\":\"OTHER\",\"recordedAt\":\"not-a-date\",\"detail\":{\"title\":\"예방접종\"}}",
                "{\"recordType\":\"OTHER\",\"recordedAt\":\"2026-10-04T10:00:00\","
                        + "\"detail\":{\"title\":\"예방접종\"}}",
                "{invalid")) {
            assertFailure(send(baby, token(owner), body, 400));
        }
        assertThat(countRecords()).isEqualTo(before);
    }

    @Test
    void inaccessibleBabiesAndInvalidAuthenticationAreRejected() throws Exception {
        long owner = insertMember(false);
        long caregiver = insertMember(false);
        long outsider = insertMember(false);
        long left = insertMember(false);
        long removed = insertMember(false);
        long baby = insertBaby(owner, false);
        long deleted = insertBaby(owner, true);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        insertMembership(baby, left, "CAREGIVER", "LEFT");
        insertMembership(baby, removed, "CAREGIVER", "REMOVED");
        insertMembership(deleted, owner, "OWNER", "ACTIVE");
        String body = "{\"recordType\":\"OTHER\",\"recordedAt\":\"" + past()
                + "\",\"detail\":{\"title\":\"예방접종\"}}";
        int before = countRecords();
        for (long member : List.of(outsider, left, removed)) {
            assertFailure(send(baby, token(member), body, 404));
        }
        assertFailure(send(deleted, token(owner), body, 404));
        assertFailure(send(Long.MAX_VALUE, token(owner), body, 404));
        assertFailure(send(0, token(owner), body, 400));
        assertFailure(send(baby, null, body, 401));
        assertFailure(send(baby, "invalid.jwt", body, 401));
        assertFailure(send(baby, token(Long.MAX_VALUE), body, 401));
        assertFailure(send(baby, token(insertMember(true)), body, 403));
        assertThat(countRecords()).isEqualTo(before);
    }

    @Test
    void detailInsertFailureRollsBackAggregate() throws Exception {
        long owner = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        int before = countRecords();
        jdbc.execute("CREATE TRIGGER reject_care_detail BEFORE INSERT ON feeding_detail "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced detail failure'");
        try {
            assertFailure(send(baby, token(owner), "{\"recordType\":\"FEEDING\",\"recordedAt\":\""
                    + past() + "\",\"detail\":{\"feedingType\":\"FORMULA\",\"amountMl\":120}}", 500));
        } finally {
            jdbc.execute("DROP TRIGGER reject_care_detail");
        }
        assertThat(countRecords()).isEqualTo(before);
    }

    @Test
    void completedRecordsListFilterCalendarAndDetailReuseCreationContract() throws Exception {
        long owner = insertMember(false);
        long caregiver = insertMember(false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        String at = "2026-10-04T10:00:00+09:00";
        String prefix = "{\"recordedAt\":\"" + at + "\",\"recordType\":\"";
        List<JsonNode> created = List.of(
                created(baby, owner, prefix + "FEEDING\",\"detail\":{\"feedingType\":\"FORMULA\","
                        + "\"amountMl\":120}}", "FEEDING"),
                created(baby, caregiver, "{\"recordType\":\"SLEEP\",\"startAt\":\"" + at
                        + "\",\"endAt\":\"2026-10-04T11:00:00+09:00\"}", "SLEEP"),
                created(baby, owner, prefix + "DIAPER\",\"detail\":{\"diaperType\":\"BOTH\"}}", "DIAPER"),
                created(baby, owner, prefix + "TEMPERATURE\",\"detail\":{\"temperatureCelsius\":37.2,"
                        + "\"measuredPart\":\"EAR\"}}", "TEMPERATURE"),
                created(baby, owner, prefix + "MEDICINE\",\"detail\":{\"medicineName\":\"약\","
                        + "\"doseValue\":2.5,\"doseUnit\":\"ML\"}}", "MEDICINE"),
                created(baby, owner, prefix + "OTHER\",\"memo\":\"예방접종\","
                        + "\"detail\":{\"title\":\"접종\"}}", "OTHER"));
        var before = jdbc.queryForMap("SELECT record_type, input_type, status, memo, version, is_deleted "
                + "FROM care_record WHERE record_id = ?", created.getFirst().path("recordId").asLong());
        var babyBefore = jdbc.queryForMap("SELECT is_deleted, created_by_member_id FROM baby WHERE baby_id = ?", baby);
        var memberBefore = jdbc.queryForMap("SELECT role, status, joined_at, ended_at FROM baby_member "
                + "WHERE baby_id = ? AND member_id = ?", baby, owner);

        JsonNode ownerList = list(baby, token(owner), "2026-10-04", null, 200);
        JsonNode caregiverList = list(baby, token(caregiver), "2026-10-04", null, 200);
        assertSuccess(ownerList);
        assertThat(caregiverList).isEqualTo(ownerList);
        assertThat(ownerList.path("data").size()).isEqualTo(6);
        for (int index = 0; index < created.size(); index++) {
            long expectedId = created.get(created.size() - 1 - index).path("recordId").asLong();
            assertThat(ownerList.path("data").get(index).path("recordId").asLong()).isEqualTo(expectedId);
        }
        assertThat(list(baby, token(owner), "2026-10-04", "FEEDING", 200)
                .path("data").size()).isEqualTo(1);
        assertThat(list(baby, token(owner), "2026-10-04", "FEEDING", 200)
                .path("data").get(0).path("detail").path("amountMl").asInt()).isEqualTo(120);
        assertThat(list(baby, token(owner), "2026-10-03", null, 200).path("data").size()).isZero();
        assertThat(calendar(baby, token(owner), "2026", "10", 200).path("data").get(0)
                .path("recordCount").asLong()).isEqualTo(6);
        assertThat(calendar(baby, token(owner), "2026", "09", 200).path("data").size()).isZero();
        for (JsonNode original : created) {
            JsonNode response = detail(baby, original.path("recordId").asLong(), token(caregiver), 200);
            assertSuccess(response);
            JsonNode data = response.path("data");
            assertThat(data.size()).isEqualTo(11);
            assertThat(data.path("recordId").asLong()).isEqualTo(original.path("recordId").asLong());
            assertThat(data.path("recordType").asText()).isEqualTo(original.path("recordType").asText());
            assertThat(data.path("recordedAt").asText()).isEqualTo(at);
            assertThat(data.path("inputType").asText()).isEqualTo("MANUAL");
            assertThat(data.path("status").asText()).isEqualTo("COMPLETED");
            assertThat(data.path("version").asLong()).isZero();
            assertThat(data.has("createdByMemberId")).isFalse();
            if (data.path("recordType").asText().equals("SLEEP")) {
                assertThat(data.path("detail").isNull()).isTrue();
                assertThat(data.path("startAt").asText()).isEqualTo(at);
            } else {
                assertThat(data.path("detail").isObject()).isTrue();
                switch (data.path("recordType").asText()) {
                    case "FEEDING" -> assertThat(data.path("detail").path("amountMl").asInt()).isEqualTo(120);
                    case "DIAPER" -> assertThat(data.path("detail").path("diaperType").asText()).isEqualTo("BOTH");
                    case "TEMPERATURE" -> {
                        assertThat(data.path("detail").path("temperatureCelsius").decimalValue())
                                .isEqualByComparingTo("37.2");
                        assertThat(data.path("detail").path("measuredPart").asText()).isEqualTo("EAR");
                    }
                    case "MEDICINE" -> {
                        assertThat(data.path("detail").path("medicineName").asText()).isEqualTo("약");
                        assertThat(data.path("detail").path("doseUnit").asText()).isEqualTo("ML");
                    }
                    case "OTHER" -> assertThat(data.path("detail").path("title").asText()).isEqualTo("접종");
                    default -> throw new AssertionError("Unexpected record type");
                }
            }
        }
        assertThat(jdbc.queryForMap("SELECT record_type, input_type, status, memo, version, is_deleted "
                + "FROM care_record WHERE record_id = ?", created.getFirst().path("recordId").asLong()))
                .isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT is_deleted, created_by_member_id FROM baby WHERE baby_id = ?", baby))
                .isEqualTo(babyBefore);
        assertThat(jdbc.queryForMap("SELECT role, status, joined_at, ended_at FROM baby_member "
                + "WHERE baby_id = ? AND member_id = ?", baby, owner)).isEqualTo(memberBefore);
        assertThat(jdbc.queryForObject("SELECT last_selected_baby_id FROM member WHERE member_id = ?",
                Long.class, owner)).isNull();
    }

    @Test
    void memberTimezoneControlsDayAndMonthBoundariesAndSleepBelongsToStartDay() throws Exception {
        long owner = insertMember(false);
        long utcCaregiver = insertMember("UTC", false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, utcCaregiver, "CAREGIVER", "ACTIVE");
        String other = "{\"recordType\":\"OTHER\",\"recordedAt\":\"%s\","
                + "\"detail\":{\"title\":\"일상\"}}";
        long september = created(baby, owner, other.formatted("2026-09-30T23:59:59+09:00"), "OTHER")
                .path("recordId").asLong();
        long october = created(baby, owner, other.formatted("2026-10-01T00:00:00+09:00"), "OTHER")
                .path("recordId").asLong();
        long beforeMidnight = created(baby, owner, other.formatted("2026-10-04T23:59:59+09:00"), "OTHER")
                .path("recordId").asLong();
        long atMidnight = created(baby, owner, other.formatted("2026-10-05T00:00:00+09:00"), "OTHER")
                .path("recordId").asLong();
        long sleep = created(baby, owner, "{\"recordType\":\"SLEEP\","
                + "\"startAt\":\"2026-10-04T23:00:00+09:00\","
                + "\"endAt\":\"2026-10-05T02:00:00+09:00\"}", "SLEEP")
                .path("recordId").asLong();

        JsonNode fourth = list(baby, token(owner), "2026-10-04", null, 200).path("data");
        assertThat(fourth.size()).isEqualTo(2);
        assertThat(fourth.get(0).path("recordId").asLong()).isEqualTo(beforeMidnight);
        assertThat(fourth.get(1).path("recordId").asLong()).isEqualTo(sleep);
        JsonNode fifth = list(baby, token(owner), "2026-10-05", null, 200).path("data");
        assertThat(fifth.size()).isEqualTo(1);
        assertThat(fifth.get(0).path("recordId").asLong()).isEqualTo(atMidnight);
        assertThat(list(baby, token(owner), "2026-09-30", null, 200).path("data").get(0)
                .path("recordId").asLong()).isEqualTo(september);
        assertThat(list(baby, token(owner), "2026-10-01", null, 200).path("data").get(0)
                .path("recordId").asLong()).isEqualTo(october);
        JsonNode octoberCalendar = calendar(baby, token(owner), "2026", "10", 200).path("data");
        assertThat(octoberCalendar.size()).isEqualTo(3);
        assertThat(octoberCalendar.get(0).path("date").asText()).isEqualTo("2026-10-01");
        assertThat(octoberCalendar.get(0).path("recordCount").asLong()).isEqualTo(1);
        assertThat(octoberCalendar.get(1).path("date").asText()).isEqualTo("2026-10-04");
        assertThat(octoberCalendar.get(1).path("recordCount").asLong()).isEqualTo(2);
        assertThat(octoberCalendar.get(2).path("date").asText()).isEqualTo("2026-10-05");
        assertThat(octoberCalendar.get(2).path("recordCount").asLong()).isEqualTo(1);
        assertThat(calendar(baby, token(owner), "2026", "09", 200).path("data").get(0)
                .path("recordCount").asLong()).isEqualTo(1);

        JsonNode utcFourth = list(baby, token(utcCaregiver), "2026-10-04", null, 200).path("data");
        assertThat(utcFourth.size()).isEqualTo(3);
        assertThat(utcFourth.get(0).path("recordedAt").asText()).endsWith("Z");
        assertThat(list(baby, token(utcCaregiver), "2026-10-05", null, 200)
                .path("data").size()).isZero();
        assertThat(calendar(baby, token(utcCaregiver), "2026", "09", 200)
                .path("data").get(0).path("recordCount").asLong()).isEqualTo(2);
        assertThat(detail(baby, atMidnight, token(utcCaregiver), 200).path("data")
                .path("recordedAt").asText()).isEqualTo("2026-10-04T15:00:00Z");
    }

    @Test
    void listCalendarAndDetailHideOtherBabyAndNonCompletedRecords() throws Exception {
        long owner = insertMember(false);
        long baby = insertBaby(owner, false);
        long otherBaby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(otherBaby, owner, "OWNER", "ACTIVE");
        String body = "{\"recordType\":\"OTHER\",\"recordedAt\":\"2026-10-04T10:00:00+09:00\","
                + "\"detail\":{\"title\":\"일상\"}}";
        long visible = created(baby, owner, body, "OTHER").path("recordId").asLong();
        long progress = created(baby, owner, body, "OTHER").path("recordId").asLong();
        long canceled = created(baby, owner, body, "OTHER").path("recordId").asLong();
        long deleted = created(baby, owner, body, "OTHER").path("recordId").asLong();
        long elsewhere = created(otherBaby, owner, body, "OTHER").path("recordId").asLong();
        jdbc.update("UPDATE care_record SET status = 'IN_PROGRESS' WHERE record_id = ?", progress);
        jdbc.update("UPDATE care_record SET status = 'CANCELED' WHERE record_id = ?", canceled);
        jdbc.update("UPDATE care_record SET is_deleted = TRUE, deleted_at = ? WHERE record_id = ?",
                LocalDateTime.of(2026, 10, 5, 0, 0), deleted);
        jdbc.update("UPDATE care_record SET version = 3 WHERE record_id = ?", visible);
        JsonNode listed = list(baby, token(owner), "2026-10-04", null, 200).path("data");
        assertThat(listed.size()).isEqualTo(1);
        assertThat(listed.get(0).path("recordId").asLong()).isEqualTo(visible);
        assertThat(listed.get(0).path("version").asLong()).isEqualTo(3);
        JsonNode days = calendar(baby, token(owner), "2026", "10", 200).path("data");
        assertThat(days.size()).isEqualTo(1);
        assertThat(days.get(0).path("recordCount").asLong()).isEqualTo(1);
        assertThat(detail(baby, visible, token(owner), 200).path("data").path("version").asLong())
                .isEqualTo(3);
        for (long hidden : List.of(progress, canceled, deleted, elsewhere, Long.MAX_VALUE)) {
            assertFailure(detail(baby, hidden, token(owner), 404));
        }
        assertThat(detail(otherBaby, elsewhere, token(owner), 200).path("data").path("babyId").asLong())
                .isEqualTo(otherBaby);
    }

    @Test
    void queryValidationAuthenticationAndMembershipAccessFollowCommonContract() throws Exception {
        long owner = insertMember(false);
        long caregiver = insertMember(false);
        long outsider = insertMember(false);
        long left = insertMember(false);
        long removed = insertMember(false);
        long baby = insertBaby(owner, false);
        long deleted = insertBaby(owner, true);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        insertMembership(baby, left, "CAREGIVER", "LEFT");
        insertMembership(baby, removed, "CAREGIVER", "REMOVED");
        insertMembership(deleted, owner, "OWNER", "ACTIVE");
        long record = created(baby, owner, "{\"recordType\":\"OTHER\","
                + "\"recordedAt\":\"2026-10-04T10:00:00+09:00\","
                + "\"detail\":{\"title\":\"일상\"}}", "OTHER").path("recordId").asLong();
        assertThat(list(baby, token(caregiver), "2026-10-04", null, 200).path("data").size()).isEqualTo(1);
        assertThat(calendar(baby, token(caregiver), "2026", "10", 200).path("data").size()).isEqualTo(1);
        assertThat(detail(baby, record, token(caregiver), 200).path("data").path("recordId").asLong())
                .isEqualTo(record);
        for (String invalid : List.of("", "2026-1-1", "2026-02-30", "not-a-date")) {
            assertFailure(list(baby, token(owner), invalid, null, 400));
        }
        assertFailure(list(baby, token(owner), null, null, 400));
        assertFailure(list(baby, token(owner), "2026-10-04", "UNKNOWN", 400));
        assertThat(calendar(baby, token(owner), "9999", "12", 200).path("data").size()).isZero();
        for (String[] invalid : List.of(new String[]{null, "10"}, new String[]{"2026", null},
                new String[]{"20", "10"}, new String[]{"2026", "0"}, new String[]{"2026", "13"},
                new String[]{"0000", "10"})) {
            assertFailure(calendar(baby, token(owner), invalid[0], invalid[1], 400));
        }
        assertFailure(detail(baby, 0, token(owner), 400));
        assertFailure(list(0, token(owner), "2026-10-04", null, 400));
        assertFailure(calendar(0, token(owner), "2026", "10", 400));
        for (long inaccessible : List.of(deleted, Long.MAX_VALUE)) {
            assertFailure(list(inaccessible, token(owner), "2026-10-04", null, 404));
            assertFailure(calendar(inaccessible, token(owner), "2026", "10", 404));
            assertFailure(detail(inaccessible, record, token(owner), 404));
        }
        for (long member : List.of(outsider, left, removed)) {
            assertFailure(list(baby, token(member), "2026-10-04", null, 404));
            assertFailure(calendar(baby, token(member), "2026", "10", 404));
            assertFailure(detail(baby, record, token(member), 404));
        }
        for (String jwt : new String[]{null, "invalid.jwt", token(Long.MAX_VALUE)}) {
            assertFailure(list(baby, jwt, "2026-10-04", null, 401));
            assertFailure(calendar(baby, jwt, "2026", "10", 401));
            assertFailure(detail(baby, record, jwt, 401));
        }
        String withdrawn = token(insertMember(true));
        assertFailure(list(baby, withdrawn, "2026-10-04", null, 403));
        assertFailure(calendar(baby, withdrawn, "2026", "10", 403));
        assertFailure(detail(baby, record, withdrawn, 403));
    }

    private JsonNode created(long babyId, long memberId, String body, String type) throws Exception {
        JsonNode response = send(babyId, token(memberId), body, 201);
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("message").isNull()).isTrue();
        assertThat(response.has("errorCode")).isFalse();
        JsonNode data = response.path("data");
        assertThat(data.size()).isEqualTo(11);
        assertThat(data.path("recordId").asLong()).isPositive();
        assertThat(data.path("babyId").asLong()).isEqualTo(babyId);
        assertThat(data.path("recordType").asText()).isEqualTo(type);
        assertThat(data.path("inputType").asText()).isEqualTo("MANUAL");
        assertThat(data.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(data.path("version").asLong()).isZero();
        assertThat(jdbc.queryForMap("SELECT baby_id, record_type, input_type, status, "
                + "created_by_member_id, completed_by_member_id, last_updated_by_member_id, "
                + "deleted_by_member_id, version, is_deleted, deleted_at FROM care_record WHERE record_id = ?",
                data.path("recordId").asLong()))
                .containsEntry("baby_id", babyId).containsEntry("record_type", type)
                .containsEntry("input_type", "MANUAL").containsEntry("status", "COMPLETED")
                .containsEntry("created_by_member_id", memberId).containsEntry("completed_by_member_id", memberId)
                .containsEntry("last_updated_by_member_id", memberId).containsEntry("deleted_by_member_id", null)
                .containsEntry("version", 0L).containsEntry("is_deleted", false).containsEntry("deleted_at", null);
        return data;
    }

    private String direct(String side) {
        return "{\"recordType\":\"FEEDING\",\"detail\":{\"feedingType\":\"DIRECT_BREASTFEEDING\","
                + "\"breastSide\":\"" + side + "\"}}";
    }

    private JsonNode start(long babyId, String jwt, String body, int expectedStatus) throws Exception {
        var request = post("/api/v1/babies/{babyId}/records/start", babyId)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        return mapper.readTree(mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
    }

    private int startAnyStatus(long babyId, String jwt, String body) throws Exception {
        return mvc.perform(post("/api/v1/babies/{babyId}/records/start", babyId)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode active(long babyId, String jwt, int expectedStatus) throws Exception {
        var request = get("/api/v1/babies/{babyId}/records/active", babyId);
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        return mapper.readTree(mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode transition(long babyId, long recordId, String jwt, String action, int expectedStatus)
            throws Exception {
        var request = patch("/api/v1/babies/{babyId}/records/{recordId}/{action}", babyId, recordId, action);
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        return mapper.readTree(mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
    }

    private int transitionAnyStatus(long babyId, long recordId, String jwt, String action) throws Exception {
        return mvc.perform(patch("/api/v1/babies/{babyId}/records/{recordId}/{action}", babyId, recordId, action)
                        .header("Authorization", "Bearer " + jwt))
                .andReturn().getResponse().getStatus();
    }

    private List<Integer> concurrently(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (Callable<Integer> task : List.of(first, second)) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            return List.of(results.get(0).get(), results.get(1).get());
        }
    }

    private JsonNode send(long babyId, String jwt, String body, int expectedStatus) throws Exception {
        var request = post("/api/v1/babies/{babyId}/records", babyId)
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (jwt != null) {
            request.header("Authorization", "Bearer " + jwt);
        }
        return mapper.readTree(mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode list(long babyId, String jwt, String date, String type, int expectedStatus) throws Exception {
        var request = get("/api/v1/babies/{babyId}/records", babyId);
        if (jwt != null) {
            request.header("Authorization", "Bearer " + jwt);
        }
        if (date != null) {
            request.param("date", date);
        }
        if (type != null) {
            request.param("recordType", type);
        }
        return mapper.readTree(mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode calendar(long babyId, String jwt, String year, String month, int expectedStatus)
            throws Exception {
        var request = get("/api/v1/babies/{babyId}/records/calendar", babyId);
        if (jwt != null) {
            request.header("Authorization", "Bearer " + jwt);
        }
        if (year != null) {
            request.param("year", year);
        }
        if (month != null) {
            request.param("month", month);
        }
        return mapper.readTree(mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode detail(long babyId, long recordId, String jwt, int expectedStatus) throws Exception {
        var request = get("/api/v1/babies/{babyId}/records/{recordId}", babyId, recordId);
        if (jwt != null) {
            request.header("Authorization", "Bearer " + jwt);
        }
        return mapper.readTree(mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
    }

    private void assertFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("data").isNull()).isTrue();
        assertThat(response.path("message").asText()).isNotBlank();
        assertThat(response.has("errorCode")).isFalse();
    }

    private void assertSuccess(JsonNode response) {
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("message").isNull()).isTrue();
        assertThat(response.has("errorCode")).isFalse();
    }

    private long insertMember(boolean deleted) {
        return insertMember("Asia/Seoul", deleted);
    }

    private long insertMember(String timezone, boolean deleted) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO member "
                    + "(member_name, timezone, is_deleted, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    new String[]{"member_id"});
            statement.setString(1, "보호자");
            statement.setString(2, timezone);
            statement.setBoolean(3, deleted);
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.setTimestamp(5, Timestamp.from(Instant.now()));
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private long insertBaby(long creator, boolean deleted) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO baby "
                    + "(created_by_member_id, baby_name, birth_date, gender, is_deleted, created_at, updated_at, deleted_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", new String[]{"baby_id"});
            statement.setLong(1, creator);
            statement.setString(2, "도윤");
            statement.setDate(3, Date.valueOf("2020-01-01"));
            statement.setString(4, "MALE");
            statement.setBoolean(5, deleted);
            statement.setTimestamp(6, Timestamp.from(Instant.now()));
            statement.setTimestamp(7, Timestamp.from(Instant.now()));
            statement.setTimestamp(8, deleted ? Timestamp.from(Instant.now()) : null);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private void insertMembership(long babyId, long memberId, String role, String state) {
        jdbc.update("INSERT INTO baby_member (baby_id, member_id, role, status, joined_at, ended_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", babyId, memberId, role, state, Timestamp.from(Instant.now()),
                "ACTIVE".equals(state) ? null : Timestamp.from(Instant.now()));
    }

    private int countRecords() {
        return jdbc.queryForObject("SELECT count(*) FROM care_record", Integer.class);
    }

    private String past() {
        return Instant.now().minusSeconds(300).toString();
    }

    private String token(long memberId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("dearus").subject(Long.toString(memberId))
                .issuedAt(now.minusSeconds(1)).expiresAt(now.plusSeconds(300)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims)).getTokenValue();
    }
}
