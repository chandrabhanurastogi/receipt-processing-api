package com.receipts.api.dto.response;

import java.math.BigDecimal;

public record LineItemResponse(
        Long id,
        String description,
        BigDecimal amount,
        BigDecimal taxAmount,
        Integer quantity
) {
}
