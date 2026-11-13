package com.bodego.platform.operations.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ApplyStockAdjustmentResource(
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long physicalCountId,
        @NotBlank @Size(max = 500) String reason
) {
}
