package com.dearusback.identity.application.model;

import com.dearusback.identity.domain.exception.IdentityErrorCode;
import com.dearusback.identity.domain.exception.IdentityException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public final class ProfileImageUpload {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private final byte[] bytes;
    private final String contentType;
    private final String extension;

    private ProfileImageUpload(byte[] bytes, String contentType, String extension) {
        this.bytes = bytes.clone();
        this.contentType = contentType;
        this.extension = extension;
    }

    public static ProfileImageUpload validate(byte[] bytes, String declaredContentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IdentityException(IdentityErrorCode.INVALID_PROFILE_IMAGE);
        }
        if (bytes.length > MAX_BYTES) {
            throw new IdentityException(IdentityErrorCode.PROFILE_IMAGE_TOO_LARGE);
        }
        String contentType = declaredContentType == null ? "" : declaredContentType.trim().toLowerCase(Locale.ROOT);
        String extension = switch (contentType) {
            case "image/jpeg" -> isJpeg(bytes) ? "jpg" : null;
            case "image/png" -> isPng(bytes) ? "png" : null;
            case "image/webp" -> isWebp(bytes) ? "webp" : null;
            default -> null;
        };
        if (extension == null) {
            throw new IdentityException(IdentityErrorCode.INVALID_PROFILE_IMAGE);
        }
        return new ProfileImageUpload(bytes, contentType, extension);
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length >= PNG_SIGNATURE.length
                && Arrays.equals(bytes, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length);
    }

    private static boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII));
    }
}
