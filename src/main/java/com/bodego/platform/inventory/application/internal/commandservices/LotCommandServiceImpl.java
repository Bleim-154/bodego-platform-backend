package com.bodego.platform.inventory.application.internal.commandservices;

import com.bodego.platform.shared.application.security.*;

import com.bodego.platform.inventory.domain.model.aggregates.Lot;
import com.bodego.platform.inventory.domain.exceptions.LotStockChangedException;
import com.bodego.platform.inventory.domain.model.commands.*;
import com.bodego.platform.inventory.domain.services.LotCommandService;
import com.bodego.platform.inventory.infrastructure.persistence.jpa.repositories.LotRepository;
import com.bodego.platform.catalog.interfaces.acl.CatalogContextFacade;
import com.bodego.platform.catalog.interfaces.acl.resources.ProductInventorySnapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LotCommandServiceImpl implements LotCommandService {
    private final BusinessAccess access;

    private final LotRepository lotRepository;
    private final CatalogContextFacade catalogContextFacade;
    private final EntityManager entityManager;

    public LotCommandServiceImpl(
            LotRepository lotRepository,
            CatalogContextFacade catalogContextFacade, BusinessAccess access, EntityManager entityManager) {
        this.access = access;
        this.lotRepository = lotRepository;
        this.catalogContextFacade = catalogContextFacade;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public Long handle(CreateLotCommand command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.write(BusinessPermission.INVENTORY_MANAGE, command.businessId());


        if (command.quantity() == null || command.quantity() != 0) {
            throw new IllegalArgumentException(
                    "New lots must start with zero quantity"
            );
        }

        validateProduct(command.productId(), actor.businessId());

        validateDuplicateBatch(
                actor.businessId(),
                command.batchNumber(),
                null
        );

        var lot = new Lot(
                actor.businessId(),
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
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.requireWrite(BusinessPermission.INVENTORY_MANAGE);


        var lot = lockFreshLot(command.lotId(), actor.businessId());

        if (lot.isEmpty()) {
            return Optional.empty();
        }

        catalogContextFacade.getProductById(actor.businessId(), lot.get().getProductId());

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
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Lot> handle(IncreaseLotQuantityCommand command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.requireWrite(BusinessPermission.MOVEMENT_REGISTER);


        var lot = lockFreshLot(command.lotId(), actor.businessId());

        if (lot.isEmpty()) {
            return Optional.empty();
        }

        catalogContextFacade.getProductById(actor.businessId(), lot.get().getProductId());

        lot.get().increaseQuantity(command.amount());

        return Optional.of(lotRepository.save(lot.get()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Lot> handle(DecreaseLotQuantityCommand command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.requireWrite(BusinessPermission.MOVEMENT_REGISTER);


        var lot = lockFreshLot(command.lotId(), actor.businessId());

        if (lot.isEmpty()) {
            return Optional.empty();
        }

        catalogContextFacade.getProductById(actor.businessId(), lot.get().getProductId());

        lot.get().decreaseQuantity(command.amount());

        return Optional.of(lotRepository.save(lot.get()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void handle(ValidateLotStockCommand command) {
        if (command == null) throw new IllegalArgumentException("Command is required");
        var actor = access.write(BusinessPermission.STOCK_ADJUSTMENT, command.businessId());


        if (command == null
                || actor.businessId() == null
                || actor.businessId() <= 0
                || command.lotId() == null
                || command.lotId() <= 0
                || command.expectedQuantity() == null
                || command.expectedQuantity() < 0) {
            throw new IllegalArgumentException(
                    "Invalid lot stock validation command");
        }

        var lot = lockFreshLot(command.lotId(), actor.businessId())
                .orElseThrow(BusinessResourceNotFoundException::new);

        catalogContextFacade.getProductById(actor.businessId(), lot.getProductId());

        if (!lot.getBusinessId().equals(actor.businessId())) {
            throw new IllegalArgumentException(
                    "Lot does not belong to the business");
        }

        if (!lot.getQuantity().equals(command.expectedQuantity())) {
            throw new LotStockChangedException();
        }
    }

    private Optional<Lot> lockFreshLot(Long lotId, Long businessId) {
        var lot = lotRepository.findWithLockByIdAndBusinessId(lotId, businessId);
        // A prior ACL read can have cached the entity before another transaction
        // committed. Acquiring a query lock alone does not refresh that state.
        // Refresh only this already tenant-scoped row while retaining its write lock.
        lot.ifPresent(current -> entityManager.refresh(current, LockModeType.PESSIMISTIC_WRITE));
        return lot;
    }

    private void validateProduct(Long productId, Long businessId) {

        if (productId == null || productId <= 0 ||
                businessId == null || businessId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid product or business ID"
            );
        }

        final ProductInventorySnapshot product;
        try {
            product = catalogContextFacade.getProductById(businessId, productId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Product does not exist or belongs to another business", exception);
        }
        if (!Boolean.TRUE.equals(product.active())) {
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
