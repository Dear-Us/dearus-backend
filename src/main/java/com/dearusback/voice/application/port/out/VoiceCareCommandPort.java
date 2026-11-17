package com.dearusback.voice.application.port.out;

import com.dearusback.voice.domain.model.VoiceAction;

public interface VoiceCareCommandPort {
    Validation validate(Long babyId, Long memberId, VoiceAction action, String draftJson);
    Long execute(Long babyId, Long memberId, VoiceAction action, String draftJson);
    Vocabulary vocabulary();
    ActiveRecord activeRecord(Long babyId, Long memberId);

    record Validation(boolean valid, boolean medicine) { }
    record Vocabulary(java.util.List<String> careTypes, java.util.List<String> feedingTypes,
                      java.util.List<String> breastSides, java.util.List<String> diaperTypes,
                      java.util.List<String> measuredParts, java.util.List<String> doseUnits) { }
    record ActiveRecord(Long recordId, String careType) { }
}
