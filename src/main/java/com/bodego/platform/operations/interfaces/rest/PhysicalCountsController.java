package com.bodego.platform.operations.interfaces.rest;

import com.bodego.platform.operations.domain.model.queries.GetAllPhysicalCountsQuery;
import com.bodego.platform.operations.domain.model.queries.GetPhysicalCountByIdQuery;
import com.bodego.platform.operations.domain.model.queries.GetPhysicalCountsByBusinessIdQuery;
import com.bodego.platform.operations.domain.model.queries.GetPhysicalCountsByLotIdQuery;
import com.bodego.platform.operations.domain.services.PhysicalCountCommandService;
import com.bodego.platform.operations.domain.services.PhysicalCountQueryService;
import com.bodego.platform.operations.interfaces.rest.resources.CreatePhysicalCountResource;
import com.bodego.platform.operations.interfaces.rest.resources.PhysicalCountResource;
import com.bodego.platform.operations.interfaces.rest.transform.CreatePhysicalCountCommandFromResourceAssembler;
import com.bodego.platform.operations.interfaces.rest.transform.PhysicalCountResourceFromEntityAssembler;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/physicalCounts")
public class PhysicalCountsController {

    private final PhysicalCountCommandService commandService;
    private final PhysicalCountQueryService queryService;

    public PhysicalCountsController(PhysicalCountCommandService commandService, PhysicalCountQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<PhysicalCountResource> createPhysicalCount(@Valid @RequestBody CreatePhysicalCountResource resource) {
        Long id = commandService.handle(CreatePhysicalCountCommandFromResourceAssembler.toCommandFromResource(resource));

        return queryService.handle(new GetPhysicalCountByIdQuery(id)).map(PhysicalCountResourceFromEntityAssembler::toResourceFromEntity).map(body -> ResponseEntity.status(HttpStatus.CREATED).body(body)).orElseGet(() -> ResponseEntity.internalServerError().build());
    }

    @GetMapping
    public ResponseEntity<List<PhysicalCountResource>> getPhysicalCounts(@RequestParam(required = false) Long businessId, @RequestParam(required = false) Long lotId) {
        if (businessId != null && lotId != null) {
            return ResponseEntity.badRequest().build();
        }

        var physicalCounts = businessId != null ? queryService.handle(new GetPhysicalCountsByBusinessIdQuery(businessId)) : lotId != null ? queryService.handle(new GetPhysicalCountsByLotIdQuery(lotId)) : queryService.handle(new GetAllPhysicalCountsQuery());

        return ResponseEntity.ok(physicalCounts.stream().map(PhysicalCountResourceFromEntityAssembler::toResourceFromEntity).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PhysicalCountResource> getPhysicalCountById(@PathVariable Long id) {
        return queryService.handle(new GetPhysicalCountByIdQuery(id)).map(PhysicalCountResourceFromEntityAssembler::toResourceFromEntity).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
