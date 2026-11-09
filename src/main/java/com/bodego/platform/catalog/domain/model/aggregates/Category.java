package com.bodego.platform.catalog.domain.model.aggregates;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;

@Entity
@Table(
        name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_categories_business_name",
                        columnNames = {"business_id", "name"}
                )
        }
)
public class Category extends AuditableAbstractAggregateRoot<Category> {

    @Getter
    @NotNull
    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Getter
    @NotBlank
    @Size(max = 100)
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Getter
    @Size(max = 255)
    @Column(name = "description", length = 255)
    private String description;

    protected Category() {
        // Required by JPA
    }

    public Category(Long businessId, String name, String description) {
        validateBusinessId(businessId);
        validateName(name);
        validateDescription(description);

        this.businessId = businessId;
        this.name = name.trim();
        this.description = description;
    }

    public Category updateInformation(String name, String description) {
        validateName(name);
        validateDescription(description);

        this.name = name.trim();
        this.description = description;
        return this;
    }

    private void validateBusinessId(Long businessId) {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException(
                    "Business ID must be greater than zero"
            );
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Category name must contain between 1 and 100 characters"
            );
        }
    }

    private void validateDescription(String description) {
        if (description != null && description.length() > 255) {
            throw new IllegalArgumentException(
                    "Category description cannot exceed 255 characters"
            );
        }
    }
}

