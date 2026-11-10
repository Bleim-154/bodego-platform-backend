package com.bodego.platform.operations.interfaces.rest;

import com.bodego.platform.operations.domain.model.queries.*;
import com.bodego.platform.operations.domain.model.valueobjects.CorrectionRequestStatus;
import com.bodego.platform.operations.domain.services.CorrectionRequestCommandService;
import com.bodego.platform.operations.domain.services.CorrectionRequestQueryService;
import com.bodego.platform.operations.interfaces.rest.resources.*;
import com.bodego.platform.operations.interfaces.rest.transform.*;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/correction-requests")
public class CorrectionRequestsController {

    private final CorrectionRequestCommandService commandService;
    private final CorrectionRequestQueryService queryService;

    public CorrectionRequestsController(
            CorrectionRequestCommandService commandService,
            CorrectionRequestQueryService queryService
    ) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<CorrectionRequestResource> create(
            @Valid @RequestBody CreateCorrectionRequestResource resource
    ) {
        var command =
                CreateCorrectionRequestCommandFromResourceAssembler
                        .toCommandFromResource(resource);

        Long id = commandService.handle(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(findResourceById(id));
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<CorrectionRequestResource> review(
            @PathVariable Long id,
            @Valid @RequestBody ReviewCorrectionRequestResource resource
    ) {
        var command =
                ReviewCorrectionRequestCommandFromResourceAssembler
                        .toCommandFromResource(id, resource);

        Long reviewedId = commandService.handle(command);

        return ResponseEntity.ok(findResourceById(reviewedId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CorrectionRequestResource> getById(
            @PathVariable Long id
    ) {
        return queryService.handle(new GetCorrectionRequestByIdQuery(id))
                .map(CorrectionRequestResourceFromEntityAssembler
                        ::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<CorrectionRequestResource>> getAll(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Long physicalCountId,
            @RequestParam(required = false) CorrectionRequestStatus status
    ) {
        if (physicalCountId != null
                && (businessId != null || status != null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "physicalCountId cannot be combined with other filters"
            );
        }

        if (status != null && businessId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Filtering by status requires businessId"
            );
        }

        var requests = physicalCountId != null
                ? queryService.handle(
                        new GetCorrectionRequestsByPhysicalCountIdQuery(
                                physicalCountId))
                : status != null
                ? queryService.handle(
                        new GetCorrectionRequestsByBusinessAndStatusQuery(
                                businessId, status))
                : businessId != null
                ? queryService.handle(
                        new GetCorrectionRequestsByBusinessIdQuery(
                                businessId))
                : queryService.handle(
                        new GetAllCorrectionRequestsQuery());

        return ResponseEntity.ok(
                requests.stream()
                        .map(CorrectionRequestResourceFromEntityAssembler
                                ::toResourceFromEntity)
                        .toList()
        );
    }

    private CorrectionRequestResource findResourceById(Long id) {
        return queryService.handle(new GetCorrectionRequestByIdQuery(id))
                .map(CorrectionRequestResourceFromEntityAssembler
                        ::toResourceFromEntity)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Correction request could not be retrieved"
                ));
    }
}
