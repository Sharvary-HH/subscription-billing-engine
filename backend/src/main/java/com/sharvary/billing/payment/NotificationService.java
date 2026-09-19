package com.sharvary.billing.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    public void send(UUID customerId, String kind, String subject, String body) {
        log.info("notification [{}] to customer {}: {} - {}", kind, customerId, subject, body);
        notifications.save(new Notification(UUID.randomUUID(), customerId, kind, subject, body, clock.instant()));
    }
}
