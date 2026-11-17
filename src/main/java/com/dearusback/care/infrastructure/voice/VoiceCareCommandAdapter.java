package com.dearusback.care.infrastructure.voice;

import com.dearusback.care.application.model.ActiveCareRecordCommandParser;
import com.dearusback.care.application.model.CareRecordCommandParser;
import com.dearusback.care.application.model.CreateCareRecordCommand;
import com.dearusback.care.application.model.StartCareRecordCommand;
import com.dearusback.care.application.port.in.ActiveCareRecordUseCase;
import com.dearusback.care.application.port.in.CreateCareRecordUseCase;
import com.dearusback.care.application.port.in.CareRecordQueryUseCase;
import com.dearusback.care.domain.model.CareRecord;
import com.dearusback.care.domain.model.CareRecord.CareRecordInputType;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.model.CareRecord.BreastSide;
import com.dearusback.care.domain.model.CareRecord.DiaperType;
import com.dearusback.care.domain.model.CareRecord.FeedingType;
import com.dearusback.care.domain.model.CareRecord.TemperatureMeasuredPart;
import com.dearusback.care.domain.model.CareRecord.MedicineDoseUnit;
import com.dearusback.voice.application.port.out.VoiceCareCommandPort;
import com.dearusback.voice.domain.model.VoiceAction;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class VoiceCareCommandAdapter implements VoiceCareCommandPort {
    private final CreateCareRecordUseCase create;
    private final ActiveCareRecordUseCase active;
    private final CareRecordQueryUseCase queries;
    private final ObjectMapper mapper;
    private final Clock clock;

    VoiceCareCommandAdapter(CreateCareRecordUseCase create, ActiveCareRecordUseCase active,
                            CareRecordQueryUseCase queries,
                            ObjectMapper mapper, Clock clock) {
        this.create = create; this.active = active; this.queries = queries;
        this.mapper = mapper; this.clock = clock;
    }

    @Override public Vocabulary vocabulary() {
        return new Vocabulary(names(CareRecordType.values()), names(FeedingType.values()),
                names(BreastSide.values()), names(DiaperType.values()),
                names(TemperatureMeasuredPart.values()), names(MedicineDoseUnit.values()));
    }

    @Override public ActiveRecord activeRecord(Long babyId, Long memberId) {
        var record = queries.active(memberId, babyId);
        return record == null ? null : new ActiveRecord(record.recordId(), record.recordType().name());
    }

    private <T extends Enum<T>> List<String> names(T[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    @Override public Validation validate(Long babyId, Long memberId, VoiceAction action, String draftJson) {
        try {
            JsonNode draft = draft(draftJson);
            if (action == VoiceAction.CREATE_COMPLETED_RECORD) {
                CreateCareRecordCommand command = CareRecordCommandParser.toCommand(babyId, memberId, draft);
                CareRecord.completed(babyId, memberId, command.recordType(), CareRecordInputType.VOICE,
                        command.recordedAt(), command.startAt(), command.endAt(), command.memo(),
                        command.detail(), clock.instant());
                return new Validation(true, command.recordType() == CareRecordType.MEDICINE);
            }
            if (action == VoiceAction.START_RECORD) {
                StartCareRecordCommand command = ActiveCareRecordCommandParser.toCommand(babyId, memberId, draft);
                return new Validation(command.recordType() == CareRecordType.SLEEP
                        || command.recordType() == CareRecordType.FEEDING, false);
            }
            return new Validation(recordId(draft) != null, false);
        } catch (RuntimeException exception) {
            return new Validation(false, false);
        }
    }

    @Override public Long execute(Long babyId, Long memberId, VoiceAction action, String draftJson) {
        JsonNode draft = draft(draftJson);
        return switch (action) {
            case CREATE_COMPLETED_RECORD -> {
                CreateCareRecordCommand value = CareRecordCommandParser.toCommand(babyId, memberId, draft);
                yield create.create(new CreateCareRecordCommand(babyId, memberId, value.recordType(),
                        CareRecordInputType.VOICE, value.recordedAt(), value.startAt(), value.endAt(),
                        value.memo(), value.detail())).id();
            }
            case START_RECORD -> {
                StartCareRecordCommand value = ActiveCareRecordCommandParser.toCommand(babyId, memberId, draft);
                yield active.start(new StartCareRecordCommand(babyId, memberId, value.recordType(),
                        CareRecordInputType.VOICE, value.memo(), value.breastSide())).id();
            }
            case END_RECORD -> active.complete(memberId, babyId, requiredRecordId(draft)).id();
        };
    }

    private JsonNode draft(String value) {
        if (value == null || value.length() > 16_384) throw new IllegalArgumentException("Invalid draft");
        JsonNode node = mapper.readTree(value);
        if (node == null || !node.isObject()) throw new IllegalArgumentException("Invalid draft");
        return node;
    }

    private Long requiredRecordId(JsonNode draft) {
        Long id = recordId(draft);
        if (id == null) throw new IllegalArgumentException("Invalid record ID");
        return id;
    }

    private Long recordId(JsonNode draft) {
        if (draft.size() != 1 || !draft.path("recordId").isIntegralNumber()
                || !draft.path("recordId").canConvertToLong()) return null;
        long id = draft.path("recordId").longValue();
        return id > 0 ? id : null;
    }
}
