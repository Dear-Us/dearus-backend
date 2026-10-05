package com.dearusback.care.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.DearUsBackApplication;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

    private JsonNode send(long babyId, String jwt, String body, int expectedStatus) throws Exception {
        var request = post("/api/v1/babies/{babyId}/records", babyId)
                .contentType(MediaType.APPLICATION_JSON).content(body);
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

    private long insertMember(boolean deleted) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO member "
                    + "(member_name, timezone, is_deleted, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    new String[]{"member_id"});
            statement.setString(1, "보호자");
            statement.setString(2, "Asia/Seoul");
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
