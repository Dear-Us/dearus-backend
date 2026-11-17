package com.dearusback.voice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dearusback.voice.domain.model.ParsedVoiceCommand;
import com.dearusback.voice.domain.model.VoiceAction;
import com.dearusback.voice.domain.model.VoiceClientStatus;
import com.dearusback.voice.domain.model.VoiceProcessingStatus;
import com.dearusback.voice.domain.model.VoiceRequest;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VoiceRequestTest {
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    @Test
    void validLifecycleReachesConfirmedAndBlocksFurtherTransitions() {
        VoiceRequest pending = VoiceRequest.received(1L, 2L, "key", "hash", NOW)
                .transcribed("약 먹였어", NOW)
                .parsed(new ParsedVoiceCommand(VoiceAction.CREATE_COMPLETED_RECORD, "{}", false, false), NOW)
                .pending(VoiceClientStatus.CONFIRMATION_REQUIRED, NOW);
        assertThat(pending.processingStatus()).isEqualTo(VoiceProcessingStatus.COMPLETED);
        VoiceRequest confirmed = pending.confirmed(VoiceAction.CREATE_COMPLETED_RECORD, "{}", 7L, NOW);
        assertThat(confirmed.status()).isEqualTo(VoiceClientStatus.CONFIRMED);
        assertThat(confirmed.recordId()).isEqualTo(7L);
        assertThatThrownBy(() -> confirmed.canceled(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> confirmed.confirmed(VoiceAction.CREATE_COMPLETED_RECORD, "{}", 8L, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void canceledAndFailedRequestsCannotAdvance() {
        VoiceRequest received = VoiceRequest.received(1L, 2L, "key", "hash", NOW);
        VoiceRequest canceled = received.canceled(NOW);
        assertThat(canceled.processingStatus()).isEqualTo(VoiceProcessingStatus.CANCELED);
        assertThat(canceled.status()).isEqualTo(VoiceClientStatus.CANCELED);
        assertThatThrownBy(() -> canceled.transcribed("text", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> canceled.failed("FAIL", NOW)).isInstanceOf(IllegalStateException.class);
        VoiceRequest failed = received.failed("FAIL", NOW);
        assertThatThrownBy(() -> failed.canceled(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void processingSequenceAndResultAreExplicit() {
        VoiceRequest transcribed = VoiceRequest.received(1L, 2L, "key", "hash", NOW)
                .transcribed("목욕했어", NOW);
        assertThat(transcribed.processingStatus()).isEqualTo(VoiceProcessingStatus.TRANSCRIBED);
        assertThatThrownBy(() -> transcribed.transcribed("again", NOW)).isInstanceOf(IllegalStateException.class);
        VoiceRequest parsed = transcribed.parsed(new ParsedVoiceCommand(VoiceAction.CREATE_COMPLETED_RECORD,
                "{}", false, false), NOW);
        assertThat(parsed.processingStatus()).isEqualTo(VoiceProcessingStatus.PARSED);
        assertThat(parsed.autoSaved(9L, NOW).status()).isEqualTo(VoiceClientStatus.AUTO_SAVED);
    }
}
