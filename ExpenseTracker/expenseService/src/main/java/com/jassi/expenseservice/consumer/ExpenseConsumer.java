package com.jassi.expenseservice.consumer;

import com.jassi.expenseservice.dto.ExpenseDto;
import com.jassi.expenseservice.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * Consumes the expense events published by dsService.
 *
 * <p>The listener container polls Kafka on a background thread and calls this
 * method once per record. Return normally and the offset is committed; throw and
 * {@link com.jassi.expenseservice.config.KafkaConsumerConfig}'s error handler
 * decides what happens next.
 */
@Service
@RequiredArgsConstructor
public class ExpenseConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExpenseConsumer.class);

    private final ExpenseService expenseService;

    @KafkaListener(
            topics = "${app.kafka.topic.name}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listen(ExpenseDto eventData,
                       @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        log.info("Consumed expense event key={} event_id={}",
                key, eventData != null ? eventData.getEventId() : null);

        // Note there is NO try/catch here, unlike the reference.
        //
        // Swallowing the exception commits the offset and loses the event
        // (notes/chapter-6 §4.3). Letting it propagate hands the record to the
        // DefaultErrorHandler, which retries it and then parks it on the
        // dead-letter topic — so a genuine failure is retried and preserved
        // rather than silently dropped, and a permanently bad record still does
        // not stall the partition.
        //
        // Events that are merely unusable (no user_id, unparseable amount) are
        // NOT exceptions — createFromEvent returns false for those, so they are
        // logged and skipped without a retry cycle.
        expenseService.createFromEvent(eventData);
    }
}
