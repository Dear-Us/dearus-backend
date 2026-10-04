package com.dearusback.identity.infrastructure.storage;

import com.dearusback.identity.application.model.ProfileImageUpload;
import com.dearusback.identity.application.model.StoredProfileImage;
import com.dearusback.identity.application.port.out.ProfileImageStoragePort;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public final class S3ProfileImageStorageAdapter implements ProfileImageStoragePort {

    private static final Pattern GENERATED_FILENAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)"
    );

    private final S3Client client;
    private final String bucket;
    private final String publicBaseUrl;
    private final Supplier<UUID> ids;

    public S3ProfileImageStorageAdapter(S3Client client, String bucket, String publicBaseUrl) {
        this(client, bucket, publicBaseUrl, UUID::randomUUID);
    }

    S3ProfileImageStorageAdapter(S3Client client, String bucket, String publicBaseUrl, Supplier<UUID> ids) {
        this.client = Objects.requireNonNull(client, "client");
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        this.bucket = bucket;
        this.publicBaseUrl = normalizePublicBaseUrl(publicBaseUrl);
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    @Override
    public StoredProfileImage store(Long memberId, ProfileImageUpload image) {
        String key = "members/" + memberId + "/profile/" + ids.get() + "." + image.extension();
        try {
            client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(image.contentType())
                    .build(), RequestBody.fromBytes(image.bytes()));
        } catch (SdkException exception) {
            throw new IdentityException(IdentityErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE);
        }
        return new StoredProfileImage(publicBaseUrl + "/" + key);
    }

    @Override
    public void deleteOwnedImage(Long memberId, String imageUrl) {
        if (!owns(memberId, imageUrl)) {
            return;
        }
        String prefix = publicBaseUrl + "/members/" + memberId + "/profile/";
        String filename = imageUrl.substring(prefix.length());
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key("members/" + memberId + "/profile/" + filename)
                .build());
    }

    @Override
    public boolean owns(Long memberId, String imageUrl) {
        String prefix = publicBaseUrl + "/members/" + memberId + "/profile/";
        return imageUrl != null && imageUrl.startsWith(prefix)
                && GENERATED_FILENAME.matcher(imageUrl.substring(prefix.length())).matches();
    }

    private static String normalizePublicBaseUrl(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("PROFILE_IMAGE_STORAGE_PUBLIC_BASE_URL must be configured");
        }
        URI uri = URI.create(configured);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getUserInfo() != null || !uri.normalize().equals(uri)) {
            throw new IllegalArgumentException("Invalid profile image public base URL");
        }
        return configured.replaceAll("/+$", "");
    }
}
