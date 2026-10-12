package com.dearusback.identity.infrastructure.storage;

import com.dearusback.identity.application.port.out.ProfileImageStoragePort;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
@EnableConfigurationProperties(ProfileImageStorageProperties.class)
public class ProfileImageStorageConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "dearus.profile-image.storage", name = "enabled", havingValue = "true")
    S3Client profileImageS3Client(ProfileImageStorageProperties properties) {
        requireConfigured(properties.bucket(), "PROFILE_IMAGE_STORAGE_BUCKET");
        requireConfigured(properties.region(), "PROFILE_IMAGE_STORAGE_REGION");
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create());
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "dearus.profile-image.storage", name = "enabled", havingValue = "true")
    ProfileImageStoragePort s3ProfileImageStoragePort(
            @Qualifier("profileImageS3Client") S3Client client, ProfileImageStorageProperties properties) {
        return new S3ProfileImageStorageAdapter(client, properties.bucket(), properties.publicBaseUrl());
    }

    @Bean
    @ConditionalOnProperty(prefix = "dearus.profile-image.storage", name = "enabled", havingValue = "false",
            matchIfMissing = true)
    ProfileImageStoragePort unavailableProfileImageStoragePort() {
        return new UnavailableProfileImageStorageAdapter();
    }

    private static void requireConfigured(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured when profile image storage is enabled");
        }
    }
}
