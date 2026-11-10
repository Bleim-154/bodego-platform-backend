package com.bodego.platform.monitoring.application.internal.outboundservices.acl;

import com.bodego.platform.catalog.interfaces.acl.CatalogContextFacade;
import com.bodego.platform.inventory.interfaces.acl.InventoryContextFacade;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service("monitoringExternalCatalogService")
public class ExternalCatalogService {

    private final CatalogContextFacade catalogContextFacade;
    private final InventoryContextFacade inventoryContextFacade;

    public ExternalCatalogService(CatalogContextFacade catalogContextFacade,
                                  InventoryContextFacade inventoryContextFacade) {
        this.catalogContextFacade = catalogContextFacade;
        this.inventoryContextFacade = inventoryContextFacade;
    }

    public List<InventoryProduct> getProductsByBusinessId(Long businessId) {
        return catalogContextFacade.getProductsByBusinessId(businessId).stream()
                .map(product -> new InventoryProduct(product.id(), product.businessId(), product.minimumStock()))
                .toList();
    }

    public List<InventoryLot> getLotsByProductId(Long businessId, Long productId) {
        return inventoryContextFacade.getLotsByProductId(businessId, productId).stream()
                .map(lot -> new InventoryLot(lot.id(), lot.businessId(), lot.productId(),
                        lot.quantity(), lot.expirationDate()))
                .toList();
    }

    public record InventoryProduct(Long id, Long businessId, Integer minimumStock) { }

    public record InventoryLot(Long id, Long businessId, Long productId,
                               Integer quantity, LocalDate expirationDate) { }
}
