package com.bodego.platform.monitoring.application.internal.commandservices;

import com.bodego.platform.shared.application.security.*;

import com.bodego.platform.monitoring.domain.model.aggregates.AuditLog;
import com.bodego.platform.monitoring.domain.model.commands.RecordAuditLogCommand;
import com.bodego.platform.monitoring.domain.services.AuditLogCommandService;
import com.bodego.platform.monitoring.infrastructure.persistence.jpa.repositories.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogCommandServiceImpl implements AuditLogCommandService {
    private final BusinessAccess access;

    private final AuditLogRepository auditLogRepository;

    public AuditLogCommandServiceImpl(AuditLogRepository auditLogRepository, BusinessAccess access) {
        this.access = access;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long handle(RecordAuditLogCommand command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.write(BusinessPermission.AUDIT_RECORD, command.businessId());


        var auditLog = new AuditLog(
                actor.businessId(), actor.userId(), command.action(),
                command.entityType(), command.entityId(), command.description());
        return auditLogRepository.save(auditLog).getId();
    }
}
