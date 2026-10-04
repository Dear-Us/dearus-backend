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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3BabyImageStorageAdapterTest {

    private final S3Client client = mock(S3Client.class);
    private final UUID id = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private final S3BabyImageStorageAdapter storage = new S3BabyImageStorageAdapter(
            client, "baby-bucket", "https://images.example", () -> id);

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
}
