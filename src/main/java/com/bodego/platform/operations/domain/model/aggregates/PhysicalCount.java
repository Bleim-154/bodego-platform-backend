package com.bodego.platform.operations.domain.model.aggregates;

import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "physical_counts")

public class PhysicalCount extends AuditableAbstractAggregateRoot<PhysicalCount> {

    @NotNull
    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @NotNull
    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull
    @Min(0)
    @Column(name = "system_quantity", nullable = false)
    private Integer systemQuantity;

    @NotNull
    @Min(0)
    @Column(name = "physical_quantity", nullable = false)
    private Integer physicalQuantity;

    @NotNull
    @Column(name = "quantity_difference", nullable = false)
    private Integer difference;

    @NotNull
    @Column(name = "count_date", nullable = false)
    private LocalDateTime date;

    @Size(max = 500)
    @Column(name = "observation", length = 500)
    private String observation;

    protected PhysicalCount() {
    }

    private void validateBusinessId(Long businessId) {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException(
                    "Business ID must be greater than zero"
            );
        }
    }

    private void validateLotId(Long lotId) {
        if (lotId == null || lotId <= 0) {
            throw new IllegalArgumentException(
                    "Lot ID must be greater than zero"
            );
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "User ID must be greater than zero"
            );
        }
    }

    private void validateSystemQuantity(Integer systemQuantity) {
        if (systemQuantity == null || systemQuantity < 0) {
            throw new IllegalArgumentException(
                    "System quantity cannot be negative"
            );
        }
    }

    private void validatePhysicalQuantity(Integer physicalQuantity) {
        if (physicalQuantity == null || physicalQuantity < 0) {
            throw new IllegalArgumentException(
                    "Physical quantity cannot be negative"
            );
        }
    }

    private void validateDate(LocalDateTime date) {
        if (date == null) {
            throw new IllegalArgumentException("Date is necessary");
        }
    }

    private String validateObservation(String observation) {
        if (observation == null || observation.isBlank()) {
            return null;
        }

        String trimObs = observation.trim();

        if (trimObs.length() > 255) {
            throw new IllegalArgumentException(
                    "Observation cannot be greater than 255 characters"
            );
        }

        return trimObs;
    }

    public PhysicalCount(Long businessId, Long lotId, Long userId, Integer systemQuantity, Integer physicalQuantity, LocalDateTime date, String observation) {

        validateBusinessId(businessId);
        validateLotId(lotId);
        validateUserId(userId);
        validateSystemQuantity(systemQuantity);
        validatePhysicalQuantity(physicalQuantity);
        validateDate(date);
        validateObservation(observation);

        this.businessId = businessId;
        this.lotId = lotId;
        this.userId = userId;
        this.systemQuantity = systemQuantity;
        this.physicalQuantity = physicalQuantity;
        this.difference = physicalQuantity - systemQuantity;
        this.date = date;
        this.observation = validateObservation(observation);
    }
}