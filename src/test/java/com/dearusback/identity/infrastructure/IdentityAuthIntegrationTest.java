package com.dearusback.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearusback.DearUsBackApplication;
import com.dearusback.identity.application.model.SocialLoginCommand;
import com.dearusback.identity.application.model.SocialLoginResult;
import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.application.service.SocialLoginService;
import com.dearusback.identity.application.port.out.RefreshTokenHasher;
import com.dearusback.identity.application.service.SocialLoginTransactionService;
import com.dearusback.identity.domain.type.SocialProvider;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@SpringBootTest(classes = DearUsBackApplication.class)
@Transactional
class IdentityAuthIntegrationTest {

    private static final String TEST_SECRET_BASE64 =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Autowired
    private SocialLoginTransactionService transactionService;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sameEmailAcrossProvidersCreatesSeparateMembers() {
        transactionService.loginOrRegister(identity(SocialProvider.GOOGLE, "google-integration", "same@example.com"));
        transactionService.loginOrRegister(identity(SocialProvider.KAKAO, "kakao-integration", "same@example.com"));

        Integer memberCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT member_id)
                FROM social_account
                WHERE provider_user_id IN ('google-integration', 'kakao-integration')
                """, Integer.class);
        Integer accountCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM social_account
                WHERE provider_user_id IN ('google-integration', 'kakao-integration')
                """, Integer.class);

        assertThat(memberCount).isEqualTo(2);
        assertThat(accountCount).isEqualTo(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFirstLoginCreatesOnlyOneMember() throws Exception {
        VerifiedSocialIdentity identity = identity(
                SocialProvider.GOOGLE,
                "google-concurrent",
                "concurrent@example.com"
        );
        CyclicBarrier barrier = new CyclicBarrier(2);
        SocialLoginService loginService = new SocialLoginService((provider, token) -> {
            try {
                barrier.await();
                return identity;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, transactionService);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<SocialLoginResult> first = executor.submit(() -> loginService.login(
                    new SocialLoginCommand(SocialProvider.GOOGLE, "first-token")
            ));
            Future<SocialLoginResult> second = executor.submit(() -> loginService.login(
                    new SocialLoginCommand(SocialProvider.GOOGLE, "second-token")
            ));

            List<SocialLoginResult> results = List.of(first.get(), second.get());

            Integer memberCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(DISTINCT member_id)
                    FROM social_account
                    WHERE provider = 'GOOGLE' AND provider_user_id = 'google-concurrent'
                    """, Integer.class);
            assertThat(memberCount).isOne();
            assertThat(results).hasSize(2);
            assertThat(results).extracting(SocialLoginResult::newMember)
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void storesOnlyRefreshTokenHash() {
        SocialLoginResult result = transactionService.loginOrRegister(identity(
                SocialProvider.GOOGLE,
                "google-refresh",
                null
        ));

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM refresh_token WHERE member_id = ?",
                String.class,
                result.member().memberId()
        );

        assertThat(storedHash)
                .isNotEqualTo(result.refreshToken())
                .isEqualTo(refreshTokenHasher.hash(result.refreshToken()))
                .hasSize(64);
    }

    @Test
    void accessTokenContainsOnlyIdentityClaims() {
        SocialLoginResult result = transactionService.loginOrRegister(identity(
                SocialProvider.NAVER,
                "naver-jwt",
                null
        ));

        byte[] secretBytes = Base64.getDecoder().decode(TEST_SECRET_BASE64);
        SecretKey key = new SecretKeySpec(secretBytes, "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        Jwt jwt = decoder.decode(result.accessToken());

        assertThat(jwt.getSubject()).isEqualTo(result.member().memberId().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("dearus");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
        assertThat(jwt.getClaims()).containsOnlyKeys("sub", "iss", "iat", "exp");
        assertThat(jwt.getClaims()).doesNotContainKeys("babyId", "OWNER", "CAREGIVER", "babyMemberRole");
    }

    private VerifiedSocialIdentity identity(SocialProvider provider, String id, String email) {
        return new VerifiedSocialIdentity(provider, id, email, "사용자", null);
    }
}
