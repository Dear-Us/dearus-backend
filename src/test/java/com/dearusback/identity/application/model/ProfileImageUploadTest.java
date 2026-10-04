package com.dearusback.identity.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.support.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

class ProfileImageUploadTest {

    @Test
    void acceptsMatchingJpegPngAndWebpSignatures() {
        assertThat(ProfileImageUpload.validate(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff},
                "image/jpeg").extension()).isEqualTo("jpg");
        assertThat(ProfileImageUpload.validate(png(), "image/png").extension()).isEqualTo("png");
        assertThat(ProfileImageUpload.validate(new byte[] {
                'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'
        }, "image/webp").extension()).isEqualTo("webp");
    }

    @Test
    void rejectsEmptyUnsupportedAndMismatchedSignatures() {
        assertInvalid(new byte[0], "image/png");
        assertInvalid("not an image".getBytes(), "image/png");
        assertInvalid(png(), "image/jpeg");
        assertInvalid(png(), "image/gif");
    }

    @Test
    void rejectsFilesLargerThanFiveMebibytes() {
        byte[] bytes = new byte[ProfileImageUpload.MAX_BYTES + 1];
        assertThatThrownBy(() -> ProfileImageUpload.validate(bytes, "image/png"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(IdentityErrorCode.PROFILE_IMAGE_TOO_LARGE));
    }

    private void assertInvalid(byte[] bytes, String contentType) {
        assertThatThrownBy(() -> ProfileImageUpload.validate(bytes, contentType))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(IdentityErrorCode.INVALID_PROFILE_IMAGE));
    }

    private byte[] png() {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    }
}
