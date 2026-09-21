package com.receipts.api.dto.response;

import java.math.BigDecimal;

public record TaxResponse(
        Long id,
        String name,
        BigDecimal rate,
        BigDecimal amount,
        String jurisdiction
) {
}
