package com.bodego.platform.catalog.interfaces.acl;

import com.bodego.platform.catalog.domain.model.commands.DecreaseLotQuantityCommand;
import com.bodego.platform.catalog.domain.model.commands.IncreaseLotQuantityCommand;
import com.bodego.platform.catalog.domain.model.commands.ValidateLotStockCommand;
import com.bodego.platform.catalog.domain.model.queries.GetLotByIdQuery;
import com.bodego.platform.catalog.domain.services.LotCommandService;
import com.bodego.platform.catalog.domain.services.LotQueryService;
import org.springframework.stereotype.Service;

@Service
public class CatalogContextFacade {

    private final LotCommandService lotCommandService;
    private final LotQueryService lotQueryService;

    public CatalogContextFacade(
            LotCommandService lotCommandService,
            LotQueryService lotQueryService) {
        this.lotCommandService = lotCommandService;
        this.lotQueryService = lotQueryService;
    }

    public void registerLotMovement(
            Long businessId,
            Long lotId,
            boolean entry,
            Integer quantity) {

        var lot = lotQueryService
                .handle(new GetLotByIdQuery(lotId))
                .orElseThrow(() ->
                        new IllegalArgumentException("Lot not found"));

        if (!lot.getBusinessId().equals(businessId)) {
            throw new IllegalArgumentException(
                    "Lot does not belong to the business");
        }

        var result = entry
                ? lotCommandService.handle(
                new IncreaseLotQuantityCommand(lotId, quantity))
                : lotCommandService.handle(
                new DecreaseLotQuantityCommand(lotId, quantity));

        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "Lot not found or stock operation could not be completed");
        }

    }

    public void validateLotStock(
            Long businessId,
            Long lotId,
            Integer expectedQuantity) {

        lotCommandService.handle(
                new ValidateLotStockCommand(
                        businessId,
                        lotId,
                        expectedQuantity
                )
        );
    }

    public Integer getLotQuantity(Long businessId, Long lotId) {

        if (businessId == null || businessId <= 0 ||
                lotId == null || lotId <= 0) {
            throw new IllegalArgumentException(
                    "Business ID and Lot ID must be positive");
        }

        var lot = lotQueryService
                .handle(new GetLotByIdQuery(lotId))
                .orElseThrow(() ->
                        new IllegalArgumentException("Lot not found"));

        if (!lot.getBusinessId().equals(businessId)) {
            throw new IllegalArgumentException(
                    "Lot does not belong to the business");
        }

        return lot.getQuantity();
    }

}
