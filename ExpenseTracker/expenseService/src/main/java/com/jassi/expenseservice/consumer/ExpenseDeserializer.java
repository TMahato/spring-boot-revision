package com.jassi.expenseservice.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jassi.expenseservice.dto.ExpenseDto;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Turns the {@code byte[]} Kafka hands back into an {@link ExpenseDto}, on the
 * consumer thread, before the record reaches {@code ExpenseConsumer}.
 *
 * <p>The mirror of dsService's {@code json.dumps(...).encode('utf-8')}. Wired in
 * as the delegate of an ErrorHandlingDeserializer — see application.properties.
 *
 * <p>Kafka builds this reflectively through a no-arg constructor, so it is NOT a
 * Spring bean and the application's ObjectMapper cannot be injected here, which
 * is why this class builds its own (notes/chapter-6 §2).
 */
public class ExpenseDeserializer implements Deserializer<ExpenseDto> {

    // Thread-safe once configured and expensive to build: one instance per JVM.
    private static final ObjectMapper objectMapper = new ObjectMapper()
            // Instant support. Without this module, created_at fails to bind.
            .registerModule(new JavaTimeModule())
            // Read ISO-8601 strings rather than expecting numeric epochs, which
            // is what a Python producer emits.
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            // Belt and braces with @JsonIgnoreProperties on the DTO: dsService
            // deploys independently, so unknown fields must never break us.
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public ExpenseDto deserialize(String topic, byte[] data) {
        if (data == null) {
            // Tombstone record (null value) — legal in Kafka. Don't parse zero bytes.
            return null;
        }
        try {
            return objectMapper.readValue(data, ExpenseDto.class);
        } catch (Exception e) {
            // Throw rather than return null. Returning null (as the reference
            // did, after printing a stack trace) hands the listener a silent null
            // and commits the offset, so a corrupt record is indistinguishable
            // from a tombstone and is lost without trace.
            //
            // Throwing is safe here ONLY because ErrorHandlingDeserializer wraps
            // this class: it catches the exception and routes the record to the
            // dead-letter topic instead of stalling the partition
            // (notes/chapter-6 §9.2).
            throw new SerializationException(
                    "Error deserializing ExpenseDto from topic " + topic, e);
        }
    }
}
