package com.dearusback.care.application.port.in;

public interface SubscribeRecordEventsUseCase {
    RecordEventSubscription subscribe(Long memberId, Long babyId, RecordEventSubscriber subscriber);
}
