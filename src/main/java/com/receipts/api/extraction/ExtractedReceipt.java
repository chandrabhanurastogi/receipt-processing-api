package com.receipts.api.extraction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExtractedReceipt(
        String merchant,
        LocalDate date,
        String currency,
        BigDecimal grandTotal,
        List<ExtractedTax> taxes,
        List<ExtractedLineItem> lineItems
) {
}
