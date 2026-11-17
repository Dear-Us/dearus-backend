package com.dearusback.voice.infrastructure.google;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.dearusback.voice.infrastructure.config.VoiceProviderConditions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(VoiceProviderConditions.GoogleLlm.class)
class GoogleGeminiConfiguration {
    @Bean(destroyMethod = "close")
    Client voiceGeminiClient(GoogleCredentials credentials,
                            @Value("${dearus.voice.google.project:}") String project,
                            @Value("${dearus.voice.google.location:global}") String location,
                            @Value("${dearus.voice.llm.provider:VERTEX_GEMINI}") String provider,
                            @Value("${dearus.voice.llm.timeout-ms:30000}") int timeoutMs) {
        if (project == null || project.isBlank() || location == null || location.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLOUD_PROJECT and GOOGLE_CLOUD_LOCATION are required for Gemini");
        }
        if (!"VERTEX_GEMINI".equals(provider)) {
            throw new IllegalStateException("DEARUS_LLM_PROVIDER must be VERTEX_GEMINI");
        }
        if (timeoutMs < 1000 || timeoutMs > 120000) {
            throw new IllegalStateException("DEARUS_LLM_TIMEOUT_MS must be 1000..120000");
        }
        return Client.builder().vertexAI(true).project(project.trim()).location(location.trim())
                .credentials(credentials)
                .httpOptions(HttpOptions.builder().apiVersion("v1").timeout(timeoutMs).build())
                .build();
    }

    @Bean
    GoogleGeminiGateway googleGeminiGateway(Client voiceGeminiClient) {
        return (model, prompt, config) -> voiceGeminiClient.models.generateContent(model, prompt, config).text();
    }
}
