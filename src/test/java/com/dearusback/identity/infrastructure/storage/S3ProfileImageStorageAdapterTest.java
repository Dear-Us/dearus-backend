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
import java.time.Duration;
import java.net.URL;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

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
        assertThat(adapter.owns(7L, BASE + "/" + KEY)).isTrue();
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
        assertThat(adapter.owns(7L, "https://k.kakaocdn.net/profile.png")).isFalse();
        assertThat(adapter.owns(8L, BASE + "/" + KEY)).isFalse();
        assertThat(adapter.owns(7L, BASE + "/members/7/profile/../../other.png")).isFalse();
        assertThat(adapter.owns(7L, BASE + ".evil.example/" + KEY)).isFalse();
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

    @Test
    void presignsOnlyTheOwnedStoredUrlWithoutChangingTheStoredValue() throws Exception {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedGetObjectRequest signed = mock(PresignedGetObjectRequest.class);
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);
        when(signed.url()).thenReturn(new URL("https://signed.example/image?X-Amz-Signature=test"));
        var adapter = new S3ProfileImageStorageAdapter(client, presigner, "profile-bucket", BASE,
                Duration.ofHours(1), () -> ID);

        assertThat(adapter.readUrl(7L, BASE + "/" + KEY))
                .isEqualTo("https://signed.example/image?X-Amz-Signature=test");
        ArgumentCaptor<GetObjectPresignRequest> request = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(request.capture());
        assertThat(request.getValue().getObjectRequest().bucket()).isEqualTo("profile-bucket");
        assertThat(request.getValue().getObjectRequest().key()).isEqualTo(KEY);
        assertThat(request.getValue().signatureDuration()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void externalNullMalformedAndSpoofedUrlsAreNeverPresignedOrDeleted() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        var adapter = new S3ProfileImageStorageAdapter(client, presigner, "profile-bucket", BASE,
                Duration.ofHours(1), () -> ID);
        String[] passthrough = {null, "https://k.kakaocdn.net/profile.png", "not a URI",
                BASE + ".evil.example/" + KEY, BASE + "/" + KEY + "?extra=1",
                BASE + "/members/8/profile/" + ID + ".png",
                BASE + "/members/7/profile/%2e%2e/" + ID + ".png"};
        for (String value : passthrough) {
            assertThat(adapter.readUrl(7L, value)).isEqualTo(value);
            adapter.deleteOwnedImage(7L, value);
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
            var adapter = new S3ProfileImageStorageAdapter(mock(S3Client.class), presigner,
                    "profile-bucket", BASE, Duration.ofHours(1), () -> ID);

            String readUrl = adapter.readUrl(7L, BASE + "/" + KEY);

            assertThat(readUrl).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256", "X-Amz-Expires=3600");
            assertThat(readUrl).contains(KEY);
        }
    }

    private S3ProfileImageStorageAdapter adapter(S3Client client) {
        return new S3ProfileImageStorageAdapter(client, mock(S3Presigner.class), "profile-bucket", BASE,
                Duration.ofHours(1), () -> ID);
    }

    private ProfileImageUpload png() {
        return ProfileImageUpload.validate(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, "image/png");
    }
}
