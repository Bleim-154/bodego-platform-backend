package com.bodego.platform.operations.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreatePhysicalCountResource(
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long lotId,
        @NotNull @PositiveOrZero Integer countedQuantity,
        @Size(max = 500) String observation
) {
}
