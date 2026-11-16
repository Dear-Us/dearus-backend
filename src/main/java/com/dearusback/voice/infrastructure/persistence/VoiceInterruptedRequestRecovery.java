package com.dearusback.voice.infrastructure.persistence;

import com.dearusback.voice.application.port.out.VoiceRequestRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class VoiceInterruptedRequestRecovery {
    private final VoiceRequestRepository requests;
    private final Clock clock;

    VoiceInterruptedRequestRecovery(VoiceRequestRepository requests, Clock clock) {
        this.requests = requests; this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void recover() {
        var now = clock.instant();
        requests.failStaleProcessing(now.minus(Duration.ofMinutes(10)), now);
    }
}
