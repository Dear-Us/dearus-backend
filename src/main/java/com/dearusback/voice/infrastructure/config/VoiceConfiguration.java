package com.dearusback.voice.infrastructure.config;

import com.dearusback.voice.application.port.out.VoiceCommandParsingPort;
import com.dearusback.voice.application.port.out.VoiceProcessingScheduler;
import com.dearusback.voice.application.port.out.VoiceTranscriptionPort;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
class VoiceConfiguration {
    @Bean
    ThreadPoolTaskExecutor voiceTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("dearus-voice-");
        return executor;
    }

    @Bean
    VoiceProcessingScheduler voiceProcessingScheduler(ThreadPoolTaskExecutor voiceTaskExecutor) {
        return voiceTaskExecutor::execute;
    }

    @Bean
    SmartInitializingSingleton voiceProviderConfigurationCheck(
            @Value("${dearus.voice.enabled:false}") boolean enabled,
            List<VoiceTranscriptionPort> transcribers, List<VoiceCommandParsingPort> parsers) {
        return () -> {
            if (enabled && (transcribers.size() != 1 || parsers.size() != 1)) {
                throw new IllegalStateException("Voice is enabled but exactly one transcription and one parsing "
                        + "adapter must be configured");
            }
        };
    }
}
