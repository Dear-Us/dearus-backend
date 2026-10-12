package com.dearusback.insight.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.DearUsBackApplication;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(classes = DearUsBackApplication.class)
class StatisticsMariaDbIntegrationTest {
    private static final String DAY = "2025-10-05";
    private static final String BEFORE = "2025-10-04";
    private static final String AFTER = "2025-10-06";

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
    void emptyStatisticsUseZerosAndNullsWithoutWritingRows() throws Exception {
        long owner = insertMember(false, "Asia/Seoul");
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        long careBefore = rowCount("care_record", baby);
        long growthBefore = rowCount("growth_record", baby);

        JsonNode daily = daily(baby, owner, DAY);
        assertThat(daily.path("feeding").path("totalCount").asLong()).isZero();
        assertThat(daily.path("feeding").path("formulaCount").asLong()).isZero();
        assertThat(daily.path("feeding").path("formulaTotalMl").asLong()).isZero();
        assertThat(daily.path("feeding").path("formulaAverageMl").isNull()).isTrue();
        assertThat(daily.path("feeding").path("directBreastfeedingAverageMinutes").isNull()).isTrue();
        assertThat(daily.path("sleep").path("count").asLong()).isZero();
        assertThat(daily.path("sleep").path("totalMinutes").asLong()).isZero();
        assertThat(daily.path("sleep").path("averageMinutes").isNull()).isTrue();
        assertThat(daily.path("diaper").path("totalCount").asLong()).isZero();
        assertThat(daily.path("temperature").path("count").asLong()).isZero();
        assertThat(daily.path("temperature").path("latestTemperatureCelsius").isNull()).isTrue();
        assertThat(daily.path("temperature").path("minTemperatureCelsius").isNull()).isTrue();
        assertThat(daily.path("temperature").path("maxTemperatureCelsius").isNull()).isTrue();
        assertThat(daily.path("medicine").path("count").asLong()).isZero();
        assertThat(daily.path("other").path("count").asLong()).isZero();

        JsonNode routine = routine(baby, owner, DAY);
        assertThat(routine.path("startDate").asText()).isEqualTo("2025-09-29");
        assertThat(routine.path("endDate").asText()).isEqualTo(DAY);
        assertThat(routine.path("days").size()).isEqualTo(7);
        assertThat(routine.path("days").get(0).path("date").asText()).isEqualTo("2025-09-29");
        assertThat(routine.path("days").get(6).path("date").asText()).isEqualTo(DAY);
        for (JsonNode point : routine.path("days")) {
            assertThat(point.path("feedingCount").asLong()).isZero();
            assertThat(point.path("formulaTotalMl").asLong()).isZero();
            assertThat(point.path("directBreastfeedingMinutes").asLong()).isZero();
            assertThat(point.path("sleepTotalMinutes").asLong()).isZero();
            assertThat(point.path("diaperCount").asLong()).isZero();
        }
        assertThat(routine.path("averages").path("feedingCountPerDay").decimalValue())
                .isEqualByComparingTo("0.0");

        JsonNode growth = growth(baby, owner);
        assertThat(growth.path("latest").isNull()).isTrue();
        assertThat(growth.path("changeFromPrevious").path("heightCm").isNull()).isTrue();
        assertThat(growth.path("changeFromPrevious").path("weightKg").isNull()).isTrue();
        assertThat(growth.path("points").size()).isZero();
        assertThat(rowCount("care_record", baby)).isEqualTo(careBefore);
        assertThat(rowCount("growth_record", baby)).isEqualTo(growthBefore);
        assertThat(daily(baby, owner, "2027-01-01").path("feeding").path("totalCount").asLong()).isZero();
    }

