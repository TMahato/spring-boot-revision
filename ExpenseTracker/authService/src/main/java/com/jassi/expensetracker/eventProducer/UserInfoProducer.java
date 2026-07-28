package com.jassi.expensetracker.eventProducer;

import com.jassi.expensetracker.model.UserInfoDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class UserInfoProducer {

    private final KafkaTemplate<String, UserInfoDto> kafkaTemplate;

    @Value("${app.kafka.topic.name}")
    private String TOPIC_NAME;

    @Autowired
    UserInfoProducer(KafkaTemplate<String, UserInfoDto> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEventToKafka(UserInfoDto userInfoDto) {
        // KEY = userId. Kafka only guarantees ordering WITHIN a partition, and the
        // partition is chosen by key — so keying on the user id is what makes two
        // events for the same user arrive in the order they were sent. Unkeyed,
        // they can land in different partitions and be processed out of order.
        // See notes/chapter-6 §7.4.
        Message<UserInfoDto> message = MessageBuilder.withPayload(userInfoDto)
                .setHeader(KafkaHeaders.TOPIC, TOPIC_NAME)
                .setHeader(KafkaHeaders.KEY, userInfoDto.getUserId())
                .build();

        kafkaTemplate.send(message);
    }
}
