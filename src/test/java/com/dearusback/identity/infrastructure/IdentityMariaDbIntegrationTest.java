package com.dearusback.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.DearUsBackApplication;
import com.dearusback.identity.application.model.ReissueTokenCommand;
import com.dearusback.identity.application.model.LogoutCommand;
import com.dearusback.identity.application.model.ReissueTokenResult;
import com.dearusback.identity.application.model.SocialLoginResult;
import com.dearusback.identity.application.model.ProfileImageUpload;
import com.dearusback.identity.application.model.StoredProfileImage;
import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.application.port.out.RefreshTokenGenerator;
import com.dearusback.identity.application.port.out.RefreshTokenHasher;
import com.dearusback.identity.application.port.out.ProfileImageStoragePort;
import com.dearusback.identity.application.service.ReissueTokenService;
import com.dearusback.identity.application.service.LogoutService;
import com.dearusback.identity.application.service.SocialLoginTransactionService;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.type.SocialProvider;
import com.dearusback.identity.infrastructure.config.AuthProperties;
import com.dearusback.identity.infrastructure.token.SecureRefreshTokenGenerator;
import com.dearusback.support.domain.exception.BusinessException;
import java.time.Instant;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.dao.DataIntegrityViolationException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(classes = {DearUsBackApplication.class, IdentityMariaDbIntegrationTest.GeneratorConfig.class})
class IdentityMariaDbIntegrationTest {

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

    @Autowired
    private SocialLoginTransactionService login;

    @Autowired
    private ReissueTokenService reissue;

    @Autowired
    private LogoutService logout;

    @Autowired
    private RefreshTokenHasher hasher;

    @Autowired
    private ControlledGenerator generator;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private AuthProperties authProperties;

    @Autowired
    private FakeProfileImageStorage profileImages;

