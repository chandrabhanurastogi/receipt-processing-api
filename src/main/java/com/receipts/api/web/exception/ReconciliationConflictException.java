package com.receipts.api.web.exception;

import java.math.BigDecimal;

/**
 * Thrown when a PATCH /transactions/{id}/items request proposes line items
 * that do not reconcile with the transaction's grand total and stored taxes.
 * Mapped to HTTP 409. Must be thrown before any managed JPA entity is
 * mutated so that a rollback has nothing to undo.
 */
public class ReconciliationConflictException extends RuntimeException {

    private final BigDecimal proposedItemsTotal;
    private final BigDecimal taxTotal;
    private final BigDecimal expectedGrandTotal;

    public ReconciliationConflictException(String message, BigDecimal proposedItemsTotal,
                                            BigDecimal taxTotal, BigDecimal expectedGrandTotal) {
        super(message);
        this.proposedItemsTotal = proposedItemsTotal;
        this.taxTotal = taxTotal;
        this.expectedGrandTotal = expectedGrandTotal;
    }

    public BigDecimal getProposedItemsTotal() {
        return proposedItemsTotal;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public BigDecimal getExpectedGrandTotal() {
        return expectedGrandTotal;
    }
}
