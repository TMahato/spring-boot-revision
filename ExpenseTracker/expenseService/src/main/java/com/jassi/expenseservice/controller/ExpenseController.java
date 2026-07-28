package com.jassi.expenseservice.controller;

import com.jassi.expenseservice.dto.ExpenseDto;
import com.jassi.expenseservice.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * The user id comes from the {@code X-User-Id} header, matching dsService's
 * {@code x-user-id} (HTTP header names are case-insensitive). Once Kong's JWT
 * plugin is enabled (notes/chapter-7 §10.5) this should come from the validated
 * token instead — today any caller can claim any user id.
 */
@RestController
@RequestMapping("/expense/v1")
@RequiredArgsConstructor
public class ExpenseController {

    private static final Logger log = LoggerFactory.getLogger(ExpenseController.class);

    private final ExpenseService expenseService;

    @GetMapping("/getExpense")
    public ResponseEntity<List<ExpenseDto>> getExpense(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(expenseService.getExpenses(userId));
    }

    @PostMapping("/addExpense")
    public ResponseEntity<?> addExpense(@RequestHeader("X-User-Id") String userId,
                                        @RequestBody ExpenseDto expenseDto) {
        expenseDto.setUserId(userId);
        try {
            return ResponseEntity.ok(expenseService.createExpense(expenseDto));
        } catch (IllegalArgumentException ex) {
            // A bad amount is the caller's fault: 400, with the reason. The
            // reference returned a bare `false` with no explanation.
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PutMapping("/updateExpense")
    public ResponseEntity<ExpenseDto> updateExpense(@RequestHeader("X-User-Id") String userId,
                                                    @RequestBody ExpenseDto expenseDto) {
        Optional<ExpenseDto> updated = expenseService.updateExpense(userId, expenseDto);
        // Scoped by userId in the query, so one user cannot update another's
        // expense — the lookup simply misses and this returns 404.
        return updated.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
