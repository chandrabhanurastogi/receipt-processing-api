package com.receipts.api.service;

import com.receipts.api.domain.ItemizeStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationServiceTest {

    private final ReconciliationService reconciliationService = new ReconciliationService();

    @Test
    void cleanFixtureReconcilesExactly() {
        // receipt-clean: items 3.50 + 8.90 + 2.60, tax 2.85, total 17.85
        List<BigDecimal> items = List.of(new BigDecimal("3.50"), new BigDecimal("8.90"), new BigDecimal("2.60"));
        List<BigDecimal> taxes = List.of(new BigDecimal("2.85"));

        boolean reconciles = reconciliationService.reconciles(items, taxes, new BigDecimal("17.85"));

        assertThat(reconciles).isTrue();
        assertThat(reconciliationService.determineStatus(true, reconciles)).isEqualTo(ItemizeStatus.COMPLETE);
    }

    @Test
    void mismatchFixtureDoesNotReconcileAndIsNotToleratedByEpsilon() {
        // receipt-mismatch: items 4.00 + 6.00 + tax 1.90 = 11.90, but total is 18.50
        List<BigDecimal> items = List.of(new BigDecimal("4.00"), new BigDecimal("6.00"));
        List<BigDecimal> taxes = List.of(new BigDecimal("1.90"));

        boolean reconciles = reconciliationService.reconciles(items, taxes, new BigDecimal("18.50"));

        assertThat(reconciles).isFalse();
        assertThat(reconciliationService.determineStatus(true, reconciles)).isEqualTo(ItemizeStatus.NEEDS_REVIEW);
    }

    @Test
    void taxOnlyFixtureWithNoReliableItemsIsNeedsReviewEvenIfFallbackTotalMatches() {
        // receipt-tax-only: no reliable items; a fallback item equal to the
        // total would trivially "reconcile" but must still be NEEDS_REVIEW.
        boolean reconciles = reconciliationService.reconciles(
                List.of(new BigDecimal("24.00")), List.of(), new BigDecimal("24.00"));

        assertThat(reconciliationService.determineStatus(false, reconciles)).isEqualTo(ItemizeStatus.NEEDS_REVIEW);
    }

    @Test
    void moneyComparisonIgnoresScaleDifferences() {
        // BigDecimal("2.0").equals(BigDecimal("2.00")) is false, but they must
        // still be treated as reconciling amounts via compareTo().
        List<BigDecimal> items = List.of(new BigDecimal("2.0"));
        List<BigDecimal> taxes = List.of();

        boolean reconciles = reconciliationService.reconciles(items, taxes, new BigDecimal("2.00"));

        assertThat(reconciles).isTrue();
    }

    @Test
    void noEpsilonToleranceForNearMisses() {
        List<BigDecimal> items = List.of(new BigDecimal("10.00"));
        List<BigDecimal> taxes = List.of();

        // Off by a single cent must still fail - no epsilon tolerance.
        boolean reconciles = reconciliationService.reconciles(items, taxes, new BigDecimal("10.01"));

        assertThat(reconciles).isFalse();
    }
}
