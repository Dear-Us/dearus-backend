package com.dearusback.care.application.service;

import com.dearusback.care.application.port.in.RecordEventSubscriber;
import com.dearusback.care.application.port.in.RecordEventSubscription;
import com.dearusback.care.application.port.in.SubscribeRecordEventsUseCase;
import com.dearusback.family.application.port.in.ActiveBabyMembershipAccess;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscribeRecordEventsService implements SubscribeRecordEventsUseCase {

    private final BabyRegistrationMemberAccess members;
    private final ActiveBabyMembershipAccess babies;
    private final RecordSubscriberRegistry registry;

    public SubscribeRecordEventsService(BabyRegistrationMemberAccess members, ActiveBabyMembershipAccess babies,
                                        RecordSubscriberRegistry registry) {
        this.members = members;
        this.babies = babies;
        this.registry = registry;
    }

    @Override
    @Transactional(readOnly = true)
    public RecordEventSubscription subscribe(Long memberId, Long babyId, RecordEventSubscriber subscriber) {
        members.requireActiveMember(memberId);
        babies.requireActiveMembership(memberId, babyId);
        return registry.register(babyId, subscriber);
    }
}
