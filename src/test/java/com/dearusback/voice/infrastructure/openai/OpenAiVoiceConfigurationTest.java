package com.dearusback.voice.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearusback.voice.application.port.out.VoiceCommandParsingPort;
import com.dearusback.voice.application.port.out.VoiceTranscriptionPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "dearus.voice.enabled=true", "dearus.voice.stt.enabled=true",
        "dearus.voice.stt.provider=OPENAI", "dearus.voice.stt.model=gpt-4o-mini-transcribe",
        "dearus.voice.llm.enabled=true", "dearus.voice.llm.provider=OPENAI",
        "dearus.voice.llm.model=gpt-4o-mini", "dearus.voice.openai.api-key=test-only-key"
})
class OpenAiVoiceConfigurationTest {
    @Autowired List<VoiceTranscriptionPort> transcribers;
    @Autowired List<VoiceCommandParsingPort> parsers;

    @Test
    void selectsOnlyOpenAiAdaptersWithoutGoogleCredentials() {
        assertThat(transcribers).hasSize(1).first().isInstanceOf(OpenAiTranscriptionAdapter.class);
        assertThat(parsers).hasSize(1).first().isInstanceOf(OpenAiParsingAdapter.class);
    }
}
