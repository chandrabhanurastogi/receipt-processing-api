package com.receipts.api.dto.response;

import java.math.BigDecimal;

public record ReconciliationConflictResponse(
        String message,
        BigDecimal proposedItemsTotal,
        BigDecimal taxTotal,
        BigDecimal expectedGrandTotal
) {
}
