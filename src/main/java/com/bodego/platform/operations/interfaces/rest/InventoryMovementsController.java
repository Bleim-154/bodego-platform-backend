
package com.bodego.platform.operations.interfaces.rest;

import com.bodego.platform.operations.domain.model.queries.*;
import com.bodego.platform.operations.domain.services.InventoryMovementCommandService;
import com.bodego.platform.operations.domain.services.InventoryMovementQueryService;
import com.bodego.platform.operations.interfaces.rest.resources.*;
import com.bodego.platform.operations.interfaces.rest.transform.*;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory-movements")
public class InventoryMovementsController {

    private final InventoryMovementCommandService commandService;
    private final InventoryMovementQueryService queryService;

    public InventoryMovementsController(
            InventoryMovementCommandService commandService,
            InventoryMovementQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<InventoryMovementResource> create(
            @Valid @RequestBody CreateInventoryMovementResource resource) {

        var command =
                CreateInventoryMovementCommandFromResourceAssembler
                        .toCommandFromResource(resource);

        Long id = commandService.handle(command);

        var movement = queryService.handle(
                new GetInventoryMovementByIdQuery(id)
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Created movement could not be retrieved"
        ));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InventoryMovementResourceFromEntityAssembler
                        .toResourceFromEntity(movement));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InventoryMovementResource> getById(
            @PathVariable Long id) {

        return queryService.handle(
                        new GetInventoryMovementByIdQuery(id)
                ).map(InventoryMovementResourceFromEntityAssembler
                        ::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<InventoryMovementResource>> getAll(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Long lotId) {

        if (businessId != null && lotId != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use only one filter: businessId or lotId"
            );
        }

        var movements = businessId != null
                ? queryService.handle(
                new GetInventoryMovementsByBusinessIdQuery(businessId))
                : lotId != null
                ? queryService.handle(
                new GetInventoryMovementsByLotIdQuery(lotId))
                : queryService.handle(
                new GetAllInventoryMovementsQuery());

        return ResponseEntity.ok(
                movements.stream()
                        .map(InventoryMovementResourceFromEntityAssembler
                                ::toResourceFromEntity)
                        .toList()
        );
    }
}
