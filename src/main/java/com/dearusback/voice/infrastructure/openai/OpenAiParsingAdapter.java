package com.dearusback.voice.infrastructure.openai;

import com.dearusback.voice.application.port.out.VoiceCareCommandPort;
import com.dearusback.voice.application.port.out.VoiceCommandParsingPort;
import com.dearusback.voice.domain.model.ParsedVoiceCommand;
import com.dearusback.voice.infrastructure.config.VoiceProviderConditions;
import com.dearusback.voice.infrastructure.parsing.VoiceCommandInterpreter;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Conditional(VoiceProviderConditions.OpenAiLlm.class)
class OpenAiParsingAdapter implements VoiceCommandParsingPort {
    private final OpenAiGateway gateway;
    private final VoiceCareCommandPort care;
    private final VoiceCommandInterpreter interpreter;
    private final String model;
    private final int timeoutMs;

    OpenAiParsingAdapter(OpenAiGateway gateway, VoiceCareCommandPort care, ObjectMapper mapper,
                         @Value("${dearus.voice.llm.model:gpt-4o-mini}") String model,
                         @Value("${dearus.voice.llm.timeout-ms:30000}") int timeoutMs) {
        if (model == null || model.isBlank() || timeoutMs < 1000 || timeoutMs > 120000) {
            throw new IllegalStateException("Invalid OpenAI LLM model or timeout");
        }
        this.gateway = gateway;
        this.care = care;
        this.interpreter = new VoiceCommandInterpreter(care, mapper);
        this.model = model.trim();
        this.timeoutMs = timeoutMs;
    }

    @Override
    public ParsedVoiceCommand parse(String transcript, String timezone, Instant requestedAt,
                                    Long babyId, Long memberId) {
        var active = care.activeRecord(babyId, memberId);
        String prompt = interpreter.prompt(transcript, timezone, requestedAt, active);
        String response = gateway.generate(model, interpreter.openAiInstruction(care.vocabulary()),
                prompt, timeoutMs);
        try {
            return interpreter.decode(response, care.vocabulary(), active, babyId, memberId);
        } catch (IllegalArgumentException exception) {
            throw new OpenAiProviderException("INVALID_RESPONSE");
        }
    }
}
