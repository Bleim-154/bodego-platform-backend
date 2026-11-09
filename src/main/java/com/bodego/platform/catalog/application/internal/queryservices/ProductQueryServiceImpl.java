package com.bodego.platform.catalog.application.internal.queryservices;

import com.bodego.platform.catalog.domain.model.aggregates.Product;
import com.bodego.platform.catalog.domain.model.queries.*;
import com.bodego.platform.catalog.domain.services.ProductQueryService;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductQueryServiceImpl implements ProductQueryService {

    private final ProductRepository productRepository;

    public ProductQueryServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public List<Product> handle(GetAllProductsQuery query) {
        return productRepository.findAll();
    }

    @Override
    public Optional<Product> handle(GetProductByIdQuery query) {
        return productRepository.findById(query.productId());
    }

    @Override
    public List<Product> handle(GetProductsByBusinessIdQuery query) {
        return productRepository.findByBusinessId(query.businessId());
    }

    @Override
    public List<Product> handle(GetProductsByCategoryIdQuery query) {
        return productRepository.findByCategoryId(query.categoryId());
    }
}
