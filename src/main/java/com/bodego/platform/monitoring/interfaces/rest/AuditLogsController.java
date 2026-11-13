package com.bodego.platform.monitoring.interfaces.rest;

import com.bodego.platform.monitoring.domain.model.queries.GetAuditLogByIdQuery;
import com.bodego.platform.monitoring.domain.model.queries.GetAuditLogsQuery;
import com.bodego.platform.monitoring.domain.services.AuditLogQueryService;
import com.bodego.platform.monitoring.interfaces.rest.resources.AuditLogResource;
import com.bodego.platform.monitoring.interfaces.rest.transform.AuditLogResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit logs", description = "Read-only business action history")
public class AuditLogsController {

    private final AuditLogQueryService auditLogQueryService;

    public AuditLogsController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping
    @Operation(summary = "Get audit logs", description = "Optional filters combine with AND. "
            + "Entity type uses exact case-sensitive matching after trimming. "
            + "Business ID must match the authenticated business.")
    public ResponseEntity<List<AuditLogResource>> getAllAuditLogs(
            @Parameter(description = "Business ID, greater than zero")
            @RequestParam(required = false) Long businessId,
            @Parameter(description = "Entity type, between 1 and 100 characters")
            @RequestParam(required = false) String entityType,
            @Parameter(description = "Entity ID, greater than zero; can be combined with entityType")
            @RequestParam(required = false) Long entityId
    ) {
        var logs = auditLogQueryService.handle(
                new GetAuditLogsQuery(businessId, entityType, entityId));
        return ResponseEntity.ok(logs.stream()
                .map(AuditLogResourceFromEntityAssembler::toResourceFromEntity)
                .toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an audit log by ID")
    public ResponseEntity<AuditLogResource> getAuditLogById(@PathVariable Long id) {
        return auditLogQueryService.handle(new GetAuditLogByIdQuery(id))
                .map(AuditLogResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
