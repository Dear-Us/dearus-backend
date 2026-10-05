package com.dearusback.family.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.DearUsBackApplication;
import com.dearusback.family.application.model.BabyImageUpload;
import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
        properties.add("dearus.invitation.base-url", () -> "https://app.example.com/invite");
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
                .isEqualTo(1);
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

    @Test
    void ownerCanPatchFieldsIndividuallyAndTogetherWithoutChangingOwnershipOrSelection() throws Exception {
        long memberId = insertMember("Asia/Seoul", false);
        long babyId = insertBaby(memberId, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(babyId, memberId, "OWNER", "ACTIVE");
        jdbc.update("UPDATE member SET last_selected_baby_id = ? WHERE member_id = ?", babyId, memberId);
        String jwt = token(memberId);
        var membershipBefore = membershipRow(babyId, memberId);
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();

        JsonNode renamed = patchBaby(Long.toString(babyId), jwt, "{\"babyName\":\"  도윤이  \"}", null, 200);
        assertSuccessDetail(renamed);
        assertBabyResponse(renamed.path("data"), babyId, "도윤이", "2020-01-01", "MALE", null, "OWNER");
        JsonNode changedDate = patchBaby(Long.toString(babyId), jwt,
                "{\"birthDate\":\"" + today + "\"}", null, 200);
        assertBabyResponse(changedDate.path("data"), babyId, "도윤이", today, "MALE", null, "OWNER");
        JsonNode changedGender = patchBaby(Long.toString(babyId), jwt, "{\"gender\":\"FEMALE\"}", null, 200);
        assertBabyResponse(changedGender.path("data"), babyId, "도윤이", today, "FEMALE", null, "OWNER");
        JsonNode multiple = patchBaby(Long.toString(babyId), jwt,
                "{\"babyName\":\"예린\",\"birthDate\":\"2019-01-01\",\"gender\":\"MALE\"}",
                null, 200);
        assertBabyResponse(multiple.path("data"), babyId, "예린", "2019-01-01", "MALE", null, "OWNER");
        JsonNode sameValue = patchBaby(Long.toString(babyId), jwt, "{\"babyName\":\"예린\"}", null, 200);
        assertBabyResponse(sameValue.path("data"), babyId, "예린", "2019-01-01", "MALE", null, "OWNER");
        assertBabyResponse(detailBaby(Long.toString(babyId), jwt, 200).path("data"),
                babyId, "예린", "2019-01-01", "MALE", null, "OWNER");
        assertThat(jdbc.queryForObject("SELECT created_by_member_id FROM baby WHERE baby_id = ?", Long.class, babyId))
                .isEqualTo(memberId);
        assertThat(membershipRow(babyId, memberId)).isEqualTo(membershipBefore);
        assertThat(selectedBaby(memberId)).isEqualTo(babyId);
        assertThat(storage.stored).isEmpty();
    }

    @Test
    void patchValidationRejectsEmptyAndMalformedUpdatesWithoutChangingDatabase() throws Exception {
        long memberId = insertMember("Asia/Seoul", false);
        long babyId = insertBaby(memberId, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(babyId, memberId, "OWNER", "ACTIVE");
        String jwt = token(memberId);
        var before = babyRow(babyId);
        for (String json : List.of("{}", "{\"removeProfileImage\":false}",
                "{\"babyName\":\"   \"}", "{\"babyName\":\"" + "a".repeat(51) + "\"}",
                "{\"birthDate\":\"" + LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1) + "\"}",
                "{\"birthDate\":\"2026-02-30\"}", "{\"gender\":\"OTHER\"}")) {
            assertFailure(patchBaby(Long.toString(babyId), jwt, json, null, 400));
        }
        assertFailure(patchBaby("0", jwt, "{\"babyName\":\"변경\"}", null, 400));
        assertFailure(patchBaby("invalid", jwt, "{\"babyName\":\"변경\"}", null, 400));
        assertFailure(patchBaby(Long.toString(babyId), jwt, "{\"removeProfileImage\":true}",
                image("image/jpeg", jpeg()), 400));
        assertThat(babyRow(babyId)).isEqualTo(before);
        assertThat(storage.stored).isEmpty();
    }

    @Test
    void onlyActiveOwnerCanPatchAndDeniedRequestsNeverUpload() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long caregiver = insertMember("Asia/Seoul", false);
        long outsider = insertMember("Asia/Seoul", false);
        Instant now = Instant.now();
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, now);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        long left = insertBaby(owner, "LEFT", "2020-01-01", "MALE", null, false, now);
        insertMembership(left, owner, "OWNER", "LEFT");
        long removed = insertBaby(owner, "REMOVED", "2020-01-01", "MALE", null, false, now);
        insertMembership(removed, owner, "OWNER", "REMOVED");
        long deleted = insertBaby(owner, "삭제", "2020-01-01", "MALE", null, true, now);
        insertMembership(deleted, owner, "OWNER", "ACTIVE");
        long noMembership = insertBaby(owner, "연결없음", "2020-01-01", "MALE", null, false, now);
        long otherBaby = insertBaby(outsider, "다른가족", "2020-01-01", "MALE", null, false, now);
        insertMembership(otherBaby, outsider, "OWNER", "ACTIVE");
        MockMultipartFile upload = image("image/jpeg", jpeg());

        assertFailure(patchBaby(Long.toString(baby), token(caregiver), "{}", upload, 403));
        for (long inaccessible : new long[]{Long.MAX_VALUE, left, removed, deleted, noMembership, otherBaby}) {
            JsonNode response = patchBaby(Long.toString(inaccessible), token(owner), "{}", upload, 404);
            assertFailure(response);
            assertThat(response.path("message").asText()).isEqualTo("아기 정보를 찾을 수 없습니다.");
        }
        assertThat(storage.stored).isEmpty();
        assertThat(babyRow(baby).get("baby_name")).isEqualTo("도윤");
    }

    @Test
    void patchRequiresValidAuthenticationAndActiveMember() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        String id = Long.toString(baby);
        assertFailure(patchBaby(id, null, "{\"babyName\":\"변경\"}", null, 401));
        assertFailure(patchBaby(id, "invalid.jwt", "{\"babyName\":\"변경\"}", null, 401));
        assertFailure(patchBaby(id, token(Long.MAX_VALUE), "{\"babyName\":\"변경\"}", null, 401));
        assertFailure(patchBaby(id, token(insertMember("Asia/Seoul", true)),
                "{\"babyName\":\"변경\"}", null, 403));
        assertThat(storage.stored).isEmpty();
    }

    @Test
    void patchAcceptsSupportedImagesAndDeletesPreviousOnlyAfterCommit() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        String oldUrl = "https://images.example/babies/profile/old.jpg";
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", oldUrl, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        String jwt = token(owner);
        String[] types = {"image/jpeg", "image/png", "image/webp"};
        byte[][] bytes = {jpeg(), png(), "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII)};
        String previous = oldUrl;
        for (int index = 0; index < types.length; index++) {
            JsonNode response = patchBaby(Long.toString(baby), jwt, "{}", image(types[index], bytes[index]), 200);
            assertSuccessDetail(response);
            String current = response.path("data").path("profileImageUrl").asText();
            assertThat(current).startsWith("https://images.example/babies/profile/");
            assertThat(current).endsWith(new String[]{".jpg", ".png", ".webp"}[index]);
            assertThat(jdbc.queryForObject("SELECT profile_image_url FROM baby WHERE baby_id = ?", String.class, baby))
                    .isEqualTo(current);
            assertThat(storage.deleted).contains(previous);
            previous = current;
        }
        assertThat(storage.stored).hasSize(3);
        assertThat(storage.deleted).hasSize(3);
        assertThat(storage.deletedDuringTransaction).containsExactly(false, false, false);
        assertBabyResponse(detailBaby(Long.toString(baby), jwt, 200).path("data"),
                baby, "도윤", "2020-01-01", "MALE", previous, "OWNER");
    }

    @Test
    void removeImageClearsUrlAndCleanupFailureDoesNotRollback() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        String oldUrl = "https://images.example/babies/profile/old.jpg";
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", oldUrl, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        storage.failDelete.set(true);
        JsonNode removed = patchBaby(Long.toString(baby), token(owner),
                "{\"removeProfileImage\":true}", null, 200);
        assertBabyResponse(removed.path("data"), baby, "도윤", "2020-01-01", "MALE", null, "OWNER");
        assertThat(jdbc.queryForObject("SELECT profile_image_url FROM baby WHERE baby_id = ?", String.class, baby))
                .isNull();
        assertThat(storage.deleted).isEmpty();

        JsonNode again = patchBaby(Long.toString(baby), token(owner),
                "{\"removeProfileImage\":true}", null, 200);
        assertBabyResponse(again.path("data"), baby, "도윤", "2020-01-01", "MALE", null, "OWNER");
        assertThat(storage.deleted).isEmpty();
    }

    @Test
    void imageValidationAndUploadFailureDoNotChangeBaby() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        String jwt = token(owner);
        var before = babyRow(baby);
        assertFailure(patchBaby(Long.toString(baby), jwt, "{}", image("image/png", new byte[0]), 400));
        assertFailure(patchBaby(Long.toString(baby), jwt, "{}", image("text/plain", "text".getBytes()), 400));
        assertFailure(patchBaby(Long.toString(baby), jwt, "{}", image("image/png", "not-png".getBytes()), 400));
        assertFailure(patchBaby(Long.toString(baby), jwt, "{}",
                image("image/png", new byte[BabyImageUpload.MAX_BYTES + 1]), 413));
        storage.failStore.set(true);
        JsonNode failure = patchBaby(Long.toString(baby), jwt, "{}", image("image/jpeg", jpeg()), 503);
        assertFailure(failure);
        assertThat(failure.path("message").asText()).isEqualTo("아기 프로필 이미지를 처리할 수 없습니다.");
        assertThat(babyRow(baby)).isEqualTo(before);
        assertThat(storage.stored).isEmpty();
    }

    @Test
    void uploadedImageIsCompensatedIfDatabaseUpdateFails() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        String oldUrl = "https://images.example/babies/profile/old.jpg";
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", oldUrl, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        var before = babyRow(baby);
        jdbc.execute("CREATE TRIGGER baby04_reject_update BEFORE UPDATE ON baby "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced update failure'");
        try {
            assertFailure(patchBaby(Long.toString(baby), token(owner), "{\"babyName\":\"변경\"}",
                    image("image/jpeg", jpeg()), 500));
        } finally {
            jdbc.execute("DROP TRIGGER baby04_reject_update");
        }
        assertThat(babyRow(baby)).isEqualTo(before);
        assertThat(storage.stored).hasSize(1);
        assertThat(storage.deleted).containsExactly(storage.stored.getFirst());
        assertThat(storage.deleted).doesNotContain(oldUrl);
    }

    @Test
    void authorizationIsRecheckedAfterUploadBeforeDatabaseMutation() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        var before = babyRow(baby);
        storage.afterStore.set(() -> jdbc.update("UPDATE baby_member SET role = 'CAREGIVER' "
                + "WHERE baby_id = ? AND member_id = ?", baby, owner));
        assertFailure(patchBaby(Long.toString(baby), token(owner), "{}", image("image/jpeg", jpeg()), 403));
        assertThat(babyRow(baby)).isEqualTo(before);
        assertThat(storage.stored).hasSize(1);
        assertThat(storage.deleted).containsExactly(storage.stored.getFirst());
    }

    @Test
    void memberCanSelectActiveOwnerAndCaregiverBabiesAndRepeatSameSelection() throws Exception {
        long currentMember = insertMember("Asia/Seoul", false);
        long otherMember = insertMember("Asia/Seoul", false);
        Instant now = Instant.now();
        long ownerBaby = insertBaby(currentMember, "도윤", "2020-01-01", "MALE", null, false, now);
        insertMembership(ownerBaby, currentMember, "OWNER", "ACTIVE");
        long caregiverBaby = insertBaby(otherMember, "서윤", "2021-02-02", "FEMALE", null, false, now);
        insertMembership(caregiverBaby, otherMember, "OWNER", "ACTIVE");
        insertMembership(caregiverBaby, currentMember, "CAREGIVER", "ACTIVE");
        var ownerBefore = babyRow(ownerBaby);
        var caregiverBefore = babyRow(caregiverBaby);
        var ownerMembershipBefore = membershipRow(ownerBaby, currentMember);
        var caregiverMembershipBefore = membershipRow(caregiverBaby, currentMember);
        String jwt = token(currentMember);

        assertSelectionChanged(changeSelection(jwt, "{\"babyId\":" + ownerBaby + "}", 200), ownerBaby);
        assertThat(selectedBaby(currentMember)).isEqualTo(ownerBaby);
        assertSelectionChanged(changeSelection(jwt, "{\"babyId\":" + caregiverBaby + "}", 200), caregiverBaby);
        assertThat(selectedBaby(currentMember)).isEqualTo(caregiverBaby);
        assertSelectionChanged(changeSelection(jwt, "{\"babyId\":" + caregiverBaby + "}", 200), caregiverBaby);
        assertThat(selectedBaby(currentMember)).isEqualTo(caregiverBaby);
        assertThat(myProfile(jwt).path("data").path("lastSelectedBabyId").asLong()).isEqualTo(caregiverBaby);

        assertThat(selectedBaby(otherMember)).isNull();
        assertThat(babyRow(ownerBaby)).isEqualTo(ownerBefore);
        assertThat(babyRow(caregiverBaby)).isEqualTo(caregiverBefore);
        assertThat(membershipRow(ownerBaby, currentMember)).isEqualTo(ownerMembershipBefore);
        assertThat(membershipRow(caregiverBaby, currentMember)).isEqualTo(caregiverMembershipBefore);
    }

    @Test
    void memberSelectionHidesInaccessibleBabiesWithIdentical404AndKeepsPreviousChoice() throws Exception {
        long currentMember = insertMember("Asia/Seoul", false);
        long otherMember = insertMember("Asia/Seoul", false);
        Instant now = Instant.now();
        long selected = insertBaby(currentMember, "선택됨", "2020-01-01", "MALE", null, false, now);
        insertMembership(selected, currentMember, "OWNER", "ACTIVE");
        jdbc.update("UPDATE member SET last_selected_baby_id = ? WHERE member_id = ?", selected, currentMember);
        long deleted = insertBaby(currentMember, "삭제", "2020-01-01", "MALE", null, true, now);
        insertMembership(deleted, currentMember, "OWNER", "ACTIVE");
        long noMembership = insertBaby(currentMember, "관계없음", "2020-01-01", "MALE", null, false, now);
        long left = insertBaby(currentMember, "나감", "2020-01-01", "MALE", null, false, now);
        insertMembership(left, currentMember, "OWNER", "LEFT");
        long removed = insertBaby(currentMember, "제외", "2020-01-01", "MALE", null, false, now);
        insertMembership(removed, currentMember, "OWNER", "REMOVED");
        long otherBaby = insertBaby(otherMember, "다른회원", "2020-01-01", "MALE", null, false, now);
        insertMembership(otherBaby, otherMember, "OWNER", "ACTIVE");
        var selectedBabyBefore = babyRow(selected);
        var selectedMembershipBefore = membershipRow(selected, currentMember);

        for (long inaccessible : new long[]{Long.MAX_VALUE, deleted, noMembership, left, removed, otherBaby}) {
            JsonNode response = changeSelection(token(currentMember), "{\"babyId\":" + inaccessible + "}", 404);
            assertFailure(response);
            assertThat(response.path("message").asText()).isEqualTo("아기 정보를 찾을 수 없습니다.");
            assertThat(selectedBaby(currentMember)).isEqualTo(selected);
        }
        assertThat(babyRow(selected)).isEqualTo(selectedBabyBefore);
        assertThat(membershipRow(selected, currentMember)).isEqualTo(selectedMembershipBefore);
        assertThat(selectedBaby(otherMember)).isNull();
    }

    @Test
    void memberSelectionRejectsMissingNonPositiveAndMalformedIds() throws Exception {
        long memberId = insertMember("Asia/Seoul", false);
        String jwt = token(memberId);
        for (String json : List.of("{}", "{\"babyId\":null}", "{\"babyId\":0}",
                "{\"babyId\":-1}", "{invalid", "{\"babyId\":\"not-a-number\"}")) {
            assertFailure(changeSelection(jwt, json, 400));
        }
        assertThat(selectedBaby(memberId)).isNull();
    }

    @Test
    void memberSelectionRequiresAuthenticationAndActiveMember() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        String json = "{\"babyId\":" + baby + "}";
        assertFailure(changeSelection(null, json, 401));
        assertFailure(changeSelection("invalid.jwt", json, 401));
        assertFailure(changeSelection(token(Long.MAX_VALUE), json, 401));
        assertFailure(changeSelection(token(insertMember("Asia/Seoul", true)), json, 403));
        assertThat(selectedBaby(owner)).isNull();
    }

    @Test
    void memberSelectionRollsBackWhenMemberUpdateFails() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        var babyBefore = babyRow(baby);
        var membershipBefore = membershipRow(baby, owner);
        jdbc.execute("CREATE TRIGGER member03_reject_update BEFORE UPDATE ON member "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced selection failure'");
        try {
            assertFailure(changeSelection(token(owner), "{\"babyId\":" + baby + "}", 500));
        } finally {
            jdbc.execute("DROP TRIGGER member03_reject_update");
        }
        assertThat(selectedBaby(owner)).isNull();
        assertThat(babyRow(baby)).isEqualTo(babyBefore);
        assertThat(membershipRow(baby, owner)).isEqualTo(membershipBefore);
    }

    @Test
    void ownerDeletesBabyAndClearsOnlyMatchingSelections() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long caregiver = insertMember("Asia/Seoul", false);
        long otherCaregiver = insertMember("Asia/Seoul", false);
        long unrelated = insertMember("Asia/Seoul", false);
        Instant now = Instant.now();
        String imageUrl = "https://images.example/babies/profile/deleted.jpg";
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", imageUrl, false, now);
        long anotherBaby = insertBaby(otherCaregiver, "서윤", "2021-01-01", "FEMALE", null, false, now);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        insertMembership(baby, otherCaregiver, "CAREGIVER", "ACTIVE");
        insertMembership(baby, unrelated, "CAREGIVER", "LEFT");
        long removedMember = insertMember("Asia/Seoul", false);
        insertMembership(baby, removedMember, "CAREGIVER", "REMOVED");
        insertMembership(anotherBaby, otherCaregiver, "OWNER", "ACTIVE");
        jdbc.update("UPDATE member SET last_selected_baby_id = ? WHERE member_id IN (?, ?)", baby, owner, caregiver);
        jdbc.update("UPDATE member SET last_selected_baby_id = ? WHERE member_id = ?", anotherBaby, otherCaregiver);
        var leftBefore = membershipRow(baby, unrelated);
        var removedBefore = membershipRow(baby, removedMember);
        var otherBabyBefore = babyRow(anotherBaby);
        String jwt = token(owner);

        assertDeleted(deleteBaby(Long.toString(baby), jwt, 200));

        var row = babyRow(baby);
        assertThat(row.get("is_deleted")).isEqualTo(true);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("profile_image_url")).isNull();
        assertThat(row.get("created_by_member_id")).isEqualTo(owner);
        assertThat(row.get("baby_name")).isEqualTo("도윤");
        for (long activeMember : new long[]{owner, caregiver, otherCaregiver}) {
            var membership = membershipRow(baby, activeMember);
            assertThat(membership.get("status")).isEqualTo("REMOVED");
            assertThat(membership.get("ended_at")).isNotNull();
        }
        assertThat(membershipRow(baby, owner).get("role")).isEqualTo("OWNER");
        assertThat(membershipRow(baby, caregiver).get("role")).isEqualTo("CAREGIVER");
        assertThat(membershipRow(baby, unrelated)).isEqualTo(leftBefore);
        assertThat(membershipRow(baby, removedMember)).isEqualTo(removedBefore);
        assertThat(selectedBaby(owner)).isNull();
        assertThat(selectedBaby(caregiver)).isNull();
        assertThat(selectedBaby(otherCaregiver)).isEqualTo(anotherBaby);
        assertThat(selectedBaby(unrelated)).isNull();
        assertThat(babyRow(anotherBaby)).isEqualTo(otherBabyBefore);
        assertThat(storage.deleted).containsExactly(imageUrl);
        assertThat(storage.deletedDuringTransaction).containsExactly(false);
        assertThat(listBabies(jwt, 200).path("data").size()).isZero();
        assertFailure(detailBaby(Long.toString(baby), jwt, 404));
        assertFailure(changeSelection(jwt, "{\"babyId\":" + baby + "}", 404));
        assertFailure(deleteBaby(Long.toString(baby), jwt, 404));
    }

    @Test
    void deletionRequiresActiveOwnerAndHidesInaccessibleBabies() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long caregiver = insertMember("Asia/Seoul", false);
        long outsider = insertMember("Asia/Seoul", false);
        long left = insertMember("Asia/Seoul", false);
        long removed = insertMember("Asia/Seoul", false);
        Instant now = Instant.now();
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, now);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        insertMembership(baby, left, "CAREGIVER", "LEFT");
        insertMembership(baby, removed, "CAREGIVER", "REMOVED");
        long deleted = insertBaby(owner, "삭제", "2020-01-01", "MALE", null, true, now);
        insertMembership(deleted, owner, "OWNER", "ACTIVE");
        var before = babyRow(baby);
        var ownerBefore = membershipRow(baby, owner);

        assertFailure(deleteBaby(Long.toString(baby), token(caregiver), 403));
        for (long member : new long[]{outsider, left, removed}) {
            JsonNode response = deleteBaby(Long.toString(baby), token(member), 404);
            assertFailure(response);
            assertThat(response.path("message").asText()).isEqualTo("아기 정보를 찾을 수 없습니다.");
        }
        for (long unavailable : new long[]{Long.MAX_VALUE, deleted}) {
            JsonNode response = deleteBaby(Long.toString(unavailable), token(owner), 404);
            assertFailure(response);
            assertThat(response.path("message").asText()).isEqualTo("아기 정보를 찾을 수 없습니다.");
        }
        assertThat(babyRow(baby)).isEqualTo(before);
        assertThat(membershipRow(baby, owner)).isEqualTo(ownerBefore);
    }

    @Test
    void deletionValidatesPathAndAuthentication() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        for (String invalid : List.of("0", "-1", "not-a-number")) {
            assertFailure(deleteBaby(invalid, token(owner), 400));
        }
        assertFailure(deleteBaby(Long.toString(baby), null, 401));
        assertFailure(deleteBaby(Long.toString(baby), "invalid.jwt", 401));
        assertFailure(deleteBaby(Long.toString(baby), token(Long.MAX_VALUE), 401));
        assertFailure(deleteBaby(Long.toString(baby), token(insertMember("Asia/Seoul", true)), 403));
        assertThat(babyRow(baby).get("is_deleted")).isEqualTo(false);
        assertOwner(baby, owner);
    }

    @Test
    void membershipFailureRollsBackBabyAndKeepsImage() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        String imageUrl = "https://images.example/babies/profile/keep.jpg";
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", imageUrl, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        var babyBefore = babyRow(baby);
        var membershipBefore = membershipRow(baby, owner);
        jdbc.execute("CREATE TRIGGER baby05_reject_membership BEFORE UPDATE ON baby_member "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced membership failure'");
        try {
            assertFailure(deleteBaby(Long.toString(baby), token(owner), 500));
        } finally {
            jdbc.execute("DROP TRIGGER baby05_reject_membership");
        }
        assertThat(babyRow(baby)).isEqualTo(babyBefore);
        assertThat(membershipRow(baby, owner)).isEqualTo(membershipBefore);
        assertThat(storage.deleted).isEmpty();
    }

    @Test
    void selectedMemberFailureRollsBackBabyAndMembershipAndKeepsImage() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        String imageUrl = "https://images.example/babies/profile/keep.jpg";
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", imageUrl, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        jdbc.update("UPDATE member SET last_selected_baby_id = ? WHERE member_id = ?", baby, owner);
        var babyBefore = babyRow(baby);
        var membershipBefore = membershipRow(baby, owner);
        jdbc.execute("CREATE TRIGGER baby05_reject_member BEFORE UPDATE ON member "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced selection clear failure'");
        try {
            assertFailure(deleteBaby(Long.toString(baby), token(owner), 500));
        } finally {
            jdbc.execute("DROP TRIGGER baby05_reject_member");
        }
        assertThat(babyRow(baby)).isEqualTo(babyBefore);
        assertThat(membershipRow(baby, owner)).isEqualTo(membershipBefore);
        assertThat(selectedBaby(owner)).isEqualTo(baby);
        assertThat(storage.deleted).isEmpty();
    }

    @Test
    void storageCleanupFailureDoesNotUndoCommittedDeletion() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE",
                "https://images.example/babies/profile/fail.jpg", false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        jdbc.update("UPDATE member SET last_selected_baby_id = ? WHERE member_id = ?", baby, owner);
        storage.failDelete.set(true);

        assertDeleted(deleteBaby(Long.toString(baby), token(owner), 200));

        assertThat(babyRow(baby).get("is_deleted")).isEqualTo(true);
        assertThat(membershipRow(baby, owner).get("status")).isEqualTo("REMOVED");
        assertThat(selectedBaby(owner)).isNull();
        assertThat(storage.deleted).isEmpty();
        assertThat(storage.deletedDuringTransaction).containsExactly(false);
    }

    @Test
    void concurrentDeletesOnlyDeleteOnce() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        String jwt = token(owner);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> deleteStatus(baby, jwt));
            Future<Integer> second = executor.submit(() -> deleteStatus(baby, jwt));
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 404);
        }
        assertThat(babyRow(baby).get("is_deleted")).isEqualTo(true);
        assertThat(membershipRow(baby, owner).get("status")).isEqualTo("REMOVED");
    }

    @Test
    void concurrentPatchCannotRestoreDeletedBaby() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        String jwt = token(owner);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> deletion = executor.submit(() -> deleteStatus(baby, jwt));
            Future<Integer> update = executor.submit(() -> {
                var request = multipart(HttpMethod.PATCH, "/api/v1/babies/{babyId}", baby)
                        .file(new MockMultipartFile("request", "request.json", MediaType.APPLICATION_JSON_VALUE,
                                "{\"babyName\":\"수정됨\"}".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + jwt);
                return mvc.perform(request).andReturn().getResponse().getStatus();
            });
            assertThat(deletion.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(update.get(10, TimeUnit.SECONDS)).isIn(200, 404);
        }
        assertThat(babyRow(baby).get("is_deleted")).isEqualTo(true);
        assertThat(membershipRow(baby, owner).get("status")).isEqualTo("REMOVED");
        assertFailure(detailBaby(Long.toString(baby), jwt, 404));
    }

    @Test
    void ownerAndCaregiverSeeOnlyActiveMembersInStableOrderWithoutStateChanges() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long later = insertMember("Asia/Seoul", false);
        long tiedFirst = insertMember("Asia/Seoul", false);
        long tiedSecond = insertMember("Asia/Seoul", false);
        long left = insertMember("Asia/Seoul", false);
        long removed = insertMember("Asia/Seoul", false);
        long other = insertMember("Asia/Seoul", false);
        String ownerImage = "https://images.example/members/owner.png";
        jdbc.update("UPDATE member SET member_name = ?, profile_image_url = ? WHERE member_id = ?",
                "김소은", ownerImage, owner);
        jdbc.update("UPDATE member SET member_name = ? WHERE member_id = ?", "늦은 보호자", later);
        jdbc.update("UPDATE member SET member_name = ? WHERE member_id = ?", "첫 동시 보호자", tiedFirst);
        jdbc.update("UPDATE member SET member_name = ? WHERE member_id = ?", "둘째 동시 보호자", tiedSecond);
        Instant now = Instant.now();
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, now);
        long otherBaby = insertBaby(other, "서윤", "2021-01-01", "FEMALE", null, false, now);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, later, "CAREGIVER", "ACTIVE");
        insertMembership(baby, tiedFirst, "CAREGIVER", "ACTIVE");
        insertMembership(baby, tiedSecond, "CAREGIVER", "ACTIVE");
        insertMembership(baby, left, "CAREGIVER", "LEFT");
        insertMembership(baby, removed, "CAREGIVER", "REMOVED");
        insertMembership(otherBaby, other, "OWNER", "ACTIVE");
        Instant tiedAt = now.minusSeconds(3600);
        jdbc.update("UPDATE baby_member SET joined_at = ? WHERE baby_id = ? AND member_id IN (?, ?)",
                Timestamp.from(tiedAt), baby, tiedFirst, tiedSecond);
        jdbc.update("UPDATE baby_member SET joined_at = ? WHERE baby_id = ? AND member_id = ?",
                Timestamp.from(now), baby, later);
        jdbc.update("UPDATE member SET last_selected_baby_id = ? WHERE member_id = ?", baby, owner);
        var babyBefore = babyRow(baby);
        var ownerBefore = memberStateRow(owner);
        var caregiverBefore = memberStateRow(tiedFirst);
        var membershipBefore = membershipRow(baby, tiedFirst);

        JsonNode fromOwner = listBabyMembers(Long.toString(baby), token(owner), 200);
        JsonNode fromCaregiver = listBabyMembers(Long.toString(baby), token(tiedFirst), 200);

        assertThat(fromOwner).isEqualTo(fromCaregiver);
        assertThat(fromOwner.size()).isEqualTo(3);
        assertThat(fromOwner.path("success").asBoolean()).isTrue();
        assertThat(fromOwner.path("message").isNull()).isTrue();
        assertThat(fromOwner.has("errorCode")).isFalse();
        JsonNode data = fromOwner.path("data");
        assertThat(data.size()).isEqualTo(4);
        assertMemberSummary(data.get(0), owner, "김소은", ownerImage, "OWNER");
        assertMemberSummary(data.get(1), tiedFirst, "첫 동시 보호자", null, "CAREGIVER");
        assertMemberSummary(data.get(2), tiedSecond, "둘째 동시 보호자", null, "CAREGIVER");
        assertMemberSummary(data.get(3), later, "늦은 보호자", null, "CAREGIVER");
        assertThat(babyRow(baby)).isEqualTo(babyBefore);
        assertThat(memberStateRow(owner)).isEqualTo(ownerBefore);
        assertThat(memberStateRow(tiedFirst)).isEqualTo(caregiverBefore);
        assertThat(membershipRow(baby, tiedFirst)).isEqualTo(membershipBefore);
    }

    @Test
    void babyMemberListHidesAllInaccessibleBabiesWithSame404() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long outsider = insertMember("Asia/Seoul", false);
        long left = insertMember("Asia/Seoul", false);
        long removed = insertMember("Asia/Seoul", false);
        Instant now = Instant.now();
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, now);
        long deleted = insertBaby(owner, "삭제", "2020-01-01", "MALE", null, true, now);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, left, "CAREGIVER", "LEFT");
        insertMembership(baby, removed, "CAREGIVER", "REMOVED");
        insertMembership(deleted, owner, "OWNER", "ACTIVE");
        var before = babyRow(baby);
        for (long requester : new long[]{outsider, left, removed}) {
            JsonNode response = listBabyMembers(Long.toString(baby), token(requester), 404);
            assertFailure(response);
            assertThat(response.path("message").asText()).isEqualTo("아기 정보를 찾을 수 없습니다.");
        }
        for (long unavailable : new long[]{Long.MAX_VALUE, deleted}) {
            JsonNode response = listBabyMembers(Long.toString(unavailable), token(owner), 404);
            assertFailure(response);
            assertThat(response.path("message").asText()).isEqualTo("아기 정보를 찾을 수 없습니다.");
        }
        assertThat(babyRow(baby)).isEqualTo(before);
    }

    @Test
    void babyMemberListValidatesRequesterAndPath() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        for (String invalid : List.of("0", "-1", "not-a-number")) {
            assertFailure(listBabyMembers(invalid, token(owner), 400));
        }
        assertFailure(listBabyMembers(Long.toString(baby), null, 401));
        assertFailure(listBabyMembers(Long.toString(baby), "invalid.jwt", 401));
        assertFailure(listBabyMembers(Long.toString(baby), token(Long.MAX_VALUE), 401));
        assertFailure(listBabyMembers(Long.toString(baby), token(insertMember("Asia/Seoul", true)), 403));
    }

    @Test
    void babyMemberListFailsWhenActiveMembershipPointsToWithdrawnMember() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long withdrawn = insertMember("Asia/Seoul", true);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, withdrawn, "CAREGIVER", "ACTIVE");
        var babyBefore = babyRow(baby);
        var withdrawnBefore = memberStateRow(withdrawn);
        var membershipBefore = membershipRow(baby, withdrawn);

        assertFailure(listBabyMembers(Long.toString(baby), token(owner), 500));

        assertThat(babyRow(baby)).isEqualTo(babyBefore);
        assertThat(memberStateRow(withdrawn)).isEqualTo(withdrawnBefore);
        assertThat(membershipRow(baby, withdrawn)).isEqualTo(membershipBefore);
    }

    @Test
    void invitationMigrationCreatesHashedTokenTableAndUniqueConstraint() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history "
                + "WHERE version = '4' AND success = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = database() AND table_name = 'baby_invitation'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns "
                + "WHERE table_schema = database() AND table_name = 'baby_invitation' "
                + "AND column_name IN ('raw_token', 'token')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.statistics "
                + "WHERE table_schema = database() AND table_name = 'baby_invitation' "
                + "AND index_name = 'uk_baby_invitation_token_hash' AND non_unique = 0", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void ownerCreatesMultiplePendingCaregiverInvitationsWithSecureHashedTokens() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long caregiver = insertMember("Asia/Seoul", false);
        Instant now = Instant.now();
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, now);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        jdbc.update("UPDATE member SET last_selected_baby_id = ? WHERE member_id = ?", baby, owner);
        var babyBefore = babyRow(baby);
        var ownerBefore = membershipRow(baby, owner);
        var caregiverBefore = membershipRow(baby, caregiver);
        String jwt = token(owner);

        JsonNode first = createInvitation(Long.toString(baby), jwt, 201);
        JsonNode second = createInvitation(Long.toString(baby), jwt, 201);

        assertInvitationResponse(first);
        assertInvitationResponse(second);
        long firstId = first.path("data").path("invitationId").asLong();
        long secondId = second.path("data").path("invitationId").asLong();
        assertThat(firstId).isNotEqualTo(secondId);
        String firstRaw = rawInvitationToken(first);
        String secondRaw = rawInvitationToken(second);
        assertThat(firstRaw).isNotEqualTo(secondRaw);
        assertThat(Base64.getUrlDecoder().decode(firstRaw)).hasSize(32);
        assertThat(Base64.getUrlDecoder().decode(secondRaw)).hasSize(32);
        assertInvitationRow(firstId, baby, owner, firstRaw, first);
        assertInvitationRow(secondId, baby, owner, secondRaw, second);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM baby_invitation "
                + "WHERE baby_id = ? AND status = 'PENDING'", Integer.class, baby)).isEqualTo(2);
        assertThat(babyRow(baby)).isEqualTo(babyBefore);
        assertThat(membershipRow(baby, owner)).isEqualTo(ownerBefore);
        assertThat(membershipRow(baby, caregiver)).isEqualTo(caregiverBefore);
        assertThat(selectedBaby(owner)).isEqualTo(baby);
        assertThat(selectedBaby(caregiver)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM baby_member WHERE baby_id = ?", Integer.class, baby))
                .isEqualTo(2);
    }

    @Test
    void invitationTokenHashUniqueConstraintRejectsDuplicate() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        String fixedTestHash = "f".repeat(64);
        Instant now = Instant.now();
        jdbc.update("INSERT INTO baby_invitation "
                + "(baby_id, created_by_member_id, token_hash, role, status, expires_at, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'CAREGIVER', 'PENDING', ?, ?, ?)",
                baby, owner, fixedTestHash, Timestamp.from(now.plusSeconds(72 * 3600)),
                Timestamp.from(now), Timestamp.from(now));

        assertThatThrownBy(() -> jdbc.update("INSERT INTO baby_invitation "
                + "(baby_id, created_by_member_id, token_hash, role, status, expires_at, "
                + "created_at, updated_at) SELECT baby_id, created_by_member_id, token_hash, "
                + "role, status, expires_at, created_at, updated_at "
                + "FROM baby_invitation WHERE token_hash = ?", fixedTestHash))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM baby_invitation WHERE baby_id = ?",
                Integer.class, baby)).isEqualTo(1);
    }

    @Test
    void invitationCreationRequiresActiveOwnerAndHidesInaccessibleBabies() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long caregiver = insertMember("Asia/Seoul", false);
        long outsider = insertMember("Asia/Seoul", false);
        long left = insertMember("Asia/Seoul", false);
        long removed = insertMember("Asia/Seoul", false);
        Instant now = Instant.now();
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, now);
        long deleted = insertBaby(owner, "삭제", "2020-01-01", "MALE", null, true, now);
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        insertMembership(baby, left, "CAREGIVER", "LEFT");
        insertMembership(baby, removed, "CAREGIVER", "REMOVED");
        insertMembership(deleted, owner, "OWNER", "ACTIVE");

        assertFailure(createInvitation(Long.toString(baby), token(caregiver), 403));
        for (long requester : new long[]{outsider, left, removed}) {
            JsonNode response = createInvitation(Long.toString(baby), token(requester), 404);
            assertFailure(response);
            assertThat(response.path("message").asText()).isEqualTo("아기 정보를 찾을 수 없습니다.");
        }
        for (long unavailable : new long[]{Long.MAX_VALUE, deleted}) {
            JsonNode response = createInvitation(Long.toString(unavailable), token(owner), 404);
            assertFailure(response);
            assertThat(response.path("message").asText()).isEqualTo("아기 정보를 찾을 수 없습니다.");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM baby_invitation WHERE baby_id IN (?, ?)",
                Integer.class, baby, deleted)).isZero();
    }

    @Test
    void invitationCreationValidatesPathAndAuthentication() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        for (String invalid : List.of("0", "-1", "not-a-number")) {
            assertFailure(createInvitation(invalid, token(owner), 400));
        }
        assertFailure(createInvitation(Long.toString(baby), null, 401));
        assertFailure(createInvitation(Long.toString(baby), "invalid.jwt", 401));
        assertFailure(createInvitation(Long.toString(baby), token(Long.MAX_VALUE), 401));
        assertFailure(createInvitation(Long.toString(baby), token(insertMember("Asia/Seoul", true)), 403));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM baby_invitation WHERE baby_id = ?",
                Integer.class, baby)).isZero();
    }

    @Test
    void invitationInsertFailureReturns500WithoutChangingBabyOrMembership() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        var babyBefore = babyRow(baby);
        var membershipBefore = membershipRow(baby, owner);
        jdbc.execute("CREATE TRIGGER invitation01_reject_insert BEFORE INSERT ON baby_invitation "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced invitation failure'");
        try {
            assertFailure(createInvitation(Long.toString(baby), token(owner), 500));
        } finally {
            jdbc.execute("DROP TRIGGER invitation01_reject_insert");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM baby_invitation WHERE baby_id = ?",
                Integer.class, baby)).isZero();
        assertThat(babyRow(baby)).isEqualTo(babyBefore);
        assertThat(membershipRow(baby, owner)).isEqualTo(membershipBefore);
        assertThat(selectedBaby(owner)).isNull();
    }

    @Test
    void invitationListCancelAndPublicPreviewPreserveHistoryAndMembership() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        var memberBefore = memberStateRow(owner);
        var membershipBefore = membershipRow(baby, owner);
        JsonNode created = createInvitation(Long.toString(baby), token(owner), 201);
        long id = created.path("data").path("invitationId").asLong();
        String raw = rawInvitationToken(created);

        JsonNode list = listInvitations(Long.toString(baby), token(owner), 200);
        assertThat(list.path("success").asBoolean()).isTrue();
        assertThat(list.path("message").isNull()).isTrue();
        assertThat(list.path("data").size()).isEqualTo(1);
        JsonNode entry = list.path("data").get(0);
        assertThat(entry.size()).isEqualTo(7);
        assertThat(entry.path("invitationId").asLong()).isEqualTo(id);
        assertThat(entry.path("role").asText()).isEqualTo("CAREGIVER");
        assertThat(entry.path("status").asText()).isEqualTo("PENDING");
        assertThat(OffsetDateTime.parse(entry.path("createdAt").asText()).getOffset().getTotalSeconds())
                .isEqualTo(9 * 3600);
        assertThat(OffsetDateTime.parse(entry.path("expiresAt").asText()).toInstant())
                .isEqualTo(OffsetDateTime.parse(entry.path("createdAt").asText()).toInstant()
                        .plusSeconds(72 * 3600));
        assertThat(entry.path("acceptedAt").isNull()).isTrue();
        assertThat(entry.path("canceledAt").isNull()).isTrue();
        for (String hidden : List.of("token", "tokenHash", "invitationUrl", "createdByMemberId",
                "acceptedByMemberId", "babyId")) {
            assertThat(entry.has(hidden)).isFalse();
        }
        assertThat(list.toString()).doesNotContain(raw);
        assertThat(list.has("errorCode")).isFalse();

        JsonNode pending = previewInvitation(raw, 200);
        assertPreview(pending, "도윤", "PENDING");
        assertThat(pending.path("data").path("babyProfileImageUrl").isNull()).isTrue();

        assertDeleted(cancelInvitation(Long.toString(baby), Long.toString(id), token(owner), 200));
        Timestamp canceledAt = jdbc.queryForObject("SELECT canceled_at FROM baby_invitation WHERE invitation_id = ?",
                Timestamp.class, id);
        assertThat(jdbc.queryForObject("SELECT status FROM baby_invitation WHERE invitation_id = ?",
                String.class, id)).isEqualTo("CANCELED");
        assertDeleted(cancelInvitation(Long.toString(baby), Long.toString(id), token(owner), 200));
        assertThat(jdbc.queryForObject("SELECT canceled_at FROM baby_invitation WHERE invitation_id = ?",
                Timestamp.class, id)).isEqualTo(canceledAt);
        JsonNode canceledEntry = listInvitations(Long.toString(baby), token(owner), 200)
                .path("data").get(0);
        assertThat(canceledEntry.path("status").asText()).isEqualTo("CANCELED");
        assertThat(OffsetDateTime.parse(canceledEntry.path("canceledAt").asText()).toInstant())
                .isEqualTo(canceledAt.toLocalDateTime().toInstant(ZoneOffset.UTC));
        assertPreview(previewInvitation(raw, 200), "도윤", "CANCELED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM baby_invitation WHERE invitation_id = ?",
                Integer.class, id)).isEqualTo(1);
        assertThat(memberStateRow(owner)).isEqualTo(memberBefore);
        assertThat(membershipRow(baby, owner)).isEqualTo(membershipBefore);
    }

    @Test
    void invitationListIsEmptyThenOrderedByCreatedAtAndIdDescending() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        assertThat(listInvitations(Long.toString(baby), token(owner), 200).path("data").size()).isZero();
        long first = createInvitation(Long.toString(baby), token(owner), 201)
                .path("data").path("invitationId").asLong();
        long second = createInvitation(Long.toString(baby), token(owner), 201)
                .path("data").path("invitationId").asLong();
        long third = createInvitation(Long.toString(baby), token(owner), 201)
                .path("data").path("invitationId").asLong();
        Instant old = Instant.now().minusSeconds(30);
        Instant tied = Instant.now().minusSeconds(10);
        jdbc.update("UPDATE baby_invitation SET created_at = ? WHERE invitation_id = ?", Timestamp.from(old), third);
        jdbc.update("UPDATE baby_invitation SET created_at = ? WHERE invitation_id = ?", Timestamp.from(tied), first);
        jdbc.update("UPDATE baby_invitation SET created_at = ? WHERE invitation_id = ?", Timestamp.from(tied), second);
        JsonNode entries = listInvitations(Long.toString(baby), token(owner), 200).path("data");
        assertThat(entries.size()).isEqualTo(3);
        assertThat(entries.get(0).path("invitationId").asLong()).isEqualTo(second);
        assertThat(entries.get(1).path("invitationId").asLong()).isEqualTo(first);
        assertThat(entries.get(2).path("invitationId").asLong()).isEqualTo(third);
    }

    @Test
    void invitationListAndPreviewPersistExpiredAndReturnAccepted() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", "https://example.com/baby.png",
                false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        JsonNode expired = createInvitation(Long.toString(baby), token(owner), 201);
        long expiredId = expired.path("data").path("invitationId").asLong();
        expireInvitationInDatabase(expiredId);
        JsonNode accepted = createInvitation(Long.toString(baby), token(owner), 201);
        long acceptedId = accepted.path("data").path("invitationId").asLong();
        jdbc.update("UPDATE baby_invitation SET status = 'ACCEPTED', accepted_by_member_id = ?, "
                + "accepted_at = ? WHERE invitation_id = ?", owner, Timestamp.from(Instant.now()), acceptedId);
        expireInvitationInDatabase(acceptedId);

        JsonNode entries = listInvitations(Long.toString(baby), token(owner), 200).path("data");
        assertThat(entries.size()).isEqualTo(2);
        assertThat(entries.get(0).path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(entries.get(0).path("acceptedAt").isNull()).isFalse();
        assertThat(entries.get(1).path("status").asText()).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM baby_invitation WHERE invitation_id = ?",
                String.class, expiredId)).isEqualTo("EXPIRED");
        assertPreview(previewInvitation(rawInvitationToken(expired), 200), "도윤", "EXPIRED");
        assertPreview(previewInvitation(rawInvitationToken(accepted), 200), "도윤", "ACCEPTED");
        assertThat(previewInvitation(rawInvitationToken(accepted), 200).path("data")
                .path("babyProfileImageUrl").asText()).isEqualTo("https://example.com/baby.png");
    }

    @Test
    void publicPreviewItselfPersistsExpiredStatusWithoutAuthentication() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        JsonNode created = createInvitation(Long.toString(baby), token(owner), 201);
        long id = created.path("data").path("invitationId").asLong();
        expireInvitationInDatabase(id);
        assertPreview(previewInvitation(rawInvitationToken(created), 200), "도윤", "EXPIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM baby_invitation WHERE invitation_id = ?",
                String.class, id)).isEqualTo("EXPIRED");
    }

    @Test
    void cancelRejectsAcceptedExpiredAndExpiredPendingWithoutOverwritingState() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        long accepted = createInvitation(Long.toString(baby), token(owner), 201)
                .path("data").path("invitationId").asLong();
        long expired = createInvitation(Long.toString(baby), token(owner), 201)
                .path("data").path("invitationId").asLong();
        long stale = createInvitation(Long.toString(baby), token(owner), 201)
                .path("data").path("invitationId").asLong();
        jdbc.update("UPDATE baby_invitation SET status = 'ACCEPTED', accepted_at = ? WHERE invitation_id = ?",
                Timestamp.from(Instant.now()), accepted);
        jdbc.update("UPDATE baby_invitation SET status = 'EXPIRED' WHERE invitation_id = ?", expired);
        expireInvitationInDatabase(stale);
        for (long id : new long[]{accepted, expired, stale}) {
            assertFailure(cancelInvitation(Long.toString(baby), Long.toString(id), token(owner), 409));
            assertThat(jdbc.queryForObject("SELECT canceled_at FROM baby_invitation WHERE invitation_id = ?",
                    Timestamp.class, id)).isNull();
        }
        assertThat(jdbc.queryForObject("SELECT status FROM baby_invitation WHERE invitation_id = ?",
                String.class, accepted)).isEqualTo("ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT status FROM baby_invitation WHERE invitation_id = ?",
                String.class, expired)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM baby_invitation WHERE invitation_id = ?",
                String.class, stale)).isEqualTo("EXPIRED");
    }

    @Test
    void invitationManagementEnforcesOwnerAndConcealsOtherBabies() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long caregiver = insertMember("Asia/Seoul", false);
        long outsider = insertMember("Asia/Seoul", false);
        long left = insertMember("Asia/Seoul", false);
        long removed = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        long other = insertBaby(owner, "다른 아기", "2020-01-01", "MALE", null, false, Instant.now());
        long deleted = insertBaby(owner, "삭제", "2020-01-01", "MALE", null, true, Instant.now());
        for (long id : new long[]{baby, other, deleted}) {
            insertMembership(id, owner, "OWNER", "ACTIVE");
        }
        insertMembership(baby, caregiver, "CAREGIVER", "ACTIVE");
        insertMembership(baby, left, "CAREGIVER", "LEFT");
        insertMembership(baby, removed, "CAREGIVER", "REMOVED");
        long invitationId = createInvitation(Long.toString(other), token(owner), 201)
                .path("data").path("invitationId").asLong();
        assertFailure(listInvitations(Long.toString(baby), token(caregiver), 403));
        assertFailure(cancelInvitation(Long.toString(baby), Long.toString(invitationId), token(caregiver), 403));
        for (long requester : new long[]{outsider, left, removed}) {
            assertFailure(listInvitations(Long.toString(baby), token(requester), 404));
            assertFailure(cancelInvitation(Long.toString(baby), Long.toString(invitationId),
                    token(requester), 404));
        }
        assertFailure(listInvitations(Long.toString(deleted), token(owner), 404));
        assertFailure(cancelInvitation(Long.toString(deleted), Long.toString(invitationId), token(owner), 404));
        assertFailure(listInvitations(Long.toString(Long.MAX_VALUE), token(owner), 404));
        assertFailure(cancelInvitation(Long.toString(baby), Long.toString(invitationId), token(owner), 404));
        assertFailure(cancelInvitation(Long.toString(baby), Long.toString(Long.MAX_VALUE), token(owner), 404));
        assertThat(jdbc.queryForObject("SELECT status FROM baby_invitation WHERE invitation_id = ?",
                String.class, invitationId)).isEqualTo("PENDING");
    }

    @Test
    void invitationEndpointSecurityValidationAndDeletedBabyPreview() throws Exception {
        long owner = insertMember("Asia/Seoul", false);
        long baby = insertBaby(owner, "도윤", "2020-01-01", "MALE", null, false, Instant.now());
        insertMembership(baby, owner, "OWNER", "ACTIVE");
        JsonNode created = createInvitation(Long.toString(baby), token(owner), 201);
        long id = created.path("data").path("invitationId").asLong();
        String raw = rawInvitationToken(created);
        for (String invalid : List.of("0", "-1", "not-a-number")) {
            assertFailure(cancelInvitation(invalid, Long.toString(id), token(owner), 400));
            assertFailure(cancelInvitation(Long.toString(baby), invalid, token(owner), 400));
        }
        for (String jwt : new String[]{null, "invalid.jwt", expiredToken(owner), token(Long.MAX_VALUE)}) {
            assertFailure(createInvitation(Long.toString(baby), jwt, 401));
            assertFailure(listInvitations(Long.toString(baby), jwt, 401));
            assertFailure(cancelInvitation(Long.toString(baby), Long.toString(id), jwt, 401));
        }
        String withdrawn = token(insertMember("Asia/Seoul", true));
        assertFailure(listInvitations(Long.toString(baby), withdrawn, 403));
        assertFailure(cancelInvitation(Long.toString(baby), Long.toString(id), withdrawn, 403));
        assertFailure(previewInvitation("nonexistent-token", 404));
        assertFailure(previewInvitation("invalid.token", 404));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/invitations/{token}", raw)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/invitations/{token}/extra", raw)).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE baby SET is_deleted = true, deleted_at = ?, updated_at = ? WHERE baby_id = ?",
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), baby);
        assertFailure(previewInvitation(raw, 404));
        assertThat(jdbc.queryForObject("SELECT status FROM baby_invitation WHERE invitation_id = ?",
                String.class, id)).isEqualTo("PENDING");
    }

    private void expireInvitationInDatabase(long invitationId) {
        Instant old = Instant.now().minusSeconds(10 * 24 * 3600);
        jdbc.update("UPDATE baby_invitation SET created_at = ?, expires_at = ? WHERE invitation_id = ?",
                Timestamp.from(old), Timestamp.from(old.plusSeconds(72 * 3600)), invitationId);
    }

    private JsonNode listInvitations(String babyId, String jwt, int expectedStatus) throws Exception {
        var request = get("/api/v1/babies/{babyId}/invitations", babyId);
        if (jwt != null) {
            request.header("Authorization", "Bearer " + jwt);
        }
        return mapper.readTree(mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode cancelInvitation(String babyId, String invitationId, String jwt,
                                      int expectedStatus) throws Exception {
        var request = delete("/api/v1/babies/{babyId}/invitations/{invitationId}", babyId, invitationId);
        if (jwt != null) {
            request.header("Authorization", "Bearer " + jwt);
        }
        return mapper.readTree(mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode previewInvitation(String rawToken, int expectedStatus) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/invitations/{token}", rawToken))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString());
    }

    private void assertPreview(JsonNode response, String babyName, String expectedStatus) {
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("message").isNull()).isTrue();
        assertThat(response.has("errorCode")).isFalse();
        JsonNode data = response.path("data");
        assertThat(data.size()).isEqualTo(5);
        assertThat(data.path("babyName").asText()).isEqualTo(babyName);
        assertThat(data.path("role").asText()).isEqualTo("CAREGIVER");
        assertThat(data.path("status").asText()).isEqualTo(expectedStatus);
        assertThat(OffsetDateTime.parse(data.path("expiresAt").asText()).getOffset().getTotalSeconds())
                .isEqualTo(9 * 3600);
        for (String hidden : List.of("babyId", "invitationId", "token", "tokenHash", "createdByMemberId",
                "acceptedByMemberId")) {
            assertThat(data.has(hidden)).isFalse();
        }
    }

    private JsonNode createInvitation(String babyId, String jwt, int expectedStatus) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/babies/{babyId}/invitations", babyId);
        if (jwt != null) {
            request.header("Authorization", "Bearer " + jwt);
        }
        String response = mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private void assertInvitationResponse(JsonNode response) {
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("message").isNull()).isTrue();
        assertThat(response.has("errorCode")).isFalse();
        JsonNode data = response.path("data");
        assertThat(data.size()).isEqualTo(5);
        assertThat(data.path("invitationId").asLong()).isPositive();
        assertThat(data.path("role").asText()).isEqualTo("CAREGIVER");
        assertThat(data.path("status").asText()).isEqualTo("PENDING");
        assertThat(data.path("invitationUrl").asText()).startsWith("https://app.example.com/invite?token=");
        assertThat(data.has("tokenHash")).isFalse();
        assertThat(data.has("token")).isFalse();
        assertThat(OffsetDateTime.parse(data.path("expiresAt").asText()).getOffset().getTotalSeconds())
                .isEqualTo(9 * 3600);
    }

    private String rawInvitationToken(JsonNode response) {
        return response.path("data").path("invitationUrl").asText()
                .substring("https://app.example.com/invite?token=".length());
    }

    private void assertInvitationRow(long invitationId, long babyId, long ownerId, String rawToken,
                                     JsonNode response) throws Exception {
        var row = jdbc.queryForMap("SELECT baby_id, created_by_member_id, token_hash, role, status, "
                + "expires_at, accepted_by_member_id, accepted_at, canceled_at, created_at, updated_at "
                + "FROM baby_invitation WHERE invitation_id = ?", invitationId);
        assertThat(row.get("baby_id")).isEqualTo(babyId);
        assertThat(row.get("created_by_member_id")).isEqualTo(ownerId);
        assertThat(row.get("role")).isEqualTo("CAREGIVER");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("accepted_by_member_id")).isNull();
        assertThat(row.get("accepted_at")).isNull();
        assertThat(row.get("canceled_at")).isNull();
        assertThat(row.get("token_hash")).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8))));
        assertThat(row.get("token_hash")).isNotEqualTo(rawToken);
        Instant createdAt = ((Timestamp) row.get("created_at")).toLocalDateTime().toInstant(ZoneOffset.UTC);
        Instant expiresAt = ((Timestamp) row.get("expires_at")).toLocalDateTime().toInstant(ZoneOffset.UTC);
        assertThat(expiresAt).isEqualTo(createdAt.plusSeconds(72 * 3600));
        assertThat(OffsetDateTime.parse(response.path("data").path("expiresAt").asText()).toInstant())
                .isEqualTo(expiresAt);
    }

    private JsonNode listBabyMembers(String babyId, String jwt, int expectedStatus) throws Exception {
        var request = get("/api/v1/babies/{babyId}/members", babyId);
        if (jwt != null) {
            request.header("Authorization", "Bearer " + jwt);
        }
        String response = mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private void assertMemberSummary(JsonNode actual, long memberId, String name, String imageUrl, String role) {
        assertThat(actual.size()).isEqualTo(4);
        assertThat(actual.path("memberId").asLong()).isEqualTo(memberId);
        assertThat(actual.path("memberName").asText()).isEqualTo(name);
        if (imageUrl == null) {
            assertThat(actual.path("profileImageUrl").isNull()).isTrue();
        } else {
            assertThat(actual.path("profileImageUrl").asText()).isEqualTo(imageUrl);
        }
        assertThat(actual.path("role").asText()).isEqualTo(role);
        for (String hidden : List.of("email", "socialAccounts", "providerUserId", "lastSelectedBabyId",
                "timezone", "isDeleted")) {
            assertThat(actual.has(hidden)).isFalse();
        }
    }

    private java.util.Map<String, Object> memberStateRow(long memberId) {
        return jdbc.queryForMap("SELECT member_name, profile_image_url, last_selected_baby_id, is_deleted "
                + "FROM member WHERE member_id = ?", memberId);
    }

    private int deleteStatus(long babyId, String jwt) throws Exception {
        return mvc.perform(delete("/api/v1/babies/{babyId}", babyId)
                .header("Authorization", "Bearer " + jwt)).andReturn().getResponse().getStatus();
    }

    private JsonNode deleteBaby(String babyId, String jwt, int expectedStatus) throws Exception {
        var request = delete("/api/v1/babies/{babyId}", babyId);
        if (jwt != null) {
            request.header("Authorization", "Bearer " + jwt);
        }
        String response = mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private void assertDeleted(JsonNode response) {
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("data").isNull()).isTrue();
        assertThat(response.path("message").isNull()).isTrue();
        assertThat(response.has("errorCode")).isFalse();
    }

    private JsonNode changeSelection(String jwt, String json, int expectedStatus) throws Exception {
        var request = patch("/api/v1/members/me/last-selected-baby")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (jwt != null) {
            request.header("Authorization", "Bearer " + jwt);
        }
        String response = mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private JsonNode myProfile(String jwt) throws Exception {
        String response = mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }

    private void assertSelectionChanged(JsonNode response, long expectedBabyId) {
        assertThat(response.size()).isEqualTo(3);
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("message").isNull()).isTrue();
        assertThat(response.has("errorCode")).isFalse();
        assertThat(response.path("data").size()).isEqualTo(1);
        assertThat(response.path("data").path("lastSelectedBabyId").asLong()).isEqualTo(expectedBabyId);
    }

    private JsonNode patchBaby(String babyId, String token, String json,
                               MockMultipartFile image, int expectedStatus) throws Exception {
        var request = multipart(HttpMethod.PATCH, "/api/v1/babies/{babyId}", babyId)
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

    private byte[] jpeg() {
        return new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00};
    }

    private byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    }

    private java.util.Map<String, Object> babyRow(long babyId) {
        return jdbc.queryForMap("SELECT created_by_member_id, baby_name, birth_date, gender, profile_image_url, "
                + "is_deleted, created_at, updated_at, deleted_at FROM baby WHERE baby_id = ?", babyId);
    }

    private java.util.Map<String, Object> membershipRow(long babyId, long memberId) {
        return jdbc.queryForMap("SELECT role, status, joined_at, ended_at FROM baby_member "
                + "WHERE baby_id = ? AND member_id = ?", babyId, memberId);
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

    private String expiredToken(long memberId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("dearus")
                .subject(Long.toString(memberId)).issuedAt(now.minusSeconds(600))
                .expiresAt(now.minusSeconds(300)).build();
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
        final List<Boolean> deletedDuringTransaction = new CopyOnWriteArrayList<>();
        final AtomicBoolean failStore = new AtomicBoolean();
        final AtomicBoolean failDelete = new AtomicBoolean();
        final AtomicReference<Runnable> afterStore = new AtomicReference<>();

        void reset() {
            stored.clear();
            deleted.clear();
            deletedDuringTransaction.clear();
            failStore.set(false);
            failDelete.set(false);
            afterStore.set(null);
        }

        @Override
        public String store(BabyImageUpload image) {
            if (failStore.getAndSet(false)) {
                throw new IllegalStateException("storage unavailable");
            }
            String url = "https://images.example/babies/profile/" + UUID.randomUUID() + "." + image.extension();
            stored.add(url);
            Runnable callback = afterStore.getAndSet(null);
            if (callback != null) {
                callback.run();
            }
            return url;
        }

        @Override
        public void deleteOwnedImage(String imageUrl) {
            deletedDuringTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (failDelete.getAndSet(false)) {
                throw new IllegalStateException("cleanup unavailable");
            }
            deleted.add(imageUrl);
        }
    }
}
