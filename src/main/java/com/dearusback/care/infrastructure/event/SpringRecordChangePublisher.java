package com.dearusback.care.infrastructure.event;

import com.dearusback.care.application.model.RecordChangedEvent;
import com.dearusback.care.application.port.out.RecordChangePublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SpringRecordChangePublisher implements RecordChangePublisher {

    private final ApplicationEventPublisher publisher;

    public SpringRecordChangePublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(RecordChangedEvent event) {
        publisher.publishEvent(event);
    }
}
