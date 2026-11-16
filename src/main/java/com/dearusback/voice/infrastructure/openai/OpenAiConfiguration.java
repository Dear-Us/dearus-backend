package com.dearusback.voice.infrastructure.openai;

import com.dearusback.voice.infrastructure.config.VoiceProviderConditions;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
class OpenAiConfiguration {
    @Bean
    @Conditional(VoiceProviderConditions.AnyOpenAi.class)
    OpenAiGateway openAiGateway(@Value("${dearus.voice.openai.api-key:}") String apiKey,
                                ObjectMapper mapper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required when OpenAI voice is enabled");
        }
        return new OpenAiHttpGateway(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build(), mapper, apiKey.trim());
    }
}
