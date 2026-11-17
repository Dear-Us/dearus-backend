package com.dearusback.voice.infrastructure.parsing;

import com.dearusback.voice.application.port.out.VoiceCareCommandPort;
import com.dearusback.voice.application.port.out.VoiceCareCommandPort.ActiveRecord;
import com.dearusback.voice.application.port.out.VoiceCareCommandPort.Vocabulary;
import com.dearusback.voice.domain.model.ParsedVoiceCommand;
import com.dearusback.voice.domain.model.VoiceAction;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class VoiceCommandInterpreter {
    public static final String SYSTEM_INSTRUCTION = """
            Interpret Korean baby-care utterances. Output exactly one JSON object.
            One utterance may produce at most one CareRecord. If multiple records are requested, set
            multipleRecords=true and ambiguous=true; do not silently choose one. Never invent missing
            names, dose amounts, units, feeding amounts, times, or other important values. MEDICINE
            name and dose must never be guessed. OTHER requires a concrete title. START_RECORD is only
            SLEEP or DIRECT_BREASTFEEDING. END_RECORD is only an existing active SLEEP or
            DIRECT_BREASTFEEDING record and must use its supplied ID. Resolve relative times from
            the supplied reference datetime and IANA timezone. Use ISO 8601 timestamps with offset.
            Set ambiguous=true whenever the intent or required values are uncertain. Do not return
            prose, markdown, arrays, or more than one command.
            """;
    private static final Set<String> ROOT_FIELDS = Set.of("action", "careType", "ambiguous",
            "multipleRecords", "recordedAt", "startAt", "endAt", "memo", "recordId", "detail");
    private final VoiceCareCommandPort care;
    private final ObjectMapper mapper;

    public VoiceCommandInterpreter(VoiceCareCommandPort care, ObjectMapper mapper) {
        this.care = care;
        this.mapper = mapper;
    }

    public String prompt(String transcript, String timezone, Instant requestedAt,
                         ActiveRecord active) {
        String reference = OffsetDateTime.ofInstant(requestedAt, ZoneId.of(timezone)).toString();
        return "referenceDateTime=" + reference + "\nmemberTimezone=" + timezone
                + "\nactiveRecordId=" + (active == null ? "none" : active.recordId())
                + "\nactiveCareType=" + (active == null ? "none" : active.careType())
                + "\ntranscript=" + transcript;
    }

    public String openAiInstruction(Vocabulary vocabulary) {
        return SYSTEM_INSTRUCTION + "\nUse only these top-level fields: action, careType, ambiguous, "
                + "multipleRecords, recordedAt, startAt, endAt, memo, recordId, detail. "
                + "ambiguous and multipleRecords are required booleans. Omit other fields when unknown or "
                + "not applicable. If the intent is unknown, return "
                + "{\"action\":null,\"careType\":null,\"ambiguous\":true,\"multipleRecords\":false}. "
                + "Allowed action: CREATE_COMPLETED_RECORD, START_RECORD, END_RECORD. "
                + "Allowed careType: " + String.join(", ", vocabulary.careTypes()) + ". "
                + "Allowed detail fields: feedingType, amountMl, breastSide, durationMinutes, diaperType, "
                + "condition, color, temperatureCelsius, measuredPart, medicineName, doseValue, doseUnit, title. "
                + "Allowed feedingType: " + String.join(", ", vocabulary.feedingTypes()) + ". "
                + "Allowed breastSide: " + String.join(", ", vocabulary.breastSides()) + ". "
                + "Allowed diaperType: " + String.join(", ", vocabulary.diaperTypes()) + ". "
                + "Allowed measuredPart: " + String.join(", ", vocabulary.measuredParts()) + ". "
                + "Allowed doseUnit: " + String.join(", ", vocabulary.doseUnits()) + ".";
    }

    public ParsedVoiceCommand decode(String response, Vocabulary vocabulary, ActiveRecord active,
                              Long babyId, Long memberId) {
        if (response == null || response.isBlank() || response.length() > 16_384) invalid();
        JsonNode root;
        try {
            root = mapper.readTree(response);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid voice response");
        }
        if (root == null || !root.isObject()
                || !root.path("ambiguous").isBoolean() || !root.path("multipleRecords").isBoolean()) invalid();
        for (var property : root.properties()) if (!ROOT_FIELDS.contains(property.getKey())) invalid();
        boolean ambiguous = root.path("ambiguous").asBoolean();
        boolean multiple = root.path("multipleRecords").asBoolean();
        JsonNode actionNode = root.get("action");
        JsonNode typeNode = root.get("careType");
        if (actionNode == null || actionNode.isNull() || typeNode == null || typeNode.isNull()) {
            if (!ambiguous && !multiple) invalid();
            return new ParsedVoiceCommand(null, null, true, multiple);
        }
        if (!actionNode.isTextual() || !typeNode.isTextual()
                || !vocabulary.careTypes().contains(typeNode.asText())) invalid();
        VoiceAction action;
        try {
            action = VoiceAction.valueOf(actionNode.asText());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid voice response");
        }
        String careType = typeNode.asText();
        validateEnums(root.path("detail"), vocabulary);
        validateTimes(root);
        Map<String, Object> draft = new LinkedHashMap<>();
        if (action == VoiceAction.END_RECORD) {
            Long id = positiveLong(root.path("recordId"));
            if (id != null) draft.put("recordId", id);
            if (!("SLEEP".equals(careType) || "FEEDING".equals(careType)) || active == null
                    || id == null || !id.equals(active.recordId()) || !careType.equals(active.careType())
                    || present(root, "recordedAt", "startAt", "endAt", "detail", "memo")) {
                ambiguous = true;
            }
        } else {
            draft.put("recordType", careType);
            copy(root, draft, "memo");
            if (action == VoiceAction.CREATE_COMPLETED_RECORD) {
                if ("SLEEP".equals(careType)) {
                    copy(root, draft, "startAt", "endAt");
                    if (present(root, "recordedAt", "detail", "recordId")) ambiguous = true;
                } else {
                    copy(root, draft, "recordedAt", "detail");
                    if (present(root, "startAt", "endAt", "recordId")) ambiguous = true;
                }
            } else {
                if (!("SLEEP".equals(careType) || "FEEDING".equals(careType))) ambiguous = true;
                if (active != null) ambiguous = true;
                if ("FEEDING".equals(careType)) copy(root, draft, "detail");
                if (present(root, "recordedAt", "startAt", "endAt", "recordId")
                        || "SLEEP".equals(careType) && present(root, "detail")) ambiguous = true;
            }
        }
        String draftJson = mapper.writeValueAsString(draft);
        if (!multiple && !ambiguous && !care.validate(babyId, memberId, action, draftJson).valid()) ambiguous = true;
        return new ParsedVoiceCommand(action, draftJson, ambiguous || multiple, multiple);
    }

    private void validateEnums(JsonNode detail, Vocabulary vocabulary) {
        if (detail.isMissingNode() || detail.isNull()) return;
        if (!detail.isObject()) invalid();
        enumField(detail, "feedingType", vocabulary.feedingTypes());
        enumField(detail, "breastSide", vocabulary.breastSides());
        enumField(detail, "diaperType", vocabulary.diaperTypes());
        enumField(detail, "measuredPart", vocabulary.measuredParts());
        enumField(detail, "doseUnit", vocabulary.doseUnits());
    }

    private void enumField(JsonNode node, String field, java.util.List<String> allowed) {
        JsonNode value = node.get(field);
        if (value != null && (!value.isTextual() || !allowed.contains(value.asText()))) invalid();
    }

    private void validateTimes(JsonNode root) {
        for (String field : new String[]{"recordedAt", "startAt", "endAt"}) {
            JsonNode value = root.get(field);
            if (value != null) {
                if (!value.isTextual()) invalid();
                try { OffsetDateTime.parse(value.asText()); }
                catch (RuntimeException exception) { invalid(); }
            }
        }
    }

    private Long positiveLong(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong()) return null;
        long id = value.longValue();
        return id > 0 ? id : null;
    }

    private boolean present(JsonNode node, String... fields) {
        for (String field : fields) if (node.has(field)) return true;
        return false;
    }

    private void copy(JsonNode from, Map<String, Object> to, String... fields) {
        for (String field : fields) if (from.has(field)) to.put(field, from.get(field));
    }

    private void invalid() { throw new IllegalArgumentException("Invalid voice response"); }
}
