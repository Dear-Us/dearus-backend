package com.dearusback.voice.infrastructure.google;

import com.dearusback.voice.application.port.out.VoiceCareCommandPort;
import com.dearusback.voice.application.port.out.VoiceCareCommandPort.ActiveRecord;
import com.dearusback.voice.application.port.out.VoiceCommandParsingPort;
import com.dearusback.voice.domain.model.ParsedVoiceCommand;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.dearusback.voice.infrastructure.config.VoiceProviderConditions;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.dearusback.voice.infrastructure.parsing.VoiceCommandInterpreter;

@Component
@Conditional(VoiceProviderConditions.GoogleLlm.class)
class GoogleGeminiParsingAdapter implements VoiceCommandParsingPort {
    private final GoogleGeminiGateway gateway;
    private final VoiceCareCommandPort care;
    private final String model;
    private final GenerateContentConfig config;
    private final VoiceCommandInterpreter interpreter;

    GoogleGeminiParsingAdapter(GoogleGeminiGateway gateway, VoiceCareCommandPort care, ObjectMapper mapper,
                               @Value("${dearus.voice.llm.model:gemini-2.5-flash}") String model) {
        if (model == null || model.isBlank()) throw new IllegalStateException("DEARUS_LLM_MODEL is required");
        this.gateway = gateway; this.care = care; this.model = model.trim();
        this.interpreter = new VoiceCommandInterpreter(care, mapper);
        config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseJsonSchema(VoiceStructuredSchema.create(care.vocabulary()))
                .systemInstruction(Content.fromParts(Part.fromText(VoiceCommandInterpreter.SYSTEM_INSTRUCTION)))
                .candidateCount(1)
                .build();
    }

    @Override
    public ParsedVoiceCommand parse(String transcript, String timezone, Instant requestedAt,
                                    Long babyId, Long memberId) {
        ActiveRecord active = care.activeRecord(babyId, memberId);
        String prompt = interpreter.prompt(transcript, timezone, requestedAt, active);
        String response;
        try {
            response = gateway.generate(model, prompt, config);
        } catch (RuntimeException exception) {
            throw new GoogleProviderException("GEMINI", "REQUEST_FAILED");
        }
        return decode(response, care.vocabulary(), active, babyId, memberId);
    }

    ParsedVoiceCommand decode(String response, VoiceCareCommandPort.Vocabulary vocabulary,
                              ActiveRecord active, Long babyId, Long memberId) {
        try {
            return interpreter.decode(response, vocabulary, active, babyId, memberId);
        } catch (IllegalArgumentException exception) {
            throw new GoogleProviderException("GEMINI", "INVALID_RESPONSE");
        }
    }
}
