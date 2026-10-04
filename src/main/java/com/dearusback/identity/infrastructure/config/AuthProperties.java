package com.dearusback.identity.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "dearus.auth")
public record AuthProperties(
        @Valid @NotNull Jwt jwt,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @Valid @NotNull Google google,
        @Valid @NotNull Kakao kakao,
        @Valid @NotNull Naver naver
) {

    public record Jwt(
            @NotBlank String issuer,
            @NotBlank String secretBase64
    ) {
    }

    public record Google(
            @NotBlank String clientId,
            @NotNull URI jwkSetUri
    ) {
    }

    public record Kakao(
            @Positive long appId,
            @NotNull URI baseUrl
    ) {
    }

    public record Naver(@NotNull URI baseUrl) {
    }
}
