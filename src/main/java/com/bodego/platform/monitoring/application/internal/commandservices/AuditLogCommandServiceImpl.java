package com.bodego.platform.monitoring.application.internal.commandservices;

import com.bodego.platform.monitoring.domain.model.aggregates.AuditLog;
import com.bodego.platform.monitoring.domain.model.commands.RecordAuditLogCommand;
import com.bodego.platform.monitoring.domain.services.AuditLogCommandService;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogCommandServiceImpl implements AuditLogCommandService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogCommandServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long handle(RecordAuditLogCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Record audit log command is required");
        }

        var auditLog = new AuditLog(
                command.businessId(), command.userId(), command.action(),
                command.entityType(), command.entityId(), command.description());
        return auditLogRepository.save(auditLog).getId();
    }
}
