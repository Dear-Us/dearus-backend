package com.dearusback.family.application.service;

import com.dearusback.family.application.model.CreateBabyResult;
import com.dearusback.family.domain.model.Baby;
import com.dearusback.family.domain.model.BabyGender;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.family.domain.repository.BabyRepository;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateBabyTransactionService {

    private final BabyRegistrationMemberAccess members;
    private final BabyRepository babies;
    private final Clock clock;

    public CreateBabyTransactionService(BabyRegistrationMemberAccess members, BabyRepository babies, Clock clock) {
        this.members = members;
        this.babies = babies;
        this.clock = clock;
    }

    @Transactional
    public CreateBabyResult create(Long memberId, String name, LocalDate birthDate,
                                   BabyGender gender, String imageUrl) {
        members.lockActiveMember(memberId);
        Baby saved = babies.save(Baby.register(memberId, name, birthDate, gender, imageUrl, clock.instant()));
        members.initializeLastSelectedBabyIdIfAbsent(memberId, saved.id());
        return new CreateBabyResult(saved.id(), saved.name(), saved.birthDate(), saved.gender(),
                saved.profileImageUrl(), BabyMemberRole.OWNER);
    }
}
