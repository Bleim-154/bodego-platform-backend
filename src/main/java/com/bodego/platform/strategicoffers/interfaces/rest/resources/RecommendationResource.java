package com.bodego.platform.strategicoffers.interfaces.rest.resources;

import com.bodego.platform.strategicoffers.domain.model.valueobjects.RecommendationType;

import java.time.Instant;

public record RecommendationResource(
        Long id,
        Long businessId,
        Long productId,
        Long lotId,
        RecommendationType type,
        String message,
        Instant generatedAt,
        boolean dismissed,
        Instant createdAt,
        Instant updatedAt
) {
}
