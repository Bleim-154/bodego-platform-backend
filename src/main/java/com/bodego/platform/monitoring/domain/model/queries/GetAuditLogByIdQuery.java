package com.bodego.platform.monitoring.domain.model.queries;

public record GetAuditLogByIdQuery(Long auditLogId) {

    public GetAuditLogByIdQuery {
        if (auditLogId == null || auditLogId <= 0) {
            throw new IllegalArgumentException("Audit log ID must be positive");
        }
    }
}
