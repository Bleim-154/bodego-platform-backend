package com.bodego.platform.operations.application.internal.commandservices;

import com.bodego.platform.operations.application.internal.outboundservices.acl.ExternalCatalogService;
import com.bodego.platform.operations.application.internal.outboundservices.acl.ExternalMonitoringService;
import com.bodego.platform.operations.domain.model.aggregates.StockAdjustment;
import com.bodego.platform.operations.domain.model.commands.ApplyStockAdjustmentCommand;
import com.bodego.platform.operations.domain.model.commands.CreateInventoryMovementCommand;
import com.bodego.platform.operations.domain.model.valueobjects.MovementType;
import com.bodego.platform.operations.domain.services.InventoryMovementCommandService;
import com.bodego.platform.operations.domain.services.StockAdjustmentCommandService;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.PhysicalCountRepository;
import com.bodego.platform.operations.infrastructure.persistence.jpa.repositories.StockAdjustmentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockAdjustmentCommandServiceImpl
        implements StockAdjustmentCommandService {

    private final StockAdjustmentRepository adjustmentRepository;
    private final PhysicalCountRepository physicalCountRepository;
    private final InventoryMovementCommandService movementCommandService;
    private final ExternalCatalogService externalCatalogService;
    private final ExternalMonitoringService externalMonitoringService;

    public StockAdjustmentCommandServiceImpl(
            StockAdjustmentRepository adjustmentRepository,
            PhysicalCountRepository physicalCountRepository,
            InventoryMovementCommandService movementCommandService,
            ExternalCatalogService externalCatalogService,
            ExternalMonitoringService externalMonitoringService) {

        this.adjustmentRepository = adjustmentRepository;
        this.physicalCountRepository = physicalCountRepository;
        this.movementCommandService = movementCommandService;
        this.externalCatalogService = externalCatalogService;
        this.externalMonitoringService = externalMonitoringService;
    }

    @Override
    @Transactional
    public Long handle(ApplyStockAdjustmentCommand command) {

        if (command == null
                || command.businessId() == null
                || command.businessId() <= 0
                || command.physicalCountId() == null
                || command.physicalCountId() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid stock adjustment command");
        }

        var count = physicalCountRepository
                .findById(command.physicalCountId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Physical count not found"));

        if (!count.getBusinessId().equals(command.businessId())) {
            throw new IllegalArgumentException(
                    "Physical count does not belong to the business");
        }

        if (count.getDifference() == 0) {
            throw new IllegalArgumentException(
                    "Physical count has no stock difference");
        }

        if (adjustmentRepository.existsByPhysicalCountId(
                count.getId())) {
            throw new IllegalStateException(
                    "Physical count already has a stock adjustment");
        }

        externalCatalogService.validateLotStock(
                command.businessId(),
                count.getLotId(),
                count.getSystemQuantity()
        );

        int difference = count.getDifference();

        MovementType type = difference > 0
                ? MovementType.ENTRY
                : MovementType.EXIT;

        int quantity = Math.toIntExact(
                Math.abs((long) difference)
        );

        movementCommandService.handle(
                new CreateInventoryMovementCommand(
                        command.businessId(),
                        count.getLotId(),
                        type,
                        quantity,
                        command.reason()
                )
        );

        var adjustment = new StockAdjustment(
                command.businessId(),
                count.getId(),
                count.getLotId(),
                count.getSystemQuantity(),
                count.getCountedQuantity(),
                command.reason()
        );

        var savedAdjustment =
                adjustmentRepository.save(adjustment);

        adjustmentRepository.flush();

        externalMonitoringService.recordAction(
                savedAdjustment.getBusinessId(), "STOCK_ADJUSTMENT_APPLIED",
                "StockAdjustment", savedAdjustment.getId(),
                "Stock adjustment for physical count " + savedAdjustment.getPhysicalCountId()
                        + ": " + savedAdjustment.getPreviousQuantity()
                        + " to " + savedAdjustment.getNewQuantity());

        return savedAdjustment.getId();
    }
}
