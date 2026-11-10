package com.bodego.platform.operations.domain.model.aggregates;

import com.bodego.platform.operations.domain.model.valueobjects.WasteReason;
import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "wastes")
@Getter
public class Waste extends AuditableAbstractAggregateRoot<Waste> {

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private Long lotId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WasteReason reason;

    @Column(length = 500)
    private String observation;

    @Column(nullable = false)
    private Instant registeredAt;

    protected Waste() {
    }

    public Waste(
            Long businessId,
            Long lotId,
            Integer quantity,
            WasteReason reason,
            String observation
    ) {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException(
                    "Business ID must be positive");
        }

        if (lotId == null || lotId <= 0) {
            throw new IllegalArgumentException(
                    "Lot ID must be positive");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    "Waste quantity must be positive");
        }

        if (reason == null) {
            throw new IllegalArgumentException(
                    "Waste reason is required");
        }

        if (observation != null && observation.length() > 500) {
            throw new IllegalArgumentException(
                    "Observation cannot exceed 500 characters");
        }

        this.businessId = businessId;
        this.lotId = lotId;
        this.quantity = quantity;
        this.reason = reason;
        this.observation = observation;
        this.registeredAt = Instant.now();
    }
}
