package com.bodego.platform.strategicoffers.application.internal.outboundservices.acl;

import com.bodego.platform.catalog.interfaces.acl.CatalogContextFacade;
import com.bodego.platform.catalog.interfaces.acl.resources.ProductInventorySnapshot;
import com.bodego.platform.inventory.interfaces.acl.InventoryContextFacade;
import com.bodego.platform.inventory.interfaces.acl.resources.LotInventorySnapshot;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("strategicOffersExternalCatalogService")
public class ExternalCatalogService {

    private final CatalogContextFacade catalogContextFacade;
    private final InventoryContextFacade inventoryContextFacade;

    public ExternalCatalogService(CatalogContextFacade catalogContextFacade,
                                  InventoryContextFacade inventoryContextFacade) {
        this.catalogContextFacade = catalogContextFacade;
        this.inventoryContextFacade = inventoryContextFacade;
    }

    public List<ProductInventorySnapshot> getProductsByBusinessId(Long businessId) {
        return catalogContextFacade.getProductsByBusinessId(businessId);
    }

    public List<LotInventorySnapshot> getLotsByProductId(Long businessId, Long productId) {
        return inventoryContextFacade.getLotsByProductId(businessId, productId);
    }

    public void validateActiveProduct(Long businessId, Long productId) {
        var product = catalogContextFacade.getProductById(businessId, productId);
        if (!Boolean.TRUE.equals(product.active())) {
            throw new IllegalArgumentException("Product must be active");
        }
    }
}
