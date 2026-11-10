package com.bodego.platform.monitoring.domain.model.aggregates;

import com.bodego.platform.monitoring.domain.model.valueobjects.AlertSeverity;
import com.bodego.platform.monitoring.domain.model.valueobjects.AlertType;
import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "alerts")
public class Alert extends AuditableAbstractAggregateRoot<Alert> {

    @NotNull
    @Positive
    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @NotNull
    @Positive
    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Positive
    @Column(name = "lot_id", updatable = false)
    private Long lotId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AlertType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private AlertSeverity severity;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255, updatable = false)
    private String message;

    @NotNull
    @Column(nullable = false)
    private Boolean resolved;

    @NotNull
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Alert() {
        // Required by JPA
    }

    public Alert(
            Long businessId,
            Long productId,
            Long lotId,
            AlertType type,
            AlertSeverity severity,
            String message
    ) {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException("Business ID must be positive");
        }

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID must be positive");
        }

        if (lotId != null && lotId <= 0) {
            throw new IllegalArgumentException("Lot ID must be positive");
        }

        if (type == null || severity == null) {
            throw new IllegalArgumentException("Alert type and severity are required");
        }

        if (type == AlertType.LOW_STOCK && lotId != null) {
            throw new IllegalArgumentException("Low stock alerts cannot reference a lot");
        }

        if (message == null || message.isBlank() || message.length() > 255) {
            throw new IllegalArgumentException(
                    "Alert message must contain between 1 and 255 characters");
        }

        this.businessId = businessId;
        this.productId = productId;
        this.lotId = lotId;
        this.type = type;
        this.severity = severity;
        this.message = message.trim();
        this.resolved = false;
        this.generatedAt = Instant.now();
    }

    public Alert resolve() {
        if (Boolean.TRUE.equals(resolved)) {
            return this;
        }

        this.resolved = true;
        this.resolvedAt = Instant.now();
        return this;
    }
}
