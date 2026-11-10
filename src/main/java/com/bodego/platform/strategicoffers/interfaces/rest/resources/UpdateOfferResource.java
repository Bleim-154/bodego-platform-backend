package com.bodego.platform.strategicoffers.interfaces.rest.resources;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateOfferResource(
        @NotBlank @Size(max = 255) String title,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax(value = "100", inclusive = false) BigDecimal discountPercentage,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
