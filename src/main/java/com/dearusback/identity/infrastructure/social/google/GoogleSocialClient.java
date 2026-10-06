package com.dearusback.identity.infrastructure.social.google;

import com.dearusback.identity.application.model.VerifiedSocialIdentity;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import com.dearusback.identity.domain.type.SocialProvider;
import com.dearusback.identity.infrastructure.config.AuthProperties;
import com.dearusback.identity.infrastructure.social.SocialProviderClient;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

@Component
public class GoogleSocialClient implements SocialProviderClient {

    private static final Set<String> ALLOWED_ISSUERS = Set.of(
            "accounts.google.com",
            "https://accounts.google.com"
    );

    private final JwtDecoder jwtDecoder;
    private final boolean configured;

    @Autowired
    public GoogleSocialClient(AuthProperties properties) {
        this.configured = properties.google().clientId() != null
                && !properties.google().clientId().isBlank();
        this.jwtDecoder = buildDecoder(properties);
    }

    GoogleSocialClient(JwtDecoder jwtDecoder) {
        this.configured = true;
        this.jwtDecoder = jwtDecoder;
    }

    private NimbusJwtDecoder buildDecoder(AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.google().jwkSetUri().toString())
                .build();
        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator();
        OAuth2TokenValidator<Jwt> issuerValidator = new JwtClaimValidator<>(
                JwtClaimNames.ISS,
                ALLOWED_ISSUERS::contains
        );
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                audience -> audience != null && audience.contains(properties.google().clientId())
        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                issuerValidator,
                audienceValidator
        ));
        return decoder;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public VerifiedSocialIdentity verify(String providerToken) {
        if (!configured) {
            throw new IdentityException(IdentityErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
        try {
            Jwt jwt = jwtDecoder.decode(providerToken);
            String subject = jwt.getSubject();
            if (subject == null || subject.isBlank()) {
                throw invalidToken();
            }
            return new VerifiedSocialIdentity(
                    SocialProvider.GOOGLE,
                    subject,
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("picture")
            );
        } catch (JwtException exception) {
            if (hasUnavailableCause(exception)) {
                throw new IdentityException(IdentityErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
            }
            throw invalidToken();
        }
    }

    private boolean hasUnavailableCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IOException
                    || current.getClass().getSimpleName().equals("RemoteKeySourceException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private IdentityException invalidToken() {
        return new IdentityException(IdentityErrorCode.INVALID_SOCIAL_TOKEN);
    }
}
