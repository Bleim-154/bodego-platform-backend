package com.bodego.platform.monitoring.domain.model.aggregates;

import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Getter
@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog extends AuditableAbstractAggregateRoot<AuditLog> {

    @NotNull
    @Positive
    @Column(name = "business_id", nullable = false, updatable = false)
    private Long businessId;

    @Positive
    @Column(name = "user_id", updatable = false)
    private Long userId;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100, updatable = false)
    private String action;

    @NotBlank
    @Size(max = 100)
    @Column(name = "entity_type", nullable = false, length = 100, updatable = false)
    private String entityType;

    @NotNull
    @Positive
    @Column(name = "entity_id", nullable = false, updatable = false)
    private Long entityId;

    @NotBlank
    @Size(max = 1000)
    @Column(nullable = false, length = 1000, updatable = false)
    private String description;

    @NotNull
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditLog() {
        // Required by JPA
    }

    public AuditLog(
            Long businessId,
            Long userId,
            String action,
            String entityType,
            Long entityId,
            String description
    ) {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException("Business ID must be positive");
        }
        if (userId != null && userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive when provided");
        }
        if (entityId == null || entityId <= 0) {
            throw new IllegalArgumentException("Entity ID must be positive");
        }
        validateText(action, 100, "Action");
        validateText(entityType, 100, "Entity type");
        validateText(description, 1000, "Description");

        this.businessId = businessId;
        this.userId = userId;
        this.action = action.trim();
        this.entityType = entityType.trim();
        this.entityId = entityId;
        this.description = description.trim();
        this.occurredAt = Instant.now();
    }

    private void validateText(String value, int maximum, String field) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(
                    field + " must contain between 1 and " + maximum + " characters");
        }
    }
}
