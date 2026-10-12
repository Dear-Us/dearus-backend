package com.dearusback.care.application.port.in;

import com.dearusback.care.application.model.RecordChangedEvent;

public interface RecordEventSubscriber {
    void send(RecordChangedEvent event);

    void heartbeat();

    void close();
}
