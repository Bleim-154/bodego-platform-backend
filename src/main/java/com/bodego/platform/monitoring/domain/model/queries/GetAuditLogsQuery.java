package com.bodego.platform.monitoring.domain.model.queries;

public record GetAuditLogsQuery(
        Long businessId,
        String entityType,
        Long entityId
) {

    public GetAuditLogsQuery {
        if (businessId != null && businessId <= 0) {
            throw new IllegalArgumentException("Business ID must be positive");
        }
        if (entityId != null && entityId <= 0) {
            throw new IllegalArgumentException("Entity ID must be positive");
        }
        if (entityType != null) {
            if (entityType.isBlank() || entityType.length() > 100) {
                throw new IllegalArgumentException(
                        "Entity type must contain between 1 and 100 characters");
            }
            entityType = entityType.trim();
        }
    }
}
