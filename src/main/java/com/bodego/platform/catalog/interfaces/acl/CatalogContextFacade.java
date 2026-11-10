package com.bodego.platform.catalog.interfaces.acl;

import com.bodego.platform.catalog.domain.model.commands.DecreaseLotQuantityCommand;
import com.bodego.platform.catalog.domain.model.commands.IncreaseLotQuantityCommand;
import com.bodego.platform.catalog.domain.model.commands.ValidateLotStockCommand;
import com.bodego.platform.catalog.domain.model.queries.GetLotByIdQuery;
import com.bodego.platform.catalog.domain.model.queries.GetLotsByProductIdQuery;
import com.bodego.platform.catalog.domain.model.queries.GetProductByIdQuery;
import com.bodego.platform.catalog.domain.model.queries.GetProductsByBusinessIdQuery;
import com.bodego.platform.catalog.domain.services.LotCommandService;
import com.bodego.platform.catalog.domain.services.LotQueryService;
import com.bodego.platform.catalog.domain.services.ProductQueryService;
import com.bodego.platform.catalog.interfaces.acl.resources.LotInventorySnapshot;
import com.bodego.platform.catalog.interfaces.acl.resources.ProductInventorySnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CatalogContextFacade {

    private final LotCommandService lotCommandService;
    private final LotQueryService lotQueryService;
    private final ProductQueryService productQueryService;

    public CatalogContextFacade(
            LotCommandService lotCommandService,
            LotQueryService lotQueryService,
            ProductQueryService productQueryService
    ) {
        this.lotCommandService = lotCommandService;
        this.lotQueryService = lotQueryService;
        this.productQueryService = productQueryService;
    }

    @Transactional(readOnly = true)
    public ProductInventorySnapshot getProductById(
            Long businessId,
            Long productId
    ) {
        if (businessId == null || businessId <= 0 || productId == null || productId <= 0) {
            throw new IllegalArgumentException("Business ID and product ID must be positive");
        }

        var product = productQueryService.handle(new GetProductByIdQuery(productId))
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (!businessId.equals(product.getBusinessId())) {
            throw new IllegalArgumentException("Product does not belong to the business");
        }

        return new ProductInventorySnapshot(
                product.getId(),
                product.getBusinessId(),
                product.getMinimumStock(),
                product.getName(),
                product.getActive()
        );
    }

    @Transactional(readOnly = true)
    public List<ProductInventorySnapshot> getProductsByBusinessId(
            Long businessId
    ) {
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
                            product.getId(),
                            product.getBusinessId(),
                            product.getMinimumStock(),
                            product.getName(),
                            product.getActive()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LotInventorySnapshot> getLotsByProductId(
            Long businessId,
            Long productId
    ) {
        if (businessId == null || businessId <= 0 || productId == null || productId <= 0) {
            throw new IllegalArgumentException("Business ID and product ID must be positive");
        }

        var product = productQueryService.handle(new GetProductByIdQuery(productId))
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (!businessId.equals(product.getBusinessId())) {
            throw new IllegalArgumentException("Product does not belong to the business");
        }

        return lotQueryService.handle(new GetLotsByProductIdQuery(productId))
                .stream()
                .map(lot -> {
                    if (!businessId.equals(lot.getBusinessId())
                            || !productId.equals(lot.getProductId())) {
                        throw new IllegalArgumentException("Lot does not belong to the business and product");
                    }
                    return new LotInventorySnapshot(
                            lot.getId(),
                            lot.getBusinessId(),
                            lot.getProductId(),
                            lot.getQuantity(),
                            lot.getExpirationDate()
                    );
                })
                .toList();
    }

    public void registerLotMovement(
            Long businessId,
            Long lotId,
            boolean entry,
            Integer quantity
    ) {

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
                new IncreaseLotQuantityCommand(
                        lotId,
                        quantity
                ))
                : lotCommandService.handle(
                new DecreaseLotQuantityCommand(
                        lotId,
                        quantity
                ));

        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "Lot not found or stock operation could not be completed");
        }

    }

    public void validateLotStock(
            Long businessId,
            Long lotId,
            Integer expectedQuantity
    ) {

        lotCommandService.handle(
                new ValidateLotStockCommand(
                        businessId,
                        lotId,
                        expectedQuantity
                )
        );
    }

    public Integer getLotQuantity(
            Long businessId,
            Long lotId
    ) {

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
