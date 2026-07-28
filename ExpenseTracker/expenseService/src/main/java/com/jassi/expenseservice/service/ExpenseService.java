package com.jassi.expenseservice.service;

import com.jassi.expenseservice.dto.ExpenseDto;
import com.jassi.expenseservice.entities.Expense;
import com.jassi.expenseservice.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

    /** Applied when the producer could not determine a currency. */
    private static final String DEFAULT_CURRENCY = "INR";

    private final ExpenseRepository expenseRepository;

    /**
     * Persist an expense that arrived on Kafka.
     *
     * <p>Idempotent by event id, because Kafka delivers at-least-once: the
     * producer's retries and any consumer rebalance between processing and offset
     * commit can redeliver the same event (notes/chapter-6 §4.4).
     *
     * @return true if a new row was written, false if this was a duplicate or the
     *         event was unusable.
     */
    @Transactional
    public boolean createFromEvent(ExpenseDto dto) {
        if (dto == null) {
            // A null payload here is a tombstone or a record the deserializer
            // rejected. Nothing to do, but do not treat it as an error.
            log.warn("Ignoring null expense event");
            return false;
        }
        if (dto.getUserId() == null || dto.getUserId().isBlank()) {
            // Without a user this row can never be read back — the only query
            // path is by user id. Drop it rather than storing an orphan.
            log.warn("Ignoring expense event with no user_id (event_id={})", dto.getEventId());
            return false;
        }

        // Fast path: skip the insert entirely on an obvious redelivery. The
        // unique constraint below is what actually guarantees correctness.
        if (dto.getEventId() != null && expenseRepository.existsByEventId(dto.getEventId())) {
            log.info("Duplicate expense event ignored (event_id={})", dto.getEventId());
            return false;
        }

        BigDecimal amount = dto.parseAmount();
        if (amount == null) {
            // The LLM returns null for anything it could not determine, and its
            // free-text amounts do not always parse. An expense with no amount is
            // not worth storing, but it is NOT a deserialization failure — the
            // partition keeps moving.
            log.warn("Ignoring expense event with unusable amount {} (event_id={})",
                    dto.getAmount(), dto.getEventId());
            return false;
        }

        Expense expense = Expense.builder()
                .eventId(dto.getEventId())
                .userId(dto.getUserId())
                .amount(amount)
                .merchant(dto.getMerchant())
                .currency(resolveCurrency(dto.getCurrency()))
                .createdAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : Instant.now())
                .build();

        try {
            expenseRepository.save(expense);
        } catch (DataIntegrityViolationException ex) {
            // Lost the race against a concurrent delivery of the same event. The
            // unique constraint on event_id did its job; the outcome we wanted
            // (exactly one row) already holds, so this is not an error.
            log.info("Concurrent duplicate expense event ignored (event_id={})", dto.getEventId());
            return false;
        }

        log.info("Stored expense for user_id={} (event_id={})", dto.getUserId(), dto.getEventId());
        return true;
    }

    /** Create an expense submitted through the REST API. */
    @Transactional
    public ExpenseDto createExpense(ExpenseDto dto) {
        BigDecimal amount = dto.parseAmount();
        if (amount == null) {
            throw new IllegalArgumentException("amount is required and must be numeric");
        }

        Expense expense = Expense.builder()
                .userId(dto.getUserId())
                .amount(amount)
                .merchant(dto.getMerchant())
                .currency(resolveCurrency(dto.getCurrency()))
                .createdAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : Instant.now())
                .build();

        return toDto(expenseRepository.save(expense));
    }

    @Transactional
    public Optional<ExpenseDto> updateExpense(String userId, ExpenseDto dto) {
        Optional<Expense> found =
                expenseRepository.findByUserIdAndExternalId(userId, dto.getExternalId());
        if (found.isEmpty()) {
            return Optional.empty();
        }

        Expense expense = found.get();

        BigDecimal amount = dto.parseAmount();
        if (amount != null) {
            expense.setAmount(amount);
        }
        if (dto.getMerchant() != null && !dto.getMerchant().isBlank()) {
            expense.setMerchant(dto.getMerchant());
        }
        if (dto.getCurrency() != null && !dto.getCurrency().isBlank()) {
            // The reference assigned getMerchant() here — a copy-paste bug that
            // wrote the merchant name into the currency column.
            expense.setCurrency(dto.getCurrency());
        }

        return Optional.of(toDto(expenseRepository.save(expense)));
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto> getExpenses(String userId) {
        return expenseRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto> getExpensesBetween(String userId, Instant from, Instant to) {
        return expenseRepository
                .findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, from, to)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private String resolveCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        return currency.trim().toUpperCase();
    }

    /**
     * Explicit mapping rather than objectMapper.convertValue(entity, dto).
     * convertValue round-trips through a Jackson tree and depends on both classes
     * agreeing on naming strategies — it breaks silently the moment they drift,
     * and it would happily try to carry the primary key across.
     */
    private ExpenseDto toDto(Expense expense) {
        return ExpenseDto.builder()
                .externalId(expense.getExternalId())
                .userId(expense.getUserId())
                .amount(expense.getAmount() != null ? expense.getAmount().toPlainString() : null)
                .merchant(expense.getMerchant())
                .currency(expense.getCurrency())
                .createdAt(expense.getCreatedAt())
                .build();
    }
}
