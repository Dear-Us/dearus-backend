package com.dearusback.identity.infrastructure.config;

import com.dearusback.identity.application.model.AuthTokenPolicy;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuthTokenPolicy authTokenPolicy(AuthProperties properties) {
        return new AuthTokenPolicy(properties.refreshTokenTtl());
    }

    @Bean
    JwtEncoder jwtEncoder(AuthProperties properties) {
        ImmutableSecret<SecurityContext> secret = new ImmutableSecret<>(jwtSecretKey(properties));
        return new NimbusJwtEncoder(secret);
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            String subject = jwt.getSubject();
            if (jwt.getExpiresAt() == null || subject == null || !subject.matches("[1-9][0-9]*")) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            }
            try {
                Long.parseLong(subject);
                return OAuth2TokenValidatorResult.success();
            } catch (NumberFormatException exception) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator(properties.jwt().issuer()),
                requiredClaims
        ));
        return decoder;
    }

    private SecretKey jwtSecretKey(AuthProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.jwt().secretBase64());
        if (keyBytes.length < 32) {
            throw new IllegalStateException("DEARUS_JWT_SECRET_BASE64 must contain at least 256 bits");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}
