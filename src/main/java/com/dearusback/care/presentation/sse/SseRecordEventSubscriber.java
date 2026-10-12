package com.dearusback.care.presentation.sse;

import com.dearusback.care.application.model.RecordChangedEvent;
import com.dearusback.care.application.port.in.RecordEventSubscriber;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseRecordEventSubscriber implements RecordEventSubscriber {

    private final SseEmitter emitter;

    public SseRecordEventSubscriber(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(RecordChangedEvent event) {
        try {
            emitter.send(SseEmitter.event().name("record-change").data(event));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public void heartbeat() {
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public void close() {
        emitter.complete();
    }
}
