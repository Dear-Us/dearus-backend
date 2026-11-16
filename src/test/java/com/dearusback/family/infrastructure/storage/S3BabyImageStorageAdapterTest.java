package com.dearusback.family.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearusback.family.application.model.BabyImageUpload;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.support.domain.exception.BusinessException;
import java.util.UUID;
import java.time.Duration;
import java.net.URL;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

class S3BabyImageStorageAdapterTest {

    private final S3Client client = mock(S3Client.class);
    private final UUID id = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private final S3BabyImageStorageAdapter storage = new S3BabyImageStorageAdapter(
            client, mock(S3Presigner.class), "baby-bucket", "https://images.example", Duration.ofHours(1), () -> id);

    @Test
    void storesWithServerGeneratedKeyAndDeletesOnlyOwnedUrl() {
        BabyImageUpload image = BabyImageUpload.validate(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff},
                "image/jpeg");
        String url = storage.store(image);
        assertThat(url).isEqualTo("https://images.example/babies/profile/" + id + ".jpg");
        ArgumentCaptor<PutObjectRequest> uploaded = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(uploaded.capture(), any(RequestBody.class));
        assertThat(uploaded.getValue().key()).isEqualTo("babies/profile/" + id + ".jpg");
        assertThat(uploaded.getValue().contentType()).isEqualTo("image/jpeg");

        storage.deleteOwnedImage("https://foreign.example/babies/profile/" + id + ".jpg");
        storage.deleteOwnedImage("https://images.example/babies/profile/../../bad.jpg");
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
        storage.deleteOwnedImage(url);
        ArgumentCaptor<DeleteObjectRequest> deleted = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(deleted.capture());
        assertThat(deleted.getValue().key()).isEqualTo("babies/profile/" + id + ".jpg");
    }

    @Test
    void sdkFailureMapsToStorageUnavailable() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(503).build());
        BabyImageUpload image = BabyImageUpload.validate(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff},
                "image/jpeg");
        assertThatThrownBy(() -> storage.store(image))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(FamilyErrorCode.BABY_IMAGE_STORAGE_UNAVAILABLE));
    }

    @Test
    void presignsOnlyTheManagedBabyObject() throws Exception {
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedGetObjectRequest signed = mock(PresignedGetObjectRequest.class);
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);
        when(signed.url()).thenReturn(new URL("https://signed.example/baby?X-Amz-Signature=test"));
        var adapter = new S3BabyImageStorageAdapter(client, presigner, "baby-bucket", "https://images.example",
                Duration.ofHours(1), () -> id);

        assertThat(adapter.readUrl("https://images.example/babies/profile/" + id + ".jpg"))
                .isEqualTo("https://signed.example/baby?X-Amz-Signature=test");
        ArgumentCaptor<GetObjectPresignRequest> request = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(request.capture());
        assertThat(request.getValue().getObjectRequest().bucket()).isEqualTo("baby-bucket");
        assertThat(request.getValue().getObjectRequest().key()).isEqualTo("babies/profile/" + id + ".jpg");
        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void externalNullMalformedAndSpoofedUrlsAreNeverPresignedOrDeleted() {
        S3Presigner presigner = mock(S3Presigner.class);
        var adapter = new S3BabyImageStorageAdapter(client, presigner, "baby-bucket", "https://images.example",
                Duration.ofHours(1), () -> id);
        String[] passthrough = {null, "https://other.example/photo.jpg", "not a URI",
                "https://images.example.evil.example/babies/profile/" + id + ".jpg",
                "https://images.example/babies/profile/" + id + ".jpg?extra=1",
                "https://images.example/babies/profile/%2e%2e/" + id + ".jpg"};
        for (String value : passthrough) {
            assertThat(adapter.readUrl(value)).isEqualTo(value);
            adapter.deleteOwnedImage(value);
        }
        verify(presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void realSdkPresignerCreatesABrowserGetUrlWithoutCallingS3() {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build()) {
            var adapter = new S3BabyImageStorageAdapter(client, presigner, "baby-bucket",
                    "https://images.example", Duration.ofHours(1), () -> id);

            String readUrl = adapter.readUrl("https://images.example/babies/profile/" + id + ".jpg");

            assertThat(readUrl).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256", "X-Amz-Expires=3600");
            assertThat(readUrl).contains("babies/profile/" + id + ".jpg");
        }
    }
}
