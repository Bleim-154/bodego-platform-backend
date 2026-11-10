package com.bodego.platform.operations.interfaces.rest.resources;

import com.bodego.platform.operations.domain.model.valueobjects.MovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateInventoryMovementResource(
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long lotId,
        @NotNull MovementType type,
        @NotNull @Positive Integer quantity,
        @NotBlank @Size(max = 255) String reason
) {
}
