package com.dearusback.voice.infrastructure.google;

import com.google.genai.types.GenerateContentConfig;

@FunctionalInterface
interface GoogleGeminiGateway {
    String generate(String model, String prompt, GenerateContentConfig config);
}
