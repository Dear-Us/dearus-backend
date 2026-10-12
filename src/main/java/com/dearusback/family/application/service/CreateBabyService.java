package com.dearusback.family.application.service;

import com.dearusback.family.application.model.CreateBabyCommand;
import com.dearusback.family.application.model.CreateBabyResult;
import com.dearusback.family.application.port.in.CreateBabyUseCase;
import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.exception.FamilyException;
import com.dearusback.identity.application.model.BabyRegistrationMember;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
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
        String name = BabyInputValidator.normalizeName(command.babyName());
        var birthDate = BabyInputValidator.parseBirthDate(command.birthDate(), member.timezone(), clock);
        var gender = BabyInputValidator.parseGender(command.gender());
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

}
