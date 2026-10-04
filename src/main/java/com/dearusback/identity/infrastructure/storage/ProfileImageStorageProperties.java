package com.dearusback.identity.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dearus.profile-image.storage")
public record ProfileImageStorageProperties(
        boolean enabled,
        String bucket,
        String region,
        String publicBaseUrl,
        String endpoint
) {
}
