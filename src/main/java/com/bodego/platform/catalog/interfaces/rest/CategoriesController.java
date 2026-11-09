package com.bodego.platform.catalog.interfaces.rest;

import com.bodego.platform.catalog.domain.model.commands.DeleteCategoryCommand;
import com.bodego.platform.catalog.domain.model.queries.GetAllCategoriesQuery;
import com.bodego.platform.catalog.domain.model.queries.GetCategoryByIdQuery;
import com.bodego.platform.catalog.domain.model.queries.GetCategoriesByBusinessIdQuery;
import com.bodego.platform.catalog.domain.services.CategoryCommandService;
import com.bodego.platform.catalog.domain.services.CategoryQueryService;
import com.bodego.platform.catalog.interfaces.rest.resources.CategoryResource;
import com.bodego.platform.catalog.interfaces.rest.resources.CreateCategoryResource;
import com.bodego.platform.catalog.interfaces.rest.resources.UpdateCategoryResource;
import com.bodego.platform.catalog.interfaces.rest.transform.CategoryResourceFromEntityAssembler;
import com.bodego.platform.catalog.interfaces.rest.transform.CreateCategoryCommandFromResourceAssembler;
import com.bodego.platform.catalog.interfaces.rest.transform.UpdateCategoryCommandFromResourceAssembler;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoriesController {

    private final CategoryCommandService categoryCommandService;
    private final CategoryQueryService categoryQueryService;

    public CategoriesController(
            CategoryCommandService categoryCommandService,
            CategoryQueryService categoryQueryService
    ) {
        this.categoryCommandService = categoryCommandService;
        this.categoryQueryService = categoryQueryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResource> createCategory(
            @Valid @RequestBody CreateCategoryResource resource
    ) {
        var command = CreateCategoryCommandFromResourceAssembler
                .toCommandFromResource(resource);

        var categoryId = categoryCommandService.handle(command);

        var query = new GetCategoryByIdQuery(categoryId);

        return categoryQueryService.handle(query)
                .map(category -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(CategoryResourceFromEntityAssembler
                                .toResourceFromEntity(category)))
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .build());
    }

    @GetMapping
    public ResponseEntity<List<CategoryResource>> getAllCategories(
            @RequestParam(required = false) Long businessId
    ) {
        var categories = businessId == null
                ? categoryQueryService.handle(new GetAllCategoriesQuery())
                : categoryQueryService.handle(
                new GetCategoriesByBusinessIdQuery(businessId));

        var resources = categories.stream()
                .map(CategoryResourceFromEntityAssembler::toResourceFromEntity)
                .toList();

        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResource> getCategoryById(
            @PathVariable Long id
    ) {
        var query = new GetCategoryByIdQuery(id);

        return categoryQueryService.handle(query)
                .map(CategoryResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResource> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryResource resource
    ) {
        var command = UpdateCategoryCommandFromResourceAssembler
                .toCommandFromResource(id, resource);

        return categoryCommandService.handle(command)
                .map(CategoryResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long id
    ) {
        if (categoryQueryService.handle(
                new GetCategoryByIdQuery(id)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        categoryCommandService.handle(new DeleteCategoryCommand(id));

        return ResponseEntity.noContent().build();
    }
}
