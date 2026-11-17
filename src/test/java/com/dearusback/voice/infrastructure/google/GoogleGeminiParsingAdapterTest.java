package com.dearusback.voice.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dearusback.voice.application.port.out.VoiceCareCommandPort;
import com.dearusback.voice.domain.model.VoiceAction;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GoogleGeminiParsingAdapterTest {
    private static final Instant REFERENCE = Instant.parse("2026-10-08T03:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();
    private final FakeCare care = new FakeCare();

    @Test
    void validStructuredOtherResultUsesReferenceTimezoneAndCareDraft() {
        AtomicReference<String> prompt = new AtomicReference<>();
        AtomicReference<String> config = new AtomicReference<>();
        var adapter = adapter((model, text, settings) -> {
            assertThat(model).isEqualTo("gemini-2.5-flash");
            prompt.set(text);
            config.set(settings.toString());
            return """
                    {"action":"CREATE_COMPLETED_RECORD","careType":"OTHER","ambiguous":false,
                     "multipleRecords":false,"recordedAt":"2026-10-08T12:00:00+09:00",
                     "detail":{"title":"목욕"}}
                    """;
        });
        var command = adapter.parse("아기 목욕했어", "Asia/Seoul", REFERENCE, 7L, 9L);
        assertThat(command.action()).isEqualTo(VoiceAction.CREATE_COMPLETED_RECORD);
        assertThat(command.ambiguous()).isFalse();
        assertThat(command.multipleRecords()).isFalse();
        assertThat(mapper.readTree(command.draftJson()).path("recordType").asText()).isEqualTo("OTHER");
        assertThat(mapper.readTree(command.draftJson()).path("detail").path("title").asText()).isEqualTo("목욕");
        assertThat(prompt.get()).contains("memberTimezone=Asia/Seoul", "referenceDateTime=2026-10-08T12:00+09:00",
                "transcript=아기 목욕했어");
        assertThat(config.get()).contains("application/json", "CREATE_COMPLETED_RECORD", "DIRECT_BREASTFEEDING");
        assertThat(care.validated).isTrue();
    }

    @Test
    void formulaUtteranceProducesExactCareDraftWithoutReview() {
        var adapter = adapter((model, prompt, config) -> {
            assertThat(prompt).contains("transcript=아기에게 분유 120ml 먹였어");
            return """
                    {"action":"CREATE_COMPLETED_RECORD","careType":"FEEDING","ambiguous":false,
                     "multipleRecords":false,"recordedAt":"2026-10-08T12:00:00+09:00",
                     "detail":{"feedingType":"FORMULA","amountMl":120}}
                    """;
        });
        var command = adapter.parse("아기에게 분유 120ml 먹였어", "Asia/Seoul", REFERENCE, 7L, 9L);
        var draft = mapper.readTree(command.draftJson());
        assertThat(command.ambiguous()).isFalse();
        assertThat(draft.path("recordType").asText()).isEqualTo("FEEDING");
        assertThat(draft.path("detail").path("feedingType").asText()).isEqualTo("FORMULA");
        assertThat(draft.path("detail").path("amountMl").asInt()).isEqualTo(120);
    }

    @Test
    void medicineFieldsMapExactlyWithoutInventingDose() {
        var adapter = adapter((model, prompt, config) -> """
                {"action":"CREATE_COMPLETED_RECORD","careType":"MEDICINE","ambiguous":false,
                 "multipleRecords":false,"recordedAt":"2026-10-08T11:00:00+09:00",
                 "detail":{"medicineName":"해열제","doseValue":2.5,"doseUnit":"ML"}}
                """);
        var command = adapter.parse("해열제 2.5밀리 줬어", "Asia/Seoul", REFERENCE, 7L, 9L);
        var detail = mapper.readTree(command.draftJson()).path("detail");
        assertThat(detail.path("medicineName").asText()).isEqualTo("해열제");
        assertThat(detail.path("doseValue").decimalValue()).isEqualByComparingTo("2.5");
        assertThat(detail.path("doseUnit").asText()).isEqualTo("ML");
        assertThat(command.ambiguous()).isFalse();
    }

    @Test
    void missingMedicineDoseIsReviewRatherThanAutoSave() {
        care.valid = false;
        var adapter = adapter((model, prompt, config) -> """
                {"action":"CREATE_COMPLETED_RECORD","careType":"MEDICINE","ambiguous":false,
                 "multipleRecords":false,"recordedAt":"2026-10-08T11:00:00+09:00",
                 "detail":{"medicineName":"해열제"}}
                """);
        assertThat(adapter.parse("해열제 줬어", "Asia/Seoul", REFERENCE, 7L, 9L).ambiguous()).isTrue();
    }

    @Test
    void malformedJsonAndInvalidEnumAreRejected() {
        for (String response : new String[]{"not json", "[]",
                "{\"ambiguous\":false,\"multipleRecords\":false,\"action\":\"BAD\",\"careType\":\"OTHER\"}",
                "{\"ambiguous\":false,\"multipleRecords\":false,\"action\":\"START_RECORD\",\"careType\":\"INVALID\"}",
                "{\"ambiguous\":false,\"multipleRecords\":false,\"action\":\"START_RECORD\",\"careType\":\"FEEDING\",\"detail\":{\"feedingType\":\"UNKNOWN\"}}"}) {
            var adapter = adapter((model, prompt, config) -> response);
            assertThatThrownBy(() -> adapter.parse("테스트", "Asia/Seoul", REFERENCE, 7L, 9L))
                    .isInstanceOf(GoogleProviderException.class).hasMessage("GEMINI INVALID_RESPONSE");
        }
    }

    @Test
    void multipleRecordFlagForcesReview() {
        var adapter = adapter((model, prompt, config) -> """
                {"action":"CREATE_COMPLETED_RECORD","careType":"OTHER","ambiguous":false,
                 "multipleRecords":true,"recordedAt":"2026-10-08T11:00:00+09:00",
                 "detail":{"title":"목욕"}}
                """);
        var command = adapter.parse("목욕하고 산책했어", "Asia/Seoul", REFERENCE, 7L, 9L);
        assertThat(command.multipleRecords()).isTrue();
        assertThat(command.ambiguous()).isTrue();
        assertThat(care.validated).isFalse();
    }

    @Test
    void startRequiresValidDirectFeedingDetailAndNoActiveRecord() {
        var adapter = adapter((model, prompt, config) -> """
                {"action":"START_RECORD","careType":"FEEDING","ambiguous":false,
                 "multipleRecords":false,"detail":{"feedingType":"DIRECT_BREASTFEEDING","breastSide":"LEFT"}}
                """);
        var command = adapter.parse("왼쪽 수유 시작", "Asia/Seoul", REFERENCE, 7L, 9L);
        assertThat(command.ambiguous()).isFalse();
        assertThat(mapper.readTree(command.draftJson()).path("detail").path("breastSide").asText())
                .isEqualTo("LEFT");
        care.active = new VoiceCareCommandPort.ActiveRecord(41L, "SLEEP");
        assertThat(adapter.parse("왼쪽 수유 시작", "Asia/Seoul", REFERENCE, 7L, 9L).ambiguous()).isTrue();
    }

    @Test
    void endOnlyTargetsSuppliedActiveRecord() {
        care.active = new VoiceCareCommandPort.ActiveRecord(41L, "SLEEP");
        var adapter = adapter((model, prompt, config) -> """
                {"action":"END_RECORD","careType":"SLEEP","ambiguous":false,
                 "multipleRecords":false,"recordId":41}
                """);
        assertThat(adapter.parse("수면 종료", "Asia/Seoul", REFERENCE, 7L, 9L).ambiguous()).isFalse();
        assertThat(mapper.readTree(care.lastDraft).path("recordId").asLong()).isEqualTo(41L);
        care.active = new VoiceCareCommandPort.ActiveRecord(42L, "SLEEP");
        assertThat(adapter.parse("수면 종료", "Asia/Seoul", REFERENCE, 7L, 9L).ambiguous()).isTrue();
    }

    @Test
    void providerErrorMapsToSafeCategory() {
        var adapter = adapter((model, prompt, config) -> {
            throw new IllegalStateException("provider response with sensitive body");
        });
        assertThatThrownBy(() -> adapter.parse("아기 목욕", "Asia/Seoul", REFERENCE, 7L, 9L))
                .isInstanceOf(GoogleProviderException.class).hasMessage("GEMINI REQUEST_FAILED").hasNoCause();
    }

    private GoogleGeminiParsingAdapter adapter(GoogleGeminiGateway gateway) {
        return new GoogleGeminiParsingAdapter(gateway, care, mapper, "gemini-2.5-flash");
    }

    private static class FakeCare implements VoiceCareCommandPort {
        boolean valid = true;
        boolean validated;
        String lastDraft;
        ActiveRecord active;

        @Override public Validation validate(Long babyId, Long memberId, VoiceAction action, String draftJson) {
            validated = true;
            lastDraft = draftJson;
            return new Validation(valid, false);
        }
        @Override public Long execute(Long babyId, Long memberId, VoiceAction action, String draftJson) {
            throw new UnsupportedOperationException();
        }
        @Override public Vocabulary vocabulary() {
            return new Vocabulary(List.of("FEEDING", "SLEEP", "DIAPER", "TEMPERATURE", "MEDICINE", "OTHER"),
                    List.of("FORMULA", "DIRECT_BREASTFEEDING"), List.of("LEFT", "RIGHT", "BOTH"),
                    List.of("URINE", "STOOL", "BOTH"), List.of("EAR", "FOREHEAD", "ARMPIT", "OTHER"),
                    List.of("ML", "MG", "DROP", "TABLET", "OTHER"));
        }
        @Override public ActiveRecord activeRecord(Long babyId, Long memberId) { return active; }
    }
}
