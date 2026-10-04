package com.dearusback.identity.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearusback.identity.application.model.ProfileImageUpload;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.support.domain.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3ProfileImageStorageAdapterTest {

    private static final UUID ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String BASE = "https://cdn.example.com/dearus";
    private static final String KEY = "members/7/profile/" + ID + ".png";

    @Test
    void usesGeneratedKeyAndValidatedContentTypeInsteadOfOriginalFilename() {
        S3Client client = mock(S3Client.class);
        var adapter = adapter(client);
        var image = png();

        var stored = adapter.store(7L, image);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("profile-bucket");
        assertThat(request.getValue().key()).isEqualTo(KEY);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(stored.url()).isEqualTo(BASE + "/" + KEY);
    }

    @Test
    void deletesOnlyAnOwnedGeneratedKeyForTheCorrectMember() {
        S3Client client = mock(S3Client.class);
        var adapter = adapter(client);
        adapter.deleteOwnedImage(7L, BASE + "/" + KEY);

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("profile-bucket");
        assertThat(request.getValue().key()).isEqualTo(KEY);
    }

    @Test
    void neverDeletesProviderForeignMemberOrUnrecognizedUrls() {
        S3Client client = mock(S3Client.class);
        var adapter = adapter(client);
        adapter.deleteOwnedImage(7L, "https://k.kakaocdn.net/profile.png");
        adapter.deleteOwnedImage(8L, BASE + "/" + KEY);
        adapter.deleteOwnedImage(7L, BASE + "/members/7/profile/../../other.png");
        adapter.deleteOwnedImage(7L, BASE + ".evil.example/" + KEY);

        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void storageFailureDoesNotExposeSdkDetails() {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("private provider details"));

        assertThatThrownBy(() -> adapter(client).store(7L, png()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(IdentityErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE));
    }

    private S3ProfileImageStorageAdapter adapter(S3Client client) {
        return new S3ProfileImageStorageAdapter(client, "profile-bucket", BASE, () -> ID);
    }

    private ProfileImageUpload png() {
        return ProfileImageUpload.validate(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, "image/png");
    }
}
