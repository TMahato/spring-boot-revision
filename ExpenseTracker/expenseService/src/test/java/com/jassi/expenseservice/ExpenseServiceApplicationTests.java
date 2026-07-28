package com.jassi.expenseservice;

import com.jassi.expenseservice.dto.ExpenseDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Amount parsing is the seam between an LLM's free text and a DECIMAL column,
 * so it is the part most worth pinning down.
 */
class ExpenseServiceApplicationTests {

    private ExpenseDto withAmount(String raw) {
        return ExpenseDto.builder().amount(raw).build();
    }

    @Test
    void parsesPlainAndDecoratedAmounts() {
        assertEquals(new BigDecimal("450"), withAmount("450").parseAmount());
        assertEquals(new BigDecimal("450.00"), withAmount("450.00").parseAmount());
        assertEquals(new BigDecimal("1200.50"), withAmount("INR 1,200.50").parseAmount());
        assertEquals(new BigDecimal("450"), withAmount("Rs 450").parseAmount());
        assertEquals(new BigDecimal("20"), withAmount("$20").parseAmount());
    }

    @Test
    void returnsNullRatherThanThrowingOnUnusableAmounts() {
        // These are what actually stall a partition if they throw during
        // deserialization instead of being rejected as business data.
        assertNull(withAmount(null).parseAmount());
        assertNull(withAmount("").parseAmount());
        assertNull(withAmount("   ").parseAmount());
        assertNull(withAmount("unknown").parseAmount());
        assertNull(withAmount("Rs").parseAmount());
    }
}
