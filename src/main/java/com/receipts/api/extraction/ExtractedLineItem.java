package com.receipts.api.extraction;

import java.math.BigDecimal;

public record ExtractedLineItem(
        String description,
        BigDecimal amount
) {
}
