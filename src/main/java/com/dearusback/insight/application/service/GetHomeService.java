package com.dearusback.insight.application.service;

import com.dearusback.care.application.port.in.CareRecordQueryUseCase;
import com.dearusback.family.application.port.in.GetBabyUseCase;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import com.dearusback.insight.application.model.HomeResult;
import com.dearusback.insight.application.port.in.GetHomeUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetHomeService implements GetHomeUseCase {

    private final BabyRegistrationMemberAccess members;
    private final GetBabyUseCase babies;
    private final CareRecordQueryUseCase records;
    private final Clock clock;

    public GetHomeService(BabyRegistrationMemberAccess members, GetBabyUseCase babies,
                          CareRecordQueryUseCase records, Clock clock) {
        this.members = members;
        this.babies = babies;
        this.records = records;
        this.clock = clock;
    }

    @Override
    public HomeResult get(Long memberId, Long babyId) {
        ZoneId zone = ZoneId.of(members.requireActiveMember(memberId).timezone());
        var baby = babies.getBaby(memberId, babyId);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        var care = records.homeData(babyId, today.atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant(), zone);
        return new HomeResult(baby, today, care);
    }
}
