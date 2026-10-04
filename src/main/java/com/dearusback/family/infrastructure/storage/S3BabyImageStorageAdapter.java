package com.dearusback.family.infrastructure.storage;

import com.dearusback.family.application.model.BabyImageUpload;
import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public final class S3BabyImageStorageAdapter implements BabyProfileImageStoragePort {

    private static final Pattern GENERATED_FILENAME = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)"
    );
    private final S3Client client;
    private final String bucket;
    private final String publicBaseUrl;
    private final Supplier<UUID> ids;

    public S3BabyImageStorageAdapter(S3Client client, String bucket, String publicBaseUrl) {
        this(client, bucket, publicBaseUrl, UUID::randomUUID);
    }

    S3BabyImageStorageAdapter(S3Client client, String bucket, String publicBaseUrl, Supplier<UUID> ids) {
        this.client = Objects.requireNonNull(client);
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        this.bucket = bucket;
        this.publicBaseUrl = normalizePublicBaseUrl(publicBaseUrl);
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
        String prefix = publicBaseUrl + "/babies/profile/";
        if (imageUrl == null || !imageUrl.startsWith(prefix)) {
            return;
        }
        String filename = imageUrl.substring(prefix.length());
        if (!GENERATED_FILENAME.matcher(filename).matches()) {
            return;
        }
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket)
                .key("babies/profile/" + filename).build());
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
