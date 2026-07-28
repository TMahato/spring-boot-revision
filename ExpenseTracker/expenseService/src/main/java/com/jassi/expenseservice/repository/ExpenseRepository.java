package com.jassi.expenseservice.repository;

import com.jassi.expenseservice.entities.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // Newest first — callers want the recent expenses, and an unordered
    // findByUserId leaves the order up to whatever MySQL feels like.
    List<Expense> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Expense> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            String userId, Instant startTime, Instant endTime);

    Optional<Expense> findByUserIdAndExternalId(String userId, String externalId);

    /** The idempotency check — see ExpenseService.createFromEvent. */
    boolean existsByEventId(String eventId);
}
