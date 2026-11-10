package com.bodego.platform.operations.domain.model.aggregates;

import com.bodego.platform.operations.domain.model.valueobjects.MovementType;
import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "inventory_movements")
@Getter
public class InventoryMovement
        extends AuditableAbstractAggregateRoot<InventoryMovement> {

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private Long lotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, length = 255)
    private String reason;

    protected InventoryMovement() {
    }

    public InventoryMovement(
            Long businessId,
            Long lotId,
            MovementType type,
            Integer quantity,
            String reason
    ) {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException(
                    "Business ID must be positive");
        }

        if (lotId == null || lotId <= 0) {
            throw new IllegalArgumentException(
                    "Lot ID must be positive");
        }

        if (type == null) {
            throw new IllegalArgumentException(
                    "Movement type is required");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Movement quantity must be positive");
        }

        if (reason == null || reason.isBlank()
                || reason.length() > 255) {
            throw new IllegalArgumentException(
                    "Movement reason must contain between 1 and 255 characters");
        }

        this.businessId = businessId;
        this.lotId = lotId;
        this.type = type;
        this.quantity = quantity;
        this.reason = reason.trim();
    }
}
