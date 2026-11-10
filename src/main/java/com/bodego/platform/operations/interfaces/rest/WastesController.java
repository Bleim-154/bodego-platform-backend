package com.bodego.platform.operations.interfaces.rest;

import com.bodego.platform.operations.domain.model.queries.*;
import com.bodego.platform.operations.domain.services.WasteCommandService;
import com.bodego.platform.operations.domain.services.WasteQueryService;
import com.bodego.platform.operations.interfaces.rest.resources.*;
import com.bodego.platform.operations.interfaces.rest.transform.*;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wastes")
public class WastesController {

    private final WasteCommandService commandService;
    private final WasteQueryService queryService;

    public WastesController(
            WasteCommandService commandService,
            WasteQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<WasteResource> create(
            @Valid @RequestBody RegisterWasteResource resource) {

        var command =
                RegisterWasteCommandFromResourceAssembler
                        .toCommandFromResource(resource);

        Long id = commandService.handle(command);

        var waste = queryService.handle(
                new GetWasteByIdQuery(id)
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Created waste could not be retrieved"
        ));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WasteResourceFromEntityAssembler
                        .toResourceFromEntity(waste));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WasteResource> getById(
            @PathVariable Long id) {

        return queryService.handle(new GetWasteByIdQuery(id))
                .map(WasteResourceFromEntityAssembler
                        ::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<WasteResource>> getAll(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Long lotId) {

        if (businessId != null && lotId != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use only one filter: businessId or lotId"
            );
        }

        var wastes = businessId != null
                ? queryService.handle(
                        new GetWastesByBusinessIdQuery(businessId))
                : lotId != null
                ? queryService.handle(
                        new GetWastesByLotIdQuery(lotId))
                : queryService.handle(new GetAllWastesQuery());

        return ResponseEntity.ok(
                wastes.stream()
                        .map(WasteResourceFromEntityAssembler
                                ::toResourceFromEntity)
                        .toList()
        );
    }
}
