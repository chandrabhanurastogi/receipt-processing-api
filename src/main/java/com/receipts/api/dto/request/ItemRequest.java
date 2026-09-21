package com.receipts.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One line item in the desired-state PATCH payload for
 * PATCH /transactions/{id}/items. The client always sends the complete
 * list of items it wants to exist after the call; edit/merge/split all
 * fall out of the same shape.
 */
public record ItemRequest(
        @NotBlank String description,
        @NotNull BigDecimal amount,
        BigDecimal taxAmount,
        Integer quantity
) {
}
