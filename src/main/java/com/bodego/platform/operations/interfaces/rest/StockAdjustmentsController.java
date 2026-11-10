package com.bodego.platform.operations.interfaces.rest;

import com.bodego.platform.operations.domain.model.queries.*;
import com.bodego.platform.operations.domain.services.StockAdjustmentCommandService;
import com.bodego.platform.operations.domain.services.StockAdjustmentQueryService;
import com.bodego.platform.operations.interfaces.rest.resources.*;
import com.bodego.platform.operations.interfaces.rest.transform.*;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock-adjustments")
public class StockAdjustmentsController {

    private final StockAdjustmentCommandService commandService;
    private final StockAdjustmentQueryService queryService;

    public StockAdjustmentsController(
            StockAdjustmentCommandService commandService,
            StockAdjustmentQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<StockAdjustmentResource> create(
            @Valid @RequestBody ApplyStockAdjustmentResource resource) {

        var command =
                ApplyStockAdjustmentCommandFromResourceAssembler
                        .toCommandFromResource(resource);

        Long id = commandService.handle(command);

        var adjustment = queryService.handle(
                new GetStockAdjustmentByIdQuery(id)
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Created stock adjustment could not be retrieved"
        ));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StockAdjustmentResourceFromEntityAssembler
                        .toResourceFromEntity(adjustment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StockAdjustmentResource> getById(
            @PathVariable Long id) {

        return queryService.handle(
                new GetStockAdjustmentByIdQuery(id)
        ).map(StockAdjustmentResourceFromEntityAssembler
                ::toResourceFromEntity)
         .map(ResponseEntity::ok)
         .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<StockAdjustmentResource>> getAll(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) Long physicalCountId) {

        int filters = (businessId != null ? 1 : 0)
                + (lotId != null ? 1 : 0)
                + (physicalCountId != null ? 1 : 0);

        if (filters > 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use only one filter"
            );
        }

        List<StockAdjustmentResource> adjustments;

        if (businessId != null) {
            adjustments = queryService.handle(
                    new GetStockAdjustmentsByBusinessIdQuery(businessId))
                    .stream()
                    .map(StockAdjustmentResourceFromEntityAssembler
                            ::toResourceFromEntity)
                    .toList();

        } else if (lotId != null) {
            adjustments = queryService.handle(
                    new GetStockAdjustmentsByLotIdQuery(lotId))
                    .stream()
                    .map(StockAdjustmentResourceFromEntityAssembler
                            ::toResourceFromEntity)
                    .toList();

        } else if (physicalCountId != null) {
            adjustments = queryService.handle(
                    new GetStockAdjustmentByPhysicalCountIdQuery(
                            physicalCountId))
                    .stream()
                    .map(StockAdjustmentResourceFromEntityAssembler
                            ::toResourceFromEntity)
                    .toList();

        } else {
            adjustments = queryService.handle(
                    new GetAllStockAdjustmentsQuery())
                    .stream()
                    .map(StockAdjustmentResourceFromEntityAssembler
                            ::toResourceFromEntity)
                    .toList();
        }

        return ResponseEntity.ok(adjustments);
    }
}
