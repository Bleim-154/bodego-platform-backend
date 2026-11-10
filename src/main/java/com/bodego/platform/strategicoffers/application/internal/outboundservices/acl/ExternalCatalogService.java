package com.bodego.platform.strategicoffers.application.internal.outboundservices.acl;

import com.bodego.platform.catalog.interfaces.acl.CatalogContextFacade;
import com.bodego.platform.catalog.interfaces.acl.resources.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("strategicOffersExternalCatalogService")
public class ExternalCatalogService {

    private final CatalogContextFacade facade;

    public ExternalCatalogService(
            CatalogContextFacade facade
    ) {
        this.facade = facade;
    }

    public List<ProductInventorySnapshot> getProductsByBusinessId(
            Long businessId
    ) {
        return facade.getProductsByBusinessId(businessId);
    }

    public List<LotInventorySnapshot> getLotsByProductId(
            Long businessId,
            Long productId
    ) {
        return facade.getLotsByProductId(businessId, productId);
    }

    public void validateActiveProduct(
            Long businessId,
            Long productId
    ) {
        var product = facade.getProductById(businessId, productId);
        if (!Boolean.TRUE.equals(product.active())) {
            throw new IllegalArgumentException("Product must be active");
        }
    }
}
