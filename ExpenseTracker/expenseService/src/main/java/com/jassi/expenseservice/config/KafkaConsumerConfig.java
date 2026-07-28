package com.jassi.expenseservice.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.apache.kafka.common.TopicPartition;
import org.springframework.util.backoff.FixedBackOff;

/**
 * What happens when consuming a record fails.
 *
 * <p>Without this, a listener exception makes the container re-poll and re-fail
 * the same record forever — the classic poison pill that stalls a partition
 * (notes/chapter-6 §4.3). Boot picks this bean up automatically and installs it
 * on the listener container factory.
 */
@Configuration
public class KafkaConsumerConfig {

    /** Suffix of the dead-letter topic. Kept explicit so it is greppable. */
    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    public DefaultErrorHandler errorHandler(KafkaOperations<String, Object> kafkaOperations) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                // Park the record on <original-topic>.DLT, keeping the same
                // partition number so ordering within a key is preserved there too.
                (ConsumerRecord<?, ?> record, Exception ex) ->
                        new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));

        // Retry twice, one second apart, then hand to the recoverer. Three total
        // attempts is enough to ride out a brief database blip without holding
        // the partition for long if the record is simply bad.
        //
        // Records that fail DESERIALIZATION are not retried at all — they can
        // never succeed — and go straight to the DLT.
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }
}
