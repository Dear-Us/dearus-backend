package com.dearusback.family.application.service;

import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.BabyGender;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

final class BabyInputValidator {

    private BabyInputValidator() {
    }

    static String normalizeName(String name) {
        if (name == null || name.trim().isBlank()) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_NAME);
        }
        String trimmed = name.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > 50) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_NAME);
        }
        return trimmed;
    }

    static LocalDate parseBirthDate(String value, String timezone, Clock clock) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new FamilyException(FamilyErrorCode.INVALID_BIRTH_DATE);
        }
        try {
            LocalDate parsed = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            requireNotFuture(parsed, timezone, clock);
            return parsed;
        } catch (DateTimeParseException exception) {
            throw new FamilyException(FamilyErrorCode.INVALID_BIRTH_DATE);
        }
    }

    static void requireNotFuture(LocalDate date, String timezone, Clock clock) {
        if (date.isAfter(LocalDate.now(clock.withZone(ZoneId.of(timezone))))) {
            throw new FamilyException(FamilyErrorCode.INVALID_BIRTH_DATE);
        }
    }

    static BabyGender parseGender(String value) {
        if (value == null) {
            throw new FamilyException(FamilyErrorCode.INVALID_GENDER);
        }
        try {
            return BabyGender.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new FamilyException(FamilyErrorCode.INVALID_GENDER);
        }
    }
}
