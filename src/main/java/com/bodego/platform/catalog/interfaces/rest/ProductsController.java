package com.bodego.platform.catalog.interfaces.rest;

import com.bodego.platform.catalog.domain.model.commands.ActivateProductCommand;
import com.bodego.platform.catalog.domain.model.commands.DeactivateProductCommand;
import com.bodego.platform.catalog.domain.model.queries.GetAllProductsQuery;
import com.bodego.platform.catalog.domain.model.queries.GetProductByIdQuery;
import com.bodego.platform.catalog.domain.model.queries.GetProductsByBusinessIdQuery;
import com.bodego.platform.catalog.domain.model.queries.GetProductsByCategoryIdQuery;
import com.bodego.platform.catalog.domain.services.ProductCommandService;
import com.bodego.platform.catalog.domain.services.ProductQueryService;
import com.bodego.platform.catalog.interfaces.rest.resources.CreateProductResource;
import com.bodego.platform.catalog.interfaces.rest.resources.UpdateProductResource;
import com.bodego.platform.catalog.interfaces.rest.resources.ProductResource;
import com.bodego.platform.catalog.interfaces.rest.transform.CreateProductCommandFromResourceAssembler;
import com.bodego.platform.catalog.interfaces.rest.transform.UpdateProductCommandFromResourceAssembler;
import com.bodego.platform.catalog.interfaces.rest.transform.ProductResourceFromEntityAssembler;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductsController {

    private final ProductCommandService productCommandService;
    private final ProductQueryService productQueryService;

    public ProductsController(
            ProductCommandService productCommandService,
            ProductQueryService productQueryService
    ) {
        this.productCommandService = productCommandService;
        this.productQueryService = productQueryService;
    }

    @PostMapping
    public ResponseEntity<ProductResource> createProduct(
            @Valid @RequestBody CreateProductResource resource
    ) {
        var command = CreateProductCommandFromResourceAssembler
                .toCommandFromResource(resource);

        var productId = productCommandService.handle(command);

        return productQueryService.handle(
                        new GetProductByIdQuery(productId))
                .map(product -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(ProductResourceFromEntityAssembler
                                .toResourceFromEntity(product)))
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .build());
    }

    @GetMapping
    public ResponseEntity<List<ProductResource>> getAllProducts(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) Long categoryId
    ) {
        if (businessId != null && categoryId != null) {
            return ResponseEntity.badRequest().build();
        }

        var products = businessId != null
                ? productQueryService.handle(
                new GetProductsByBusinessIdQuery(businessId))
                : categoryId != null
                ? productQueryService.handle(
                new GetProductsByCategoryIdQuery(categoryId))
                : productQueryService.handle(
                new GetAllProductsQuery());

        var resources = products.stream()
                .map(ProductResourceFromEntityAssembler::toResourceFromEntity)
                .toList();

        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResource> getProductById(
            @PathVariable Long id
    ) {
        return productQueryService.handle(new GetProductByIdQuery(id))
                .map(ProductResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResource> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductResource resource
    ) {
        var command = UpdateProductCommandFromResourceAssembler
                .toCommandFromResource(id, resource);

        return productCommandService.handle(command)
                .map(ProductResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ProductResource> activateProduct(
            @PathVariable Long id
    ) {
        return productCommandService.handle(
                        new ActivateProductCommand(id))
                .map(ProductResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ProductResource> deactivateProduct(
            @PathVariable Long id
    ) {
        return productCommandService.handle(
                        new DeactivateProductCommand(id))
                .map(ProductResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
