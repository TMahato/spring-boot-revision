package com.jassi.userservice.consumer;

import com.jassi.userservice.entities.UserInfoDto;
import com.jassi.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes the user events published by the auth service (ExpenseTracker).
 *
 * The listener container polls Kafka on a background thread and calls this method
 * once per record. Return normally and the offset is committed; throw and the
 * error handler decides what happens next.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceConsumer.class);

    private final UserService userService;

    @KafkaListener(topics = "${app.kafka.topic.name}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(UserInfoDto eventData) {
        try {
            log.info("Consumed user event for userId={}", eventData != null ? eventData.getUserId() : null);
            userService.createOrUpdateUser(eventData);
        } catch (Exception ex) {
            // Swallowing here means the offset still commits and the event is lost.
            // That is deliberate for now (a bad event must not stall the partition),
            // but the real fix is a DefaultErrorHandler with retries + a dead-letter
            // topic, so failures are retried and then parked rather than dropped.
            log.error("Exception while consuming kafka event", ex);
        }
    }
}
