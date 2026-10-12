package com.dearusback.insight.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.DearUsBackApplication;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
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
@SpringBootTest(classes = {DearUsBackApplication.class, HomeMariaDbIntegrationTest.FixedClock.class})
class HomeMariaDbIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

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

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock homeTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean NamedParameterJdbcTemplate named;

    @Test
    void emptyHomeShowsBabyRoleAndZeroCountsWithoutChangingState() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long caregiver = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        var babyBefore = jdbc.queryForMap("SELECT baby_name, is_deleted, created_by_member_id "
                + "FROM baby WHERE baby_id = ?", baby);
        var membershipBefore = jdbc.queryForMap("SELECT role, status FROM baby_member "
                + "WHERE baby_id = ? AND member_id = ?", baby, owner);
        JsonNode ownerHome = home(baby, token(owner), 200);
        assertSuccess(ownerHome);
        JsonNode data = ownerHome.path("data");
        assertThat(data.size()).isEqualTo(5);
        assertThat(data.path("baby").path("babyId").asLong()).isEqualTo(baby);
        assertThat(data.path("baby").path("babyName").asText()).isEqualTo("도윤");
        assertThat(data.path("baby").path("birthDate").asText()).isEqualTo("2026-08-15");
        assertThat(data.path("baby").path("gender").asText()).isEqualTo("MALE");
        assertThat(data.path("baby").path("profileImageUrl").isNull()).isTrue();
        assertThat(data.path("baby").path("myRole").asText()).isEqualTo("OWNER");
        assertThat(data.path("activeRecord").isNull()).isTrue();
        assertThat(data.path("lastCompletedRecord").isNull()).isTrue();
        assertThat(data.path("recentRecords").size()).isZero();
        assertThat(data.path("todaySummary").path("date").asText()).isEqualTo("2026-10-05");
        for (String count : List.of("totalRecordCount", "feedingCount", "sleepCount", "diaperCount",
                "temperatureCount", "medicineCount", "otherCount")) {
            assertThat(data.path("todaySummary").path(count).asLong()).isZero();
        }
        assertThat(home(baby, token(caregiver), 200).path("data").path("baby")
                .path("myRole").asText()).isEqualTo("CAREGIVER");
        assertThat(jdbc.queryForMap("SELECT baby_name, is_deleted, created_by_member_id "
                + "FROM baby WHERE baby_id = ?", baby)).isEqualTo(babyBefore);
        assertThat(jdbc.queryForMap("SELECT role, status FROM baby_member "
                + "WHERE baby_id = ? AND member_id = ?", baby, owner)).isEqualTo(membershipBefore);
        assertThat(jdbc.queryForObject("SELECT last_selected_baby_id FROM member WHERE member_id = ?",
                Long.class, owner)).isNull();
    }

    @Test
    void mixedCareRecordsProduceTodayCountsRecentFiveAndLatestCompleted() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        long feeding = create(baby, owner, at("FEEDING", "2026-10-05T18:00:00+09:00",
                "{\"feedingType\":\"FORMULA\",\"amountMl\":120}"));
        create(baby, owner, "{\"recordType\":\"SLEEP\","
                + "\"startAt\":\"2026-10-05T09:00:00+09:00\","
                + "\"endAt\":\"2026-10-05T10:00:00+09:00\"}");
        long diaper = create(baby, owner, at("DIAPER", "2026-10-05T19:00:00+09:00",
                "{\"diaperType\":\"BOTH\"}"));
        long temperature = create(baby, owner, at("TEMPERATURE", "2026-10-05T18:30:00+09:00",
                "{\"temperatureCelsius\":37.2,\"measuredPart\":\"EAR\"}"));
        long medicine = create(baby, owner, at("MEDICINE", "2026-10-05T17:00:00+09:00",
                "{\"medicineName\":\"약\",\"doseValue\":2.5,\"doseUnit\":\"ML\"}"));
        long other = create(baby, owner, at("OTHER", "2026-10-05T16:00:00+09:00",
                "{\"title\":\"산책\"}"));
        create(baby, owner, at("OTHER", "2026-10-05T00:00:00+09:00", "{\"title\":\"자정\"}"));
        create(baby, owner, "{\"recordType\":\"SLEEP\","
                + "\"startAt\":\"2026-10-04T23:00:00+09:00\","
                + "\"endAt\":\"2026-10-05T02:00:00+09:00\"}");
        long deleted = create(baby, owner, at("OTHER", "2026-10-05T20:00:00+09:00",
                "{\"title\":\"삭제됨\"}"));
        deleteRecord(baby, deleted, owner);
        long canceled = start(baby, owner, "{\"recordType\":\"SLEEP\"}");
        cancel(baby, canceled, owner);
        long active = start(baby, owner, direct("LEFT"));
        var careBefore = jdbc.queryForMap("SELECT status, version, updated_at, is_deleted "
                + "FROM care_record WHERE record_id = ?", feeding);
        var detailBefore = jdbc.queryForMap("SELECT feeding_type, amount_ml, breast_side, duration_minutes "
                + "FROM feeding_detail WHERE record_id = ?", feeding);

        JsonNode data = home(baby, token(owner), 200).path("data");
        JsonNode summary = data.path("todaySummary");
        assertThat(summary.path("date").asText()).isEqualTo("2026-10-05");
        assertThat(summary.path("totalRecordCount").asLong()).isEqualTo(7);
        assertThat(summary.path("feedingCount").asLong()).isEqualTo(1);
        assertThat(summary.path("sleepCount").asLong()).isEqualTo(1);
        assertThat(summary.path("diaperCount").asLong()).isEqualTo(1);
        assertThat(summary.path("temperatureCount").asLong()).isEqualTo(1);
        assertThat(summary.path("medicineCount").asLong()).isEqualTo(1);
        assertThat(summary.path("otherCount").asLong()).isEqualTo(2);
        assertThat(data.path("activeRecord").path("recordId").asLong()).isEqualTo(active);
        assertThat(data.path("activeRecord").path("detail").path("durationMinutes").isNull()).isTrue();
        assertThat(data.path("lastCompletedRecord").path("recordId").asLong()).isEqualTo(diaper);
        JsonNode recent = data.path("recentRecords");
        assertThat(recent.size()).isEqualTo(5);
        assertThat(recent.get(0).path("recordId").asLong()).isEqualTo(diaper);
        assertThat(recent.get(1).path("recordId").asLong()).isEqualTo(temperature);
        assertThat(recent.get(2).path("recordId").asLong()).isEqualTo(feeding);
        assertThat(recent.get(3).path("recordId").asLong()).isEqualTo(medicine);
        assertThat(recent.get(4).path("recordId").asLong()).isEqualTo(other);
        assertThat(recent.get(0).path("detail").path("diaperType").asText()).isEqualTo("BOTH");
        assertThat(recent.get(1).path("detail").path("temperatureCelsius").decimalValue())
                .isEqualByComparingTo("37.2");
        assertThat(recent.get(2).path("detail").path("amountMl").asInt()).isEqualTo(120);
        assertThat(recent.get(3).path("detail").path("medicineName").asText()).isEqualTo("약");
        assertThat(recent.get(4).path("detail").path("title").asText()).isEqualTo("산책");
        for (JsonNode record : recent) {
            assertThat(record.path("status").asText()).isEqualTo("COMPLETED");
            assertThat(record.size()).isEqualTo(11);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM care_record WHERE record_id = ?", String.class, active))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT status FROM care_record WHERE record_id = ?", String.class, canceled))
                .isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM care_record WHERE record_id = ?",
                Boolean.class, deleted)).isTrue();
        assertThat(jdbc.queryForMap("SELECT status, version, updated_at, is_deleted "
                + "FROM care_record WHERE record_id = ?", feeding)).isEqualTo(careBefore);
        assertThat(jdbc.queryForMap("SELECT feeding_type, amount_ml, breast_side, duration_minutes "
                + "FROM feeding_detail WHERE record_id = ?", feeding)).isEqualTo(detailBefore);
        assertThat(jdbc.queryForObject("SELECT last_selected_baby_id FROM member WHERE member_id = ?",
                Long.class, owner)).isNull();
    }

    @Test
    void lastCompletedAndRecentRecordsStillReturnYesterdayWhenTodayIsEmpty() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        long other = create(baby, owner, at("OTHER", "2026-10-04T19:00:00+09:00",
                "{\"title\":\"어제\"}"));
        long sleep = create(baby, owner, "{\"recordType\":\"SLEEP\","
                + "\"startAt\":\"2026-10-04T19:00:00+09:00\","
                + "\"endAt\":\"2026-10-04T20:00:00+09:00\"}");
        JsonNode data = home(baby, token(owner), 200).path("data");
        assertThat(data.path("todaySummary").path("totalRecordCount").asLong()).isZero();
        assertThat(data.path("lastCompletedRecord").path("recordId").asLong()).isEqualTo(sleep);
        assertThat(data.path("lastCompletedRecord").path("detail").isNull()).isTrue();
        assertThat(data.path("recentRecords").size()).isEqualTo(2);
        assertThat(data.path("recentRecords").get(0).path("recordId").asLong()).isEqualTo(sleep);
        assertThat(data.path("recentRecords").get(1).path("recordId").asLong()).isEqualTo(other);
    }

    @Test
    void memberTimezoneBoundaryAndRecordedAtTieDetermineCountsAndOrder() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long utcCaregiver = insertMember("UTC", false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, utcCaregiver, "CAREGIVER", "ACTIVE");
        long first = create(baby, owner, at("OTHER", "2026-10-05T00:00:00+09:00",
                "{\"title\":\"자정1\"}"));
        long second = create(baby, owner, at("OTHER", "2026-10-05T00:00:00+09:00",
                "{\"title\":\"자정2\"}"));
        long later = create(baby, owner, at("OTHER", "2026-10-05T10:00:00+09:00",
                "{\"title\":\"오전\"}"));
        JsonNode seoul = home(baby, token(owner), 200).path("data");
        JsonNode utc = home(baby, token(utcCaregiver), 200).path("data");
        assertThat(seoul.path("todaySummary").path("totalRecordCount").asLong()).isEqualTo(3);
        assertThat(utc.path("todaySummary").path("totalRecordCount").asLong()).isEqualTo(1);
        assertThat(seoul.path("todaySummary").path("date").asText()).isEqualTo("2026-10-05");
        assertThat(utc.path("todaySummary").path("date").asText()).isEqualTo("2026-10-05");
        assertThat(seoul.path("recentRecords").get(0).path("recordId").asLong()).isEqualTo(later);
        assertThat(seoul.path("recentRecords").get(1).path("recordId").asLong()).isEqualTo(second);
        assertThat(seoul.path("recentRecords").get(2).path("recordId").asLong()).isEqualTo(first);
        assertThat(utc.path("lastCompletedRecord").path("recordId").asLong()).isEqualTo(later);
        assertThat(utc.path("recentRecords").get(1).path("recordedAt").asText()).endsWith("Z");
    }

    @Test
    void activeSleepAndLongRunningDirectFeedingRemainVisible() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long sleepBaby = insertBaby(owner, false);
        long feedingBaby = insertBaby(owner, false);
        insertMembership(sleepBaby, owner, "OWNER", "ACTIVE");
        insertMembership(feedingBaby, owner, "OWNER", "ACTIVE");
        long sleep = start(sleepBaby, owner, "{\"recordType\":\"SLEEP\"}");
        long direct = start(feedingBaby, owner, direct("RIGHT"));
        jdbc.update("UPDATE care_record SET recorded_at = DATE_SUB(recorded_at, INTERVAL 15 HOUR), "
                + "start_at = DATE_SUB(start_at, INTERVAL 15 HOUR) WHERE record_id = ?", direct);
        JsonNode sleepHome = home(sleepBaby, token(owner), 200).path("data");
        assertThat(sleepHome.path("activeRecord").path("recordId").asLong()).isEqualTo(sleep);
        assertThat(sleepHome.path("activeRecord").path("detail").isNull()).isTrue();
        assertThat(sleepHome.path("todaySummary").path("totalRecordCount").asLong()).isZero();
        JsonNode feedingHome = home(feedingBaby, token(owner), 200).path("data");
        assertThat(feedingHome.path("activeRecord").path("recordId").asLong()).isEqualTo(direct);
        assertThat(feedingHome.path("activeRecord").path("detail").path("breastSide").asText())
                .isEqualTo("RIGHT");
        assertThat(feedingHome.path("activeRecord").path("detail").path("durationMinutes").isNull()).isTrue();
        assertThat(feedingHome.path("lastCompletedRecord").isNull()).isTrue();
        assertThat(feedingHome.path("recentRecords").size()).isZero();
    }

    @Test
    void fiveRecentSameTypeRecordsLoadDetailInOneBatch() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        for (int index = 0; index < 6; index++) {
            create(baby, owner, at("FEEDING", "2026-10-05T1" + index + ":00:00+09:00",
                    "{\"feedingType\":\"FORMULA\",\"amountMl\":" + (100 + index) + "}"));
        }
        clearInvocations(named);
        JsonNode recent = home(baby, token(owner), 200).path("data").path("recentRecords");
        assertThat(recent.size()).isEqualTo(5);
        assertThat(recent.get(0).path("detail").path("amountMl").asInt()).isEqualTo(105);
        assertThat(recent.get(4).path("detail").path("amountMl").asInt()).isEqualTo(101);
        verify(named, times(1)).query(anyString(), anyMap(), any(RowCallbackHandler.class));
    }

    @Test
    void inaccessibleBabiesAndInvalidMembersFollowCommonFailureContract() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long outsider = insertMember("Asia/Seoul", false);
        long left = insertMember("Asia/Seoul", false);
        long removed = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, false);
        long deleted = insertBaby(owner, true);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, left, "CAREGIVER", "LEFT");
        insertMembership(baby, removed, "CAREGIVER", "REMOVED");
        insertMembership(deleted, owner, "OWNER", "ACTIVE");
        assertFailure(home(0, token(owner), 400));
        assertFailure(home(Long.MAX_VALUE, token(owner), 404));
        assertFailure(home(deleted, token(owner), 404));
        for (long member : List.of(outsider, left, removed)) {
            assertFailure(home(baby, token(member), 404));
        }
        assertFailure(home(baby, null, 401));
        assertFailure(home(baby, "invalid.jwt", 401));
        assertFailure(home(baby, token(Long.MAX_VALUE), 401));
        assertFailure(home(baby, token(insertMember("Asia/Seoul", true)), 403));
    }

    private JsonNode home(long babyId, String jwt, int expectedStatus) throws Exception {
        var request = get("/api/v1/babies/{babyId}/home", babyId);
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        return mapper.readTree(mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
    }

    private long create(long babyId, long memberId, String body) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/babies/{babyId}/records", babyId)
                        .header("Authorization", "Bearer " + token(memberId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("data").path("recordId").asLong();
    }

    private long start(long babyId, long memberId, String body) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/babies/{babyId}/records/start", babyId)
                        .header("Authorization", "Bearer " + token(memberId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .path("data").path("recordId").asLong();
    }

    private void cancel(long babyId, long recordId, long memberId) throws Exception {
        mvc.perform(patch("/api/v1/babies/{babyId}/records/{recordId}/cancel", babyId, recordId)
                .header("Authorization", "Bearer " + token(memberId))).andExpect(status().isOk());
    }

    private void deleteRecord(long babyId, long recordId, long memberId) throws Exception {
        mvc.perform(delete("/api/v1/babies/{babyId}/records/{recordId}", babyId, recordId)
                .header("Authorization", "Bearer " + token(memberId)).param("version", "0"))
                .andExpect(status().isOk());
    }

    private String at(String type, String recordedAt, String detail) {
        return "{\"recordType\":\"" + type + "\",\"recordedAt\":\"" + recordedAt
                + "\",\"detail\":" + detail + "}";
    }

    private String direct(String side) {
        return "{\"recordType\":\"FEEDING\",\"detail\":{\"feedingType\":\"DIRECT_BREASTFEEDING\","
                + "\"breastSide\":\"" + side + "\"}}";
    }

    private void assertSuccess(JsonNode response) {
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("message").isNull()).isTrue();
        assertThat(response.has("errorCode")).isFalse();
    }

    private void assertFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("data").isNull()).isTrue();
        assertThat(response.path("message").asText()).isNotBlank();
        assertThat(response.has("errorCode")).isFalse();
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
            statement.setTimestamp(4, Timestamp.from(NOW));
            statement.setTimestamp(5, Timestamp.from(NOW));
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
            statement.setDate(3, Date.valueOf("2026-08-15"));
            statement.setString(4, "MALE");
            statement.setBoolean(5, deleted);
            statement.setTimestamp(6, Timestamp.from(NOW));
            statement.setTimestamp(7, Timestamp.from(NOW));
            statement.setTimestamp(8, deleted ? Timestamp.from(NOW) : null);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private void insertMembership(long babyId, long memberId, String role, String state) {
        jdbc.update("INSERT INTO baby_member (baby_id, member_id, role, status, joined_at, ended_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", babyId, memberId, role, state, Timestamp.from(NOW),
                "ACTIVE".equals(state) ? null : Timestamp.from(NOW));
    }

    private String token(long memberId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("dearus").subject(Long.toString(memberId))
                .issuedAt(now.minusSeconds(1)).expiresAt(now.plusSeconds(300)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims)).getTokenValue();
    }
}
