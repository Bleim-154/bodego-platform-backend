package com.bodego.platform.catalog.interfaces.acl;

import com.bodego.platform.catalog.domain.model.queries.GetProductByIdQuery;
import com.bodego.platform.catalog.domain.model.queries.GetProductsByBusinessIdQuery;
import com.bodego.platform.catalog.domain.services.ProductQueryService;
import com.bodego.platform.catalog.interfaces.acl.resources.ProductInventorySnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogContextFacade {

    private final ProductQueryService productQueryService;

    public CatalogContextFacade(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    @Transactional(readOnly = true)
    public ProductInventorySnapshot getProductById(Long businessId, Long productId) {
        if (businessId == null || businessId <= 0 || productId == null || productId <= 0) {
            throw new IllegalArgumentException("Business ID and product ID must be positive");
        }

        var product = productQueryService.handle(new GetProductByIdQuery(productId))
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (!businessId.equals(product.getBusinessId())) {
            throw new IllegalArgumentException("Product does not belong to the business");
        }

        return new ProductInventorySnapshot(
                product.getId(), product.getBusinessId(), product.getMinimumStock(),
                product.getName(), product.getActive());
    }

    @Transactional(readOnly = true)
    public List<ProductInventorySnapshot> getProductsByBusinessId(Long businessId) {
        if (businessId == null || businessId <= 0) {
            throw new IllegalArgumentException("Business ID must be positive");
        }

        return productQueryService.handle(new GetProductsByBusinessIdQuery(businessId))
                .stream()
                .map(product -> {
                    if (!businessId.equals(product.getBusinessId())) {
                        throw new IllegalArgumentException("Product does not belong to the business");
                    }
                    return new ProductInventorySnapshot(
                            product.getId(), product.getBusinessId(), product.getMinimumStock(),
                            product.getName(), product.getActive());
                })
                .toList();
    }
}
