package com.bodego.platform.operations.interfaces.rest.resources;

import java.time.Instant;

public record PhysicalCountResource(
        Long id,
        Long businessId,
        Long lotId,
        Integer systemQuantity,
        Integer countedQuantity,
        Integer difference,
        String observation,
        Instant countedAt,
        Instant createdAt
) {
}
