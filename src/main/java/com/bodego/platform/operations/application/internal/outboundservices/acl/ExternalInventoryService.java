package com.bodego.platform.operations.application.internal.outboundservices.acl;

import com.bodego.platform.inventory.interfaces.acl.InventoryContextFacade;
import com.bodego.platform.inventory.interfaces.acl.exceptions.LotStockValidationConflictException;
import com.bodego.platform.operations.domain.exceptions.PhysicalCountStockChangedException;
import com.bodego.platform.operations.domain.model.valueobjects.MovementType;
import org.springframework.stereotype.Service;

@Service
public class ExternalInventoryService {

    private final InventoryContextFacade inventoryContextFacade;

    public ExternalInventoryService(InventoryContextFacade inventoryContextFacade) {
        this.inventoryContextFacade = inventoryContextFacade;
    }

    public void registerMovement(Long businessId, Long lotId, MovementType type, Integer quantity) {
        if (type == null) {
            throw new IllegalArgumentException("Movement type is required");
        }
        inventoryContextFacade.registerLotMovement(businessId, lotId, type == MovementType.ENTRY, quantity);
    }

    public void validateLotStock(Long businessId, Long lotId, Integer expectedQuantity) {
        try {
            inventoryContextFacade.validateLotStock(businessId, lotId, expectedQuantity);
        } catch (LotStockValidationConflictException conflict) {
            throw new PhysicalCountStockChangedException(conflict);
        }
    }

    public Integer getLotQuantity(Long businessId, Long lotId) {
        return inventoryContextFacade.getLotQuantity(businessId, lotId);
    }
}
