package com.dearusback.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dearusback.DearUsBackApplication;
import com.dearusback.identity.application.model.ReissueTokenCommand;
import com.dearusback.identity.application.model.ReissueTokenResult;
import com.dearusback.identity.application.model.SocialLoginResult;
import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.application.port.out.RefreshTokenGenerator;
import com.dearusback.identity.application.port.out.RefreshTokenHasher;
import com.dearusback.identity.application.service.ReissueTokenService;
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
    private RefreshTokenHasher hasher;

    @Autowired
    private ControlledGenerator generator;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

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
