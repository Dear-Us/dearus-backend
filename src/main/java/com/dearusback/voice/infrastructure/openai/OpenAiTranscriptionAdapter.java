package com.dearusback.voice.infrastructure.openai;

import com.dearusback.voice.application.port.out.VoiceTranscriptionPort;
import com.dearusback.voice.infrastructure.config.VoiceProviderConditions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(VoiceProviderConditions.OpenAiStt.class)
class OpenAiTranscriptionAdapter implements VoiceTranscriptionPort {
    private final OpenAiGateway gateway;
    private final String model;
    private final int timeoutMs;

    OpenAiTranscriptionAdapter(OpenAiGateway gateway,
                               @Value("${dearus.voice.stt.model:gpt-4o-mini-transcribe}") String model,
                               @Value("${dearus.voice.stt.timeout-ms:30000}") int timeoutMs) {
        if (model == null || model.isBlank() || timeoutMs < 1000 || timeoutMs > 120000) {
            throw new IllegalStateException("Invalid OpenAI STT model or timeout");
        }
        this.gateway = gateway;
        this.model = model.trim();
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String transcribe(byte[] audio, String contentType) {
        return gateway.transcribe(audio, contentType, model, timeoutMs);
    }
}
