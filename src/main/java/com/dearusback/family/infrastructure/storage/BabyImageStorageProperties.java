package com.dearusback.family.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dearus.baby-image.storage")
public record BabyImageStorageProperties(boolean enabled, String bucket, String region,
                                         String publicBaseUrl, String endpoint) {
}
