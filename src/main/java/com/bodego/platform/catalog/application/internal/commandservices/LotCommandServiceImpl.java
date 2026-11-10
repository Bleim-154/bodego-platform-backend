package com.bodego.platform.catalog.application.internal.commandservices;

import com.bodego.platform.catalog.domain.model.aggregates.Lot;
import com.bodego.platform.catalog.domain.model.commands.*;
import com.bodego.platform.catalog.domain.services.LotCommandService;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.LotRepository;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LotCommandServiceImpl implements LotCommandService {

    private final LotRepository lotRepository;
    private final ProductRepository productRepository;

    public LotCommandServiceImpl(
            LotRepository lotRepository,
            ProductRepository productRepository
    ) {
        this.lotRepository = lotRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public Long handle(CreateLotCommand command) {

        if (command.quantity() == null || command.quantity() != 0) {
            throw new IllegalArgumentException(
                    "New lots must start with zero quantity"
            );
        }

        validateProduct(command.productId(), command.businessId());

        validateDuplicateBatch(
                command.businessId(),
                command.batchNumber(),
                null
        );

        var lot = new Lot(
                command.businessId(),
                command.productId(),
                command.batchNumber(),
                command.quantity(),
                command.expirationDate()
        );

        return lotRepository.save(lot).getId();
    }

    @Override
    @Transactional
    public Optional<Lot> handle(UpdateLotCommand command) {

        var lot = lotRepository.findWithLockById(command.lotId());

        if (lot.isEmpty()) {
            return Optional.empty();
        }

        validateDuplicateBatch(
                lot.get().getBusinessId(),
                command.batchNumber(),
                lot.get().getId()
        );

        lot.get().updateInformation(
                command.batchNumber(),
                command.expirationDate()
        );

        return Optional.of(lotRepository.save(lot.get()));
    }

    @Override
    @Transactional
    public Optional<Lot> handle(IncreaseLotQuantityCommand command) {

        var lot = lotRepository.findWithLockById(command.lotId());

        if (lot.isEmpty()) {
            return Optional.empty();
        }

        lot.get().increaseQuantity(command.amount());

        return Optional.of(lotRepository.save(lot.get()));
    }

    @Override
    @Transactional
    public Optional<Lot> handle(DecreaseLotQuantityCommand command) {

        var lot = lotRepository.findWithLockById(command.lotId());

        if (lot.isEmpty()) {
            return Optional.empty();
        }

        lot.get().decreaseQuantity(command.amount());

        return Optional.of(lotRepository.save(lot.get()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(ValidateLotStockCommand command) {

        if (command == null
                || command.businessId() == null
                || command.businessId() <= 0
                || command.lotId() == null
                || command.lotId() <= 0
                || command.expectedQuantity() == null
                || command.expectedQuantity() < 0) {
            throw new IllegalArgumentException(
                    "Invalid lot stock validation command");
        }

        var lot = lotRepository.findWithLockById(command.lotId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Lot not found"));

        if (!lot.getBusinessId().equals(command.businessId())) {
            throw new IllegalArgumentException(
                    "Lot does not belong to the business");
        }

        if (!lot.getQuantity().equals(command.expectedQuantity())) {
            throw new IllegalStateException(
                    "Lot stock has changed since the physical count");
        }
    }

    private void validateProduct(Long productId, Long businessId) {

        if (productId == null || productId <= 0 ||
                businessId == null || businessId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid product or business ID"
            );
        }

        var product = productRepository.findByIdAndBusinessId(
                productId,
                businessId
        ).orElseThrow(() -> new IllegalArgumentException(
                "Product does not exist or belongs to another business"
        ));

        if (!product.getActive()) {
            throw new IllegalArgumentException(
                    "Cannot create a lot for an inactive product"
            );
        }
    }

    private void validateDuplicateBatch(
            Long businessId,
            String batchNumber,
            Long excludedId
    ) {
        if (batchNumber == null || batchNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid batch number"
            );
        }

        String normalizedNumber = batchNumber.trim();

        boolean exists = excludedId == null
                ? lotRepository.existsByBusinessIdAndBatchNumberIgnoreCase(
                businessId, normalizedNumber)
                : lotRepository.existsByBusinessIdAndBatchNumberIgnoreCaseAndIdNot(
                businessId, normalizedNumber, excludedId);

        if (exists) {
            throw new IllegalArgumentException(
                    "Batch number already exists in this business"
            );
        }
    }
}
