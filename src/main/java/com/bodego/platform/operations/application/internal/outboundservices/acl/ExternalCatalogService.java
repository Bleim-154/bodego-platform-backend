package com.bodego.platform.operations.application.internal.outboundservices.acl;

import com.bodego.platform.catalog.interfaces.acl.CatalogContextFacade;
import com.bodego.platform.operations.domain.model.valueobjects.MovementType;
import org.springframework.stereotype.Service;

@Service
public class ExternalCatalogService {

    private final CatalogContextFacade catalogContextFacade;

    public ExternalCatalogService(
            CatalogContextFacade catalogContextFacade) {
        this.catalogContextFacade = catalogContextFacade;
    }

    public void registerMovement(
            Long businessId,
            Long lotId,
            MovementType type,
            Integer quantity) {

        if (type == null) {
            throw new IllegalArgumentException(
                    "Movement type is required");
        }

        catalogContextFacade.registerLotMovement(
                businessId,
                lotId,
                type == MovementType.ENTRY,
                quantity
        );
    }

    public void validateLotStock(
            Long businessId,
            Long lotId,
            Integer expectedQuantity) {

        catalogContextFacade.validateLotStock(
                businessId,
                lotId,
                expectedQuantity
        );
    }

    public Integer getLotQuantity(Long businessId, Long lotId) {
        return catalogContextFacade.getLotQuantity(
                businessId,
                lotId
        );
    }

}
