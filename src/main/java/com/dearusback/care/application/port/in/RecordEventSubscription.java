package com.dearusback.care.application.port.in;

@FunctionalInterface
public interface RecordEventSubscription extends AutoCloseable {
    @Override
    void close();
}
