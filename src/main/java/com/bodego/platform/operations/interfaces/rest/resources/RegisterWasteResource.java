package com.bodego.platform.operations.interfaces.rest.resources;

import com.bodego.platform.operations.domain.model.valueobjects.WasteReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegisterWasteResource(
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long lotId,
        @NotNull @Positive Integer quantity,
        @NotNull WasteReason reason,
        @Size(max = 500) String observation
) {
}
