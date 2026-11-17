package com.dearusback.voice.application.service;

import com.dearusback.voice.application.port.out.VoiceCommandParsingPort;
import com.dearusback.voice.application.port.out.VoiceProcessingScheduler;
import com.dearusback.voice.application.port.out.VoiceTranscriptionPort;
import com.dearusback.voice.domain.exception.VoiceErrorCode;
import com.dearusback.voice.domain.exception.VoiceException;
import com.dearusback.voice.domain.model.ParsedVoiceCommand;
import com.dearusback.voice.domain.model.VoiceAction;
import com.dearusback.voice.domain.model.VoiceRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class VoiceWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(VoiceWorkflowService.class);
    private static final Set<String> ACCEPTED = Set.of("audio/mp4", "audio/x-m4a", "audio/m4a",
            "audio/wav", "audio/x-wav", "audio/wave");
    private final VoiceRequestTransactions transactions;
    private final List<VoiceTranscriptionPort> transcribers;
    private final List<VoiceCommandParsingPort> parsers;
    private final VoiceProcessingScheduler scheduler;
    private final boolean enabled;
    private final int maxAudioBytes;

    public VoiceWorkflowService(VoiceRequestTransactions transactions, List<VoiceTranscriptionPort> transcribers,
                                List<VoiceCommandParsingPort> parsers, VoiceProcessingScheduler scheduler,
                                @Value("${dearus.voice.enabled:false}") boolean enabled,
                                @Value("${dearus.voice.max-audio-bytes:5242880}") int maxAudioBytes) {
        this.transactions = transactions; this.transcribers = transcribers; this.parsers = parsers;
        this.scheduler = scheduler; this.enabled = enabled; this.maxAudioBytes = maxAudioBytes;
    }

    public VoiceRequest upload(Long memberId, Long babyId, String key, byte[] audio, String contentType) {
        available();
        if (memberId == null || babyId == null || babyId <= 0 || key == null) invalid();
        try {
            if (!UUID.fromString(key).toString().equalsIgnoreCase(key)) invalid();
        } catch (IllegalArgumentException exception) {
            invalid();
        }
        transactions.authorize(memberId, babyId);
        String type = contentType == null ? "" : contentType.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ACCEPTED.contains(type) || audio == null || audio.length == 0) {
            throw new VoiceException(VoiceErrorCode.INVALID_AUDIO);
        }
        if (audio.length > maxAudioBytes) throw new VoiceException(VoiceErrorCode.AUDIO_TOO_LARGE);
        if (!looksLikeAudio(audio, type)) throw new VoiceException(VoiceErrorCode.INVALID_AUDIO);
        String hash = hash(babyId, type, audio);
        VoiceRequestTransactions.Registration registered;
        try {
            registered = transactions.register(memberId, babyId, key, hash);
        } catch (DataIntegrityViolationException exception) {
            if (!idempotencyUniqueViolation(exception)) throw exception;
            return transactions.existing(memberId, babyId, key, hash);
        }
        if (registered.created()) {
            Long id = registered.request().id();
            try {
                scheduler.submit(() -> process(id, babyId, memberId, registered.timezone(),
                        registered.request().createdAt(), audio, type));
            } catch (RuntimeException exception) {
                log.warn("Voice processing queue unavailable for request {}", id);
                transactions.fail(id, "PROCESSING_UNAVAILABLE");
            }
        }
        return registered.request();
    }

    public VoiceRequest get(Long memberId, Long babyId, Long id) {
        return transactions.get(memberId, babyId, positive(id));
    }

    public VoiceRequest confirm(Long memberId, Long babyId, Long id, VoiceAction action, String draft) {
        return transactions.confirm(memberId, babyId, positive(id), action, draft);
    }

    public VoiceRequest cancel(Long memberId, Long babyId, Long id) {
        return transactions.cancel(memberId, babyId, positive(id));
    }

    private void process(Long id, Long babyId, Long memberId, String timezone,
                         Instant requestedAt, byte[] audio, String type) {
        if (!transactions.canProcess(id)) return;
        String transcript;
        try {
            transcript = transcribers.getFirst().transcribe(audio, type);
        } catch (RuntimeException exception) {
            log.warn("Voice transcription failed for request {} ({})", id, exception.getClass().getSimpleName());
            transactions.fail(id, "TRANSCRIPTION_FAILED");
            return;
        }
        if (transcript == null || transcript.isBlank()) {
            transactions.fail(id, "EMPTY_TRANSCRIPT");
            return;
        }
        if (!transactions.transcribed(id, transcript) || !transactions.canProcess(id)) return;
        ParsedVoiceCommand parsed;
        try {
            parsed = parsers.getFirst().parse(transcript, timezone, requestedAt, babyId, memberId);
            if (parsed == null || parsed.draftJson() != null && parsed.draftJson().length() > 16_384) {
                throw new IllegalArgumentException("Invalid parsed voice command");
            }
        } catch (RuntimeException exception) {
            log.warn("Voice parsing failed for request {} ({})", id, exception.getClass().getSimpleName());
            transactions.fail(id, "PARSING_FAILED");
            return;
        }
        if (!transactions.parsed(id, parsed)) return;
        try {
            transactions.finish(id, parsed);
        } catch (RuntimeException exception) {
            log.warn("Voice care execution failed for request {} ({})", id, exception.getClass().getSimpleName());
            transactions.fail(id, "CARE_EXECUTION_FAILED");
        }
    }

    private void available() {
        if (!enabled || transcribers.isEmpty() || parsers.isEmpty()) {
            throw new VoiceException(VoiceErrorCode.VOICE_UNAVAILABLE);
        }
    }

    private Long positive(Long id) {
        if (id == null || id <= 0) throw new VoiceException(VoiceErrorCode.INVALID_REQUEST);
        return id;
    }

    private void invalid() { throw new VoiceException(VoiceErrorCode.INVALID_REQUEST); }

    private boolean looksLikeAudio(byte[] bytes, String type) {
        if (type.contains("wav") || type.contains("wave")) {
            return bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WAVE");
        }
        return bytes.length >= 12 && ascii(bytes, 4, "ftyp");
    }

    private boolean ascii(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) return false;
        for (int index = 0; index < value.length(); index++) {
            if (bytes[offset + index] != (byte) value.charAt(index)) return false;
        }
        return true;
    }

    private String hash(Long babyId, String type, byte[] audio) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(babyId).array());
            digest.update(type.getBytes(StandardCharsets.UTF_8));
            digest.update(audio);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean idempotencyUniqueViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT)
                    .contains("uk_voice_request_member_key")) return true;
        }
        return false;
    }
}
