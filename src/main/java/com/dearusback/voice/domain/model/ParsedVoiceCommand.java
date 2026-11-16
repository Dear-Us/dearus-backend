package com.dearusback.voice.domain.model;

public record ParsedVoiceCommand(VoiceAction action, String draftJson,
                                 boolean ambiguous, boolean multipleRecords) { }
