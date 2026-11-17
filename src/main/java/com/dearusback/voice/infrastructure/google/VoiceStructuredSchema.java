package com.dearusback.voice.infrastructure.google;

import com.dearusback.voice.application.port.out.VoiceCareCommandPort.Vocabulary;
import com.dearusback.voice.domain.model.VoiceAction;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VoiceStructuredSchema {
    private VoiceStructuredSchema() { }

    static Map<String, Object> create(Vocabulary vocabulary) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("feedingType", choice(vocabulary.feedingTypes()));
        detail.put("amountMl", primitive("integer"));
        detail.put("breastSide", choice(vocabulary.breastSides()));
        detail.put("durationMinutes", primitive("integer"));
        detail.put("diaperType", choice(vocabulary.diaperTypes()));
        detail.put("condition", primitive("string"));
        detail.put("color", primitive("string"));
        detail.put("temperatureCelsius", primitive("number"));
        detail.put("measuredPart", choice(vocabulary.measuredParts()));
        detail.put("medicineName", primitive("string"));
        detail.put("doseValue", primitive("number"));
        detail.put("doseUnit", choice(vocabulary.doseUnits()));
        detail.put("title", primitive("string"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", choice(Arrays.stream(VoiceAction.values()).map(Enum::name).toList()));
        properties.put("careType", choice(vocabulary.careTypes()));
        properties.put("ambiguous", primitive("boolean"));
        properties.put("multipleRecords", primitive("boolean"));
        properties.put("recordedAt", primitive("string"));
        properties.put("startAt", primitive("string"));
        properties.put("endAt", primitive("string"));
        properties.put("memo", primitive("string"));
        properties.put("recordId", primitive("integer"));
        properties.put("detail", Map.of("type", "object", "properties", detail));
        return Map.of("type", "object", "properties", properties,
                "required", List.of("ambiguous", "multipleRecords"));
    }

    private static Map<String, Object> primitive(String type) { return Map.of("type", type); }
    private static Map<String, Object> choice(List<String> allowed) {
        return Map.of("type", "string", "enum", allowed);
    }
}
