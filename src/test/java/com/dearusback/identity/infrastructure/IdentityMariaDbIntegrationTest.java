package com.dearusback.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.DearUsBackApplication;
import com.dearusback.identity.application.model.ReissueTokenCommand;
import com.dearusback.identity.application.model.LogoutCommand;
import com.dearusback.identity.application.model.ReissueTokenResult;
import com.dearusback.identity.application.model.SocialLoginResult;
import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.application.port.out.RefreshTokenGenerator;
import com.dearusback.identity.application.port.out.RefreshTokenHasher;
import com.dearusback.identity.application.service.ReissueTokenService;
import com.dearusback.identity.application.service.LogoutService;
import com.dearusback.identity.application.service.SocialLoginTransactionService;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.type.SocialProvider;
import com.dearusback.identity.infrastructure.config.AuthProperties;
import com.dearusback.identity.infrastructure.token.SecureRefreshTokenGenerator;
import com.dearusback.support.domain.exception.BusinessException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
        jdbc.update("UPDATE member SET member_name = ?, email = ?, profile_image_url = ?, last_selected_baby_id = ? WHERE member_id = ?",
                "기존 이름", "member@example.com", "https://example.com/profile.png", 77L,
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
                .andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"))
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
