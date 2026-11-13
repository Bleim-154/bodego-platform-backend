package com.bodego.platform.operations.domain.model.aggregates;

import com.bodego.platform.operations.domain.model.valueobjects.CorrectionRequestStatus;
import com.bodego.platform.operations.domain.exceptions.CorrectionRequestAlreadyReviewedException;
import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "correction_requests")
@Getter
public class CorrectionRequest
        extends AuditableAbstractAggregateRoot<CorrectionRequest> {

    @Column(nullable = false)
    private Long businessId;

    @Column(nullable = false)
    private Long physicalCountId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CorrectionRequestStatus status;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant reviewedAt;

    @Column(length = 500)
    private String reviewComment;

    protected CorrectionRequest() {
    }

    public CorrectionRequest(
            Long businessId,
            Long physicalCountId,
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

        if (reason == null || reason.isBlank()
                || reason.length() > 500) {
            throw new IllegalArgumentException(
                    "Reason must contain between 1 and 500 characters");
        }

        this.businessId = businessId;
        this.physicalCountId = physicalCountId;
        this.reason = reason.trim();
        this.status = CorrectionRequestStatus.PENDING;
        this.requestedAt = Instant.now();
    }

    public void approve(String reviewComment) {
        validatePending();
        validateReviewComment(reviewComment);

        this.status = CorrectionRequestStatus.APPROVED;
        this.reviewComment = normalize(reviewComment);
        this.reviewedAt = Instant.now();
    }

    public void reject(String reviewComment) {
        validatePending();
        validateReviewComment(reviewComment);

        this.status = CorrectionRequestStatus.REJECTED;
        this.reviewComment = normalize(reviewComment);
        this.reviewedAt = Instant.now();
    }

    private void validatePending() {
        if (status != CorrectionRequestStatus.PENDING) {
            throw new CorrectionRequestAlreadyReviewedException();
        }
    }

    private void validateReviewComment(String comment) {
        if (comment != null && comment.length() > 500) {
            throw new IllegalArgumentException(
                    "Review comment cannot exceed 500 characters");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
