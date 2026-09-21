package com.receipts.api.service;

import com.receipts.api.domain.ItemizeStatus;
import com.receipts.api.domain.MonetaryAmounts;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Isolated, side-effect-free reconciliation rule shared by process(),
 * itemize(), and the PATCH items validation path.
 *
 * Rule (approved): sum(lineItemAmounts) + sum(taxAmounts) compareTo
 * grandTotal == 0. No epsilon tolerance is applied - amounts are normalized
 * to scale 2 and compared exactly via BigDecimal.compareTo(), never
 * equals(), since equals() also compares scale.
 */
@Service
public class ReconciliationService {

    public BigDecimal sum(List<BigDecimal> amounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal amount : amounts) {
            total = total.add(MonetaryAmounts.normalize(amount));
        }
        return MonetaryAmounts.normalize(total);
    }

    public boolean reconciles(List<BigDecimal> lineItemAmounts, List<BigDecimal> taxAmounts, BigDecimal grandTotal) {
        BigDecimal computed = sum(lineItemAmounts).add(sum(taxAmounts));
        return MonetaryAmounts.equalsExact(MonetaryAmounts.normalize(computed), MonetaryAmounts.normalize(grandTotal));
    }

    /**
     * Determines itemize_status from whether reliable items exist and whether
     * they reconcile. Reconciliation success alone is not enough for
     * COMPLETE: an empty item list (fallback-only) must still be
     * NEEDS_REVIEW even if a single fallback item trivially reconciles.
     */
    public ItemizeStatus determineStatus(boolean hasReliableLineItems, boolean reconciles) {
        if (!hasReliableLineItems) {
            return ItemizeStatus.NEEDS_REVIEW;
        }
        return reconciles ? ItemizeStatus.COMPLETE : ItemizeStatus.NEEDS_REVIEW;
    }
}
