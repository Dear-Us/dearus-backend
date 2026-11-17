package com.dearusback.voice.infrastructure.google;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class GoogleCredentialFactory {
    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private GoogleCredentialFactory() { }

    public static GoogleCredentials load(String credentialJson) {
        return load(credentialJson, GoogleCredentials::getApplicationDefault);
    }

    static GoogleCredentials load(String credentialJson, DefaultCredentialLoader defaultLoader) {
        try {
            GoogleCredentials credentials;
            if (credentialJson == null || credentialJson.isBlank()) {
                credentials = defaultLoader.load();
            } else {
                credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(
                        credentialJson.getBytes(StandardCharsets.UTF_8)));
                if (!(credentials instanceof ServiceAccountCredentials)) {
                    throw new IllegalStateException("Google credential JSON must be a service account");
                }
            }
            return credentials.createScoped(List.of(CLOUD_PLATFORM_SCOPE));
        } catch (IOException | RuntimeException exception) {
            // Credential parsing errors can contain source snippets. Never attach or log the cause.
            throw new IllegalStateException("Google credentials could not be loaded");
        }
    }

    @FunctionalInterface
    interface DefaultCredentialLoader {
        GoogleCredentials load() throws IOException;
    }
}
