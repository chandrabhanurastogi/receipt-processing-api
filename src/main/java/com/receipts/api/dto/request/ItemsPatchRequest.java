package com.receipts.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ItemsPatchRequest(
        @NotNull List<@Valid ItemRequest> items
) {
}
