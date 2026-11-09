
package com.bodego.platform.catalog.domain.model.aggregates;

import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "products")
public class Product extends AuditableAbstractAggregateRoot<Product> {

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "minimum_stock", nullable = false)
    private Integer minimumStock;

    @Column(name = "active", nullable = false)
    private Boolean active;

    protected Product() {
        // Required by JPA
    }

    public Product(
            Long businessId,
            Long categoryId,
            String name,
            String description,
            BigDecimal price,
            Integer minimumStock
    ) {
        validateInformation(
                businessId, categoryId, name, description,
                price, minimumStock
        );

        this.businessId = businessId;
        this.categoryId = categoryId;
        this.name = name.trim();
        this.description = description;
        this.price = price;
        this.minimumStock = minimumStock;
        this.active = true;
    }

    public Product updateInformation(
            Long categoryId,
            String name,
            String description,
            BigDecimal price,
            Integer minimumStock
    ) {
        validateInformation(
                this.businessId, categoryId, name, description,
                price, minimumStock
        );

        this.categoryId = categoryId;
        this.name = name.trim();
        this.description = description;
        this.price = price;
        this.minimumStock = minimumStock;

        return this;
    }

    public Product deactivate() {
        this.active = false;
        return this;
    }

    public Product activate() {
        this.active = true;
        return this;
    }

    private void validateInformation(
            Long businessId,
            Long categoryId,
            String name,
            String description,
            BigDecimal price,
            Integer minimumStock
    ) {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException("Invalid business ID");
        }

        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException("Invalid category ID");
        }

        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new IllegalArgumentException("Invalid product name");
        }

        if (description != null && description.length() > 255) {
            throw new IllegalArgumentException("Description is too long");
        }

        if (price == null || price.signum() < 0 ||
                price.scale() > 2 ||
                price.precision() - price.scale() > 8) {
            throw new IllegalArgumentException("Invalid product price");
        }

        if (minimumStock == null || minimumStock < 0) {
            throw new IllegalArgumentException("Invalid minimum stock");
        }
    }
}
