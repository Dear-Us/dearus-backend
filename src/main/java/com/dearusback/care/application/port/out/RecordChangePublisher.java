package com.dearusback.care.application.port.out;

import com.dearusback.care.application.model.RecordChangedEvent;

public interface RecordChangePublisher {
    void publish(RecordChangedEvent event);
}
