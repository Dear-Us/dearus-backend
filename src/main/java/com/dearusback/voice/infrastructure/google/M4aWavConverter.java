package com.dearusback.voice.infrastructure.google;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class M4aWavConverter {
    private static final Logger log = LoggerFactory.getLogger(M4aWavConverter.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    // Leave room below the Speech V2 inline-content limit (10 MB).
    private static final long MAX_WAV_BYTES = 9_000_000;
    private final Path temporaryRoot;

    M4aWavConverter() {
        this(Path.of(System.getProperty("java.io.tmpdir")));
    }

    M4aWavConverter(Path temporaryRoot) {
        this.temporaryRoot = temporaryRoot;
    }

    byte[] convert(byte[] m4a) {
        Path directory = null;
        Process process = null;
        try {
            directory = Files.createTempDirectory(temporaryRoot, "dearus-voice-");
            Path input = directory.resolve("input.m4a");
            Path output = directory.resolve("output.wav");
            Files.write(input, m4a);
            process = new ProcessBuilder("ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error",
                    "-threads", "1", "-i", input.toString(), "-vn", "-ac", "1", "-ar", "16000",
                    "-c:a", "pcm_s16le", "-f", "wav", "-fs", Long.toString(MAX_WAV_BYTES),
                    output.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new GoogleProviderException("STT", "AUDIO_CONVERSION_TIMEOUT");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output)) {
                throw new GoogleProviderException("STT", "AUDIO_CONVERSION_FAILED");
            }
            long size = Files.size(output);
            if (size < 44 || size >= MAX_WAV_BYTES - 100_000) {
                throw new GoogleProviderException("STT", "AUDIO_CONVERSION_FAILED");
            }
            byte[] wav = Files.readAllBytes(output);
            if (!header(wav, 0, "RIFF") || !header(wav, 8, "WAVE")) {
                throw new GoogleProviderException("STT", "AUDIO_CONVERSION_FAILED");
            }
            return wav;
        } catch (IOException exception) {
            throw new GoogleProviderException("STT", "AUDIO_CONVERSION_FAILED");
        } catch (InterruptedException exception) {
            if (process != null) {
                process.destroyForcibly();
                Thread.interrupted();
                try { process.waitFor(); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
            Thread.currentThread().interrupt();
            throw new GoogleProviderException("STT", "AUDIO_CONVERSION_INTERRUPTED");
        } finally {
            if (directory != null) {
                delete(directory.resolve("input.m4a"));
                delete(directory.resolve("output.wav"));
                delete(directory);
            }
        }
    }

    private void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Never log a path, file name, audio bytes, or process stderr.
            log.warn("Voice STT temporary audio cleanup failed");
        }
    }

    private boolean header(byte[] bytes, int offset, String value) {
        for (int index = 0; index < value.length(); index++) {
            if (bytes[offset + index] != value.charAt(index)) return false;
        }
        return true;
    }
}
