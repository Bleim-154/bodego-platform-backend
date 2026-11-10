package com.bodego.platform.monitoring.domain.services;

import com.bodego.platform.monitoring.domain.model.aggregates.AuditLog;
import com.bodego.platform.monitoring.domain.model.queries.GetAuditLogByIdQuery;
import com.bodego.platform.monitoring.domain.model.queries.GetAuditLogsQuery;

import java.util.List;
import java.util.Optional;

public interface AuditLogQueryService {

    Optional<AuditLog> handle(GetAuditLogByIdQuery query);

    List<AuditLog> handle(GetAuditLogsQuery query);
}
