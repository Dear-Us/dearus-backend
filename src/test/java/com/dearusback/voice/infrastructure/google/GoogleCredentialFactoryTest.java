package com.dearusback.voice.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GoogleCredentialFactoryTest {
    @Test
    void blankCredentialUsesApplicationDefaultLoader() {
        AtomicInteger calls = new AtomicInteger();
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken("ephemeral-test-value", new Date()));
        assertThat(GoogleCredentialFactory.load("  ", () -> {
            calls.incrementAndGet();
            return credentials;
        })).isNotNull();
        assertThat(calls).hasValue(1);
    }

    @Test
    void malformedJsonAndNonServiceAccountAreSanitizedAndNeverUseAdc() {
        AtomicInteger calls = new AtomicInteger();
        for (String json : new String[]{"{not json", "{\"type\":\"authorized_user\"}"}) {
            assertThatThrownBy(() -> GoogleCredentialFactory.load(json, () -> {
                calls.incrementAndGet();
                return null;
            })).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Google credentials could not be loaded")
                    .hasNoCause();
        }
        assertThat(calls).hasValue(0);
    }

    @Test
    void parsesEphemeralServiceAccountJsonInMemoryWithoutAdc() throws Exception {
        var pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
        String json = new ObjectMapper().writeValueAsString(Map.of(
                "type", "service_account", "project_id", "ephemeral-project",
                "private_key_id", "ephemeral-key", "private_key", pem,
                "client_email", "ephemeral@example.invalid",
                "client_id", "123", "token_uri", "https://oauth2.googleapis.com/token"));
        GoogleCredentials credentials = GoogleCredentialFactory.load(json, () -> {
            throw new AssertionError("ADC must not be used for supplied JSON");
        });
        assertThat(credentials).isInstanceOf(ServiceAccountCredentials.class);
        assertThat(((ServiceAccountCredentials) credentials).getScopes())
                .containsExactly("https://www.googleapis.com/auth/cloud-platform");
    }

    @Test
    void adcFailureDoesNotExposeProviderError() {
        assertThatThrownBy(() -> GoogleCredentialFactory.load("", () -> {
            throw new IllegalArgumentException("sensitive source details");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("Google credentials could not be loaded")
                .hasNoCause();
    }
}
