package com.dearusback.support.domain.model;

import java.net.URI;

public final class ExternalImageUrl {

    private ExternalImageUrl() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String url = value.trim();
        if (url.isEmpty() || url.length() > 2048) {
            throw new IllegalArgumentException("Invalid image URL");
        }
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("Invalid image URL");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid image URL", exception);
        }
        return url;
    }
}
