package com.bodego.platform.operations.domain.model.aggregates;

import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(
        name = "stock_adjustments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_adjustment_physical_count",
                columnNames = "physical_count_id"
        )
)
@Getter
public class StockAdjustment
        extends AuditableAbstractAggregateRoot<StockAdjustment> {

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false, unique = true)
    private Long physicalCountId;

    @Column(nullable = false)
    private Long lotId;

    @Column(nullable = false)
    private Integer previousQuantity;

    @Column(nullable = false)
    private Integer newQuantity;

    @Column(nullable = false)
    private Integer difference;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(nullable = false)
    private Instant adjustedAt;

    protected StockAdjustment() {
    }

    public StockAdjustment(
            Long businessId,
            Long physicalCountId,
            Long lotId,
            Integer previousQuantity,
            Integer newQuantity,
            String reason
    ) {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException(
                    "Business ID must be positive");
        }

        if (physicalCountId == null || physicalCountId <= 0) {
            throw new IllegalArgumentException(
                    "Physical count ID must be positive");
        }

        if (lotId == null || lotId <= 0) {
            throw new IllegalArgumentException(
                    "Lot ID must be positive");
        }

        if (previousQuantity == null || previousQuantity < 0) {
            throw new IllegalArgumentException(
                    "Previous quantity cannot be negative");
        }

        if (newQuantity == null || newQuantity < 0) {
            throw new IllegalArgumentException(
                    "New quantity cannot be negative");
        }

        if (previousQuantity.equals(newQuantity)) {
            throw new IllegalArgumentException(
                    "Stock adjustment difference cannot be zero");
        }

        if (reason == null || reason.isBlank()
                || reason.length() > 255) {
            throw new IllegalArgumentException(
                    "Reason must contain between 1 and 255 characters");
        }

        this.businessId = businessId;
        this.physicalCountId = physicalCountId;
        this.lotId = lotId;
        this.previousQuantity = previousQuantity;
        this.newQuantity = newQuantity;
        this.difference = newQuantity - previousQuantity;
        this.reason = reason.trim();
        this.adjustedAt = Instant.now();
    }
}
