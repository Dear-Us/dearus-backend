package com.dearusback.identity.infrastructure.token;

import com.dearusback.identity.application.model.IssuedAccessToken;
import com.dearusback.identity.application.port.out.AccessTokenIssuer;
import com.dearusback.identity.infrastructure.config.AuthProperties;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;

    public JwtAccessTokenIssuer(JwtEncoder jwtEncoder, AuthProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @Override
    public IssuedAccessToken issue(Long memberId, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(memberId.toString())
                .issuer(properties.jwt().issuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, properties.accessTokenTtl().toSeconds());
    }
}
