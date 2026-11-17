package com.dearusback.voice.infrastructure.openai;

final class OpenAiProviderException extends RuntimeException {
    OpenAiProviderException(String category) {
        super("OPENAI " + category);
    }
}
