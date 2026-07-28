package com.jassi.expenseservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The event contract as this service sees it — what arrives on the expense topic
 * from dsService, and what the REST endpoints exchange.
 *
 * <p>SnakeCaseStrategy matches the producer's naming (user_id, created_at, …),
 * and ignoreUnknown=true is what lets dsService add fields without breaking this
 * consumer (notes/chapter-6 §4.2).
 *
 * <p><b>amount is a String, not a BigDecimal.</b> That is deliberate and it is
 * the single most important thing about this class. dsService gets the amount out
 * of an LLM, so what actually lands on the topic is free text — "450", "450.00",
 * "1,200", "Rs 450", or null when the model could not determine it. Declaring the
 * field as BigDecimal (as the reference did) means Jackson throws while
 * DESERIALIZING anything that isn't a clean number, which fails on the consumer
 * thread before the listener is even entered — a poison pill that stalls the
 * partition (notes/chapter-6 §4.3).
 *
 * <p>Holding the raw text and parsing it in the service layer turns that into an
 * ordinary business rejection: the record is consumed, judged unusable, and the
 * partition keeps moving. {@link #parseAmount()} does the parsing.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpenseDto {

    /**
     * Producer-assigned unique id for this event. This is what makes the consumer
     * idempotent — Kafka is at-least-once, so the same event can legitimately
     * arrive twice (notes/chapter-6 §4.4). Null for expenses created through the
     * REST endpoint, which is not a redelivery risk.
     */
    @JsonProperty("event_id")
    private String eventId;

    /** Public-facing id of the stored expense. Assigned on persist, not by the producer. */
    @JsonProperty("external_id")
    private String externalId;

    @JsonProperty("user_id")
    private String userId;

    /** Raw text from the producer — see the class javadoc. Nullable. */
    @JsonProperty("amount")
    private String amount;

    @JsonProperty("merchant")
    private String merchant;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("created_at")
    private Instant createdAt;

    /**
     * Best-effort conversion of the raw amount text to a number.
     *
     * @return the parsed amount, or {@code null} if it is absent or unparseable —
     *         never throws, because a bad amount is a business decision for the
     *         caller, not a deserialization failure.
     */
    public BigDecimal parseAmount() {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        // Strip everything that isn't a digit, a dot or a leading minus: handles
        // "Rs 450", "INR 1,200.50", "$20".
        String cleaned = amount.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isBlank() || cleaned.equals("-") || cleaned.equals(".")) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
