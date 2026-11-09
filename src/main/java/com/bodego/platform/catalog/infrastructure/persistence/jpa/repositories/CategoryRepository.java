package com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories;

import com.bodego.platform.catalog.domain.model.aggregates.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Find all categories belonging to a business
    List<Category> findByBusinessId(Long businessId);

    // Find a category by its ID and business
    Optional<Category> findByIdAndBusinessId(
            Long id,
            Long businessId
    );

    // Check if a category name already exists in a business
    boolean existsByBusinessIdAndNameIgnoreCase(
            Long businessId,
            String name
    );

    // Check duplicate names when updating a category
    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(
            Long businessId,
            String name,
            Long id
    );
}
