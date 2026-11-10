package com.bodego.platform.strategicoffers.domain.model.aggregates;

import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import com.bodego.platform.strategicoffers.domain.model.valueobjects.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "recommendations")
public class Recommendation extends AuditableAbstractAggregateRoot<Recommendation> {

    @NotNull
    @Positive
    @Column(nullable = false, updatable = false)
    private Long businessId;

    @NotNull
    @Positive
    @Column(nullable = false, updatable = false)
    private Long productId;

    @Positive
    @Column(updatable = false)
    private Long lotId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private RecommendationType type;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255, updatable = false)
    private String message;

    @NotNull
    @Column(nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(nullable = false)
    private boolean dismissed;

    protected Recommendation() {
        // Required by JPA
    }

    public Recommendation(
            Long businessId,
            Long productId,
            Long lotId,
            RecommendationType type,
            String message,
            Instant generatedAt
    ) {
        validateInformation(
                businessId, productId, lotId, type, message, generatedAt
        );

        this.businessId = businessId;
        this.productId = productId;
        this.lotId = lotId;
        this.type = type;
        this.message = message.trim();
        this.generatedAt = generatedAt;
        this.dismissed = false;
    }

    public boolean dismiss() {
        if (dismissed) {
            return false;
        }
        dismissed = true;
        return true;
    }

    private void validateInformation(
            Long businessId,
            Long productId,
            Long lotId,
            RecommendationType type,
            String message,
            Instant generatedAt
    ) {
        StrategicOffersValidation.positive(businessId);
        StrategicOffersValidation.positive(productId);
        StrategicOffersValidation.optionalPositive(lotId);
        if (type == null || generatedAt == null) {
            throw new IllegalArgumentException("Type and generation time are required");
        }

        if ((type == RecommendationType.EXPIRING) != (lotId != null)) {
            throw new IllegalArgumentException("Only expiring recommendations must reference a lot");
        }

        if (message == null || message.isBlank() || message.length() > 255) {
            throw new IllegalArgumentException("Message must contain between 1 and 255 characters");
        }
    }
}
