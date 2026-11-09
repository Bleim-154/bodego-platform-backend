package com.bodego.platform.catalog.application.internal.commandservices;

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

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;


    public CategoryCommandServiceImpl(
            CategoryRepository categoryRepository,
            ProductRepository productRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public Long handle(CreateCategoryCommand command) {

        var name = command.name();

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name cannot be blank");
        }

        if (categoryRepository.existsByBusinessIdAndNameIgnoreCase(
                command.businessId(), name.trim())) {
            throw new CategoryAlreadyExistsException(
                    "Category already exists in this business"
            );
        }

        var category = new Category(
                command.businessId(),
                name,
                command.description()
        );

        return categoryRepository.save(category).getId();
    }

    @Override
    @Transactional
    public Optional<Category> handle(UpdateCategoryCommand command) {

        var category = categoryRepository.findById(command.categoryId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category not found"
                ));

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

        var category = categoryRepository.findById(command.categoryId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Category not found"
                ));

        if (productRepository.existsByCategoryId(category.getId())) {
            throw new CategoryHasProductsException(
                    "Cannot delete a category with associated products"
            );
        }

        categoryRepository.delete(category);
    }
}
