package com.bodego.platform.catalog.domain.services;

import com.bodego.platform.catalog.domain.model.aggregates.Product;
import com.bodego.platform.catalog.domain.model.queries.GetAllProductsQuery;
import com.bodego.platform.catalog.domain.model.queries.GetProductByIdQuery;
import com.bodego.platform.catalog.domain.model.queries.GetProductsByBusinessIdQuery;
import com.bodego.platform.catalog.domain.model.queries.GetProductsByCategoryIdQuery;

import java.util.List;
import java.util.Optional;

public interface ProductQueryService {

    List<Product> handle(GetAllProductsQuery query);

    Optional<Product> handle(GetProductByIdQuery query);

    List<Product> handle(GetProductsByBusinessIdQuery query);

    List<Product> handle(GetProductsByCategoryIdQuery query);
}
