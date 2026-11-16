package com.dearusback.voice.infrastructure.google;

import com.google.cloud.speech.v2.RecognizeRequest;
import com.google.cloud.speech.v2.RecognizeResponse;

@FunctionalInterface
interface GoogleSpeechGateway {
    RecognizeResponse recognize(RecognizeRequest request);
}
