package com.bodego.platform.monitoring.domain.services;

import com.bodego.platform.monitoring.domain.model.commands.RecordAuditLogCommand;

public interface AuditLogCommandService {

    Long handle(RecordAuditLogCommand command);
}
