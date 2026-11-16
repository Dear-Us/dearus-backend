package com.dearusback.voice.application.port.out;

public interface VoiceProcessingScheduler {
    void submit(Runnable task);
}
