package com.dearusback.voice.infrastructure.openai;

interface OpenAiGateway {
    String transcribe(byte[] audio, String contentType, String model, int timeoutMs);
    String generate(String model, String instructions, String prompt, int timeoutMs);
}
