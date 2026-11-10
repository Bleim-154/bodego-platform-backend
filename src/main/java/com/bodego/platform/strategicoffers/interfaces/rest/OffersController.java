package com.bodego.platform.strategicoffers.interfaces.rest;

import com.bodego.platform.strategicoffers.domain.model.commands.*;
import com.bodego.platform.strategicoffers.domain.model.queries.*;
import com.bodego.platform.strategicoffers.domain.services.*;
import com.bodego.platform.strategicoffers.interfaces.rest.resources.*;
import com.bodego.platform.strategicoffers.interfaces.rest.transform.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/offers")
@Tag(name = "Offers", description = "Commercial offers. Business ID is not authorization; IAM pending.")
public class OffersController {

    private final OfferCommandService commands;
    private final OfferQueryService queries;

    public OffersController(
            OfferCommandService commands,
            OfferQueryService queries
    ) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @Operation(summary = "Create active offer", description = "Product must be active and belong to the specified business. Discount is strictly between 0 and 100; endDate >= startDate.")
    public ResponseEntity<OfferResource> create(
            @Valid @RequestBody CreateOfferResource resource
    ) {
        var id = commands.handle(CreateOfferCommandFromResourceAssembler.toCommandFromResource(resource));
        return queries.handle(new GetOfferByIdQuery(id))
                .map(OfferResourceFromEntityAssembler::toResourceFromEntity)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r))
                .orElseGet(() -> ResponseEntity.internalServerError().build());
    }

    @GetMapping
    @Operation(summary = "List offers", description = "Optional filters combine with AND. active is the stored flag; effective validity also requires startDate <= evaluation date <= endDate. Business ID is not authorization.")
    public ResponseEntity<List<OfferResource>> getAll(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(queries.handle(new GetOffersQuery(
                        businessId,
                        productId,
                        active
                ))
                .stream()
                .map(OfferResourceFromEntityAssembler::toResourceFromEntity)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OfferResource> getById(
            @PathVariable Long id
    ) {
        return queries.handle(new GetOfferByIdQuery(id))
                .map(OfferResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update offer", description = "Business and product references are immutable. Does not change activation state.")
    public ResponseEntity<OfferResource> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOfferResource resource
    ) {
        return commands.handle(UpdateOfferCommandFromResourceAssembler.toCommandFromResource(id, resource))
                .map(OfferResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<OfferResource> activate(
            @PathVariable Long id
    ) {
        return commands.handle(new ActivateOfferCommand(id))
                .map(OfferResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<OfferResource> deactivate(
            @PathVariable Long id
    ) {
        return commands.handle(new DeactivateOfferCommand(id))
                .map(OfferResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
