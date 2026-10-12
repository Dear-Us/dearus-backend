package com.dearusback.care.infrastructure.event;

import com.dearusback.care.application.service.RecordSubscriberRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecordEventHeartbeat {

    private final RecordSubscriberRegistry registry;

    public RecordEventHeartbeat(RecordSubscriberRegistry registry) {
        this.registry = registry;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void sendHeartbeat() {
        registry.heartbeat();
    }
}
