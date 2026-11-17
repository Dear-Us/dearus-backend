package com.dearusback.voice.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M4aWavConverterTest {
    @TempDir Path temporaryRoot;

    @Test
    void decodesAacM4aToPcm16Mono16kAndRemovesTemporaryFiles() throws Exception {
        Assumptions.assumeTrue(ffmpegAvailable(), "FFmpeg is verified in the production Docker image");
        Path input = temporaryRoot.resolve("fixture.m4a");
        Process fixture = new ProcessBuilder("ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=0.25", "-c:a", "aac", input.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(fixture.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(fixture.exitValue()).isZero();
        byte[] wav = new M4aWavConverter(temporaryRoot).convert(Files.readAllBytes(input));
        assertThat(new String(wav, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(wav, 8, 4)).isEqualTo("WAVE");
        ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(header.getShort(20)).isEqualTo((short) 1); // PCM
        assertThat(header.getShort(22)).isEqualTo((short) 1); // mono
        assertThat(header.getInt(24)).isEqualTo(16_000);
        assertThat(header.getShort(34)).isEqualTo((short) 16);
        try (var files = Files.list(temporaryRoot)) {
            assertThat(files.toList()).containsExactly(input);
        }
    }

    @Test
    void invalidM4aFailsWithoutRetainingAudioOrRawProviderOutput() throws Exception {
        Assumptions.assumeTrue(ffmpegAvailable(), "FFmpeg is verified in the production Docker image");
        byte[] invalid = new byte[]{0, 0, 0, 12, 'f', 't', 'y', 'p', 'M', '4', 'A', ' '};
        assertThatThrownBy(() -> new M4aWavConverter(temporaryRoot).convert(invalid))
                .isInstanceOf(GoogleProviderException.class)
                .hasMessage("STT AUDIO_CONVERSION_FAILED").hasNoCause();
        try (var files = Files.list(temporaryRoot)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    private boolean ffmpegAvailable() {
        try {
            Process check = new ProcessBuilder("ffmpeg", "-version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            return check.waitFor(2, TimeUnit.SECONDS) && check.exitValue() == 0;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }
}
