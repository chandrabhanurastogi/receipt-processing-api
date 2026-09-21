package com.receipts.api.dto.response;

import com.receipts.api.domain.ItemizeStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TransactionResponse(
        Long id,
        Long receiptId,
        String merchant,
        LocalDate date,
        String currency,
        BigDecimal grandTotal,
        ItemizeStatus itemizeStatus,
        List<TaxResponse> taxes,
        List<LineItemResponse> lineItems
) {
}
