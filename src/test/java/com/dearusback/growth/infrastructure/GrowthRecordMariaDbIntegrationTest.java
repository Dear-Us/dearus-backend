package com.dearusback.growth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.context.annotation.Primary;
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
@Import(GrowthRecordMariaDbIntegrationTest.FixedGrowthClock.class)
class GrowthRecordMariaDbIntegrationTest {
    // In Seoul it is October 6, while in UTC and Honolulu it is still October 5.
    private static final Instant FIXED_NOW = Instant.parse("2026-10-05T15:30:00Z");
    private static final String TODAY = "2026-10-06";
    private static final String YESTERDAY = "2026-10-05";

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
    static class FixedGrowthClock {
        @Bean
        @Primary
        Clock growthTestClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired ObjectMapper mapper;

    @Test
    void mariaDbCrudLifecycleSortingDuplicateDatesAndAudit() throws Exception {
        long owner = insertMember(false, "Asia/Seoul");
        long caregiver = insertMember(false, "Asia/Seoul");
        long baby = insertBaby(owner, false);
        long otherBaby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        insertMembership(otherBaby, owner, "OWNER", "ACTIVE");

        assertThat(list(baby, owner).path("data").size()).isZero();
        JsonNode both = create(baby, owner, body(YESTERDAY, "61.5", "6.8", "집에서 측정"));
        JsonNode heightOnly = create(baby, caregiver, body(TODAY, "62.0", null, null));
        JsonNode weightOnly = create(baby, owner, body(TODAY, null, "6.9", null));
        long bothId = id(both);
        long heightId = id(heightOnly);
        long weightId = id(weightOnly);
        assertThat(both.path("version").asLong()).isZero();
        assertThat(both.path("babyId").asLong()).isEqualTo(baby);
        assertThat(heightOnly.path("weightKg").isNull()).isTrue();
        assertThat(weightOnly.path("heightCm").isNull()).isTrue();
        assertThat(detail(baby, bothId, owner).path("data").path("memo").asText()).isEqualTo("집에서 측정");

        JsonNode rows = list(baby, caregiver).path("data");
        assertThat(rows.size()).isEqualTo(3);
        assertThat(rows.get(0).path("growthRecordId").asLong()).isEqualTo(weightId);
        assertThat(rows.get(1).path("growthRecordId").asLong()).isEqualTo(heightId);
        assertThat(rows.get(2).path("growthRecordId").asLong()).isEqualTo(bothId);
        assertFailure(get(url(otherBaby) + "/" + bothId), owner, 404);
        assertFailure(get(url(baby) + "/" + Long.MAX_VALUE), owner, 404);

        JsonNode updated = update(baby, bothId, caregiver,
                "{\"version\":0,\"measuredDate\":\"" + TODAY
                        + "\",\"heightCm\":null,\"weightKg\":7.1,\"memo\":null}");
        assertThat(updated.path("version").asLong()).isEqualTo(1);
        assertThat(updated.path("measuredDate").asText()).isEqualTo(TODAY);
        assertThat(updated.path("heightCm").isNull()).isTrue();
        assertThat(updated.path("weightKg").decimalValue()).isEqualByComparingTo("7.1");
        assertThat(updated.path("memo").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT created_by_member_id FROM growth_record WHERE growth_record_id = ?",
                Long.class, bothId)).isEqualTo(owner);
        assertThat(jdbc.queryForObject("SELECT last_updated_by_member_id FROM growth_record WHERE growth_record_id = ?",
                Long.class, bothId)).isEqualTo(caregiver);

