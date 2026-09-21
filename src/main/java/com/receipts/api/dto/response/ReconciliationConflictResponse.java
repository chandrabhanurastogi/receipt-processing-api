package com.receipts.api.dto.response;

import java.math.BigDecimal;

public record ReconciliationConflictResponse(
        String error,
        String message,
        BigDecimal expectedTotal,
        BigDecimal itemsTotal,
        BigDecimal taxTotal,
        BigDecimal calculatedTotal,
        BigDecimal difference
) {
}
