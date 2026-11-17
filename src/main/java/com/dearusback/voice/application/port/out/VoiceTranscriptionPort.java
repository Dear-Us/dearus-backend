package com.dearusback.voice.application.port.out;

public interface VoiceTranscriptionPort {
    String transcribe(byte[] audio, String contentType);
}
