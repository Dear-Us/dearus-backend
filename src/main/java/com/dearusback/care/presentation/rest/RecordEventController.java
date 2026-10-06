package com.dearusback.care.presentation.rest;

import com.dearusback.care.application.port.in.RecordEventSubscription;
import com.dearusback.care.application.port.in.SubscribeRecordEventsUseCase;
import com.dearusback.care.presentation.sse.SseRecordEventSubscriber;
import com.dearusback.support.application.exception.CommonErrorCode;
import com.dearusback.support.domain.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Tag(name = "Event", description = "공동 보호자 기록 변경 SSE 알림")
@SecurityRequirement(name = "bearerAuth")
public class RecordEventController {

    private static final long CONNECTION_TIMEOUT_MILLIS = Duration.ofHours(1).toMillis();
    private final SubscribeRecordEventsUseCase subscriptions;

    public RecordEventController(SubscribeRecordEventsUseCase subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping("/api/v1/babies/{babyId}/events")
    @Operation(operationId = "subscribeRecordEvents", summary = "기록 변경 SSE 구독", tags = "Event", description = "ACTIVE 공동 보호자만 Bearer 헤더로 연결한다. text/event-stream 장기 연결이며 record-change 이벤트에는 eventType, babyId, recordId, recordType, occurredAt이 담긴다. 약 30초 heartbeat는 주석이다. 알림은 best-effort이고 replay가 없으므로 재연결 후 HOME을 재조회한다. Swagger UI는 장기 SSE 스트림 테스트에 제한이 있을 수 있다.")
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
