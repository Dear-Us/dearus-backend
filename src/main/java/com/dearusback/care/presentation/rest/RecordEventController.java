package com.dearusback.care.presentation.rest;

import com.dearusback.care.application.port.in.RecordEventSubscription;
import com.dearusback.care.application.port.in.SubscribeRecordEventsUseCase;
import com.dearusback.care.presentation.sse.SseRecordEventSubscriber;
import com.dearusback.support.application.exception.CommonErrorCode;
import com.dearusback.support.domain.exception.BusinessException;
import java.time.Duration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class RecordEventController {

    private static final long CONNECTION_TIMEOUT_MILLIS = Duration.ofHours(1).toMillis();
    private final SubscribeRecordEventsUseCase subscriptions;

    public RecordEventController(SubscribeRecordEventsUseCase subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping("/api/v1/babies/{babyId}/events")
    public SseEmitter subscribe(@AuthenticationPrincipal Long memberId, @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MILLIS);
        SseRecordEventSubscriber subscriber = new SseRecordEventSubscriber(emitter);
        RecordEventSubscription subscription = subscriptions.subscribe(memberId, babyId, subscriber);
        emitter.onCompletion(subscription::close);
        emitter.onTimeout(() -> {
            subscription.close();
            emitter.complete();
        });
        emitter.onError(error -> {
            subscription.close();
            emitter.completeWithError(error);
        });
        try {
            subscriber.heartbeat();
        } catch (RuntimeException exception) {
            subscription.close();
            subscriber.close();
            throw exception;
        }
        return emitter;
    }
}
