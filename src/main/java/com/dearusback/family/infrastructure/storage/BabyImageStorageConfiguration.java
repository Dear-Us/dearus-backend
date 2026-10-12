package com.dearusback.family.infrastructure.storage;

import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import java.net.URI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
@EnableConfigurationProperties(BabyImageStorageProperties.class)
public class BabyImageStorageConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "dearus.baby-image.storage", name = "enabled", havingValue = "true")
    S3Client babyImageS3Client(BabyImageStorageProperties properties) {
        requireConfigured(properties.bucket(), "BABY_IMAGE_STORAGE_BUCKET");
        requireConfigured(properties.region(), "BABY_IMAGE_STORAGE_REGION");
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "dearus.baby-image.storage", name = "enabled", havingValue = "true")
    BabyProfileImageStoragePort s3BabyImageStoragePort(
            @Qualifier("babyImageS3Client") S3Client client, BabyImageStorageProperties properties) {
        return new S3BabyImageStorageAdapter(client, properties.bucket(), properties.publicBaseUrl());
    }

    @Bean
    @ConditionalOnProperty(prefix = "dearus.baby-image.storage", name = "enabled", havingValue = "false",
            matchIfMissing = true)
    BabyProfileImageStoragePort unavailableBabyImageStoragePort() {
        return new UnavailableBabyImageStorageAdapter();
    }

    private static void requireConfigured(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured when baby image storage is enabled");
        }
    }
}
