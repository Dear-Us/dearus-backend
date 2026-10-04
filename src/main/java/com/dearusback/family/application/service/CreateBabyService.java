package com.dearusback.family.application.service;

import com.dearusback.family.application.model.CreateBabyCommand;
import com.dearusback.family.application.model.CreateBabyResult;
import com.dearusback.family.application.port.in.CreateBabyUseCase;
import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.family.domain.model.BabyGender;
import com.dearusback.identity.application.model.BabyRegistrationMember;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CreateBabyService implements CreateBabyUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateBabyService.class);
    private final BabyRegistrationMemberAccess members;
    private final BabyProfileImageStoragePort storage;
    private final CreateBabyTransactionService transaction;
    private final Clock clock;

    public CreateBabyService(BabyRegistrationMemberAccess members, BabyProfileImageStoragePort storage,
                             CreateBabyTransactionService transaction, Clock clock) {
        this.members = members;
        this.storage = storage;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public CreateBabyResult create(CreateBabyCommand command) {
        BabyRegistrationMember member = members.requireActiveMember(command.memberId());
        String name = normalizeName(command.babyName());
        LocalDate birthDate = parseBirthDate(command.birthDate(), member.timezone());
        BabyGender gender = parseGender(command.gender());
        String uploadedUrl = null;
        if (command.profileImage() != null) {
            try {
                uploadedUrl = storage.store(command.profileImage());
            } catch (RuntimeException exception) {
                throw new FamilyException(FamilyErrorCode.BABY_IMAGE_STORAGE_UNAVAILABLE);
            }
        }
        try {
            return transaction.create(command.memberId(), name, birthDate, gender, uploadedUrl);
        } catch (RuntimeException original) {
            if (uploadedUrl != null) {
                try {
                    storage.deleteOwnedImage(uploadedUrl);
                } catch (RuntimeException cleanupFailure) {
                    log.warn("Baby image compensation failed", cleanupFailure);
                }
            }
            throw original;
        }
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isBlank()) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_NAME);
        }
        String trimmed = name.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > 50) {
            throw new FamilyException(FamilyErrorCode.INVALID_BABY_NAME);
        }
        return trimmed;
    }

    private LocalDate parseBirthDate(String value, String timezone) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new FamilyException(FamilyErrorCode.INVALID_BIRTH_DATE);
        }
        try {
            LocalDate parsed = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            if (parsed.isAfter(LocalDate.now(clock.withZone(ZoneId.of(timezone))))) {
                throw new FamilyException(FamilyErrorCode.INVALID_BIRTH_DATE);
            }
            return parsed;
        } catch (DateTimeParseException exception) {
            throw new FamilyException(FamilyErrorCode.INVALID_BIRTH_DATE);
        }
    }

    private static BabyGender parseGender(String value) {
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