    @Test
    void dailyAndRoutineAggregateActualCareRecordsWithTimezoneOverlapAndExclusions() throws Exception {
        long owner = insertMember(false, "Asia/Seoul");
        long caregiver = insertMember(false, "UTC");
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");

        long firstFormula = care(baby, owner, recorded("FEEDING", "2025-10-05T00:30:00+09:00",
                "{\"feedingType\":\"FORMULA\",\"amountMl\":120}"));
        care(baby, owner, recorded("FEEDING", "2025-10-05T23:30:00+09:00",
                "{\"feedingType\":\"FORMULA\",\"amountMl\":240}"));
        care(baby, caregiver, recorded("FEEDING", "2025-10-05T12:00:00+09:00",
                "{\"feedingType\":\"DIRECT_BREASTFEEDING\",\"breastSide\":\"LEFT\","
                        + "\"durationMinutes\":20}"));
        for (String type : List.of("URINE", "STOOL", "BOTH")) {
            care(baby, owner, recorded("DIAPER", "2025-10-05T12:00:00+09:00",
                    "{\"diaperType\":\"" + type + "\",\"condition\":null,\"color\":null}"));
        }
        long firstTemperature = care(baby, owner, recorded("TEMPERATURE", "2025-10-05T12:00:00+09:00",
                "{\"temperatureCelsius\":36.8,\"measuredPart\":\"EAR\"}"));
        long secondTemperature = care(baby, owner, recorded("TEMPERATURE", "2025-10-05T12:00:00+09:00",
                "{\"temperatureCelsius\":37.2,\"measuredPart\":\"EAR\"}"));
        assertThat(secondTemperature).isGreaterThan(firstTemperature);
        care(baby, owner, recorded("MEDICINE", "2025-10-05T12:00:00+09:00",
                "{\"medicineName\":\"비타민\",\"doseValue\":1,\"doseUnit\":\"ML\"}"));
        care(baby, owner, recorded("OTHER", "2025-10-05T12:00:00+09:00", "{\"title\":\"산책\"}"));
        sleep(baby, owner, "2025-10-04T23:00:00+09:00", "2025-10-05T02:00:00+09:00");
        sleep(baby, owner, "2025-10-05T23:00:00+09:00", "2025-10-06T02:00:00+09:00");
        sleep(baby, owner, "2025-09-28T23:00:00+09:00", "2025-09-29T01:00:00+09:00");
        care(baby, owner, recorded("FEEDING", "2025-09-28T12:00:00+09:00",
                "{\"feedingType\":\"FORMULA\",\"amountMl\":500}"));
        care(baby, owner, recorded("FEEDING", "2025-10-06T12:00:00+09:00",
                "{\"feedingType\":\"FORMULA\",\"amountMl\":600}"));
        long deleted = care(baby, owner, recorded("FEEDING", "2025-10-05T10:00:00+09:00",
                "{\"feedingType\":\"FORMULA\",\"amountMl\":999}"));
        request(delete("/api/v1/babies/" + baby + "/records/" + deleted).param("version", "0"), owner, 200);
        long canceled = request(post("/api/v1/babies/" + baby + "/records/start")
                .contentType(MediaType.APPLICATION_JSON).content("{\"recordType\":\"SLEEP\"}"), owner, 201)
                .path("data").path("recordId").asLong();
        request(patch("/api/v1/babies/" + baby + "/records/" + canceled + "/cancel"), owner, 200);
        jdbc.update("UPDATE care_record SET recorded_at = ?, start_at = ? WHERE record_id = ?",
                Timestamp.valueOf("2025-10-05 03:00:00"), Timestamp.valueOf("2025-10-05 03:00:00"), canceled);
        long active = request(post("/api/v1/babies/" + baby + "/records/start")
                .contentType(MediaType.APPLICATION_JSON).content("{\"recordType\":\"SLEEP\"}"), owner, 201)
                .path("data").path("recordId").asLong();
        jdbc.update("UPDATE care_record SET recorded_at = ?, start_at = ? WHERE record_id = ?",
                Timestamp.valueOf("2025-10-05 04:00:00"), Timestamp.valueOf("2025-10-05 04:00:00"), active);
        Timestamp careUpdatedBefore = jdbc.queryForObject(
                "SELECT updated_at FROM care_record WHERE record_id = ?", Timestamp.class, firstFormula);
        long careCountBefore = rowCount("care_record", baby);

        JsonNode day = daily(baby, owner, DAY);
        assertThat(day.path("date").asText()).isEqualTo(DAY);
        assertThat(day.path("feeding").path("totalCount").asLong()).isEqualTo(3);
        assertThat(day.path("feeding").path("formulaCount").asLong()).isEqualTo(2);
        assertThat(day.path("feeding").path("formulaTotalMl").asLong()).isEqualTo(360);
        assertThat(day.path("feeding").path("formulaAverageMl").decimalValue()).isEqualByComparingTo("180.0");
        assertThat(day.path("feeding").path("directBreastfeedingCount").asLong()).isEqualTo(1);
        assertThat(day.path("feeding").path("directBreastfeedingTotalMinutes").asLong()).isEqualTo(20);
        assertThat(day.path("feeding").path("directBreastfeedingAverageMinutes").decimalValue())
                .isEqualByComparingTo("20.0");
        assertThat(day.path("sleep").path("count").asLong()).isEqualTo(2);
        assertThat(day.path("sleep").path("totalMinutes").asLong()).isEqualTo(180);
        assertThat(day.path("sleep").path("averageMinutes").decimalValue()).isEqualByComparingTo("90.0");
        assertThat(day.path("diaper").path("totalCount").asLong()).isEqualTo(3);
        assertThat(day.path("diaper").path("urineCount").asLong()).isEqualTo(2);
        assertThat(day.path("diaper").path("stoolCount").asLong()).isEqualTo(2);
        assertThat(day.path("temperature").path("count").asLong()).isEqualTo(2);
        assertThat(day.path("temperature").path("latestTemperatureCelsius").decimalValue())
                .isEqualByComparingTo("37.2");
        assertThat(day.path("temperature").path("minTemperatureCelsius").decimalValue())
                .isEqualByComparingTo("36.8");
        assertThat(day.path("temperature").path("maxTemperatureCelsius").decimalValue())
                .isEqualByComparingTo("37.2");
        assertThat(day.path("medicine").path("count").asLong()).isEqualTo(1);
        assertThat(day.path("other").path("count").asLong()).isEqualTo(1);
        assertThat(daily(baby, owner, AFTER).path("sleep").path("totalMinutes").asLong()).isEqualTo(120);
        assertThat(daily(baby, owner, BEFORE).path("sleep").path("totalMinutes").asLong()).isEqualTo(60);
        assertThat(daily(baby, caregiver, DAY).path("feeding").path("formulaCount").asLong()).isEqualTo(1);
        assertThat(daily(baby, caregiver, BEFORE).path("feeding").path("formulaCount").asLong())
                .isEqualTo(1);

        JsonNode routine = routine(baby, owner, DAY);
        assertThat(routine.path("days").size()).isEqualTo(7);
        JsonNode days = routine.path("days");
        assertThat(days.get(0).path("date").asText()).isEqualTo("2025-09-29");
        assertThat(days.get(0).path("sleepTotalMinutes").asLong()).isEqualTo(60);
        assertThat(days.get(5).path("date").asText()).isEqualTo(BEFORE);
        assertThat(days.get(5).path("sleepTotalMinutes").asLong()).isEqualTo(60);
        assertThat(days.get(6).path("date").asText()).isEqualTo(DAY);
        assertThat(days.get(6).path("feedingCount").asLong()).isEqualTo(3);
        assertThat(days.get(6).path("formulaTotalMl").asLong()).isEqualTo(360);
        assertThat(days.get(6).path("directBreastfeedingMinutes").asLong()).isEqualTo(20);
        assertThat(days.get(6).path("sleepTotalMinutes").asLong()).isEqualTo(180);
        assertThat(days.get(6).path("diaperCount").asLong()).isEqualTo(3);
        assertThat(days.get(1).path("feedingCount").asLong()).isZero();
        JsonNode averages = routine.path("averages");
        assertThat(averages.path("feedingCountPerDay").decimalValue()).isEqualByComparingTo("0.4");
        assertThat(averages.path("formulaTotalMlPerDay").decimalValue()).isEqualByComparingTo("51.4");
        assertThat(averages.path("directBreastfeedingMinutesPerDay").decimalValue())
                .isEqualByComparingTo("2.9");
        assertThat(averages.path("sleepMinutesPerDay").decimalValue()).isEqualByComparingTo("42.9");
        assertThat(averages.path("diaperCountPerDay").decimalValue()).isEqualByComparingTo("0.4");
        assertThat(rowCount("care_record", baby)).isEqualTo(careCountBefore);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM care_record WHERE record_id = ?",
                Timestamp.class, firstFormula)).isEqualTo(careUpdatedBefore);
    }

    @Test
    void growthTrendUsesMetricSpecificPreviousValueAndExcludesDeletedRows() throws Exception {
        long owner = insertMember(false, "Asia/Seoul");
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        long first = growthRecord(baby, owner, "2025-09-01", "58.0", "7.0");
        JsonNode single = growth(baby, owner);
        assertThat(single.path("latest").path("heightCm").decimalValue()).isEqualByComparingTo("58.0");
        assertThat(single.path("changeFromPrevious").path("heightCm").isNull()).isTrue();
        assertThat(single.path("changeFromPrevious").path("weightKg").isNull()).isTrue();
        long heightOnly = growthRecord(baby, owner, "2025-10-01", "60.0", null);
        long weightOnly = growthRecord(baby, owner, DAY, null, "6.8");
        long latest = growthRecord(baby, owner, DAY, "61.5", "6.6");
        long deleted = growthRecord(baby, owner, AFTER, "99.0", "99.0");
        request(delete("/api/v1/babies/" + baby + "/growth-records/" + deleted).param("version", "0"),
                owner, 200);
        Timestamp growthUpdatedBefore = jdbc.queryForObject(
                "SELECT updated_at FROM growth_record WHERE growth_record_id = ?", Timestamp.class, first);
        long growthCountBefore = rowCount("growth_record", baby);

        JsonNode trend = growth(baby, owner);
        assertThat(trend.path("latest").path("measuredDate").asText()).isEqualTo(DAY);
        assertThat(trend.path("latest").path("heightCm").decimalValue()).isEqualByComparingTo("61.5");
        assertThat(trend.path("latest").path("weightKg").decimalValue()).isEqualByComparingTo("6.6");
        assertThat(trend.path("changeFromPrevious").path("heightCm").decimalValue())
                .isEqualByComparingTo("1.5");
        assertThat(trend.path("changeFromPrevious").path("weightKg").decimalValue())
                .isEqualByComparingTo("-0.2");
        JsonNode points = trend.path("points");
        assertThat(points.size()).isEqualTo(4);
        assertThat(points.get(0).path("growthRecordId").asLong()).isEqualTo(first);
        assertThat(points.get(1).path("growthRecordId").asLong()).isEqualTo(heightOnly);
        assertThat(points.get(2).path("growthRecordId").asLong()).isEqualTo(weightOnly);
        assertThat(points.get(3).path("growthRecordId").asLong()).isEqualTo(latest);
        assertThat(points.toString()).doesNotContain("99.0");
        assertThat(rowCount("growth_record", baby)).isEqualTo(growthCountBefore);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM growth_record WHERE growth_record_id = ?",
                Timestamp.class, first)).isEqualTo(growthUpdatedBefore);
    }

    @Test
    void missingMetricOnLatestPointIsNotInterpolated() throws Exception {
        long owner = insertMember(false, "Asia/Seoul");
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        growthRecord(baby, owner, "2025-09-01", "58", "7");
        growthRecord(baby, owner, "2025-10-01", "60", "6.8");
        growthRecord(baby, owner, DAY, "61", null);

        JsonNode trend = growth(baby, owner);
        assertThat(trend.path("latest").path("weightKg").isNull()).isTrue();
        assertThat(trend.path("changeFromPrevious").path("heightCm").decimalValue())
                .isEqualByComparingTo("1");
        assertThat(trend.path("changeFromPrevious").path("weightKg").isNull()).isTrue();
    }

    @Test
    void statisticsValidateInputsAndBabyMembershipWithCommonErrorEnvelope() throws Exception {
        long owner = insertMember(false, "Asia/Seoul");
        long caregiver = insertMember(false, "Asia/Seoul");
        long outsider = insertMember(false, "Asia/Seoul");
        long left = insertMember(false, "Asia/Seoul");
        long removed = insertMember(false, "Asia/Seoul");
        long withdrawn = insertMember(true, "Asia/Seoul");
        long baby = insertBaby(owner, false);
        long deletedBaby = insertBaby(owner, true);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        insertMembership(baby, left, "CAREGIVER", "LEFT");
        insertMembership(baby, removed, "CAREGIVER", "REMOVED");
        insertMembership(deletedBaby, owner, "OWNER", "ACTIVE");

        assertThat(daily(baby, caregiver, DAY).path("date").asText()).isEqualTo(DAY);
        assertThat(routine(baby, caregiver, DAY).path("days").size()).isEqualTo(7);
        assertThat(growth(baby, caregiver).path("points").size()).isZero();
        for (String path : List.of(stats(baby) + "/daily?date=" + DAY,
                stats(baby) + "/routine?endDate=" + DAY, stats(baby) + "/growth")) {
            for (long member : List.of(outsider, left, removed)) {
                failure(get(path), member, 404);
            }
            failure(get(path), withdrawn, 403);
            failure(get(path), Long.MAX_VALUE, 401);
            JsonNode anonymous = mapper.readTree(mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString());
            assertThat(anonymous.path("success").asBoolean()).isFalse();
            assertThat(anonymous.has("errorCode")).isFalse();
        }
        for (String path : List.of(stats(deletedBaby) + "/growth", stats(Long.MAX_VALUE) + "/growth")) {
            failure(get(path), owner, 404);
        }
        for (String path : List.of(stats(0) + "/growth", stats(0) + "/daily?date=" + DAY,
                stats(0) + "/routine?endDate=" + DAY, stats(baby) + "/daily",
                stats(baby) + "/daily?date=2025-02-30", stats(baby) + "/daily?date=not-a-date",
                stats(baby) + "/routine", stats(baby) + "/routine?endDate=bad")) {
            failure(get(path), owner, 400);
        }
    }

    private JsonNode daily(long babyId, long memberId, String date) throws Exception {
        return request(get(stats(babyId) + "/daily").param("date", date), memberId, 200).path("data");
    }

    private JsonNode routine(long babyId, long memberId, String endDate) throws Exception {
        return request(get(stats(babyId) + "/routine").param("endDate", endDate), memberId, 200).path("data");
    }

    private JsonNode growth(long babyId, long memberId) throws Exception {
        return request(get(stats(babyId) + "/growth"), memberId, 200).path("data");
    }

    private long care(long babyId, long memberId, String body) throws Exception {
        return request(post("/api/v1/babies/" + babyId + "/records")
                .contentType(MediaType.APPLICATION_JSON).content(body), memberId, 201)
                .path("data").path("recordId").asLong();
    }

    private long sleep(long babyId, long memberId, String start, String end) throws Exception {
        return care(babyId, memberId, "{\"recordType\":\"SLEEP\",\"startAt\":\""
                + start + "\",\"endAt\":\"" + end + "\"}");
    }

    private String recorded(String type, String at, String detail) {
        return "{\"recordType\":\"" + type + "\",\"recordedAt\":\"" + at
                + "\",\"detail\":" + detail + "}";
    }

    private long growthRecord(long babyId, long memberId, String date, String height, String weight)
            throws Exception {
        return request(post("/api/v1/babies/" + babyId + "/growth-records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"measuredDate\":\"" + date + "\",\"heightCm\":" + height
                        + ",\"weightKg\":" + weight + ",\"memo\":null}"), memberId, 201)
                .path("data").path("growthRecordId").asLong();
    }

    private JsonNode request(MockHttpServletRequestBuilder builder, long memberId, int expected) throws Exception {
        JsonNode response = mapper.readTree(mvc.perform(builder.header("Authorization", "Bearer " + token(memberId)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
        assertThat(response.path("success").asBoolean()).isEqualTo(expected < 400);
        assertThat(response.has("errorCode")).isFalse();
        return response;
    }

    private void failure(MockHttpServletRequestBuilder builder, long memberId, int expected) throws Exception {
        JsonNode response = request(builder, memberId, expected);
        assertThat(response.path("data").isNull()).isTrue();
        assertThat(response.path("message").asText()).isNotBlank();
    }

    private String stats(long babyId) {
        return "/api/v1/babies/" + babyId + "/stats";
    }

    private long rowCount(String table, long babyId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE baby_id = ?", Long.class, babyId);
    }

    private long insertMember(boolean deleted, String timezone) {
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

    private String token(long memberId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("dearus").subject(Long.toString(memberId))
                .issuedAt(now.minusSeconds(1)).expiresAt(now.plusSeconds(300)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims)).getTokenValue();
    }
}
