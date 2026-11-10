package com.bodego.platform.monitoring.application.internal.queryservices;

import com.bodego.platform.monitoring.domain.model.aggregates.AuditLog;
import com.bodego.platform.monitoring.domain.model.queries.GetAuditLogByIdQuery;
import com.bodego.platform.monitoring.domain.model.queries.GetAuditLogsQuery;
import com.bodego.platform.monitoring.domain.services.AuditLogQueryService;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AuditLogQueryServiceImpl implements AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogQueryServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public Optional<AuditLog> handle(GetAuditLogByIdQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("Audit log query is required");
        }
        return auditLogRepository.findById(query.auditLogId());
    }

    @Override
    public List<AuditLog> handle(GetAuditLogsQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("Audit logs query is required");
        }
        return auditLogRepository.findByFilters(
                query.businessId(), query.entityType(), query.entityId());
    }
}
