package com.bodego.platform.monitoring.interfaces.rest;

import com.bodego.platform.monitoring.domain.model.commands.ResolveAlertCommand;
import com.bodego.platform.monitoring.domain.model.queries.GetAlertByIdQuery;
import com.bodego.platform.monitoring.domain.model.queries.GetAlertsQuery;
import com.bodego.platform.monitoring.domain.services.AlertCommandService;
import com.bodego.platform.monitoring.domain.services.AlertQueryService;
import com.bodego.platform.monitoring.domain.services.AlertGenerationService;
import com.bodego.platform.monitoring.interfaces.rest.resources.GenerateAlertsResource;
import com.bodego.platform.monitoring.interfaces.rest.transform.GenerateAlertsCommandFromResourceAssembler;
import jakarta.validation.Valid;
import com.bodego.platform.monitoring.interfaces.rest.resources.AlertResource;
import com.bodego.platform.monitoring.interfaces.rest.transform.AlertResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts", description = "Inventory alerts: queries and resolution")
public class AlertsController {

    private final AlertCommandService alertCommandService;
    private final AlertQueryService alertQueryService;
    private final AlertGenerationService alertGenerationService;

    public AlertsController(
            AlertCommandService alertCommandService,
            AlertQueryService alertQueryService,
            AlertGenerationService alertGenerationService
    ) {
        this.alertCommandService = alertCommandService;
        this.alertQueryService = alertQueryService;
        this.alertGenerationService = alertGenerationService;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate inventory alerts", description = "Evaluates a business using UTC dates. "
            + "Returns only newly generated alerts; repeated requests without changes return an empty list. "
            + "Business ID must match the authenticated business. Expiration includes today through today + 7 days.")
    public ResponseEntity<List<AlertResource>> generateAlerts(
            @Valid @RequestBody GenerateAlertsResource resource
    ) {
        var alerts = alertGenerationService.handle(
                GenerateAlertsCommandFromResourceAssembler.toCommandFromResource(resource));
        return ResponseEntity.ok(alerts.stream()
                .map(AlertResourceFromEntityAssembler::toResourceFromEntity)
                .toList());
    }

    @GetMapping
    @Operation(summary = "Get alerts", description = "Optional filters combine with AND. "
            + "Omitted filters only include the authenticated business. Business ID must match the authenticated business.")
    public ResponseEntity<List<AlertResource>> getAllAlerts(
            @Parameter(description = "Business ID, greater than zero")
            @RequestParam(required = false) Long businessId,
            @Parameter(description = "Product ID, greater than zero")
            @RequestParam(required = false) Long productId,
            @Parameter(description = "Lot ID, greater than zero; omitted includes alerts without a lot")
            @RequestParam(required = false) Long lotId,
            @Parameter(description = "false for active alerts, true for resolved alerts")
            @RequestParam(required = false) Boolean resolved
    ) {
        var alerts = alertQueryService.handle(
                new GetAlertsQuery(businessId, productId, lotId, resolved));

        return ResponseEntity.ok(alerts.stream()
                .map(AlertResourceFromEntityAssembler::toResourceFromEntity)
                .toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an alert by ID")
    public ResponseEntity<AlertResource> getAlertById(@PathVariable Long id) {
        return alertQueryService.handle(new GetAlertByIdQuery(id))
                .map(AlertResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Resolve an alert", description = "No request body. "
            + "Repeated resolution returns the alert without changing its resolution date.")
    public ResponseEntity<AlertResource> resolveAlert(@PathVariable Long id) {
        return alertCommandService.handle(new ResolveAlertCommand(id))
                .map(AlertResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
