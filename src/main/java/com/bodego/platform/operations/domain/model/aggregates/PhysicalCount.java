package com.bodego.platform.operations.domain.model.aggregates;

import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "physical_counts")
@Getter
public class PhysicalCount
        extends AuditableAbstractAggregateRoot<PhysicalCount> {

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private Long lotId;

    @Column(nullable = false)
    private Integer systemQuantity;

    @Column(nullable = false)
    private Integer countedQuantity;

    @Column(nullable = false)
    private Integer difference;

    @Column(length = 500)
    private String observation;

    @Column(nullable = false)
    private Instant countedAt;

    protected PhysicalCount() {
    }

    public PhysicalCount(
            Long businessId,
            Long lotId,
            Integer systemQuantity,
            Integer countedQuantity,
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

        if (systemQuantity == null || systemQuantity < 0) {
            throw new IllegalArgumentException(
                    "System quantity cannot be negative");
        }

        if (countedQuantity == null || countedQuantity < 0) {
            throw new IllegalArgumentException(
                    "Counted quantity cannot be negative");
        }

        if (observation != null && observation.length() > 500) {
            throw new IllegalArgumentException(
                    "Observation cannot exceed 500 characters");
        }

        this.businessId = businessId;
        this.lotId = lotId;
        this.systemQuantity = systemQuantity;
        this.countedQuantity = countedQuantity;
        this.difference = countedQuantity - systemQuantity;
        this.observation = observation;
        this.countedAt = Instant.now();
    }
}
