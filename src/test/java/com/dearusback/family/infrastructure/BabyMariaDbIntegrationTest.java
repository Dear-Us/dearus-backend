package com.dearusback.family.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.DearUsBackApplication;
import com.dearusback.family.application.model.BabyImageUpload;
import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(classes = {DearUsBackApplication.class, BabyMariaDbIntegrationTest.StorageConfig.class})
class BabyMariaDbIntegrationTest {

    @Container
    static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.4")
            .withDatabaseName("dearus_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MARIADB::getJdbcUrl);
        properties.add("spring.datasource.username", MARIADB::getUsername);
        properties.add("spring.datasource.password", MARIADB::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired FakeBabyImageStorage storage;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    void flywayCreatesFamilyTablesAndConstraints() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '3' AND success = 1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = database() AND table_name IN ('baby', 'baby_member')", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = database() AND table_name = 'baby_invitation'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints "
                + "WHERE constraint_schema = database() AND table_name = 'baby_member' "
                + "AND constraint_type = 'UNIQUE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.key_column_usage "
                + "WHERE table_schema = database() AND table_name = 'baby_member' "
                + "AND referenced_table_name = 'baby'", Integer.class)).isEqualTo(1);
    }

    @Test
    void firstAndSecondBabyPersistOwnerAndInitializeSelectionOnlyOnce() throws Exception {
        long memberId = insertMember("Asia/Seoul", false);
        String token = token(memberId);
        JsonNode first = send(token, request("  도윤  ", LocalDate.now(ZoneId.of("Asia/Seoul")).toString(), "MALE"), null);
        assertThat(first.path("success").asBoolean()).isTrue();
        assertThat(first.path("message").isNull()).isTrue();
        assertThat(first.path("data").path("babyName").asText()).isEqualTo("도윤");
        assertThat(first.path("data").path("birthDate").asText())
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")).toString());
        assertThat(first.path("data").path("gender").asText()).isEqualTo("MALE");
        assertThat(first.path("data").path("profileImageUrl").isNull()).isTrue();
        assertThat(first.path("data").path("myRole").asText()).isEqualTo("OWNER");
        assertThat(first.has("errorCode")).isFalse();
        long firstId = first.path("data").path("babyId").asLong();
        assertThat(jdbc.queryForObject("SELECT baby_name FROM baby WHERE baby_id = ?", String.class, firstId))
                .isEqualTo("도윤");
        assertOwner(firstId, memberId);
        assertThat(selectedBaby(memberId)).isEqualTo(firstId);

        JsonNode second = send(token, request("예린", "2020-01-01", "FEMALE"), null);
        long secondId = second.path("data").path("babyId").asLong();
        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(second.path("data").path("gender").asText()).isEqualTo("FEMALE");
        assertOwner(secondId, memberId);
        assertThat(selectedBaby(memberId)).isEqualTo(firstId);
        assertThat(storage.stored).isEmpty();
    }

    @Test
    void invalidNamesDatesAndGendersReturn400WithoutWrites() throws Exception {
        long memberId = insertMember("Asia/Seoul", false);
        String token = token(memberId);
        long before = babyCount();
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        List<String> invalid = List.of(
                "{\"birthDate\":\"" + today + "\",\"gender\":\"MALE\"}",
                request("  ", today, "MALE"), request("a".repeat(51), today, "MALE"),
                "{\"babyName\":\"아기\",\"gender\":\"MALE\"}",
                request("아기", LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1).toString(), "MALE"),
                request("아기", "2026-02-30", "MALE"), request("아기", "2026-1-01", "MALE"),
                "{\"babyName\":\"아기\",\"birthDate\":\"" + today + "\"}",
                request("아기", today, "UNKNOWN"));
        for (String json : invalid) {
            JsonNode response = perform(token, json, null, 400);
            assertFailure(response);
        }
        assertThat(babyCount()).isEqualTo(before);
    }

    @Test
    void imageFormatsAndValidation() throws Exception {
        long memberId = insertMember("Asia/Seoul", false);
        String token = token(memberId);
        String json = request("아기", "2020-01-01", "FEMALE");
        byte[][] valid = {
                {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00},
                {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a},
                "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII)
        };
        String[] types = {"image/jpeg", "image/png", "image/webp"};
        for (int index = 0; index < valid.length; index++) {
            JsonNode response = send(token, json, image(types[index], valid[index]));
            assertThat(response.path("data").path("profileImageUrl").asText())
                    .startsWith("https://images.example/babies/profile/");
            assertOwner(response.path("data").path("babyId").asLong(), memberId);
        }
        assertThat(storage.stored).hasSize(3);
        long before = babyCount();
        assertFailure(perform(token, json, image("image/png", new byte[0]), 400));
        assertFailure(perform(token, json, image("text/plain", "hello".getBytes(StandardCharsets.UTF_8)), 400));
        assertFailure(perform(token, json, image("image/png", "not-png".getBytes(StandardCharsets.UTF_8)), 400));
        assertFailure(perform(token, json, image("image/png", new byte[BabyImageUpload.MAX_BYTES + 1]), 413));
        assertThat(babyCount()).isEqualTo(before);
    }

    @Test
    void storageFailureReturns503WithoutDatabaseChanges() throws Exception {
        long memberId = insertMember("Asia/Seoul", false);
        long before = babyCount();
        storage.failStore.set(true);
        assertFailure(perform(token(memberId), request("아기", "2020-01-01", "MALE"),
                image("image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}), 503));
        assertThat(babyCount()).isEqualTo(before);
        assertThat(selectedBaby(memberId)).isNull();
    }

    @Test
    void authenticationFailures() throws Exception {
        String json = request("아기", "2020-01-01", "MALE");
        assertFailure(perform(null, json, null, 401));
        assertFailure(perform("invalid.jwt", json, null, 401));
        assertFailure(perform(token(Long.MAX_VALUE), json, null, 401));
        assertFailure(perform(token(insertMember("Asia/Seoul", true)), json, null, 403));
    }

    @Test
    void missingRequestPartAndMalformedJsonReturn400() throws Exception {
        long memberId = insertMember("Asia/Seoul", false);
        mvc.perform(multipart("/api/v1/babies").header("Authorization", "Bearer " + token(memberId)))
                .andExpect(status().isBadRequest());
        assertFailure(perform(token(memberId), "{invalid", null, 400));
    }

    @Test
    void ownerInsertFailureRollsBackBaby() throws Exception {
        long memberId = insertMember("Asia/Seoul", false);
        long before = babyCount();
        jdbc.execute("CREATE TRIGGER baby02_reject_owner BEFORE INSERT ON baby_member "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced owner failure'");
        try {
            assertFailure(perform(token(memberId), request("아기", "2020-01-01", "MALE"), null, 500));
        } finally {
            jdbc.execute("DROP TRIGGER baby02_reject_owner");
        }
        assertThat(babyCount()).isEqualTo(before);
        assertThat(selectedBaby(memberId)).isNull();
    }

    @Test
    void selectionUpdateFailureRollsBackBabyAndOwnerAndCompensatesImage() throws Exception {
        long memberId = insertMember("Asia/Seoul", false);
        long babiesBefore = babyCount();
        long ownersBefore = ownerCount();
        jdbc.execute("CREATE TRIGGER baby02_reject_selection BEFORE UPDATE ON member "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced selection failure'");
        try {
            assertFailure(perform(token(memberId), request("아기", "2020-01-01", "MALE"),
                    image("image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}), 500));
        } finally {
            jdbc.execute("DROP TRIGGER baby02_reject_selection");
        }
        assertThat(babyCount()).isEqualTo(babiesBefore);
        assertThat(ownerCount()).isEqualTo(ownersBefore);
        assertThat(selectedBaby(memberId)).isNull();
        assertThat(storage.stored).hasSize(1);
        assertThat(storage.deleted).containsExactly(storage.stored.getFirst());
    }

    @Test
    void failedCompensationDoesNotMaskDatabaseFailure() throws Exception {
        long memberId = insertMember("Asia/Seoul", false);
        long before = babyCount();
        storage.failDelete.set(true);
        jdbc.execute("CREATE TRIGGER baby02_reject_selection BEFORE UPDATE ON member "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced selection failure'");
        try {
            assertFailure(perform(token(memberId), request("아기", "2020-01-01", "MALE"),
                    image("image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}), 500));
        } finally {
            jdbc.execute("DROP TRIGGER baby02_reject_selection");
        }
        assertThat(babyCount()).isEqualTo(before);
        assertThat(storage.stored).hasSize(1);
        assertThat(storage.deleted).isEmpty();
    }

    @Test
    void concurrentFirstRegistrationsBothPersistWithOnlyOneSelection() throws Exception {
        long memberId = insertMember("Asia/Seoul", false);
        String jwt = token(memberId);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<JsonNode> first = executor.submit(() -> send(jwt, request("첫째", "2020-01-01", "MALE"), null));
            Future<JsonNode> second = executor.submit(() -> send(jwt, request("둘째", "2021-01-01", "FEMALE"), null));
            long firstId = first.get(20, TimeUnit.SECONDS).path("data").path("babyId").asLong();
            long secondId = second.get(20, TimeUnit.SECONDS).path("data").path("babyId").asLong();
            assertThat(firstId).isNotEqualTo(secondId);
            assertOwner(firstId, memberId);
            assertOwner(secondId, memberId);
            assertThat(selectedBaby(memberId)).isIn(firstId, secondId);
        }
    }

    @Test
    void listBabiesFiltersStatusesDeletionAndOtherMembersAndSortsDeterministically() throws Exception {
        long currentMember = insertMember("Asia/Seoul", false);
        long otherMember = insertMember("Asia/Seoul", false);
        Instant firstTime = Instant.parse("2026-01-01T00:00:00Z");
        Instant secondTime = firstTime.plusSeconds(60);
        Instant sameTime = firstTime.plusSeconds(120);

        long ownerBaby = insertBaby(currentMember, "첫째", "2020-01-01", "MALE", null, false, firstTime);
        insertMembership(ownerBaby, currentMember, "OWNER", "ACTIVE");
        long caregiverBaby = insertBaby(otherMember, "둘째", "2021-02-02", "FEMALE",
                "https://images.example/babies/profile/second.jpg", false, secondTime);
        insertMembership(caregiverBaby, otherMember, "OWNER", "ACTIVE");
        insertMembership(caregiverBaby, currentMember, "CAREGIVER", "ACTIVE");

        long tieFirst = insertBaby(currentMember, "동일시각1", "2022-03-03", "MALE", null, false, sameTime);
        long tieSecond = insertBaby(currentMember, "동일시각2", "2023-04-04", "FEMALE", null, false, sameTime);
        insertMembership(tieFirst, currentMember, "OWNER", "ACTIVE");
        insertMembership(tieSecond, currentMember, "OWNER", "ACTIVE");

        long leftBaby = insertBaby(currentMember, "탈퇴", "2020-01-01", "MALE", null, false, firstTime);
        insertMembership(leftBaby, currentMember, "OWNER", "LEFT");
        long removedBaby = insertBaby(currentMember, "제외", "2020-01-01", "MALE", null, false, firstTime);
        insertMembership(removedBaby, currentMember, "OWNER", "REMOVED");
        long deletedBaby = insertBaby(currentMember, "삭제", "2020-01-01", "MALE", null, true, firstTime);
        insertMembership(deletedBaby, currentMember, "OWNER", "ACTIVE");
        long otherBaby = insertBaby(otherMember, "다른회원", "2020-01-01", "MALE", null, false, firstTime);
        insertMembership(otherBaby, otherMember, "OWNER", "ACTIVE");

        jdbc.update("UPDATE member SET last_selected_baby_id = ? WHERE member_id = ?", caregiverBaby, currentMember);
        JsonNode response = listBabies(token(currentMember), 200);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("message").isNull()).isTrue();
        assertThat(response.has("errorCode")).isFalse();
        JsonNode data = response.path("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isEqualTo(4);
        assertBabyResponse(data.get(0), ownerBaby, "첫째", "2020-01-01", "MALE", null, "OWNER");
        assertBabyResponse(data.get(1), caregiverBaby, "둘째", "2021-02-02", "FEMALE",
                "https://images.example/babies/profile/second.jpg", "CAREGIVER");
        assertBabyResponse(data.get(2), tieFirst, "동일시각1", "2022-03-03", "MALE", null, "OWNER");
        assertBabyResponse(data.get(3), tieSecond, "동일시각2", "2023-04-04", "FEMALE", null, "OWNER");
        assertThat(selectedBaby(currentMember)).isEqualTo(caregiverBaby);
    }

    @Test
    void listBabiesWithoutActiveMembershipReturnsEmptyArray() throws Exception {
        long currentMember = insertMember("Asia/Seoul", false);
        long otherMember = insertMember("Asia/Seoul", false);
        long baby = insertBaby(otherMember, "다른회원", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, otherMember, "OWNER", "ACTIVE");
        JsonNode response = listBabies(token(currentMember), 200);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("data").isArray()).isTrue();
        assertThat(response.path("data").size()).isZero();
        assertThat(response.path("message").isNull()).isTrue();
        assertThat(selectedBaby(currentMember)).isNull();
    }

    @Test
    void listBabiesChecksAuthenticationAndMemberStatus() throws Exception {
        assertFailure(listBabies(null, 401));
        assertFailure(listBabies("invalid.jwt", 401));
        assertFailure(listBabies(token(Long.MAX_VALUE), 401));
        assertFailure(listBabies(token(insertMember("Asia/Seoul", true)), 403));
    }

    @Test
    void detailReturnsOwnerAndCaregiverWithSharedResponseWithoutChangingState() throws Exception {
        long currentMember = insertMember("Asia/Seoul", false);
        long otherMember = insertMember("Asia/Seoul", false);
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        long ownerBaby = insertBaby(currentMember, "도윤", "2020-01-01", "MALE", null, false, createdAt);
        insertMembership(ownerBaby, currentMember, "OWNER", "ACTIVE");
        long caregiverBaby = insertBaby(otherMember, "서윤", "2021-02-02", "FEMALE",
                "https://images.example/babies/profile/second.jpg", false, createdAt);
        insertMembership(caregiverBaby, otherMember, "OWNER", "ACTIVE");
        insertMembership(caregiverBaby, currentMember, "CAREGIVER", "ACTIVE");
        jdbc.update("UPDATE member SET last_selected_baby_id = ? WHERE member_id = ?", ownerBaby, currentMember);

        var babyBefore = jdbc.queryForMap("SELECT baby_name, birth_date, gender, profile_image_url, "
                + "is_deleted, created_at, updated_at, deleted_at FROM baby WHERE baby_id = ?", caregiverBaby);
        var membershipBefore = jdbc.queryForMap("SELECT role, status, joined_at, ended_at "
                + "FROM baby_member WHERE baby_id = ? AND member_id = ?", caregiverBaby, currentMember);

        JsonNode owner = detailBaby(Long.toString(ownerBaby), token(currentMember), 200);
        assertSuccessDetail(owner);
        assertBabyResponse(owner.path("data"), ownerBaby, "도윤", "2020-01-01", "MALE", null, "OWNER");
        JsonNode caregiver = detailBaby(Long.toString(caregiverBaby), token(currentMember), 200);
        assertSuccessDetail(caregiver);
        assertBabyResponse(caregiver.path("data"), caregiverBaby, "서윤", "2021-02-02", "FEMALE",
                "https://images.example/babies/profile/second.jpg", "CAREGIVER");

        assertThat(selectedBaby(currentMember)).isEqualTo(ownerBaby);
        assertThat(jdbc.queryForMap("SELECT baby_name, birth_date, gender, profile_image_url, "
                + "is_deleted, created_at, updated_at, deleted_at FROM baby WHERE baby_id = ?", caregiverBaby))
                .isEqualTo(babyBefore);
        assertThat(jdbc.queryForMap("SELECT role, status, joined_at, ended_at "
                + "FROM baby_member WHERE baby_id = ? AND member_id = ?", caregiverBaby, currentMember))
                .isEqualTo(membershipBefore);
    }

    @Test
    void detailHidesMissingDeletedAndInaccessibleBabiesWithIdentical404() throws Exception {
        long currentMember = insertMember("Asia/Seoul", false);
        long otherMember = insertMember("Asia/Seoul", false);
        Instant now = Instant.now();
        long deleted = insertBaby(currentMember, "삭제", "2020-01-01", "MALE", null, true, now);
        insertMembership(deleted, currentMember, "OWNER", "ACTIVE");
        long noMembership = insertBaby(currentMember, "연결없음", "2020-01-01", "MALE", null, false, now);
        long left = insertBaby(currentMember, "탈퇴", "2020-01-01", "MALE", null, false, now);
        insertMembership(left, currentMember, "OWNER", "LEFT");
        long removed = insertBaby(currentMember, "제외", "2020-01-01", "MALE", null, false, now);
        insertMembership(removed, currentMember, "OWNER", "REMOVED");
        long otherBaby = insertBaby(otherMember, "다른회원", "2020-01-01", "MALE", null, false, now);
        insertMembership(otherBaby, otherMember, "OWNER", "ACTIVE");

        String jwt = token(currentMember);
        for (long babyId : new long[]{Long.MAX_VALUE, deleted, noMembership, left, removed, otherBaby}) {
            JsonNode response = detailBaby(Long.toString(babyId), jwt, 404);
            assertFailure(response);
            assertThat(response.path("message").asText()).isEqualTo("아기 정보를 찾을 수 없습니다.");
        }
        assertThat(selectedBaby(currentMember)).isNull();
    }

    @Test
    void detailRejectsInvalidPathIdsAs400() throws Exception {
        long currentMember = insertMember("Asia/Seoul", false);
        String jwt = token(currentMember);
        for (String invalid : List.of("0", "-1", "not-a-number", "9223372036854775808")) {
            assertFailure(detailBaby(invalid, jwt, 400));
        }
    }

    @Test
    void detailChecksAuthenticationAndMemberStatus() throws Exception {
        long currentMember = insertMember("Asia/Seoul", false);
        long baby = insertBaby(currentMember, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, currentMember, "OWNER", "ACTIVE");
        String id = Long.toString(baby);
        assertFailure(detailBaby(id, null, 401));
        assertFailure(detailBaby(id, "invalid.jwt", 401));
        assertFailure(detailBaby(id, token(Long.MAX_VALUE), 401));
        assertFailure(detailBaby(id, token(insertMember("Asia/Seoul", true)), 403));
    }

    private JsonNode detailBaby(String babyId, String token, int expectedStatus) throws Exception {
        var request = get("/api/v1/babies/{babyId}", babyId);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        String response = mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private void assertSuccessDetail(JsonNode response) {
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("message").isNull()).isTrue();
        assertThat(response.has("errorCode")).isFalse();
    }

    private long insertBaby(long creatorId, String name, String birthDate, String gender,
                            String imageUrl, boolean deleted, Instant createdAt) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO baby "
                    + "(created_by_member_id, baby_name, birth_date, gender, profile_image_url, "
                    + "is_deleted, created_at, updated_at, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"baby_id"});
            statement.setLong(1, creatorId);
            statement.setString(2, name);
            statement.setDate(3, Date.valueOf(birthDate));
            statement.setString(4, gender);
            statement.setString(5, imageUrl);
            statement.setBoolean(6, deleted);
            statement.setTimestamp(7, Timestamp.from(createdAt));
            statement.setTimestamp(8, Timestamp.from(createdAt));
            statement.setTimestamp(9, deleted ? Timestamp.from(createdAt.plusSeconds(1)) : null);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private void insertMembership(long babyId, long memberId, String role, String status) {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO baby_member (baby_id, member_id, role, status, joined_at, ended_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", babyId, memberId, role, status, Timestamp.from(now),
                "ACTIVE".equals(status) ? null : Timestamp.from(now.plusSeconds(1)));
    }

    private JsonNode listBabies(String token, int expectedStatus) throws Exception {
        var request = get("/api/v1/babies");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        String response = mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private void assertBabyResponse(JsonNode actual, long id, String name, String birthDate,
                                    String gender, String imageUrl, String role) {
        assertThat(actual.size()).isEqualTo(6);
        assertThat(actual.path("babyId").asLong()).isEqualTo(id);
        assertThat(actual.path("babyName").asText()).isEqualTo(name);
        assertThat(actual.path("birthDate").asText()).isEqualTo(birthDate);
        assertThat(actual.path("gender").asText()).isEqualTo(gender);
        if (imageUrl == null) {
            assertThat(actual.path("profileImageUrl").isNull()).isTrue();
        } else {
            assertThat(actual.path("profileImageUrl").asText()).isEqualTo(imageUrl);
        }
        assertThat(actual.path("myRole").asText()).isEqualTo(role);
        assertThat(actual.has("isSelected")).isFalse();
        assertThat(actual.has("errorCode")).isFalse();
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

    private String token(long memberId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("dearus")
                .subject(Long.toString(memberId)).issuedAt(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(300)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                claims)).getTokenValue();
    }

    private JsonNode send(String token, String json, MockMultipartFile image) throws Exception {
        return perform(token, json, image, 201);
    }

    private JsonNode perform(String token, String json, MockMultipartFile image, int expectedStatus) throws Exception {
        var request = multipart("/api/v1/babies")
                .file(new MockMultipartFile("request", "request.json", MediaType.APPLICATION_JSON_VALUE,
                        json.getBytes(StandardCharsets.UTF_8)));
        if (image != null) {
            request.file(image);
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        String response = mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private MockMultipartFile image(String type, byte[] bytes) {
        return new MockMultipartFile("profileImage", "untrusted-name", type, bytes);
    }

    private String request(String name, String date, String gender) {
        return "{\"babyName\":\"" + name + "\",\"birthDate\":\"" + date
                + "\",\"gender\":\"" + gender + "\"}";
    }

    private void assertFailure(JsonNode response) {
        assertThat(response.path("success").asBoolean()).isFalse();
        assertThat(response.path("data").isNull()).isTrue();
        assertThat(response.path("message").asText()).isNotBlank();
        assertThat(response.has("errorCode")).isFalse();
    }

    private void assertOwner(long babyId, long memberId) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM baby_member WHERE baby_id = ? AND member_id = ? "
                + "AND role = 'OWNER' AND status = 'ACTIVE' AND joined_at IS NOT NULL AND ended_at IS NULL", Integer.class,
                babyId, memberId)).isEqualTo(1);
    }

    private Long selectedBaby(long memberId) {
        return jdbc.queryForObject("SELECT last_selected_baby_id FROM member WHERE member_id = ?", Long.class,
                memberId);
    }

    private long babyCount() {
        return jdbc.queryForObject("SELECT count(*) FROM baby", Long.class);
    }

    private long ownerCount() {
        return jdbc.queryForObject("SELECT count(*) FROM baby_member", Long.class);
    }

    @TestConfiguration
    static class StorageConfig {
        @Bean
        @Primary
        FakeBabyImageStorage fakeBabyImageStorage() {
            return new FakeBabyImageStorage();
        }
    }

    static class FakeBabyImageStorage implements BabyProfileImageStoragePort {
        final List<String> stored = new CopyOnWriteArrayList<>();
        final List<String> deleted = new CopyOnWriteArrayList<>();
        final AtomicBoolean failStore = new AtomicBoolean();
        final AtomicBoolean failDelete = new AtomicBoolean();

        void reset() {
            stored.clear();
            deleted.clear();
            failStore.set(false);
            failDelete.set(false);
        }

        @Override
        public String store(BabyImageUpload image) {
            if (failStore.getAndSet(false)) {
                throw new IllegalStateException("storage unavailable");
            }
            String url = "https://images.example/babies/profile/" + UUID.randomUUID() + "." + image.extension();
            stored.add(url);
            return url;
        }

        @Override
        public void deleteOwnedImage(String imageUrl) {
            if (failDelete.getAndSet(false)) {
                throw new IllegalStateException("cleanup unavailable");
            }
            deleted.add(imageUrl);
        }
    }
}
