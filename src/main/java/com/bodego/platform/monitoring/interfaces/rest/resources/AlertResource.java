package com.bodego.platform.monitoring.interfaces.rest.resources;

import com.bodego.platform.monitoring.domain.model.valueobjects.AlertSeverity;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertType;

import java.time.Instant;

public record AlertResource(
        Long id,
        Long businessId,
        Long productId,
        Long lotId,
        AlertType type,
        AlertSeverity severity,
        String message,
        Boolean resolved,
        Instant generatedAt,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
