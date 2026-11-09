package com.bodego.platform.catalog.interfaces.rest.resources;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateProductResource(
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        @NotNull @DecimalMin("0.00")
        @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @Min(0) Integer minimumStock
) {}
