package com.bodego.platform.strategicoffers.domain.model.aggregates;

import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "offers")
public class Offer extends AuditableAbstractAggregateRoot<Offer> {

    @NotNull
    @Positive
    @Column(nullable = false, updatable = false)
    private Long businessId;

    @NotNull
    @Positive
    @Column(nullable = false, updatable = false)
    private Long productId;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String title;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @DecimalMax(value = "100", inclusive = false)
    @Column(nullable = false, columnDefinition = "numeric")
    private BigDecimal discountPercentage;

    @NotNull
    @Column(nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active;

    protected Offer() {
        // Required by JPA
    }

    public Offer(
            Long businessId,
            Long productId,
            String title,
            BigDecimal discountPercentage,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateInformation(
                businessId, productId, title, discountPercentage,
                startDate, endDate
        );

        this.businessId = businessId;
        this.productId = productId;
        this.title = title.trim();
        this.discountPercentage = discountPercentage;
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = true;
    }

    public boolean updateInformation(
            String title,
            BigDecimal discountPercentage,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateInformation(
                this.businessId, this.productId, title, discountPercentage,
                startDate, endDate
        );

        boolean changed = !title.trim().equals(this.title) || this.discountPercentage == null
                || discountPercentage.compareTo(this.discountPercentage) != 0
                || !startDate.equals(this.startDate) || !endDate.equals(this.endDate);
        if (!changed) {
            return false;
        }

        this.title = title.trim();
        this.discountPercentage = discountPercentage;
        this.startDate = startDate;
        this.endDate = endDate;
        return true;
    }

    public boolean activate() {
        if (active) {
            return false;
        }
        active = true;
        return true;
    }

    public boolean deactivate() {
        if (!active) {
            return false;
        }
        active = false;
        return true;
    }

    public boolean isEffectiveOn(
            LocalDate date
    ) {
        if (date == null) {
            throw new IllegalArgumentException("Evaluation date is required");
        }

        return active && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    private void validateInformation(
            Long businessId,
            Long productId,
            String title,
            BigDecimal discountPercentage,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException("ID must be positive");
        }

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("ID must be positive");
        }

        if (title == null || title.isBlank() || title.length() > 255) {
            throw new IllegalArgumentException("Title must contain between 1 and 255 characters");
        }

        if (discountPercentage == null || discountPercentage.signum() <= 0
                || discountPercentage.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new IllegalArgumentException("Discount must be greater than 0 and less than 100");
        }

        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }
    }
}
