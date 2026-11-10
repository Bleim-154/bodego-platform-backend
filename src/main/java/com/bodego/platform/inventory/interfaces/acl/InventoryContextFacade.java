package com.bodego.platform.inventory.interfaces.acl;

import com.bodego.platform.catalog.interfaces.acl.CatalogContextFacade;
import com.bodego.platform.inventory.domain.model.commands.DecreaseLotQuantityCommand;
import com.bodego.platform.inventory.domain.model.commands.IncreaseLotQuantityCommand;
import com.bodego.platform.inventory.domain.model.commands.ValidateLotStockCommand;
import com.bodego.platform.inventory.domain.model.queries.GetLotByIdQuery;
import com.bodego.platform.inventory.domain.model.queries.GetLotsByProductIdQuery;
import com.bodego.platform.inventory.domain.services.LotCommandService;
import com.bodego.platform.inventory.domain.services.LotQueryService;
import com.bodego.platform.inventory.interfaces.acl.resources.LotInventorySnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryContextFacade {

    private final LotCommandService lotCommandService;
    private final LotQueryService lotQueryService;
    private final CatalogContextFacade catalogContextFacade;

    public InventoryContextFacade(
            LotCommandService lotCommandService,
            LotQueryService lotQueryService,
            CatalogContextFacade catalogContextFacade) {
        this.lotCommandService = lotCommandService;
        this.lotQueryService = lotQueryService;
        this.catalogContextFacade = catalogContextFacade;
    }

    @Transactional(readOnly = true)
    public List<LotInventorySnapshot> getLotsByProductId(Long businessId, Long productId) {
        if (businessId == null || businessId <= 0 || productId == null || productId <= 0) {
            throw new IllegalArgumentException("Business ID and product ID must be positive");
        }
        catalogContextFacade.getProductById(businessId, productId);

        return lotQueryService.handle(new GetLotsByProductIdQuery(productId))
                .stream()
                .map(lot -> {
                    if (!businessId.equals(lot.getBusinessId()) || !productId.equals(lot.getProductId())) {
                        throw new IllegalArgumentException("Lot does not belong to the business and product");
                    }
                    return new LotInventorySnapshot(lot.getId(), lot.getBusinessId(), lot.getProductId(),
                            lot.getQuantity(), lot.getExpirationDate());
                })
                .toList();
    }

    public void registerLotMovement(Long businessId, Long lotId, boolean entry, Integer quantity) {
        var lot = lotQueryService.handle(new GetLotByIdQuery(lotId))
                .orElseThrow(() -> new IllegalArgumentException("Lot not found"));
        if (!lot.getBusinessId().equals(businessId)) {
            throw new IllegalArgumentException("Lot does not belong to the business");
        }

        var result = entry
                ? lotCommandService.handle(new IncreaseLotQuantityCommand(lotId, quantity))
                : lotCommandService.handle(new DecreaseLotQuantityCommand(lotId, quantity));
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Lot not found or stock operation could not be completed");
        }
    }

    public void validateLotStock(Long businessId, Long lotId, Integer expectedQuantity) {
        lotCommandService.handle(new ValidateLotStockCommand(businessId, lotId, expectedQuantity));
    }

    public Integer getLotQuantity(Long businessId, Long lotId) {
        if (businessId == null || businessId <= 0 || lotId == null || lotId <= 0) {
            throw new IllegalArgumentException("Business ID and Lot ID must be positive");
        }
        var lot = lotQueryService.handle(new GetLotByIdQuery(lotId))
                .orElseThrow(() -> new IllegalArgumentException("Lot not found"));
        if (!lot.getBusinessId().equals(businessId)) {
            throw new IllegalArgumentException("Lot does not belong to the business");
        }
        return lot.getQuantity();
    }
}
