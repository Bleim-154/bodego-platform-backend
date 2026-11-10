package com.bodego.platform.operations.interfaces.rest;

import com.bodego.platform.operations.domain.model.queries.*;
import com.bodego.platform.operations.domain.services.PhysicalCountCommandService;
import com.bodego.platform.operations.domain.services.PhysicalCountQueryService;
import com.bodego.platform.operations.interfaces.rest.resources.*;
import com.bodego.platform.operations.interfaces.rest.transform.*;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/physical-counts")
public class PhysicalCountsController {

    private final PhysicalCountCommandService commandService;
    private final PhysicalCountQueryService queryService;

    public PhysicalCountsController(
            PhysicalCountCommandService commandService,
            PhysicalCountQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<PhysicalCountResource> create(
            @Valid @RequestBody CreatePhysicalCountResource resource) {

        var command =
                CreatePhysicalCountCommandFromResourceAssembler
                        .toCommandFromResource(resource);

        Long id = commandService.handle(command);

        var count = queryService.handle(
                new GetPhysicalCountByIdQuery(id)
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Created physical count could not be retrieved"
        ));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PhysicalCountResourceFromEntityAssembler
                        .toResourceFromEntity(count));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PhysicalCountResource> getById(
            @PathVariable Long id) {

        return queryService.handle(new GetPhysicalCountByIdQuery(id))
                .map(PhysicalCountResourceFromEntityAssembler
                        ::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<PhysicalCountResource>> getAll(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Long lotId) {

        if (businessId != null && lotId != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use only one filter: businessId or lotId"
            );
        }

        var counts = businessId != null
                ? queryService.handle(
                new GetPhysicalCountsByBusinessIdQuery(businessId))
                : lotId != null
                ? queryService.handle(
                new GetPhysicalCountsByLotIdQuery(lotId))
                : queryService.handle(new GetAllPhysicalCountsQuery());

        return ResponseEntity.ok(
                counts.stream()
                        .map(PhysicalCountResourceFromEntityAssembler
                                ::toResourceFromEntity)
                        .toList()
        );
    }
}
