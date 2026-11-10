package com.bodego.platform.strategicoffers.interfaces.rest;

import com.bodego.platform.strategicoffers.domain.model.commands.*;
import com.bodego.platform.strategicoffers.domain.model.queries.*;
import com.bodego.platform.strategicoffers.domain.model.valueobjects.RecommendationType;
import com.bodego.platform.strategicoffers.domain.services.*;
import com.bodego.platform.strategicoffers.interfaces.rest.resources.*;
import com.bodego.platform.strategicoffers.interfaces.rest.transform.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recommendations")
@Tag(name = "Recommendations", description = "Strategic inventory recommendations. Business ID is not authorization; IAM pending.")
public class RecommendationsController {

    private final RecommendationCommandService commands;
    private final RecommendationQueryService queries;

    public RecommendationsController(
            RecommendationCommandService commands,
            RecommendationQueryService queries
    ) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate recommendations", description = "Evaluates active products once using UTC; expiration includes today through today + 30 days. Returns only new recommendations.")
    public ResponseEntity<List<RecommendationResource>> generate(
            @Valid @RequestBody GenerateRecommendationsResource resource
    ) {
        return ResponseEntity.ok(commands.handle(GenerateRecommendationsCommandFromResourceAssembler.toCommandFromResource(resource))
                .stream()
                .map(RecommendationResourceFromEntityAssembler::toResourceFromEntity)
                .toList());
    }

    @GetMapping
    @Operation(summary = "List recommendations", description = "Optional filters combine with AND. Business ID is a filter, not authorization.")
    public ResponseEntity<List<RecommendationResource>> getAll(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) RecommendationType type,
            @RequestParam(required = false) Boolean dismissed
    ) {
        return ResponseEntity.ok(queries.handle(new GetRecommendationsQuery(
                        businessId,
                        productId,
                        type,
                        dismissed
                ))
                .stream()
                .map(RecommendationResourceFromEntityAssembler::toResourceFromEntity)
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecommendationResource> getById(
            @PathVariable Long id
    ) {
        return queries.handle(new GetRecommendationByIdQuery(id))
                .map(RecommendationResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/dismiss")
    @Operation(summary = "Dismiss recommendation", description = "Idempotent; no request body.")
    public ResponseEntity<RecommendationResource> dismiss(
            @PathVariable Long id
    ) {
        return commands.handle(new DismissRecommendationCommand(id))
                .map(RecommendationResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
