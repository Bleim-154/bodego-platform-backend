package com.bodego.platform.monitoring.domain.model.queries;

public record GetAlertsQuery(
        Long businessId,
        Long productId,
        Long lotId,
        Boolean resolved
) {

    public GetAlertsQuery {
        if (businessId != null && businessId <= 0) {
            throw new IllegalArgumentException("Business ID must be positive");
        }
        if (productId != null && productId <= 0) {
            throw new IllegalArgumentException("Product ID must be positive");
        }
        if (lotId != null && lotId <= 0) {
            throw new IllegalArgumentException("Lot ID must be positive");
        }
    }
}
