package com.dearusback.family.infrastructure.token;

import com.dearusback.family.application.port.out.InvitationUrlPort;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InvitationUrlAdapter implements InvitationUrlPort {

    private final String baseUrl;

    public InvitationUrlAdapter(@Value("${dearus.invitation.base-url:}") String configuredBaseUrl) {
        this.baseUrl = configuredBaseUrl;
    }

    @Override
    public String createUrl(String rawToken) {
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("Invitation base URL is not configured");
        }
        URI uri = URI.create(baseUrl);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !uri.normalize().equals(uri)) {
            throw new IllegalStateException("Invitation base URL is invalid");
        }
        return baseUrl + "?token=" + rawToken;
    }
}
