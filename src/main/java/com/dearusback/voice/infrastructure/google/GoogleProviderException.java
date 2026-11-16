package com.dearusback.voice.infrastructure.google;

public final class GoogleProviderException extends RuntimeException {
    private final String provider;
    private final String category;

    public GoogleProviderException(String provider, String category) {
        super(provider + " " + category);
        this.provider = provider;
        this.category = category;
    }

    public String provider() { return provider; }
    public String category() { return category; }
}
