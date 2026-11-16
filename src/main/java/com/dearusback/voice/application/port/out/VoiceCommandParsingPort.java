package com.dearusback.voice.application.port.out;

import com.dearusback.voice.domain.model.ParsedVoiceCommand;
import java.time.Instant;

public interface VoiceCommandParsingPort {
    ParsedVoiceCommand parse(String transcript, String timezone, Instant requestedAt,
                             Long babyId, Long memberId);
}
