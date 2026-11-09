package com.bodego.platform.catalog.application.internal.commandservices;

import com.bodego.platform.catalog.domain.model.aggregates.Product;
import com.bodego.platform.catalog.domain.model.commands.*;
import com.bodego.platform.catalog.domain.services.ProductCommandService;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.CategoryRepository;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.ProductRepository;
import com.bodego.platform.catalog.domain.exceptions.ProductAlreadyExistsException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ProductCommandServiceImpl implements ProductCommandService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductCommandServiceImpl(
            ProductRepository productRepository,
            CategoryRepository categoryRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public Long handle(CreateProductCommand command) {

        validateCategory(command.categoryId(), command.businessId());

        validateDuplicateName(
                command.businessId(),
                command.name(),
                null
        );

        var product = new Product(
                command.businessId(),
                command.categoryId(),
                command.name(),
                command.description(),
                command.price(),
                command.minimumStock()
        );

        return productRepository.save(product).getId();
    }

    @Override
    @Transactional
    public Optional<Product> handle(UpdateProductCommand command) {

        var optionalProduct = productRepository.findById(
                command.productId()
        );

        if (optionalProduct.isEmpty()) {
            return Optional.empty();
        }

        var product = optionalProduct.get();

        validateCategory(
                command.categoryId(),
                product.getBusinessId()
        );

        validateDuplicateName(
                product.getBusinessId(),
                command.name(),
                product.getId()
        );

        product.updateInformation(
                command.categoryId(),
                command.name(),
                command.description(),
                command.price(),
                command.minimumStock()
        );

        return Optional.of(productRepository.save(product));
    }

    @Override
    @Transactional
    public Optional<Product> handle(ActivateProductCommand command) {

        return productRepository.findById(command.productId())
                .map(product -> {
                    product.activate();
                    return productRepository.save(product);
                });
    }

    @Override
    @Transactional
    public Optional<Product> handle(DeactivateProductCommand command) {

        return productRepository.findById(command.productId())
                .map(product -> {
                    product.deactivate();
                    return productRepository.save(product);
                });
    }

    private void validateCategory(Long categoryId, Long businessId) {

        if (categoryId == null || categoryId <= 0 ||
                businessId == null || businessId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid category or business ID"
            );
        }

        if (categoryRepository.findByIdAndBusinessId(
                categoryId, businessId).isEmpty()) {
            throw new IllegalArgumentException(
                    "Category does not exist or belongs to another business"
            );
        }
    }

    private void validateDuplicateName(
            Long businessId,
            String name,
            Long productId
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Product name cannot be blank"
            );
        }

        String normalizedName = name.trim();

        boolean exists = productId == null
                ? productRepository.existsByBusinessIdAndNameIgnoreCase(
                businessId, normalizedName)
                : productRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(
                businessId, normalizedName, productId);

        if (exists) {
            throw new ProductAlreadyExistsException(
                    "Product name already exists in this business"
            );
        }
    }
}
