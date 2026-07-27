package com.jassi.userservice.deserializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jassi.userservice.entities.UserInfoDto;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Turns the {@code byte[]} Kafka hands back into a {@link UserInfoDto}, on the
 * consumer thread, before the record reaches {@code AuthServiceConsumer}.
 *
 * The mirror of the auth service's UserInfoSerializer. Wired in via
 * {@code spring.kafka.consumer.value-deserializer}.
 *
 * Kafka builds this reflectively through a no-arg constructor, so it is NOT a
 * Spring bean — the ObjectMapper bean in UserServiceConfig cannot be injected
 * here, which is why this class builds its own.
 */
public class UserInfoDeserializer implements Deserializer<UserInfoDto> {

    // Thread-safe once configured and expensive to build: one instance per JVM.
    private static final ObjectMapper objectMapper = new ObjectMapper()
            // Belt and braces with @JsonIgnoreProperties on the DTO: the producer
            // deploys independently, so unknown fields must never break us.
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public UserInfoDto deserialize(String topic, byte[] data) {
        if (data == null) {
            // Tombstone record (null value) — legal in Kafka. Don't parse zero bytes.
            return null;
        }
        try {
            return objectMapper.readValue(data, UserInfoDto.class);
        } catch (Exception e) {
            // Throw rather than return null. Returning null (as the reference does)
            // hands the listener a silent null and commits the offset, so a corrupt
            // record is indistinguishable from a tombstone and is lost without trace.
            throw new SerializationException("Error deserializing UserInfoDto from topic " + topic, e);
        }
    }
}
