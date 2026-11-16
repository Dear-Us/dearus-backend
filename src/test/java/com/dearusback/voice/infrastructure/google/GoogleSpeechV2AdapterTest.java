package com.dearusback.voice.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.cloud.speech.v2.RecognizeRequest;
import com.google.cloud.speech.v2.RecognizeResponse;
import com.google.cloud.speech.v2.SpeechRecognitionAlternative;
import com.google.cloud.speech.v2.SpeechRecognitionResult;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GoogleSpeechV2AdapterTest {
    @Test
    void buildsV2ImplicitRecognizerAndAggregatesOrderedResults() {
        AtomicReference<RecognizeRequest> captured = new AtomicReference<>();
        GoogleSpeechV2Adapter adapter = new GoogleSpeechV2Adapter(request -> {
            captured.set(request);
            return RecognizeResponse.newBuilder()
                    .addResults(result("아기 우유", "ignored alternative"))
                    .addResults(SpeechRecognitionResult.getDefaultInstance())
                    .addResults(result("먹었어"))
                    .build();
        }, converter(), "project-for-test", "global", "ko-KR", "short");
        assertThat(adapter.transcribe(new byte[]{1, 2, 3}, "audio/wav")).isEqualTo("아기 우유 먹었어");
        RecognizeRequest request = captured.get();
        assertThat(request.getRecognizer()).isEqualTo("projects/project-for-test/locations/global/recognizers/_");
        assertThat(request.getConfig().hasAutoDecodingConfig()).isTrue();
        assertThat(request.getConfig().getLanguageCodesList()).containsExactly("ko-KR");
        assertThat(request.getConfig().getModel()).isEqualTo("short");
        assertThat(request.getContent().toByteArray()).containsExactly(1, 2, 3);
    }

    @Test
    void noAlternativeOrBlankTranscriptIsNotSuccessful() {
        GoogleSpeechV2Adapter adapter = new GoogleSpeechV2Adapter(request -> RecognizeResponse.newBuilder()
                .addResults(SpeechRecognitionResult.getDefaultInstance())
                .addResults(result("   ")).build(), converter(), "project", "global", "ko-KR", "short");
        assertThatThrownBy(() -> adapter.transcribe(new byte[]{1}, "audio/wav"))
                .isInstanceOf(GoogleProviderException.class).hasMessage("STT EMPTY_TRANSCRIPT");
    }

    @Test
    void providerFailureDoesNotLeakRawMessage() {
        GoogleSpeechV2Adapter adapter = new GoogleSpeechV2Adapter(request -> {
            throw new IllegalStateException("sensitive provider response");
        }, converter(), "project", "global", "ko-KR", "short");
        assertThatThrownBy(() -> adapter.transcribe(new byte[]{1}, "audio/wav"))
                .isInstanceOf(GoogleProviderException.class).hasMessage("STT REQUEST_FAILED").hasNoCause();
    }

    @Test
    void m4aIsConvertedBeforeSpeechRequestAndWavIsNotConverted() {
        AtomicReference<byte[]> convertedInput = new AtomicReference<>();
        AtomicReference<RecognizeRequest> captured = new AtomicReference<>();
        M4aWavConverter converter = new M4aWavConverter() {
            @Override byte[] convert(byte[] m4a) {
                convertedInput.set(m4a);
                return new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
            }
        };
        GoogleSpeechV2Adapter adapter = new GoogleSpeechV2Adapter(request -> {
            captured.set(request);
            return RecognizeResponse.newBuilder().addResults(result("아기에게 분유 120ml 먹였어")).build();
        }, converter, "project", "global", "ko-KR", "short");
        byte[] m4a = new byte[]{0, 0, 0, 12, 'f', 't', 'y', 'p', 'M', '4', 'A', ' '};
        assertThat(adapter.transcribe(m4a, "audio/x-m4a")).isEqualTo("아기에게 분유 120ml 먹였어");
        assertThat(convertedInput.get()).containsExactly(m4a);
        assertThat(captured.get().getContent().toByteArray())
                .startsWith((byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F');
        convertedInput.set(null);
        byte[] wav = new byte[]{1, 2, 3};
        adapter.transcribe(wav, "audio/wav");
        assertThat(convertedInput.get()).isNull();
        assertThat(captured.get().getContent().toByteArray()).containsExactly(wav);
    }

    @Test
    void failedM4aConversionNeverCallsSpeechOrLeaksInput() {
        M4aWavConverter converter = new M4aWavConverter() {
            @Override byte[] convert(byte[] m4a) {
                throw new GoogleProviderException("STT", "AUDIO_CONVERSION_FAILED");
            }
        };
        GoogleSpeechV2Adapter adapter = new GoogleSpeechV2Adapter(request -> {
            throw new AssertionError("Speech must not receive undecodable audio");
        }, converter, "project", "global", "ko-KR", "short");
        assertThatThrownBy(() -> adapter.transcribe(new byte[]{1, 2, 3}, "audio/mp4"))
                .isInstanceOf(GoogleProviderException.class)
                .hasMessage("STT AUDIO_CONVERSION_FAILED").hasNoCause();
    }

    private M4aWavConverter converter() {
        return new M4aWavConverter();
    }

    private SpeechRecognitionResult result(String... alternatives) {
        var builder = SpeechRecognitionResult.newBuilder();
        for (String alternative : alternatives) {
            builder.addAlternatives(SpeechRecognitionAlternative.newBuilder().setTranscript(alternative));
        }
        return builder.build();
    }
}
