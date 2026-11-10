package com.bodego.platform.monitoring.interfaces.acl;

import com.bodego.platform.monitoring.domain.model.commands.RecordAuditLogCommand;
import com.bodego.platform.monitoring.domain.services.AuditLogCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringContextFacade {

    private final AuditLogCommandService auditLogCommandService;

    public MonitoringContextFacade(AuditLogCommandService auditLogCommandService) {
        this.auditLogCommandService = auditLogCommandService;
    }

    /**
     * Records a successful business action in the caller's transaction.
     * The caller supplies validated business/entity references and a trusted user ID,
     * or null while IAM is unavailable. This method does not authorize the caller.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Long recordAction(
            Long businessId,
            Long userId,
            String action,
            String entityType,
            Long entityId,
            String description
    ) {
        return auditLogCommandService.handle(new RecordAuditLogCommand(
                businessId, userId, action, entityType, entityId, description));
    }
}
