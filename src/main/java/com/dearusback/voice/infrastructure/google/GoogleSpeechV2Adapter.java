package com.dearusback.voice.infrastructure.google;

import com.dearusback.voice.application.port.out.VoiceTranscriptionPort;
import com.google.cloud.speech.v2.AutoDetectDecodingConfig;
import com.google.cloud.speech.v2.RecognitionConfig;
import com.google.cloud.speech.v2.RecognizeRequest;
import com.google.cloud.speech.v2.RecognizeResponse;
import com.google.cloud.speech.v2.RecognizerName;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.dearusback.voice.infrastructure.config.VoiceProviderConditions;
import org.springframework.stereotype.Component;

@Component
@Conditional(VoiceProviderConditions.GoogleStt.class)
class GoogleSpeechV2Adapter implements VoiceTranscriptionPort {
    private static final Logger log = LoggerFactory.getLogger(GoogleSpeechV2Adapter.class);
    private final GoogleSpeechGateway gateway;
    private final M4aWavConverter converter;
    private final String recognizer;
    private final String languageCode;
    private final String model;

    GoogleSpeechV2Adapter(GoogleSpeechGateway gateway, M4aWavConverter converter,
                          @Value("${dearus.voice.google.project:}") String project,
                          @Value("${dearus.voice.stt.location:global}") String location,
                          @Value("${dearus.voice.stt.language-code:ko-KR}") String languageCode,
                          @Value("${dearus.voice.stt.model:short}") String model) {
        this.gateway = gateway;
        this.converter = converter;
        this.recognizer = RecognizerName.of(required(project, "GOOGLE_CLOUD_PROJECT"),
                required(location, "DEARUS_STT_LOCATION"), "_").toString();
        this.languageCode = required(languageCode, "DEARUS_STT_LANGUAGE_CODE");
        this.model = required(model, "DEARUS_STT_MODEL");
    }

    @Override
    public String transcribe(byte[] audio, String contentType) {
        boolean m4a = contentType != null && List.of("audio/mp4", "audio/x-m4a", "audio/m4a")
                .contains(contentType.toLowerCase(Locale.ROOT));
        byte[] recognizedAudio;
        try {
            recognizedAudio = m4a ? converter.convert(audio) : audio;
        } catch (GoogleProviderException exception) {
            log.warn("Voice STT audio conversion failed: category={}, inputBytes={}",
                    exception.category(), audio.length);
            throw exception;
        }
        RecognizeRequest request = RecognizeRequest.newBuilder()
                .setRecognizer(recognizer)
                .setConfig(RecognitionConfig.newBuilder()
                        .setAutoDecodingConfig(AutoDetectDecodingConfig.getDefaultInstance())
                        .addLanguageCodes(languageCode)
                        .setModel(model))
                .setContent(ByteString.copyFrom(recognizedAudio))
                .build();
        RecognizeResponse response;
        try {
            response = gateway.recognize(request);
        } catch (RuntimeException exception) {
            log.warn("Voice STT request failed: mediaType={}, inputBytes={}, sentBytes={}, providerStatus={}, category={}",
                    m4a ? "M4A" : "WAV", audio.length, recognizedAudio.length,
                    status(exception), category(exception));
            throw new GoogleProviderException("STT", "REQUEST_FAILED");
        }
        String transcript = aggregate(response);
        if (transcript.isBlank()) throw new GoogleProviderException("STT", "EMPTY_TRANSCRIPT");
        return transcript;
    }

    private static String status(RuntimeException exception) {
        if (exception instanceof com.google.api.gax.rpc.ApiException apiException) {
            return apiException.getStatusCode().getCode().name();
        }
        return exception.getClass().getSimpleName();
    }

    private static String category(RuntimeException exception) {
        String message = exception.getMessage();
        if (message != null && message.toLowerCase(Locale.ROOT).contains("recognitionaudio empty")) {
            return "PROVIDER_REPORTED_EMPTY_AUDIO";
        }
        return "PROVIDER_REQUEST_FAILED";
    }

    static String aggregate(RecognizeResponse response) {
        if (response == null) return "";
        List<String> segments = new ArrayList<>();
        response.getResultsList().forEach(result -> {
            if (result.getAlternativesCount() > 0) {
                String value = result.getAlternatives(0).getTranscript().trim();
                if (!value.isBlank()) segments.add(value);
            }
        });
        return String.join(" ", segments);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for Voice STT");
        return value.trim();
    }
}
