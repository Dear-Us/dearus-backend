package com.dearusback.voice.infrastructure.google;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechSettings;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.dearusback.voice.infrastructure.config.VoiceProviderConditions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(VoiceProviderConditions.GoogleStt.class)
class GoogleSpeechConfiguration {
    @Bean(destroyMethod = "close")
    SpeechClient speechV2Client(GoogleCredentials credentials,
                                @Value("${dearus.voice.stt.timeout-ms:30000}") int timeoutMs) {
        if (timeoutMs < 1000 || timeoutMs > 120000) {
            throw new IllegalStateException("DEARUS_STT_TIMEOUT_MS must be 1000..120000");
        }
        SpeechSettings.Builder settings = SpeechSettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials));
        Duration timeout = Duration.ofMillis(timeoutMs);
        settings.recognizeSettings().setRetrySettings(settings.recognizeSettings().getRetrySettings().toBuilder()
                .setInitialRpcTimeoutDuration(timeout)
                .setMaxRpcTimeoutDuration(timeout)
                .setTotalTimeoutDuration(timeout)
                .build());
        try {
            return SpeechClient.create(settings.build());
        } catch (IOException exception) {
            throw new IllegalStateException("Speech-to-Text V2 client initialization failed");
        }
    }

    @Bean
    GoogleSpeechGateway googleSpeechGateway(SpeechClient speechV2Client) {
        return speechV2Client::recognize;
    }
}
