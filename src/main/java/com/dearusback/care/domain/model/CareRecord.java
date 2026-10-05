package com.dearusback.care.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.util.Objects;

public record CareRecord(Long id, Long babyId, CareRecordType recordType, CareRecordInputType inputType,
                         CareRecordStatus status, OffsetDateTime recordedAt, OffsetDateTime startAt,
                         OffsetDateTime endAt, String memo, Detail detail, Long createdByMemberId,
                         Long completedByMemberId, Long lastUpdatedByMemberId, Long deletedByMemberId,
                         long version, boolean deleted, Instant createdAt, Instant updatedAt, Instant deletedAt) {

    public CareRecord {
        Objects.requireNonNull(babyId);
        Objects.requireNonNull(recordType);
        Objects.requireNonNull(inputType);
        Objects.requireNonNull(status);
        Objects.requireNonNull(recordedAt);
        Objects.requireNonNull(createdByMemberId);
        Objects.requireNonNull(lastUpdatedByMemberId);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public static CareRecord completed(Long babyId, Long memberId, CareRecordType type,
                                       CareRecordInputType inputType, OffsetDateTime recordedAt,
                                       OffsetDateTime startAt, OffsetDateTime endAt, String memo,
                                       Detail detail, Instant now) {
        if (type == CareRecordType.SLEEP) {
            if (detail != null || recordedAt != null || startAt == null || endAt == null
                    || !startAt.toInstant().isBefore(endAt.toInstant()) || endAt.toInstant().isAfter(now)) {
                throw new IllegalArgumentException("Invalid completed sleep record");
            }
            recordedAt = startAt;
        } else if (recordedAt == null || recordedAt.toInstant().isAfter(now) || startAt != null || endAt != null
                || !matches(type, detail) || (detail instanceof FeedingDetail feeding
                && feeding.feedingType() == FeedingType.DIRECT_BREASTFEEDING
                && feeding.durationMinutes() == null)) {
            throw new IllegalArgumentException("Invalid completed record");
        }
        return new CareRecord(null, babyId, type, inputType, CareRecordStatus.COMPLETED, recordedAt,
                startAt, endAt, memo, detail, memberId, memberId, memberId, null,
                0, false, now, now, null);
    }

    public static CareRecord startSleep(Long babyId, Long memberId, CareRecordInputType inputType,
                                        OffsetDateTime now, String memo) {
        return new CareRecord(null, babyId, CareRecordType.SLEEP, inputType, CareRecordStatus.IN_PROGRESS,
                now, now, null, memo, null, memberId, null, memberId, null, 0, false,
                now.toInstant(), now.toInstant(), null);
    }

    public static CareRecord startBreastfeeding(Long babyId, Long memberId, CareRecordInputType inputType,
                                                OffsetDateTime now, String memo, BreastSide side) {
        if (side == null || side == BreastSide.BOTH) {
            throw new IllegalArgumentException("Invalid active breast side");
        }
        return new CareRecord(null, babyId, CareRecordType.FEEDING, inputType, CareRecordStatus.IN_PROGRESS,
                now, now, null, memo, new FeedingDetail(FeedingType.DIRECT_BREASTFEEDING, null, side, null),
                memberId, null, memberId, null, 0, false, now.toInstant(), now.toInstant(), null);
    }

    public CareRecord complete(OffsetDateTime now, Long memberId) {
        requireInProgress();
        if (now.toInstant().isBefore(startAt.toInstant())) {
            throw new IllegalArgumentException("Completion precedes start");
        }
        Detail completedDetail = detail;
        if (detail instanceof FeedingDetail feeding) {
            Duration elapsed = Duration.between(startAt.toInstant(), now.toInstant());
            long seconds = elapsed.getSeconds() + (elapsed.getNano() > 0 ? 1 : 0);
            long minutes = Math.max(1, (seconds + 59) / 60);
            if (minutes > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Duration exceeds supported range");
            }
            completedDetail = new FeedingDetail(FeedingType.DIRECT_BREASTFEEDING, null,
                    feeding.breastSide(), (int) minutes);
        }
        return new CareRecord(id, babyId, recordType, inputType, CareRecordStatus.COMPLETED,
                recordedAt, startAt, now, memo, completedDetail, createdByMemberId, memberId, memberId,
                deletedByMemberId, version, deleted, createdAt, now.toInstant(), deletedAt);
    }

    public CareRecord cancel(Instant now, Long memberId) {
        requireInProgress();
        return new CareRecord(id, babyId, recordType, inputType, CareRecordStatus.CANCELED,
                recordedAt, startAt, null, memo, detail, createdByMemberId, null, memberId,
                deletedByMemberId, version, deleted, createdAt, now, deletedAt);
    }

    private void requireInProgress() {
        boolean validSleep = recordType == CareRecordType.SLEEP && detail == null;
        boolean validFeeding = recordType == CareRecordType.FEEDING && detail instanceof FeedingDetail feeding
                && feeding.feedingType() == FeedingType.DIRECT_BREASTFEEDING
                && feeding.breastSide() != BreastSide.BOTH && feeding.durationMinutes() == null;
        if (deleted || status != CareRecordStatus.IN_PROGRESS || (!validSleep && !validFeeding)) {
            throw new IllegalStateException("Record is not in progress");
        }
    }

    public CareRecord inZone(ZoneId zone) {
        return new CareRecord(id, babyId, recordType, inputType, status,
                recordedAt.atZoneSameInstant(zone).toOffsetDateTime(),
                startAt == null ? null : startAt.atZoneSameInstant(zone).toOffsetDateTime(),
                endAt == null ? null : endAt.atZoneSameInstant(zone).toOffsetDateTime(), memo, detail,
                createdByMemberId, completedByMemberId, lastUpdatedByMemberId, deletedByMemberId,
                version, deleted, createdAt, updatedAt, deletedAt);
    }

    private static boolean matches(CareRecordType type, Detail detail) {
        return switch (type) {
            case FEEDING -> detail instanceof FeedingDetail;
            case DIAPER -> detail instanceof DiaperDetail;
            case TEMPERATURE -> detail instanceof TemperatureDetail;
            case MEDICINE -> detail instanceof MedicineDetail;
            case OTHER -> detail instanceof OtherDetail;
            case SLEEP -> false;
        };
    }

    public CareRecord persisted(Long recordId, long storedVersion) {
        return new CareRecord(recordId, babyId, recordType, inputType, status, recordedAt, startAt, endAt,
                memo, detail, createdByMemberId, completedByMemberId, lastUpdatedByMemberId,
                deletedByMemberId, storedVersion, deleted, createdAt, updatedAt, deletedAt);
    }

    public enum CareRecordType { FEEDING, SLEEP, DIAPER, TEMPERATURE, MEDICINE, OTHER }
    public enum CareRecordStatus { COMPLETED, IN_PROGRESS, CANCELED }
    public enum CareRecordInputType { MANUAL, VOICE }
    public enum FeedingType { FORMULA, DIRECT_BREASTFEEDING }
    public enum BreastSide { LEFT, RIGHT, BOTH }
    public enum DiaperType { URINE, STOOL, BOTH }
    public enum TemperatureMeasuredPart { EAR, FOREHEAD, ARMPIT, OTHER }
    public enum MedicineDoseUnit { ML, MG, DROP, TABLET, OTHER }

    public sealed interface Detail permits FeedingDetail, DiaperDetail, TemperatureDetail, MedicineDetail, OtherDetail {
    }

    public record FeedingDetail(FeedingType feedingType, Integer amountMl, BreastSide breastSide,
                                Integer durationMinutes) implements Detail {
        public FeedingDetail {
            Objects.requireNonNull(feedingType);
            if (feedingType == FeedingType.FORMULA) {
                if (amountMl == null || amountMl <= 0 || breastSide != null || durationMinutes != null) {
                    throw new IllegalArgumentException("Invalid formula detail");
                }
            } else if (amountMl != null || breastSide == null || (durationMinutes != null && durationMinutes <= 0)) {
                throw new IllegalArgumentException("Invalid breastfeeding detail");
            }
        }
    }

    public record DiaperDetail(DiaperType diaperType, String condition, String color) implements Detail {
        public DiaperDetail { Objects.requireNonNull(diaperType); }
    }

    public record TemperatureDetail(BigDecimal temperatureCelsius,
                                    TemperatureMeasuredPart measuredPart) implements Detail {
        public TemperatureDetail {
            if (temperatureCelsius == null || temperatureCelsius.signum() <= 0 || measuredPart == null) {
                throw new IllegalArgumentException("Invalid temperature detail");
            }
        }
    }

    public record MedicineDetail(String medicineName, BigDecimal doseValue,
                                 MedicineDoseUnit doseUnit) implements Detail {
        public MedicineDetail {
            medicineName = requiredName(medicineName);
            if (doseValue == null || doseValue.signum() <= 0 || doseUnit == null) {
                throw new IllegalArgumentException("Invalid medicine detail");
            }
        }
    }

    public record OtherDetail(String title) implements Detail {
        public OtherDetail { title = requiredName(title); }
    }

    private static String requiredName(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new IllegalArgumentException("Name must contain 1 to 100 characters");
        }
        return value.trim();
    }
}
