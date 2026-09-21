package com.receipts.api.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Centralizes the monetary scale/rounding convention for the whole
 * application: amounts are persisted and compared at scale 2 (minor
 * currency unit), HALF_UP rounding. Money values must always be compared
 * with {@link BigDecimal#compareTo(BigDecimal)}, never {@link BigDecimal#equals(Object)},
 * since equals() also compares scale (e.g. 2.0 vs 2.00).
 */
public final class MonetaryAmounts {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private MonetaryAmounts() {
    }

    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(SCALE, ROUNDING_MODE);
    }

    public static boolean equalsExact(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }
}
