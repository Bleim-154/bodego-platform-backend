package com.bodego.platform.monitoring.domain.model.commands;

public record RecordAuditLogCommand(
        Long businessId,
        Long userId,
        String action,
        String entityType,
        Long entityId,
        String description
) {
}
