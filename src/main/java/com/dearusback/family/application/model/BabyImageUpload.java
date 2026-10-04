package com.dearusback.family.application.model;

import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public final class BabyImageUpload {

    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private final byte[] bytes;
    private final String contentType;
    private final String extension;

    private BabyImageUpload(byte[] bytes, String contentType, String extension) {
        this.bytes = bytes.clone();
        this.contentType = contentType;
        this.extension = extension;
    }

    public static BabyImageUpload validate(byte[] bytes, String declaredContentType) {
        if (bytes == null || bytes.length == 0) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_IMAGE);
        }
        if (bytes.length > MAX_BYTES) {
            throw new FamilyException(FamilyErrorCode.BABY_IMAGE_TOO_LARGE);
        }
        String type = declaredContentType == null ? "" : declaredContentType.trim().toLowerCase(Locale.ROOT);
        String extension = switch (type) {
            case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff ? "jpg" : null;
            case "image/png" -> bytes.length >= PNG_SIGNATURE.length
                    && Arrays.equals(bytes, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length)
                    ? "png" : null;
            case "image/webp" -> bytes.length >= 12
                    && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                    && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII)) ? "webp" : null;
            default -> null;
        };
        if (extension == null) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_IMAGE);
        }
        return new BabyImageUpload(bytes, type, extension);
    }

    public byte[] bytes() { return bytes.clone(); }
    public String contentType() { return contentType; }
    public String extension() { return extension; }
}
