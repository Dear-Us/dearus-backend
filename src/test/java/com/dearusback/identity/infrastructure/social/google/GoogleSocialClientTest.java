package com.dearusback.identity.infrastructure.social.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.infrastructure.config.AuthProperties;
import com.dearusback.support.domain.exception.BusinessException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class GoogleSocialClientTest {

    @Test
    void rejectsGoogleLoginWhenClientIdIsNotConfigured() {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Jwt("dearus", "unused-test-secret"),
                Duration.ofMinutes(30),
                Duration.ofDays(30),
                new AuthProperties.Google("", URI.create("https://example.com/jwks")),
                new AuthProperties.Kakao(1234, URI.create("https://example.com")),
                new AuthProperties.Naver(URI.create("https://example.com"))
        );
        GoogleSocialClient client = new GoogleSocialClient(properties);

        assertThatThrownBy(() -> client.verify("google-id-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
                        .isEqualTo(IdentityErrorCode.SOCIAL_PROVIDER_UNAVAILABLE));
    }

    @Test
    void mapsVerifiedGoogleSubjectAndProfile() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        GoogleSocialClient client = new GoogleSocialClient(decoder);
        Jwt jwt = new Jwt(
                "token",
                Instant.parse("2026-10-04T00:00:00Z"),
                Instant.parse("2026-10-04T01:00:00Z"),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", "google-user-1",
                        "email", "user@example.com",
                        "name", "Google User",
                        "picture", "https://image.example/google.png"
                )
        );
        when(decoder.decode("google-id-token")).thenReturn(jwt);

        VerifiedSocialIdentity identity = client.verify("google-id-token");

        assertThat(identity.providerUserId()).isEqualTo("google-user-1");
        assertThat(identity.email()).isEqualTo("user@example.com");
        assertThat(identity.displayName()).isEqualTo("Google User");
    }

    @Test
    void mapsInvalidGoogleTokenToAuthenticationFailure() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        GoogleSocialClient client = new GoogleSocialClient(decoder);
        when(decoder.decode("invalid-token")).thenThrow(new JwtException("invalid"));

        assertThatThrownBy(() -> client.verify("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).errorCode())
                        .isEqualTo(IdentityErrorCode.INVALID_SOCIAL_TOKEN));
    }
}
