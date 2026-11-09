package com.bodego.platform.catalog.interfaces.rest.resources;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreateLotResource(
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long productId,
        @NotBlank @Size(max = 100) String batchNumber,
        @NotNull @Min(0) Integer quantity,
        LocalDate expirationDate
) {
}
