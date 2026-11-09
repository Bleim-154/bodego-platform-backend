package com.bodego.platform.catalog.interfaces.rest;

import com.bodego.platform.catalog.domain.model.queries.*;
import com.bodego.platform.catalog.domain.services.LotCommandService;
import com.bodego.platform.catalog.domain.services.LotQueryService;
import com.bodego.platform.catalog.interfaces.rest.resources.*;
import com.bodego.platform.catalog.interfaces.rest.transform.*;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lots")
public class LotsController {

    private final LotCommandService lotCommandService;
    private final LotQueryService lotQueryService;

    public LotsController(
            LotCommandService lotCommandService,
            LotQueryService lotQueryService
    ) {
        this.lotCommandService = lotCommandService;
        this.lotQueryService = lotQueryService;
    }

    @PostMapping
    public ResponseEntity<LotResource> createLot(
            @Valid @RequestBody CreateLotResource resource
    ) {
        var command = CreateLotCommandFromResourceAssembler
                .toCommandFromResource(resource);

        var lotId = lotCommandService.handle(command);

        return lotQueryService.handle(new GetLotByIdQuery(lotId))
                .map(lot -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(LotResourceFromEntityAssembler
                                .toResourceFromEntity(lot)))
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .build());
    }

    @GetMapping
    public ResponseEntity<List<LotResource>> getAllLots(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Long productId
    ) {
        if (businessId != null && productId != null) {
            return ResponseEntity.badRequest().build();
        }

        var lots = businessId != null
                ? lotQueryService.handle(
                new GetLotsByBusinessIdQuery(businessId))
                : productId != null
                ? lotQueryService.handle(
                new GetLotsByProductIdQuery(productId))
                : lotQueryService.handle(
                new GetAllLotsQuery());

        var resources = lots.stream()
                .map(LotResourceFromEntityAssembler::toResourceFromEntity)
                .toList();

        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LotResource> getLotById(
            @PathVariable Long id
    ) {
        return lotQueryService.handle(new GetLotByIdQuery(id))
                .map(LotResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<LotResource> updateLot(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLotResource resource
    ) {
        var command = UpdateLotCommandFromResourceAssembler
                .toCommandFromResource(id, resource);

        return lotCommandService.handle(command)
                .map(LotResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
