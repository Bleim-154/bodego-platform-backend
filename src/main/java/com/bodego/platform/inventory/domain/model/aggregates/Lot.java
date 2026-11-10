package com.bodego.platform.inventory.domain.model.aggregates;

import com.bodego.platform.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Entity
@Table(
        name = "lots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_lots_business_batch_number",
                        columnNames = {"business_id", "batch_number"}
                )
        }
)
public class Lot extends AuditableAbstractAggregateRoot<Lot> {

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "batch_number", nullable = false, length = 100)
    private String batchNumber;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    protected Lot() {
        // Required by JPA
    }

    public Lot(
            Long businessId,
            Long productId,
            String batchNumber,
            Integer quantity,
            LocalDate expirationDate
    ) {
        validateInformation(
                businessId, productId, batchNumber, quantity
        );

        this.businessId = businessId;
        this.productId = productId;
        this.batchNumber = batchNumber.trim();
        this.quantity = quantity;
        this.expirationDate = expirationDate;
    }

    public Lot updateInformation(
            String batchNumber,
            LocalDate expirationDate
    ) {
        validateBatchNumber(batchNumber);

        this.batchNumber = batchNumber.trim();
        this.expirationDate = expirationDate;

        return this;
    }

    public Lot increaseQuantity(Integer amount) {
        validatePositiveAmount(amount);
        this.quantity += amount;
        return this;
    }

    public Lot decreaseQuantity(Integer amount) {
        validatePositiveAmount(amount);

        if (amount > this.quantity) {
            throw new IllegalArgumentException(
                    "Insufficient quantity in lot"
            );
        }

        this.quantity -= amount;
        return this;
    }

    private void validateInformation(
            Long businessId,
            Long productId,
            String batchNumber,
            Integer quantity
    ) {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException("Invalid business ID");
        }

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Invalid product ID");
        }

        validateBatchNumber(batchNumber);

        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException(
                    "Lot quantity cannot be negative"
            );
        }
    }

    private void validateBatchNumber(String batchNumber) {
        if (batchNumber == null || batchNumber.isBlank() ||
                batchNumber.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Invalid batch number"
            );
        }
    }

    private void validatePositiveAmount(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero"
            );
        }
    }
}
