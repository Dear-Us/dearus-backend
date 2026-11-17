package com.dearusback.voice.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dearusback.voice.application.port.out.VoiceCareCommandPort;
import com.dearusback.voice.domain.model.VoiceAction;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenAiParsingAdapterTest {
    @Test
    void usesCareVocabularyAndValidatedDraft() {
        VoiceCareCommandPort care = mock(VoiceCareCommandPort.class);
        when(care.vocabulary()).thenReturn(new VoiceCareCommandPort.Vocabulary(
                List.of("OTHER"), List.of("FORMULA"), List.of("LEFT"), List.of("URINE"),
                List.of("EAR"), List.of("ML")));
        when(care.validate(any(), any(), any(), any()))
                .thenReturn(new VoiceCareCommandPort.Validation(true, false));
        AtomicReference<String> instructions = new AtomicReference<>();
        OpenAiGateway gateway = new OpenAiGateway() {
            @Override public String transcribe(byte[] audio, String contentType, String model, int timeoutMs) {
                throw new UnsupportedOperationException();
            }
            @Override public String generate(String model, String system, String prompt, int timeoutMs) {
                assertThat(model).isEqualTo("gpt-4o-mini");
                assertThat(prompt).contains("memberTimezone=Asia/Seoul", "transcript=목욕했어");
                instructions.set(system);
                return """
                        {"action":"CREATE_COMPLETED_RECORD","careType":"OTHER","ambiguous":false,
                        "multipleRecords":false,"recordedAt":"2026-10-08T12:00:00+09:00",
                        "detail":{"title":"목욕"}}
                        """;
            }
        };
        var adapter = new OpenAiParsingAdapter(gateway, care, new ObjectMapper(), "gpt-4o-mini", 3000);
        var command = adapter.parse("목욕했어", "Asia/Seoul", Instant.parse("2026-10-08T03:00:00Z"),
                7L, 9L);
        assertThat(command.action()).isEqualTo(VoiceAction.CREATE_COMPLETED_RECORD);
        assertThat(command.ambiguous()).isFalse();
        assertThat(command.draftJson()).contains("\"title\":\"목욕\"");
        assertThat(instructions.get()).contains("Allowed careType: OTHER", "multipleRecords");
    }
}