    @Test
    void v1MemberReceivesDefaultTimezoneAfterV2Migration() throws Exception {
        String schema = "timezone_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(MARIADB.getJdbcUrl(), "root", MARIADB.getPassword());
             var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + schema);
        }

        String jdbcUrl = "jdbc:mariadb://" + MARIADB.getHost() + ":" + MARIADB.getMappedPort(3306) + "/" + schema;
        Flyway.configure()
                .dataSource(jdbcUrl, "root", MARIADB.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1"))
                .load().migrate();
        JdbcTemplate isolatedJdbc = new JdbcTemplate(new DriverManagerDataSource(
                jdbcUrl, "root", MARIADB.getPassword()
        ));
        isolatedJdbc.update("""
                INSERT INTO member (member_name, is_deleted, created_at, updated_at)
                VALUES ('기존 회원', false, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """);

        Flyway.configure()
                .dataSource(jdbcUrl, "root", MARIADB.getPassword())
                .locations("classpath:db/migration")
                .load().migrate();

        assertThat(isolatedJdbc.queryForObject("SELECT timezone FROM member WHERE member_name = '기존 회원'",
                String.class)).isEqualTo("Asia/Seoul");
        assertThat(isolatedJdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version IN ('1', '2') AND success = 1
                """, Integer.class)).isEqualTo(2);
        assertThat(isolatedJdbc.queryForObject("""
                SELECT IS_NULLABLE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'member' AND COLUMN_NAME = 'timezone'
                """, String.class, schema)).isEqualTo("NO");
    }

    @Test
    void newAuth01MemberPersistsTimezoneAndDatabaseRejectsNull() {
        SocialLoginResult loginResult = register();
        Long memberId = loginResult.member().memberId();

        assertThat(jdbc.queryForObject("SELECT timezone FROM member WHERE member_id = ?",
                String.class, memberId)).isEqualTo("Asia/Seoul");
        assertThatThrownBy(() -> jdbc.update("UPDATE member SET timezone = NULL WHERE member_id = ?", memberId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT timezone FROM member WHERE member_id = ?",
                String.class, memberId)).isEqualTo("Asia/Seoul");
    }

    @Test
    void rotatesTokenAndAllowsAnotherRotation() {
        SocialLoginResult original = register();

        ReissueTokenResult first = reissue.reissue(new ReissueTokenCommand(original.refreshToken()));
        ReissueTokenResult second = reissue.reissue(new ReissueTokenCommand(first.refreshToken()));

        assertThat(first.accessToken()).isNotBlank();
        assertThat(first.refreshToken()).isNotEqualTo(original.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(first.accessTokenExpiresInSeconds()).isEqualTo(1800);
        assertThat(first.refreshTokenExpiresInSeconds()).isEqualTo(2592000);
        assertThat(first.tokenType()).isEqualTo("Bearer");
        assertThat(revokedAt(original.refreshToken())).isNotNull();
        assertThat(revokedAt(first.refreshToken())).isNotNull();
        assertThat(revokedAt(second.refreshToken())).isNull();

        String storedHash = jdbc.queryForObject(
                "SELECT token_hash FROM refresh_token WHERE token_hash = ?",
                String.class,
                hasher.hash(second.refreshToken())
        );
        assertThat(storedHash).isEqualTo(hasher.hash(second.refreshToken()))
                .isNotEqualTo(second.refreshToken());
    }

    @Test
    void usedUnknownExpiredAndRevokedTokensAllFailWithSameCode() {
        SocialLoginResult original = register();
        reissue.reissue(new ReissueTokenCommand(original.refreshToken()));
        assertInvalid(original.refreshToken());
        assertInvalid("unknown-" + UUID.randomUUID());

        SocialLoginResult expired = register();
        jdbc.update("""
                UPDATE refresh_token
                   SET expires_at = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY)
                 WHERE token_hash = ?
                """, hasher.hash(expired.refreshToken()));
        assertInvalid(expired.refreshToken());

        SocialLoginResult revoked = register();
        jdbc.update("UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP(6) WHERE token_hash = ?",
                hasher.hash(revoked.refreshToken()));
        assertInvalid(revoked.refreshToken());
    }

    @Test
    void withdrawnMemberGetsForbidden() {
        SocialLoginResult original = register();
        jdbc.update("UPDATE member SET is_deleted = true WHERE member_id = ?", original.member().memberId());

        assertThatThrownBy(() -> reissue.reissue(new ReissueTokenCommand(original.refreshToken())))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(IdentityErrorCode.WITHDRAWN_MEMBER));
        assertThat(revokedAt(original.refreshToken())).isNull();
    }

    @Test
    void failedNewSessionInsertRollsBackOldTokenRevocation() {
        SocialLoginResult original = register();
        String forcedToken = "duplicate-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO refresh_token (member_id, token_hash, expires_at, created_at)
                VALUES (?, ?, DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 30 DAY), CURRENT_TIMESTAMP(6))
                """, original.member().memberId(), hasher.hash(forcedToken));
        generator.forceNext(forcedToken);

        assertThatThrownBy(() -> reissue.reissue(new ReissueTokenCommand(original.refreshToken())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(revokedAt(original.refreshToken())).isNull();
        assertThat(reissue.reissue(new ReissueTokenCommand(original.refreshToken())).refreshToken())
                .isNotBlank();
    }

    @Test
    void concurrentReuseHasExactlyOneWinner() throws Exception {
        SocialLoginResult original = register();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> calls = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                calls.add(executor.submit(() -> {
                    start.await();
                    try {
                        return reissue.reissue(new ReissueTokenCommand(original.refreshToken()));
                    } catch (BusinessException error) {
                        return error;
                    }
                }));
            }
            start.countDown();

            List<Object> results = new ArrayList<>();
            for (Future<Object> call : calls) {
                results.add(call.get(20, TimeUnit.SECONDS));
            }
            assertThat(results.stream().filter(ReissueTokenResult.class::isInstance).count()).isOne();
            assertThat(results.stream().filter(BusinessException.class::isInstance).count()).isOne();
            assertThat(results.stream().filter(BusinessException.class::isInstance)
                    .map(BusinessException.class::cast)
                    .map(BusinessException::errorCode))
                    .containsExactly(IdentityErrorCode.INVALID_REFRESH_TOKEN);
            Integer sessionCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM refresh_token WHERE member_id = ?",
                    Integer.class,
                    original.member().memberId()
            );
            assertThat(sessionCount).isEqualTo(2);
        }
    }

    @Test
    void newAccessTokenContainsOnlyMemberIdentityClaims() {
        SocialLoginResult original = register();
        ReissueTokenResult result = reissue.reissue(new ReissueTokenCommand(original.refreshToken()));

        byte[] secret = Base64.getDecoder().decode(authProperties.jwt().secretBase64());
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        Jwt jwt = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256)
                .build().decode(result.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(original.member().memberId().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("dearus");
        assertThat(jwt.getClaims()).containsOnlyKeys("sub", "iss", "iat", "exp");
    }

    @Test
    void reissueHttpContractDoesNotRequireAccessToken() throws Exception {
        SocialLoginResult original = register();

        mvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + original.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.member").doesNotExist())
                .andExpect(jsonPath("$.data.isNewMember").doesNotExist())
                .andExpect(jsonPath("$.message").isEmpty());

        SocialLoginResult withBadBearer = register();
        mvc.perform(post("/api/v1/auth/reissue")
                        .header("Authorization", "Bearer expired.access.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + withBadBearer.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void logoutRevokesOnlyTheRequestedSessionAndOtherSessionCanReissue() {
        VerifiedSocialIdentity identity = identity();
        SocialLoginResult first = login.loginOrRegister(identity);
        SocialLoginResult second = login.loginOrRegister(identity);

        assertThat(first.member().memberId()).isEqualTo(second.member().memberId());
        logout.logout(new LogoutCommand(first.refreshToken()));

        assertThat(revokedAt(first.refreshToken())).isNotNull();
        assertThat(revokedAt(second.refreshToken())).isNull();
        assertInvalid(first.refreshToken());
        assertThat(reissue.reissue(new ReissueTokenCommand(second.refreshToken())).refreshToken())
                .isNotBlank();
    }

    @Test
    void repeatedUnknownExpiredAndAlreadyRevokedLogoutAlwaysSucceeds() throws Exception {
        SocialLoginResult original = register();
        logout.logout(new LogoutCommand(original.refreshToken()));
        Instant firstRevokedAt = revokedAt(original.refreshToken());
        logout.logout(new LogoutCommand(original.refreshToken()));
        assertThat(revokedAt(original.refreshToken())).isEqualTo(firstRevokedAt);

        assertThat(logoutHttp("unknown-" + UUID.randomUUID(), null))
                .isEqualTo("{\"success\":true,\"data\":null,\"message\":null}");

        SocialLoginResult expired = register();
        jdbc.update("UPDATE refresh_token SET expires_at = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY) WHERE token_hash = ?",
                hasher.hash(expired.refreshToken()));
        assertThat(logoutHttp(expired.refreshToken(), null))
                .isEqualTo("{\"success\":true,\"data\":null,\"message\":null}");
        assertThat(revokedAt(expired.refreshToken())).isNotNull();
    }

    @Test
    void withdrawnMemberCanLogoutWithoutMemberLookup() throws Exception {
        SocialLoginResult original = register();
        jdbc.update("UPDATE member SET is_deleted = true WHERE member_id = ?", original.member().memberId());

        assertThat(logoutHttp(original.refreshToken(), null))
                .isEqualTo("{\"success\":true,\"data\":null,\"message\":null}");

        assertThat(revokedAt(original.refreshToken())).isNotNull();
    }

    @Test
    void logoutHttpContractIsIdenticalForPresentAndUnknownTokens() throws Exception {
        SocialLoginResult original = register();
        String unknown = "unknown-" + UUID.randomUUID();

        String first = logoutHttp(original.refreshToken(), null);
        String repeated = logoutHttp(original.refreshToken(), "Bearer expired.access.token");
        String absent = logoutHttp(unknown, null);

        assertThat(first).isEqualTo("{\"success\":true,\"data\":null,\"message\":null}");
        assertThat(repeated).isEqualTo(first);
        assertThat(absent).isEqualTo(first);
        assertThat(revokedAt(original.refreshToken())).isNotNull();

        mvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + original.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(IdentityErrorCode.INVALID_REFRESH_TOKEN.message()));
    }

    @Test
    void concurrentLogoutRequestsAllSucceedAndDoNotStoreRawToken() throws Exception {
        SocialLoginResult original = register();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> calls = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 4; i++) {
                calls.add(executor.submit(() -> {
                    start.await();
                    return logoutHttp(original.refreshToken(), null);
                }));
            }
            start.countDown();
            for (Future<String> call : calls) {
                assertThat(call.get(20, TimeUnit.SECONDS))
                        .isEqualTo("{\"success\":true,\"data\":null,\"message\":null}");
            }
        }
        assertThat(revokedAt(original.refreshToken())).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE token_hash = ?",
                Integer.class, original.refreshToken())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE token_hash = ?",
                Integer.class, hasher.hash(original.refreshToken()))).isOne();
    }

    @Test
    void logoutAndReissueRaceCanOnlyConsumeOldTokenOnce() throws Exception {
        SocialLoginResult original = register();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Object> reissueCall = executor.submit(() -> {
                start.await();
                try {
                    return reissue.reissue(new ReissueTokenCommand(original.refreshToken()));
                } catch (BusinessException error) {
                    return error;
                }
            });
            Future<?> logoutCall = executor.submit(() -> {
                start.await();
                logout.logout(new LogoutCommand(original.refreshToken()));
                return null;
            });
            start.countDown();
            Object reissueResult = reissueCall.get(20, TimeUnit.SECONDS);
            logoutCall.get(20, TimeUnit.SECONDS);

            assertThat(reissueResult).isInstanceOfAny(ReissueTokenResult.class, BusinessException.class);
            if (reissueResult instanceof BusinessException error) {
                assertThat(error.errorCode()).isEqualTo(IdentityErrorCode.INVALID_REFRESH_TOKEN);
            }
            assertThat(revokedAt(original.refreshToken())).isNotNull();
            assertInvalid(original.refreshToken());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE member_id = ?",
                    Integer.class, original.member().memberId()))
                    .isEqualTo(reissueResult instanceof ReissueTokenResult ? 2 : 1);
        }
    }

    @Test
    void validAccessTokenReturnsPersistedMemberAndSocialAccountWithoutInternalIdentity() throws Exception {
        String providerUserId = UUID.randomUUID().toString();
        SocialLoginResult loginResult = login.loginOrRegister(new VerifiedSocialIdentity(
                SocialProvider.GOOGLE, providerUserId, "google@example.com", "김소은", null
        ));
        jdbc.update("UPDATE member SET member_name = ?, email = ?, timezone = ?, profile_image_url = ?, last_selected_baby_id = ? WHERE member_id = ?",
                "기존 이름", "member@example.com", "Europe/Paris", "https://example.com/profile.png", 77L,
                loginResult.member().memberId());
        login.loginOrRegister(new VerifiedSocialIdentity(
                SocialProvider.GOOGLE, providerUserId, "google@example.com", "Provider 이름", null
        ));

        String body = mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + loginResult.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.memberId").value(loginResult.member().memberId()))
                .andExpect(jsonPath("$.data.email").value("member@example.com"))
                .andExpect(jsonPath("$.data.memberName").value("기존 이름"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://example.com/profile.png"))
                .andExpect(jsonPath("$.data.timezone").value("Europe/Paris"))
                .andExpect(jsonPath("$.data.lastSelectedBabyId").value(77))
                .andExpect(jsonPath("$.data.socialAccounts[0].provider").value("GOOGLE"))
                .andExpect(jsonPath("$.data.socialAccounts[0].providerEmail").value("google@example.com"))
                .andExpect(jsonPath("$.message").isEmpty())
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("providerUserId", providerUserId, "socialAccountId");
    }

    @Test
    void nullableProfileAndSocialEmailRemainNull() throws Exception {
        SocialLoginResult loginResult = register();

        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + loginResult.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").isEmpty())
                .andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.lastSelectedBabyId").isEmpty())
                .andExpect(jsonPath("$.data.socialAccounts[0].providerEmail").isEmpty());
    }

    @Test
    void missingMalformedTamperedExpiredWrongIssuerAndInvalidSubjectTokensShareOneUnauthorizedResponse()
            throws Exception {
        SocialLoginResult loginResult = register();
        String original = loginResult.accessToken();
        int signatureStart = original.lastIndexOf('.') + 1;
        String tampered = original.substring(0, signatureStart)
                + (original.charAt(signatureStart) == 'A' ? 'B' : 'A')
                + original.substring(signatureStart + 1);

        assertUnauthorizedProfile(null);
        assertUnauthorizedProfile("Basic credentials");
        assertUnauthorizedProfile("Bearer");
        assertUnauthorizedProfile("Bearer " + tampered);
        assertUnauthorizedProfile("Bearer " + signedToken("1", "dearus", Instant.now().minusSeconds(60)));
        assertUnauthorizedProfile("Bearer " + signedToken("1", "another-issuer", Instant.now().plusSeconds(600)));
        assertUnauthorizedProfile("Bearer " + signedToken("1", "dearus", null));
        assertUnauthorizedProfile("Bearer " + signedToken(null, "dearus", Instant.now().plusSeconds(600)));
        assertUnauthorizedProfile("Bearer " + signedToken("not-a-member-id", "dearus", Instant.now().plusSeconds(600)));
        assertUnauthorizedProfile("Bearer " + signedToken("9223372036854775808", "dearus", Instant.now().plusSeconds(600)));
    }

    @Test
    void validJwtForMissingMemberIsUnauthorized() throws Exception {
        assertUnauthorizedProfile("Bearer " + signedToken(
                "9223372036854775807", "dearus", Instant.now().plusSeconds(600)
        ));
    }

    @Test
    void withdrawnMemberIsForbiddenEvenWithValidAccessToken() throws Exception {
        SocialLoginResult loginResult = register();
        jdbc.update("UPDATE member SET is_deleted = true WHERE member_id = ?", loginResult.member().memberId());

        String response = mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + loginResult.accessToken()))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).isEqualTo("{\"success\":false,\"data\":null,\"message\":\"탈퇴한 회원입니다.\"}");
    }

    @Test
    void publicAuthRouteDoesNotRequireJwtAndOnlyPostIsPublic() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"APPLE\",\"providerToken\":\"token\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void member02NamePatchPersistsAndMember01ReturnsDatabaseTimezone() throws Exception {
        SocialLoginResult account = register();
        Long memberId = account.member().memberId();
        jdbc.update("UPDATE member SET timezone = ?, profile_image_url = ? WHERE member_id = ?",
                "Europe/Paris", "https://provider.example/avatar.png", memberId);

        mvc.perform(profilePatch("{\"memberName\":\"  새 이름  \",\"removeProfileImage\":false}", null)
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberName").value("새 이름"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://provider.example/avatar.png"))
                .andExpect(jsonPath("$.data.timezone").value("Europe/Paris"))
                .andExpect(jsonPath("$.errorCode").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT member_name FROM member WHERE member_id = ?", String.class, memberId))
                .isEqualTo("새 이름");
        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberName").value("새 이름"))
                .andExpect(jsonPath("$.data.timezone").value("Europe/Paris"));
    }

    @Test
    void member02ImagePatchPersistsAndNeverDeletesProviderUrl() throws Exception {
        SocialLoginResult account = register();
        Long memberId = account.member().memberId();
        String providerUrl = "https://provider.example/avatar.png";
        jdbc.update("UPDATE member SET profile_image_url = ? WHERE member_id = ?", providerUrl, memberId);

        String response = mvc.perform(profilePatch("{\"removeProfileImage\":false}", png())
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value(
                        "https://images.example/members/" + memberId + "/profile/test.png"))
                .andExpect(jsonPath("$.data.socialAccounts[0].provider").value("GOOGLE"))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("providerUserId", "socialAccountId");

        assertThat(jdbc.queryForObject("SELECT profile_image_url FROM member WHERE member_id = ?",
                String.class, memberId)).isEqualTo(
                "https://images.example/members/" + memberId + "/profile/test.png");
        assertThat(profileImages.deleted).doesNotContain(providerUrl);
        mvc.perform(get("/api/v1/members/me")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(jsonPath("$.data.profileImageUrl").value(
                        "https://images.example/members/" + memberId + "/profile/test.png"));
    }

    @Test
    void member02RemoveImageClearsDbAndDeletesOnlyOwnedUrl() throws Exception {
        SocialLoginResult account = register();
        Long memberId = account.member().memberId();
        String oldUrl = "https://images.example/members/" + memberId + "/profile/old.png";
        jdbc.update("UPDATE member SET profile_image_url = ? WHERE member_id = ?", oldUrl, memberId);

        mvc.perform(profilePatch("{\"removeProfileImage\":true}", null)
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty());

        assertThat(jdbc.queryForObject("SELECT profile_image_url FROM member WHERE member_id = ?",
                String.class, memberId)).isNull();
        assertThat(profileImages.deleted).contains(oldUrl);
    }

    @Test
    void member02RemovingProviderImageDoesNotDeleteExternalObject() throws Exception {
        SocialLoginResult account = register();
        Long memberId = account.member().memberId();
        String providerUrl = "https://provider.example/" + UUID.randomUUID() + "/avatar.png";
        jdbc.update("UPDATE member SET profile_image_url = ? WHERE member_id = ?", providerUrl, memberId);

        mvc.perform(profilePatch("{\"removeProfileImage\":true}", null)
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").isEmpty());

        assertThat(profileImages.deleted).doesNotContain(providerUrl);
        assertThat(jdbc.queryForObject("SELECT profile_image_url FROM member WHERE member_id = ?",
                String.class, memberId)).isNull();
    }

    @Test
    void member02RejectsInvalidMultipartAndKeepsDatabaseUnchanged() throws Exception {
        SocialLoginResult account = register();
        Long memberId = account.member().memberId();
        String bearer = "Bearer " + account.accessToken();

        mvc.perform(profilePatch("{\"memberName\":null,\"removeProfileImage\":false}", null)
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").doesNotExist());
        mvc.perform(profilePatch("{\"removeProfileImage\":true}", png())
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(profilePatch("{\"memberName\":\"   \"}", null)
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(profilePatch("{\"memberName\":\"" + "x".repeat(51) + "\"}", null)
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(profilePatch("{\"memberName\":\"x\"}",
                        new MockMultipartFile("profileImage", "fake.png", "image/png", "not-png".getBytes()))
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(profilePatch("{}", new MockMultipartFile("profileImage", "empty.png", "image/png", new byte[0]))
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(profilePatch("{}", new MockMultipartFile("profileImage", "image.gif", "image/gif",
                        new byte[] {'G', 'I', 'F'}))
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(profilePatch("{}", new MockMultipartFile("profileImage", "huge.png", "image/png",
                        new byte[ProfileImageUpload.MAX_BYTES + 1]))
                        .header("Authorization", bearer))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").doesNotExist());
        mvc.perform(multipart(HttpMethod.PATCH, "/api/v1/members/me")
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT member_name FROM member WHERE member_id = ?", String.class, memberId))
                .isEqualTo("사용자");
    }

    @Test
    void member02StorageFailureIsUnavailableAndDoesNotChangeMember() throws Exception {
        SocialLoginResult account = register();
        Long memberId = account.member().memberId();
        profileImages.failNextStore.set(true);

        mvc.perform(profilePatch("{\"memberName\":\"변경 시도\"}", png())
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("프로필 이미지를 처리할 수 없습니다."))
                .andExpect(jsonPath("$.errorCode").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT member_name FROM member WHERE member_id = ?", String.class, memberId))
                .isEqualTo("사용자");
        assertThat(jdbc.queryForObject("SELECT profile_image_url FROM member WHERE member_id = ?",
                String.class, memberId)).isNull();
    }

    @Test
    void member02RequiresValidJwtAndExistingActiveMember() throws Exception {
        SocialLoginResult account = register();
        mvc.perform(profilePatch("{\"memberName\":\"새 이름\"}", null))
                .andExpect(status().isUnauthorized());
        mvc.perform(profilePatch("{\"memberName\":\"새 이름\"}", null)
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(profilePatch("{\"memberName\":\"새 이름\"}", null)
                        .header("Authorization", "Bearer " + signedToken(
                                "9223372036854775807", "dearus", Instant.now().plusSeconds(600))))
                .andExpect(status().isUnauthorized());

        jdbc.update("UPDATE member SET is_deleted = true WHERE member_id = ?", account.member().memberId());
        mvc.perform(profilePatch("{\"memberName\":\"새 이름\"}", null)
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    void member04WithdrawsWithoutBabiesAndRevokesEverySession() throws Exception {
        VerifiedSocialIdentity socialIdentity = identity();
        SocialLoginResult first = login.loginOrRegister(socialIdentity);
        SocialLoginResult second = login.loginOrRegister(socialIdentity);
        SocialLoginResult third = login.loginOrRegister(socialIdentity);
        long memberId = first.member().memberId();
        jdbc.update("UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP(6) WHERE token_hash = ?",
                hasher.hash(third.refreshToken()));
        Instant alreadyRevoked = revokedAt(third.refreshToken());
        jdbc.update("UPDATE member SET last_selected_baby_id = ? WHERE member_id = ?", 777L, memberId);
        long socialCount = jdbc.queryForObject("SELECT count(*) FROM social_account WHERE member_id = ?",
                Long.class, memberId);

        assertWithdrawalSuccess(withdrawHttp(first.accessToken(), 200));

        var member = memberRow(memberId);
        assertThat(member.get("is_deleted")).isEqualTo(true);
        assertThat(member.get("deleted_at")).isNotNull();
        assertThat(member.get("last_selected_baby_id")).isNull();
        assertThat(revokedAt(first.refreshToken())).isNotNull();
        assertThat(revokedAt(second.refreshToken())).isNotNull();
        assertThat(revokedAt(third.refreshToken())).isEqualTo(alreadyRevoked);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_account WHERE member_id = ?",
                Long.class, memberId)).isEqualTo(socialCount);
        mvc.perform(post("/api/v1/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + second.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").doesNotExist());
        assertThatThrownBy(() -> login.loginOrRegister(socialIdentity))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(IdentityErrorCode.WITHDRAWN_MEMBER));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member WHERE member_id = ?", Long.class, memberId))
                .isEqualTo(1L);
    }

    @Test
    void member04LeavesActiveCaregiversWithoutChangingPastOrOtherRelationships() throws Exception {
        SocialLoginResult account = register();
        long memberId = account.member().memberId();
        long otherMember = register().member().memberId();
        long activeBaby = insertWithdrawalBaby(otherMember, false);
        long leftBaby = insertWithdrawalBaby(otherMember, false);
        long removedBaby = insertWithdrawalBaby(otherMember, false);
        long deletedBaby = insertWithdrawalBaby(otherMember, true);
        insertWithdrawalMembership(activeBaby, memberId, "CAREGIVER", "ACTIVE");
        insertWithdrawalMembership(activeBaby, otherMember, "OWNER", "ACTIVE");
        insertWithdrawalMembership(leftBaby, memberId, "CAREGIVER", "LEFT");
        insertWithdrawalMembership(removedBaby, memberId, "CAREGIVER", "REMOVED");
        insertWithdrawalMembership(deletedBaby, memberId, "CAREGIVER", "ACTIVE");
        var leftBefore = withdrawalMembershipRow(leftBaby, memberId);
        var removedBefore = withdrawalMembershipRow(removedBaby, memberId);
        var deletedBefore = withdrawalMembershipRow(deletedBaby, memberId);
        var otherBefore = withdrawalMembershipRow(activeBaby, otherMember);

        assertWithdrawalSuccess(withdrawHttp(account.accessToken(), 200));

        var active = withdrawalMembershipRow(activeBaby, memberId);
        assertThat(active.get("status")).isEqualTo("LEFT");
        assertThat(active.get("ended_at")).isNotNull();
        assertThat(withdrawalMembershipRow(leftBaby, memberId)).isEqualTo(leftBefore);
        assertThat(withdrawalMembershipRow(removedBaby, memberId)).isEqualTo(removedBefore);
        assertThat(withdrawalMembershipRow(deletedBaby, memberId)).isEqualTo(deletedBefore);
        assertThat(withdrawalMembershipRow(activeBaby, otherMember)).isEqualTo(otherBefore);
    }

    @Test
    void member04ActiveOwnerBlocksEveryWithdrawalChange() throws Exception {
        SocialLoginResult account = register();
        long memberId = account.member().memberId();
        long otherMember = register().member().memberId();
        long ownedBaby = insertWithdrawalBaby(memberId, false);
        long caregiverBaby = insertWithdrawalBaby(otherMember, false);
        insertWithdrawalMembership(ownedBaby, memberId, "OWNER", "ACTIVE");
        insertWithdrawalMembership(caregiverBaby, memberId, "CAREGIVER", "ACTIVE");
        var memberBefore = memberRow(memberId);
        var caregiverBefore = withdrawalMembershipRow(caregiverBaby, memberId);

        String response = withdrawHttp(account.accessToken(), 409);

        assertThat(response).contains("\"success\":false", "\"data\":null")
                .doesNotContain("errorCode");
        assertThat(memberRow(memberId)).isEqualTo(memberBefore);
        assertThat(withdrawalMembershipRow(caregiverBaby, memberId)).isEqualTo(caregiverBefore);
        assertThat(revokedAt(account.refreshToken())).isNull();
        assertThat(reissue.reissue(new ReissueTokenCommand(account.refreshToken())).refreshToken()).isNotBlank();
    }

    @Test
    void member04DeletedBabyOwnerHistoryDoesNotBlockWithdrawal() throws Exception {
        SocialLoginResult account = register();
        long memberId = account.member().memberId();
        long deletedBaby = insertWithdrawalBaby(memberId, true);
        insertWithdrawalMembership(deletedBaby, memberId, "OWNER", "ACTIVE");
        var pastMembership = withdrawalMembershipRow(deletedBaby, memberId);

        assertWithdrawalSuccess(withdrawHttp(account.accessToken(), 200));

        assertThat(memberRow(memberId).get("is_deleted")).isEqualTo(true);
        assertThat(withdrawalMembershipRow(deletedBaby, memberId)).isEqualTo(pastMembership);
    }

    @Test
    void member04OwnedImageIsRemovedAfterCommitAndProviderImageIsNotDeleted() throws Exception {
        SocialLoginResult owned = register();
        long ownedId = owned.member().memberId();
        String ownedUrl = "https://images.example/members/" + ownedId + "/profile/" + UUID.randomUUID() + ".png";
        jdbc.update("UPDATE member SET profile_image_url = ? WHERE member_id = ?", ownedUrl, ownedId);

        assertWithdrawalSuccess(withdrawHttp(owned.accessToken(), 200));
        assertThat(memberRow(ownedId).get("profile_image_url")).isNull();
        assertThat(profileImages.deleted).contains(ownedUrl);
        assertThat(profileImages.deletedDuringTransaction.getLast()).isFalse();

        SocialLoginResult provider = register();
        long providerId = provider.member().memberId();
        String providerUrl = "https://provider.example/" + UUID.randomUUID() + "/avatar.png";
        jdbc.update("UPDATE member SET profile_image_url = ? WHERE member_id = ?", providerUrl, providerId);
        int attempts = profileImages.deletedDuringTransaction.size();

        assertWithdrawalSuccess(withdrawHttp(provider.accessToken(), 200));
        assertThat(profileImages.deletedDuringTransaction).hasSize(attempts);
        assertThat(profileImages.deleted).doesNotContain(providerUrl);
        assertThat(memberRow(providerId).get("profile_image_url")).isNull();
    }

    @Test
    void member04ImageCleanupFailureKeepsSuccessfulWithdrawal() throws Exception {
        SocialLoginResult account = register();
        long memberId = account.member().memberId();
        String ownedUrl = "https://images.example/members/" + memberId + "/profile/" + UUID.randomUUID() + ".png";
        jdbc.update("UPDATE member SET profile_image_url = ? WHERE member_id = ?", ownedUrl, memberId);
        profileImages.failNextDelete.set(true);

        assertWithdrawalSuccess(withdrawHttp(account.accessToken(), 200));

        assertThat(memberRow(memberId).get("is_deleted")).isEqualTo(true);
        assertThat(memberRow(memberId).get("profile_image_url")).isNull();
        assertThat(profileImages.deleted).doesNotContain(ownedUrl);
        assertThat(profileImages.deletedDuringTransaction.getLast()).isFalse();
    }

    @Test
    void member04RequiresAuthenticationAndRejectsAlreadyWithdrawnMember() throws Exception {
        SocialLoginResult account = register();
        assertThat(withdrawHttp(null, 401)).doesNotContain("errorCode");
        assertThat(withdrawHttp("invalid.jwt", 401)).doesNotContain("errorCode");
        assertThat(withdrawHttp(signedToken("9223372036854775807", "dearus",
                Instant.now().plusSeconds(600)), 401)).doesNotContain("errorCode");
        assertWithdrawalSuccess(withdrawHttp(account.accessToken(), 200));
        assertThat(withdrawHttp(account.accessToken(), 403)).doesNotContain("errorCode");
    }

    @Test
    void member04ConcurrentReissueCannotLeaveActiveSessionAfterWithdrawal() throws Exception {
        SocialLoginResult account = register();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> withdrawal = executor.submit(() -> {
                start.await();
                return withdrawHttp(account.accessToken(), 200);
            });
            Future<Object> rotation = executor.submit(() -> {
                start.await();
                try {
                    return reissue.reissue(new ReissueTokenCommand(account.refreshToken()));
                } catch (BusinessException error) {
                    return error;
                }
            });
            start.countDown();
            assertWithdrawalSuccess(withdrawal.get(20, TimeUnit.SECONDS));
            assertThat(rotation.get(20, TimeUnit.SECONDS))
                    .isInstanceOfAny(ReissueTokenResult.class, BusinessException.class);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token "
                + "WHERE member_id = ? AND revoked_at IS NULL", Long.class, account.member().memberId()))
                .isZero();
    }

    @Test
    void member04ConcurrentBabyCreationNeverLeavesWithdrawnOwner() throws Exception {
        SocialLoginResult account = register();
        long memberId = account.member().memberId();
        CountDownLatch start = new CountDownLatch(1);
        int withdrawalStatus;
        int creationStatus;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> withdrawal = executor.submit(() -> {
                start.await();
                return mvc.perform(delete("/api/v1/members/me")
                        .header("Authorization", "Bearer " + account.accessToken()))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> creation = executor.submit(() -> {
                start.await();
                return mvc.perform(multipart("/api/v1/babies")
                        .file(new MockMultipartFile("request", "request.json", MediaType.APPLICATION_JSON_VALUE,
                                "{\"babyName\":\"동시성\",\"birthDate\":\"2020-01-01\",\"gender\":\"MALE\"}"
                                        .getBytes()))
                        .header("Authorization", "Bearer " + account.accessToken()))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();
            withdrawalStatus = withdrawal.get(20, TimeUnit.SECONDS);
            creationStatus = creation.get(20, TimeUnit.SECONDS);
        }
        assertThat(List.of(withdrawalStatus, creationStatus))
                .isIn(List.of(200, 403), List.of(409, 201));
        long ownerCount = jdbc.queryForObject("SELECT count(*) FROM baby_member membership "
                + "JOIN baby ON baby.baby_id = membership.baby_id WHERE membership.member_id = ? "
                + "AND membership.role = 'OWNER' AND membership.status = 'ACTIVE' "
                + "AND baby.is_deleted = false", Long.class, memberId);
        boolean deleted = (boolean) memberRow(memberId).get("is_deleted");
        assertThat(deleted && ownerCount > 0).isFalse();
    }

    @Test
    void member04CaregiverFailureRollsBackMemberAndTokens() throws Exception {
        WithdrawalFixture fixture = withdrawalFixture();
        assertWithdrawalFailureRollsBack(fixture, "baby_member", "member04_reject_caregiver");
    }

    @Test
    void member04TokenFailureRollsBackCaregiverAndMember() throws Exception {
        WithdrawalFixture fixture = withdrawalFixture();
        assertWithdrawalFailureRollsBack(fixture, "refresh_token", "member04_reject_token");
    }

    @Test
    void member04MemberFailureRollsBackCaregiverAndTokens() throws Exception {
        WithdrawalFixture fixture = withdrawalFixture();
        assertWithdrawalFailureRollsBack(fixture, "member", "member04_reject_member");
    }

    private WithdrawalFixture withdrawalFixture() {
        SocialLoginResult account = register();
        long memberId = account.member().memberId();
        long ownerId = register().member().memberId();
        long babyId = insertWithdrawalBaby(ownerId, false);
        insertWithdrawalMembership(babyId, memberId, "CAREGIVER", "ACTIVE");
        String imageUrl = "https://images.example/members/" + memberId + "/profile/" + UUID.randomUUID() + ".png";
        jdbc.update("UPDATE member SET profile_image_url = ?, last_selected_baby_id = ? WHERE member_id = ?",
                imageUrl, babyId, memberId);
        return new WithdrawalFixture(account, babyId, imageUrl);
    }

    private void assertWithdrawalFailureRollsBack(WithdrawalFixture fixture, String table, String triggerName)
            throws Exception {
        long memberId = fixture.account().member().memberId();
        var memberBefore = memberRow(memberId);
        var caregiverBefore = withdrawalMembershipRow(fixture.babyId(), memberId);
        jdbc.execute("CREATE TRIGGER " + triggerName + " BEFORE UPDATE ON " + table
                + " FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced withdrawal failure'");
        try {
            assertThat(withdrawHttp(fixture.account().accessToken(), 500)).doesNotContain("errorCode");
        } finally {
            jdbc.execute("DROP TRIGGER " + triggerName);
        }
        assertThat(memberRow(memberId)).isEqualTo(memberBefore);
        assertThat(withdrawalMembershipRow(fixture.babyId(), memberId)).isEqualTo(caregiverBefore);
        assertThat(revokedAt(fixture.account().refreshToken())).isNull();
        assertThat(profileImages.deleted).doesNotContain(fixture.imageUrl());
    }

    private String withdrawHttp(String token, int expectedStatus) throws Exception {
        var request = delete("/api/v1/members/me");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private void assertWithdrawalSuccess(String response) {
        assertThat(response).isEqualTo("{\"success\":true,\"data\":null,\"message\":null}");
    }

    private java.util.Map<String, Object> memberRow(long memberId) {
        return jdbc.queryForMap("SELECT member_name, profile_image_url, last_selected_baby_id, "
                + "is_deleted, deleted_at FROM member WHERE member_id = ?", memberId);
    }

    private java.util.Map<String, Object> withdrawalMembershipRow(long babyId, long memberId) {
        return jdbc.queryForMap("SELECT role, status, joined_at, ended_at FROM baby_member "
                + "WHERE baby_id = ? AND member_id = ?", babyId, memberId);
    }

    private long insertWithdrawalBaby(long creatorId, boolean deleted) {
        Instant now = Instant.now();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO baby "
                    + "(created_by_member_id, baby_name, birth_date, gender, is_deleted, "
                    + "created_at, updated_at, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"baby_id"});
            statement.setLong(1, creatorId);
            statement.setString(2, "테스트 아기");
            statement.setDate(3, Date.valueOf("2020-01-01"));
            statement.setString(4, "MALE");
            statement.setBoolean(5, deleted);
            statement.setTimestamp(6, Timestamp.from(now));
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setTimestamp(8, deleted ? Timestamp.from(now) : null);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private void insertWithdrawalMembership(long babyId, long memberId, String role, String status) {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO baby_member (baby_id, member_id, role, status, joined_at, ended_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", babyId, memberId, role, status,
                Timestamp.from(now), "ACTIVE".equals(status) ? null : Timestamp.from(now));
    }

    private record WithdrawalFixture(SocialLoginResult account, long babyId, String imageUrl) {
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder profilePatch(
            String json, MockMultipartFile image
    ) {
        var request = multipart(HttpMethod.PATCH, "/api/v1/members/me")
                .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes()));
        if (image != null) {
            request.file(image);
        }
        return request;
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("profileImage", "../../traversal.png", "image/png",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
    }

    private void assertUnauthorizedProfile(String authorization) throws Exception {
        var request = get("/api/v1/members/me");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        String response = mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).isEqualTo(
                "{\"success\":false,\"data\":null,\"message\":\"인증 정보가 유효하지 않습니다.\"}"
        );
    }

    private String signedToken(String subject, String issuer, Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(Instant.now().minusSeconds(3600));
        if (expiresAt != null) {
            claims.expiresAt(expiresAt);
        }
        if (subject != null) {
            claims.subject(subject);
        }
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims.build()
        )).getTokenValue();
    }

    private SocialLoginResult register() {
        return login.loginOrRegister(identity());
    }

    private VerifiedSocialIdentity identity() {
        return new VerifiedSocialIdentity(
                SocialProvider.GOOGLE,
                UUID.randomUUID().toString(),
                null,
                "사용자",
                null
        );
    }

    private String logoutHttp(String rawToken, String authorization) throws Exception {
        var request = post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + rawToken + "\"}");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Instant revokedAt(String rawToken) {
        return jdbc.queryForObject("SELECT revoked_at FROM refresh_token WHERE token_hash = ?",
                (result, row) -> {
                    java.sql.Timestamp value = result.getTimestamp(1);
                    return value == null ? null : value.toInstant();
                }, hasher.hash(rawToken));
    }

    private void assertInvalid(String rawToken) {
        assertThatThrownBy(() -> reissue.reissue(new ReissueTokenCommand(rawToken)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(IdentityErrorCode.INVALID_REFRESH_TOKEN));
    }

    @TestConfiguration
    static class GeneratorConfig {

        @Bean
        @Primary
        ControlledGenerator controlledGenerator() {
            return new ControlledGenerator();
        }

        @Bean
        @Primary
        FakeProfileImageStorage fakeProfileImageStorage() {
            return new FakeProfileImageStorage();
        }
    }

    static class FakeProfileImageStorage implements ProfileImageStoragePort {

        private final List<String> deleted = new CopyOnWriteArrayList<>();
        private final List<Boolean> deletedDuringTransaction = new CopyOnWriteArrayList<>();
        private final AtomicBoolean failNextStore = new AtomicBoolean();
        private final AtomicBoolean failNextDelete = new AtomicBoolean();

        @Override
        public StoredProfileImage store(Long memberId, ProfileImageUpload image) {
            if (failNextStore.getAndSet(false)) {
                throw new IllegalStateException("private object storage details");
            }
            return new StoredProfileImage("https://images.example/members/" + memberId + "/profile/test.png");
        }

        @Override
        public boolean owns(Long memberId, String imageUrl) {
            return imageUrl != null
                    && imageUrl.startsWith("https://images.example/members/" + memberId + "/profile/");
        }

        @Override
        public String readUrl(Long memberId, String storedImageUrl) {
            return storedImageUrl;
        }

        @Override
        public void deleteOwnedImage(Long memberId, String imageUrl) {
            deletedDuringTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (failNextDelete.getAndSet(false)) {
                throw new IllegalStateException("cleanup unavailable");
            }
            if (owns(memberId, imageUrl)) {
                deleted.add(imageUrl);
            }
        }
    }

    static class ControlledGenerator implements RefreshTokenGenerator {

        private final AtomicReference<String> forced = new AtomicReference<>();
        private final SecureRefreshTokenGenerator delegate = new SecureRefreshTokenGenerator();

        void forceNext(String token) {
            forced.set(token);
        }

        @Override
        public String generate() {
            String value = forced.getAndSet(null);
            return value == null ? delegate.generate() : value;
        }
    }
}
