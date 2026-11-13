package com.bodego.platform.catalog.application.internal.queryservices;

import com.bodego.platform.shared.application.security.*;

import com.bodego.platform.catalog.domain.model.aggregates.Product;
import com.bodego.platform.catalog.domain.model.queries.*;
import com.bodego.platform.catalog.domain.services.ProductQueryService;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class ProductQueryServiceImpl implements ProductQueryService {
    private final BusinessAccess access;

    private final ProductRepository productRepository;

    public ProductQueryServiceImpl(ProductRepository productRepository, BusinessAccess access) {
        this.access = access;
        this.productRepository = productRepository;
    }

    @Override
    public List<Product> handle(GetAllProductsQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.CATALOG_READ);

        return productRepository.findByBusinessId(actor.businessId());
    }

    @Override
    public Optional<Product> handle(GetProductByIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.CATALOG_READ);

        return productRepository.findByIdAndBusinessId(query.productId(), actor.businessId());
    }

    @Override
    public List<Product> handle(GetProductsByBusinessIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.CATALOG_READ);
        BusinessAccess.verifyClaim(actor, query.businessId());

        return productRepository.findByBusinessId(actor.businessId());
    }

    @Override
    public List<Product> handle(GetProductsByCategoryIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.CATALOG_READ);

        return productRepository.findByCategoryIdAndBusinessId(query.categoryId(), actor.businessId());
    }
}
