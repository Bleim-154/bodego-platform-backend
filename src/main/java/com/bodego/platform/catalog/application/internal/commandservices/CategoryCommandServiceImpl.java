package com.bodego.platform.catalog.application.internal.commandservices;

import com.bodego.platform.shared.application.security.*;

import com.bodego.platform.catalog.domain.exceptions.CategoryAlreadyExistsException;
import com.bodego.platform.catalog.domain.model.aggregates.Category;
import com.bodego.platform.catalog.domain.model.commands.CreateCategoryCommand;
import com.bodego.platform.catalog.domain.model.commands.UpdateCategoryCommand;
import com.bodego.platform.catalog.domain.model.commands.DeleteCategoryCommand;
import com.bodego.platform.catalog.domain.services.CategoryCommandService;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.ProductRepository;
import com.bodego.platform.catalog.domain.exceptions.CategoryHasProductsException;

import java.util.Optional;

@Service
public class CategoryCommandServiceImpl implements CategoryCommandService {
    private final BusinessAccess access;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;


    public CategoryCommandServiceImpl(
            CategoryRepository categoryRepository,
            ProductRepository productRepository, BusinessAccess access) {
        this.access = access;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public Long handle(CreateCategoryCommand command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.write(BusinessPermission.CATALOG_MANAGE, command.businessId());


        var name = command.name();

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name cannot be blank");
        }

        if (categoryRepository.existsByBusinessIdAndNameIgnoreCase(
                actor.businessId(), name.trim())) {
            throw new CategoryAlreadyExistsException(
                    "Category already exists in this business"
            );
        }

        var category = new Category(
                actor.businessId(),
                name,
                command.description()
        );

        return categoryRepository.save(category).getId();
    }

    @Override
    @Transactional
    public Optional<Category> handle(UpdateCategoryCommand command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.requireWrite(BusinessPermission.CATALOG_MANAGE);


        var category = categoryRepository.findByIdAndBusinessId(command.categoryId(), actor.businessId())
                .orElseThrow(BusinessResourceNotFoundException::new);

        var name = command.name();

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name cannot be blank");
        }

        if (categoryRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(
                category.getBusinessId(),
                name.trim(),
                category.getId())) {
            throw new CategoryAlreadyExistsException(
                    "Category name already exists in this business"
            );
        }

        category.updateInformation(name, command.description());

        return Optional.of(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void handle(DeleteCategoryCommand command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.requireWrite(BusinessPermission.DENIED);


        var category = categoryRepository.findByIdAndBusinessId(command.categoryId(), actor.businessId())
                .orElseThrow(BusinessResourceNotFoundException::new);

        if (productRepository.existsByCategoryId(category.getId())) {
            throw new CategoryHasProductsException(
                    "Cannot delete a category with associated products"
            );
        }

        categoryRepository.delete(category);
    }
}
