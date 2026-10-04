package com.dearusback.identity.infrastructure.config;

import com.dearusback.identity.application.model.AuthTokenPolicy;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

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
        byte[] keyBytes = Base64.getDecoder().decode(properties.jwt().secretBase64());
        if (keyBytes.length < 32) {
            throw new IllegalStateException("DEARUS_JWT_SECRET_BASE64 must contain at least 256 bits");
        }
        SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        ImmutableSecret<SecurityContext> secret = new ImmutableSecret<>(secretKey);
        return new NimbusJwtEncoder(secret);
    }
}