        JsonNode identical = update(baby, bothId, caregiver, "{\"version\":1,\"weightKg\":7.1}");
        assertThat(identical.path("version").asLong()).isEqualTo(2);
        deleteRecord(baby, bothId, caregiver, 2);
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM growth_record WHERE growth_record_id = ?",
                Boolean.class, bothId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM growth_record WHERE growth_record_id = ?",
                Timestamp.class, bothId)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT deleted_by_member_id FROM growth_record WHERE growth_record_id = ?",
                Long.class, bothId)).isEqualTo(caregiver);
        assertThat(jdbc.queryForObject("SELECT last_updated_by_member_id FROM growth_record WHERE growth_record_id = ?",
                Long.class, bothId)).isEqualTo(caregiver);
        assertThat(list(baby, owner).path("data").size()).isEqualTo(2);
        assertFailure(get(url(baby) + "/" + bothId), owner, 404);
    }

    @Test
    void invalidMeasurementsDatesAndCreateBodiesReturn400() throws Exception {
        long owner = insertMember(false, "Asia/Seoul");
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");

        for (String body : List.of(
                "{}",
                "{\"heightCm\":61}",
                body(TODAY, null, null, null),
                body(TODAY, "0", null, null),
                body(TODAY, "-1", null, null),
                body(TODAY, null, "0", null),
                body(TODAY, null, "-1", null),
                body("2026-10-07", "61", null, null),
                body("2026-02-30", "61", null, null),
                "{\"measuredDate\":\"2026-10-06\",\"heightCm\":\"61\"}",
                "{\"measuredDate\":\"2026-10-06\",\"heightCm\":61,\"extra\":1}")) {
            assertFailure(post(url(baby)).contentType(MediaType.APPLICATION_JSON).content(body), owner, 400);
        }
        assertThat(list(baby, owner).path("data").size()).isZero();
    }

    @Test
    void futureDateUsesAuthenticatedMembersTimezoneRatherThanJvmDefault() throws Exception {
        long seoul = insertMember(false, "Asia/Seoul");
        long honolulu = insertMember(false, "Pacific/Honolulu");
        long baby = insertBaby(seoul, false);
        insertMembership(baby, seoul, "OWNER", "ACTIVE");
        insertMembership(baby, honolulu, "CAREGIVER", "ACTIVE");

        long recordId = id(create(baby, seoul, body(TODAY, "61", null, null)));
        assertFailure(post(url(baby)).contentType(MediaType.APPLICATION_JSON)
                .content(body(TODAY, "61", null, null)), honolulu, 400);
        assertFailure(patch(url(baby) + "/" + recordId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"memo\":\"new\"}"), honolulu, 400);
        create(baby, honolulu, body(YESTERDAY, null, "6.8", null));
        assertThat(list(baby, seoul).path("data").size()).isEqualTo(2);
    }

    @Test
    void partialUpdateDistinguishesMissingFieldsFromExplicitNullAndRejectsStaleVersion() throws Exception {
        long owner = insertMember(false, "Asia/Seoul");
        long caregiver = insertMember(false, "Asia/Seoul");
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        long id = id(create(baby, owner, body(YESTERDAY, "60", "6", "old")));

        JsonNode memoCleared = update(baby, id, caregiver, "{\"version\":0,\"memo\":null}");
        assertThat(memoCleared.path("heightCm").decimalValue()).isEqualByComparingTo("60");
        assertThat(memoCleared.path("weightKg").decimalValue()).isEqualByComparingTo("6");
        assertThat(memoCleared.path("memo").isNull()).isTrue();
        JsonNode heightCleared = update(baby, id, caregiver, "{\"version\":1,\"heightCm\":null}");
        assertThat(heightCleared.path("heightCm").isNull()).isTrue();
        assertThat(heightCleared.path("weightKg").decimalValue()).isEqualByComparingTo("6");
        assertFailure(patch(url(baby) + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2,\"weightKg\":null}"), caregiver, 400);
        assertFailure(patch(url(baby) + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2}"), caregiver, 400);
        assertFailure(patch(url(baby) + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"memo\":\"x\"}"), caregiver, 400);
        assertFailure(patch(url(baby) + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":-1,\"memo\":\"x\"}"), caregiver, 400);
        assertFailure(patch(url(baby) + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2,\"measuredDate\":\"2026-10-07\"}"), caregiver, 400);
        assertFailure(patch(url(baby) + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"memo\":\"stale\"}"), owner, 409);
        assertThat(detail(baby, id, owner).path("data").path("version").asLong()).isEqualTo(2);
        assertFailure(delete(url(baby) + "/" + id).param("version", "0"), owner, 409);
        assertFailure(delete(url(baby) + "/" + id), owner, 400);
        assertFailure(delete(url(baby) + "/" + id).param("version", "-1"), owner, 400);
    }

    @Test
    void heightWeightAndMemoCanBeChangedIndependently() throws Exception {
        long owner = insertMember(false, "Asia/Seoul");
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        long id = id(create(baby, owner, body(YESTERDAY, "60", "6", "old")));

        JsonNode taller = update(baby, id, owner, "{\"version\":0,\"heightCm\":62.0}");
        assertThat(taller.path("heightCm").decimalValue()).isEqualByComparingTo("62.0");
        assertThat(taller.path("weightKg").decimalValue()).isEqualByComparingTo("6");
        JsonNode weightCleared = update(baby, id, owner, "{\"version\":1,\"weightKg\":null}");
        assertThat(weightCleared.path("weightKg").isNull()).isTrue();
        assertThat(weightCleared.path("heightCm").decimalValue()).isEqualByComparingTo("62.0");
        JsonNode memoChanged = update(baby, id, owner, "{\"version\":2,\"memo\":\"new\"}");
        assertThat(memoChanged.path("memo").asText()).isEqualTo("new");
        assertThat(memoChanged.path("version").asLong()).isEqualTo(3);
    }

    @Test
    void permissionsAndMissingResourcesUseCommonErrorEnvelope() throws Exception {
        long owner = insertMember(false, "Asia/Seoul");
        long outsider = insertMember(false, "Asia/Seoul");
        long left = insertMember(false, "Asia/Seoul");
        long removed = insertMember(false, "Asia/Seoul");
        long withdrawn = insertMember(true, "Asia/Seoul");
        long baby = insertBaby(owner, false);
        long deletedBaby = insertBaby(owner, true);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, left, "CAREGIVER", "LEFT");
        insertMembership(baby, removed, "CAREGIVER", "REMOVED");
        insertMembership(deletedBaby, owner, "OWNER", "ACTIVE");
        long id = id(create(baby, owner, body(YESTERDAY, "61", null, null)));

        for (long member : List.of(outsider, left, removed)) {
            assertFailure(get(url(baby)), member, 404);
            assertFailure(get(url(baby) + "/" + id), member, 404);
            assertFailure(post(url(baby)).contentType(MediaType.APPLICATION_JSON)
                    .content(body(YESTERDAY, "61", null, null)), member, 404);
            assertFailure(patch(url(baby) + "/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":0,\"memo\":\"x\"}"), member, 404);
            assertFailure(delete(url(baby) + "/" + id).param("version", "0"), member, 404);
        }
        assertFailure(get(url(deletedBaby)), owner, 404);
        assertFailure(get(url(Long.MAX_VALUE)), owner, 404);
        assertFailure(get(url(0)), owner, 400);
        assertFailure(get(url(baby) + "/0"), owner, 400);
        assertFailure(get(url(baby)), withdrawn, 403);
        assertFailure(get(url(baby)), Long.MAX_VALUE, 401);
        JsonNode anonymous = mapper.readTree(mvc.perform(get(url(baby))).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString());
        assertThat(anonymous.path("success").asBoolean()).isFalse();
        assertThat(anonymous.has("errorCode")).isFalse();
    }

    @Test
    void sameVersionConcurrentPatchAllowsExactlyOneWriter() throws Exception {
        long owner = insertMember(false, "Asia/Seoul");
        long caregiver = insertMember(false, "Asia/Seoul");
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        long id = id(create(baby, owner, body(YESTERDAY, "61", "6", null)));

        List<Integer> outcomes = race(
                () -> perform(patch(url(baby) + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"memo\":\"owner\"}"), owner),
                () -> perform(patch(url(baby) + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"memo\":\"caregiver\"}"), caregiver));

        assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
        assertThat(detail(baby, id, owner).path("data").path("version").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT memo FROM growth_record WHERE growth_record_id = ?",
                String.class, id)).isIn("owner", "caregiver");
    }

    @Test
    void identicalConcurrentPatchesStillAdvanceVersionOnlyOnce() throws Exception {
        long owner = insertMember(false, "Asia/Seoul");
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        long id = id(create(baby, owner, body(YESTERDAY, "61", "6", null)));

        List<Integer> outcomes = race(
                () -> perform(patch(url(baby) + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"memo\":null}"), owner),
                () -> perform(patch(url(baby) + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"memo\":null}"), owner));

        assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
        assertThat(detail(baby, id, owner).path("data").path("version").asLong()).isEqualTo(1);
    }

    @Test
    void patchVersusDeleteCannotOverwriteCommittedWinner() throws Exception {
        long owner = insertMember(false, "Asia/Seoul");
        long caregiver = insertMember(false, "Asia/Seoul");
        long baby = insertBaby(owner, false);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        long id = id(create(baby, owner, body(YESTERDAY, "61", "6", null)));

        List<Integer> outcomes = race(
                () -> perform(patch(url(baby) + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"memo\":\"edited\"}"), owner),
                () -> perform(delete(url(baby) + "/" + id).param("version", "0"), caregiver));

        assertThat(outcomes.stream().filter(code -> code == 200).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(code -> code == 404 || code == 409).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM growth_record WHERE growth_record_id = ?",
                Long.class, id)).isEqualTo(1);
        boolean deleted = jdbc.queryForObject("SELECT is_deleted FROM growth_record WHERE growth_record_id = ?",
                Boolean.class, id);
        if (deleted) {
            assertFailure(get(url(baby) + "/" + id), owner, 404);
        } else {
            assertThat(detail(baby, id, owner).path("data").path("memo").asText()).isEqualTo("edited");
        }
    }

    private JsonNode create(long babyId, long memberId, String body) throws Exception {
        JsonNode response = response(post(url(babyId)).contentType(MediaType.APPLICATION_JSON).content(body),
                memberId, 201);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("message").isNull()).isTrue();
        return response.path("data");
    }

    private JsonNode list(long babyId, long memberId) throws Exception {
        return response(get(url(babyId)), memberId, 200);
    }

    private JsonNode detail(long babyId, long recordId, long memberId) throws Exception {
        return response(get(url(babyId) + "/" + recordId), memberId, 200);
    }

    private JsonNode update(long babyId, long recordId, long memberId, String body) throws Exception {
        return response(patch(url(babyId) + "/" + recordId)
                .contentType(MediaType.APPLICATION_JSON).content(body), memberId, 200).path("data");
    }

    private void deleteRecord(long babyId, long recordId, long memberId, long version) throws Exception {
        JsonNode response = response(delete(url(babyId) + "/" + recordId)
                .param("version", Long.toString(version)), memberId, 200);
        assertThat(response.path("data").isNull()).isTrue();
    }

    private void assertFailure(MockHttpServletRequestBuilder builder, long memberId, int expected) throws Exception {
        JsonNode response = response(builder, memberId, expected);
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("data").isNull()).isTrue();
        assertThat(response.path("message").asText()).isNotBlank();
        assertThat(response.has("errorCode")).isFalse();
    }

    private JsonNode response(MockHttpServletRequestBuilder builder, long memberId, int expected) throws Exception {
        return mapper.readTree(mvc.perform(builder.header("Authorization", "Bearer " + token(memberId)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private int perform(MockHttpServletRequestBuilder builder, long memberId) throws Exception {
        return mvc.perform(builder.header("Authorization", "Bearer " + token(memberId)))
                .andReturn().getResponse().getStatus();
    }

    private List<Integer> race(CheckedStatus first, CheckedStatus second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var left = pool.submit(() -> {
                ready.countDown();
                start.await();
                return first.run();
            });
            var right = pool.submit(() -> {
                ready.countDown();
                start.await();
                return second.run();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS));
        }
    }

    @FunctionalInterface
    private interface CheckedStatus {
        int run() throws Exception;
    }

    private String url(long babyId) {
        return "/api/v1/babies/" + babyId + "/growth-records";
    }

    private long id(JsonNode record) {
        return record.path("growthRecordId").asLong();
    }

    private String body(String day, String height, String weight, String memo) {
        return "{\"measuredDate\":\"" + day + "\",\"heightCm\":" + height
                + ",\"weightKg\":" + weight + ",\"memo\":"
                + (memo == null ? "null" : "\"" + memo + "\"") + "}";
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
