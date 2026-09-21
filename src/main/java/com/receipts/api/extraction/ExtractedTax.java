package com.receipts.api.extraction;

import java.math.BigDecimal;

public record ExtractedTax(
        String name,
        BigDecimal rate,
        BigDecimal amount
) {
}
