package com.dearusback.care.application.model;

import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import com.dearusback.care.domain.model.CareRecord;
import java.time.Instant;
import java.util.Objects;

public record RecordChangedEvent(EventType eventType, Long babyId, Long recordId,
                                 CareRecordType recordType, Instant occurredAt) {
    public RecordChangedEvent {
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(babyId);
        Objects.requireNonNull(recordId);
        Objects.requireNonNull(recordType);
        Objects.requireNonNull(occurredAt);
    }

    public static RecordChangedEvent from(EventType type, CareRecord record) {
        return new RecordChangedEvent(type, record.babyId(), record.id(), record.recordType(),
                record.updatedAt());
    }

    public enum EventType {
        RECORD_CREATED,
        RECORD_STARTED,
        RECORD_COMPLETED,
        RECORD_CANCELED,
        RECORD_UPDATED,
        RECORD_DELETED
    }
}
