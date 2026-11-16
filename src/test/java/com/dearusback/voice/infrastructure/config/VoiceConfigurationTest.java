package com.dearusback.voice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class VoiceConfigurationTest {
    @Test
    void disabledWithoutProvidersIsValid() {
        assertThatCode(() -> new VoiceConfiguration()
                .voiceProviderConfigurationCheck(false, List.of(), List.of())
                .afterSingletonsInstantiated()).doesNotThrowAnyException();
    }

    @Test
    void enabledWithoutProvidersFailsWithActionableMessage() {
        assertThatThrownBy(() -> new VoiceConfiguration()
                .voiceProviderConfigurationCheck(true, List.of(), List.of())
                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transcription and one parsing adapter");
    }
}
