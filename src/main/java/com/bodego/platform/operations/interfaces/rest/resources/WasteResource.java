package com.bodego.platform.operations.interfaces.rest.resources;

import com.bodego.platform.operations.domain.model.valueobjects.WasteReason;
import java.time.Instant;

public record WasteResource(
        Long id,
        Long businessId,
        Long lotId,
        Integer quantity,
        WasteReason reason,
        String observation,
        Instant registeredAt,
        Instant createdAt
) {
}
