package com.dearusback.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearusback.identity.application.model.MyProfileResult;
import com.dearusback.identity.application.model.ProfileImageUpload;
import com.dearusback.identity.application.model.StoredProfileImage;
import com.dearusback.identity.application.model.UpdateMyProfileCommand;
import com.dearusback.identity.application.port.out.ProfileImageStoragePort;
import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.support.domain.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdateMyProfileServiceTest {

    private static final String NEW_IMAGE = "https://images.example/members/1/profile/new.png";
    private static final String OLD_IMAGE = "https://images.example/members/1/profile/old.png";

    @Test
    void nameOnlyTrimsAndKeepsExistingImage() {
        var transaction = mock(UpdateMyProfileTransactionService.class);
        var storage = mock(ProfileImageStoragePort.class);
        when(transaction.update(1L, "새 이름", false, null))
                .thenReturn(updated("새 이름", OLD_IMAGE, OLD_IMAGE));

        MyProfileResult result = new UpdateMyProfileService(transaction, storage)
                .update(new UpdateMyProfileCommand(1L, "  새 이름  ", false, null));

        assertThat(result.memberName()).isEqualTo("새 이름");
        assertThat(result.profileImageUrl()).isEqualTo(OLD_IMAGE);
        assertThat(result.timezone()).isEqualTo("Europe/Paris");
        verify(storage, never()).store(any(), any());
        verify(storage, never()).deleteOwnedImage(any(), any());
    }

    @Test
    void replacementUploadsBeforeTransactionAndDeletesOldAfterItReturns() {
        var transaction = mock(UpdateMyProfileTransactionService.class);
        var storage = mock(ProfileImageStoragePort.class);
        var image = image();
        when(storage.store(1L, image)).thenReturn(new StoredProfileImage(NEW_IMAGE));
        when(transaction.update(1L, null, false, NEW_IMAGE))
                .thenAnswer(invocation -> {
                    verify(storage).store(1L, image);
                    verify(storage, never()).deleteOwnedImage(any(), any());
                    return updated("기존 이름", NEW_IMAGE, OLD_IMAGE);
                });

        MyProfileResult result = new UpdateMyProfileService(transaction, storage)
                .update(new UpdateMyProfileCommand(1L, null, false, image));

        assertThat(result.profileImageUrl()).isEqualTo(NEW_IMAGE);
        verify(storage).deleteOwnedImage(1L, OLD_IMAGE);
    }

    @Test
    void removalStoresNullAndDeletesOldAfterCommit() {
        var transaction = mock(UpdateMyProfileTransactionService.class);
        var storage = mock(ProfileImageStoragePort.class);
        when(transaction.update(1L, null, true, null)).thenReturn(updated("기존 이름", null, OLD_IMAGE));

        MyProfileResult result = new UpdateMyProfileService(transaction, storage)
                .update(new UpdateMyProfileCommand(1L, null, true, null));

        assertThat(result.profileImageUrl()).isNull();
        verify(storage).deleteOwnedImage(1L, OLD_IMAGE);
    }

    @Test
    void externalUrlPersistsWithoutUploadAndCleansUpPreviousOwnedImage() {
        var transaction = mock(UpdateMyProfileTransactionService.class);
        var storage = mock(ProfileImageStoragePort.class);
        String externalUrl = "https://cdn.example.com/profile.png";
        when(transaction.update(1L, null, false, externalUrl))
                .thenReturn(updated("기존 이름", externalUrl, OLD_IMAGE));

        MyProfileResult result = new UpdateMyProfileService(transaction, storage)
                .update(new UpdateMyProfileCommand(1L, null, false, null, externalUrl));

        assertThat(result.profileImageUrl()).isEqualTo(externalUrl);
        verify(storage, never()).store(any(), any());
        verify(storage).deleteOwnedImage(1L, OLD_IMAGE);
    }

    @Test
    void invalidOrConflictingExternalUrlsAreRejectedBeforeWrite() {
        var transaction = mock(UpdateMyProfileTransactionService.class);
        var storage = mock(ProfileImageStoragePort.class);
        var service = new UpdateMyProfileService(transaction, storage);
        assertError(() -> service.update(new UpdateMyProfileCommand(1L, null, false, null,
                "http://cdn.example.com/image.png")), IdentityErrorCode.INVALID_PROFILE_IMAGE_URL);
        assertError(() -> service.update(new UpdateMyProfileCommand(1L, null, false, image(),
                "https://cdn.example.com/image.png")), IdentityErrorCode.INVALID_PROFILE_UPDATE);
        assertError(() -> service.update(new UpdateMyProfileCommand(1L, null, true, null,
                "https://cdn.example.com/image.png")), IdentityErrorCode.INVALID_PROFILE_UPDATE);
        when(storage.owns(1L, NEW_IMAGE)).thenReturn(true);
        assertError(() -> service.update(new UpdateMyProfileCommand(1L, null, false, null, NEW_IMAGE)),
                IdentityErrorCode.INVALID_PROFILE_IMAGE_URL);
        verify(transaction, never()).update(any(), any(), eq(false), any());
    }

    @Test
    void noChangesConflictAndInvalidNamesFailBeforeStorage() {
        var transaction = mock(UpdateMyProfileTransactionService.class);
        var storage = mock(ProfileImageStoragePort.class);
        var service = new UpdateMyProfileService(transaction, storage);
        assertError(() -> service.update(new UpdateMyProfileCommand(1L, null, false, null)),
                IdentityErrorCode.INVALID_PROFILE_UPDATE);
        assertError(() -> service.update(new UpdateMyProfileCommand(1L, null, true, image())),
                IdentityErrorCode.INVALID_PROFILE_UPDATE);
        assertError(() -> service.update(new UpdateMyProfileCommand(1L, "   ", false, null)),
                IdentityErrorCode.INVALID_MEMBER_NAME);
        assertError(() -> service.update(new UpdateMyProfileCommand(1L, "x".repeat(51), false, image())),
                IdentityErrorCode.INVALID_MEMBER_NAME);
        verify(storage, never()).store(any(), any());
        verify(transaction, never()).update(any(), any(), eq(false), any());
    }

    @Test
    void uploadFailureReturnsUnavailableWithoutDbUpdate() {
        var transaction = mock(UpdateMyProfileTransactionService.class);
        var storage = mock(ProfileImageStoragePort.class);
        var image = image();
        when(storage.store(1L, image)).thenThrow(new IllegalStateException("private S3 details"));

        assertError(() -> new UpdateMyProfileService(transaction, storage)
                .update(new UpdateMyProfileCommand(1L, null, false, image)),
                IdentityErrorCode.PROFILE_IMAGE_STORAGE_UNAVAILABLE);
        verify(transaction, never()).update(any(), any(), eq(false), any());
    }

    @Test
    void failedDbUpdateCompensatesNewUploadAndRetainsOriginalError() {
        var transaction = mock(UpdateMyProfileTransactionService.class);
        var storage = mock(ProfileImageStoragePort.class);
        var image = image();
        var dbFailure = new IllegalStateException("DB failure");
        when(storage.store(1L, image)).thenReturn(new StoredProfileImage(NEW_IMAGE));
        when(transaction.update(1L, null, false, NEW_IMAGE)).thenThrow(dbFailure);

        assertThatThrownBy(() -> new UpdateMyProfileService(transaction, storage)
                .update(new UpdateMyProfileCommand(1L, null, false, image)))
                .isSameAs(dbFailure);
        verify(storage).deleteOwnedImage(1L, NEW_IMAGE);
    }

    @Test
    void failedCompensationNeverMasksDbError() {
        var transaction = mock(UpdateMyProfileTransactionService.class);
        var storage = mock(ProfileImageStoragePort.class);
        var image = image();
        var dbFailure = new IllegalStateException("DB failure");
        when(storage.store(1L, image)).thenReturn(new StoredProfileImage(NEW_IMAGE));
        when(transaction.update(1L, null, false, NEW_IMAGE)).thenThrow(dbFailure);
        org.mockito.Mockito.doThrow(new IllegalStateException("S3 failure"))
                .when(storage).deleteOwnedImage(1L, NEW_IMAGE);

        assertThatThrownBy(() -> new UpdateMyProfileService(transaction, storage)
                .update(new UpdateMyProfileCommand(1L, null, false, image)))
                .isSameAs(dbFailure);
    }

    @Test
    void failedCleanupAfterDbCommitDoesNotRollbackResult() {
        var transaction = mock(UpdateMyProfileTransactionService.class);
        var storage = mock(ProfileImageStoragePort.class);
        var image = image();
        when(storage.store(1L, image)).thenReturn(new StoredProfileImage(NEW_IMAGE));
        when(transaction.update(1L, null, false, NEW_IMAGE))
                .thenReturn(updated("기존 이름", NEW_IMAGE, OLD_IMAGE));
        org.mockito.Mockito.doThrow(new IllegalStateException("S3 failure"))
                .when(storage).deleteOwnedImage(1L, OLD_IMAGE);

        MyProfileResult result = new UpdateMyProfileService(transaction, storage)
                .update(new UpdateMyProfileCommand(1L, null, false, image));

        assertThat(result.profileImageUrl()).isEqualTo(NEW_IMAGE);
    }

    private UpdateMyProfileTransactionService.UpdatedProfile updated(
            String name, String imageUrl, String previousImageUrl
    ) {
        return new UpdateMyProfileTransactionService.UpdatedProfile(
                new MyProfileResult(1L, "member@example.com", name, imageUrl,
                        "Europe/Paris", null, List.of()), previousImageUrl
        );
    }

    private ProfileImageUpload image() {
        return ProfileImageUpload.validate(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, "image/png");
    }

    private void assertError(Runnable action, IdentityErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode()).isEqualTo(expected));
    }
}
