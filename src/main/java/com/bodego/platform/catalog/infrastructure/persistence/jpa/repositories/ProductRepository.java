
package com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.catalog.domain.model.aggregates.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Find all products belonging to a business
    List<Product> findByBusinessId(Long businessId);

    // Find a product by ID and business
    Optional<Product> findByIdAndBusinessId(
            Long id,
            Long businessId
    );

    // Find all products belonging to a category
    List<Product> findByCategoryId(Long categoryId);

    // Find products by category and business
    List<Product> findByCategoryIdAndBusinessId(
            Long categoryId,
            Long businessId
    );

    // Check if a category has associated products
    boolean existsByCategoryId(Long categoryId);

    // Check for duplicate product names within a business
    boolean existsByBusinessIdAndNameIgnoreCase(
            Long businessId,
            String name
    );

    // Check duplicates when updating a product
    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(
            Long businessId,
            String name,
            Long id
    );
}
