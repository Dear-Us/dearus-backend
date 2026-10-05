package com.dearusback.care.application.service;

import com.dearusback.care.application.model.RecordChangedEvent;
import com.dearusback.care.application.port.in.RecordEventSubscriber;
import com.dearusback.care.application.port.in.RecordEventSubscription;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RecordSubscriberRegistry {

    private final ConcurrentHashMap<Long, Set<RecordEventSubscriber>> subscribers = new ConcurrentHashMap<>();

    public RecordEventSubscription register(Long babyId, RecordEventSubscriber subscriber) {
        subscribers.compute(babyId, (id, existing) -> {
            Set<RecordEventSubscriber> group = existing == null ? ConcurrentHashMap.newKeySet() : existing;
            group.add(subscriber);
            return group;
        });
        return () -> remove(babyId, subscriber);
    }

    public void broadcast(RecordChangedEvent event) {
        Set<RecordEventSubscriber> group = subscribers.get(event.babyId());
        if (group == null) {
            return;
        }
        for (RecordEventSubscriber subscriber : group) {
            deliver(event.babyId(), subscriber, () -> subscriber.send(event));
        }
    }

    public void heartbeat() {
        subscribers.forEach((babyId, group) -> {
            for (RecordEventSubscriber subscriber : group) {
                deliver(babyId, subscriber, subscriber::heartbeat);
            }
        });
    }

    public int subscriberCount(Long babyId) {
        Set<RecordEventSubscriber> group = subscribers.get(babyId);
        return group == null ? 0 : group.size();
    }

    private void deliver(Long babyId, RecordEventSubscriber subscriber, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            remove(babyId, subscriber);
            try {
                subscriber.close();
            } catch (RuntimeException ignored) {
                // A failed connection must not affect the committed Care transaction.
            }
        }
    }

    private void remove(Long babyId, RecordEventSubscriber subscriber) {
        subscribers.computeIfPresent(babyId, (id, group) -> {
            group.remove(subscriber);
            return group.isEmpty() ? null : group;
        });
    }
}
