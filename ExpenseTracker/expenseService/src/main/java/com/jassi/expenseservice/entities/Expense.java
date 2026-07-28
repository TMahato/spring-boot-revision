package com.jassi.expenseservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A persisted expense.
 *
 * <p>Deliberately carries no Jackson annotations: this is the storage model, not
 * the wire contract. {@link com.jassi.expenseservice.dto.ExpenseDto} is the wire
 * contract, and the two are mapped explicitly in the service. Keeping them apart
 * is the fix for the coupling that notes/chapter-6 §7.5 flags in the auth service.
 */
@Entity
@Table(
        name = "expenses",
        indexes = {
                @Index(name = "idx_expenses_user_id", columnList = "user_id"),
                @Index(name = "idx_expenses_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The producer's event id, carried through from Kafka.
     *
     * <p>UNIQUE, and that constraint is the real idempotency guarantee. The
     * existsByEventId check in the service is an optimisation that avoids the
     * common case; this constraint is what holds when two consumer threads race
     * on the same redelivered event. Null for REST-created expenses.
     */
    @Column(name = "event_id", unique = true)
    private String eventId;

    /** Public-facing id, handed out by the API instead of the numeric primary key. */
    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /**
     * precision/scale matter: the MySQL default for BigDecimal is DECIMAL(19,2),
     * which silently rounds. 19,4 keeps sub-paisa amounts intact.
     */
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "merchant")
    private String merchant;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * @PrePersist only — the reference also had @PreUpdate here, which meant an
     * update could retroactively stamp createdAt on a row that somehow lacked one.
     * Creation timestamps are set once.
     */
    @PrePersist
    void onCreate() {
        if (this.externalId == null) {
            this.externalId = UUID.randomUUID().toString();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
