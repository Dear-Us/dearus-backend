package com.dearusback.care.infrastructure.event;

import com.dearusback.care.application.model.RecordChangedEvent;
import com.dearusback.care.application.service.RecordSubscriberRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RecordChangedAfterCommitListener {

    private static final Logger log = LoggerFactory.getLogger(RecordChangedAfterCommitListener.class);
    private final RecordSubscriberRegistry registry;

    public RecordChangedAfterCommitListener(RecordSubscriberRegistry registry) {
        this.registry = registry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(RecordChangedEvent event) {
        try {
            registry.broadcast(event);
        } catch (RuntimeException exception) {
            log.warn("Record change notification failed after commit for record {}", event.recordId(), exception);
        }
    }
}
