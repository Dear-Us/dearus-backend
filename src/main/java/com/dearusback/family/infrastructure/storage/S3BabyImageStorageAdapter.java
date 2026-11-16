package com.dearusback.family.infrastructure.storage;

import com.dearusback.family.application.model.BabyImageUpload;
import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public final class S3BabyImageStorageAdapter implements BabyProfileImageStoragePort {

    private static final Pattern GENERATED_FILENAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)"
    );
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;
    private final URI publicBaseUri;
    private final Duration readUrlTtl;
    private final Supplier<UUID> ids;

    public S3BabyImageStorageAdapter(S3Client client, S3Presigner presigner, String bucket,
                                     String publicBaseUrl, Duration readUrlTtl) {
        this(client, presigner, bucket, publicBaseUrl, readUrlTtl, UUID::randomUUID);
    }

    S3BabyImageStorageAdapter(S3Client client, S3Presigner presigner, String bucket,
                              String publicBaseUrl, Duration readUrlTtl, Supplier<UUID> ids) {
        this.client = Objects.requireNonNull(client);
        this.presigner = Objects.requireNonNull(presigner);
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        this.bucket = bucket;
        this.publicBaseUrl = normalizePublicBaseUrl(publicBaseUrl);
        this.publicBaseUri = URI.create(this.publicBaseUrl);
        this.readUrlTtl = requireValidTtl(readUrlTtl);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public String store(BabyImageUpload image) {
        String key = "babies/profile/" + ids.get() + "." + image.extension();
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType(image.contentType()).build(), RequestBody.fromBytes(image.bytes()));
        } catch (SdkException exception) {
            throw new FamilyException(FamilyErrorCode.BABY_IMAGE_STORAGE_UNAVAILABLE);
        }
        return publicBaseUrl + "/" + key;
    }

    @Override
    public void deleteOwnedImage(String imageUrl) {
        String key = ownedKey(imageUrl);
        if (key == null) {
            return;
        }
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket)
                .key(key).build());
    }

    @Override
    public String readUrl(String storedImageUrl) {
        String key = ownedKey(storedImageUrl);
        if (key == null) {
            return storedImageUrl;
        }
        try {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(readUrlTtl)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build()).url().toExternalForm();
        } catch (SdkException exception) {
            throw new FamilyException(FamilyErrorCode.BABY_IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    private String ownedKey(String imageUrl) {
        if (imageUrl == null) {
            return null;
        }
        URI candidate;
        try {
            candidate = URI.create(imageUrl);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (candidate.getScheme() == null || candidate.getHost() == null
                || !publicBaseUri.getScheme().equalsIgnoreCase(candidate.getScheme())
                || !publicBaseUri.getHost().equalsIgnoreCase(candidate.getHost())
                || publicBaseUri.getPort() != candidate.getPort()
                || candidate.getRawUserInfo() != null || candidate.getRawQuery() != null
                || candidate.getRawFragment() != null || !candidate.normalize().equals(candidate)) {
            return null;
        }
        String keyPrefix = "babies/profile/";
        String pathPrefix = publicBaseUri.getRawPath() + "/" + keyPrefix;
        String path = candidate.getRawPath();
        if (path == null || !path.startsWith(pathPrefix)) {
            return null;
        }
        String filename = path.substring(pathPrefix.length());
        return GENERATED_FILENAME.matcher(filename).matches() ? keyPrefix + filename : null;
    }

    private static Duration requireValidTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException("Image read URL TTL must be between 1 second and 7 days");
        }
        return ttl;
    }

    private static String normalizePublicBaseUrl(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("BABY_IMAGE_STORAGE_PUBLIC_BASE_URL must be configured");
        }
        URI uri = URI.create(configured);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getUserInfo() != null || !uri.normalize().equals(uri)) {
            throw new IllegalArgumentException("Invalid baby image public base URL");
        }
        return configured.replaceAll("/+$", "");
    }
}
