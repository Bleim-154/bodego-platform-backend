package com.bodego.platform.monitoring.interfaces.rest.resources;

import java.time.Instant;

public record AuditLogResource(
        Long id,
        Long businessId,
        Long userId,
        String action,
        String entityType,
        Long entityId,
        String description,
        Instant occurredAt,
        Instant createdAt,
        Instant updatedAt
) {
}
