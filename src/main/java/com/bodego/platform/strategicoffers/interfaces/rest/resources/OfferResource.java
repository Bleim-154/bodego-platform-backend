package com.bodego.platform.strategicoffers.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record OfferResource(
        Long id,
        Long businessId,
        Long productId,
        String title,
        BigDecimal discountPercentage,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
