package com.bodego.platform.catalog.domain.services;

import com.bodego.platform.catalog.domain.model.aggregates.Category;
import com.bodego.platform.catalog.domain.model.queries.GetAllCategoriesQuery;
import com.bodego.platform.catalog.domain.model.queries.GetCategoryByIdQuery;
import com.bodego.platform.catalog.domain.model.queries.GetCategoriesByBusinessIdQuery;

import java.util.List;
import java.util.Optional;

public interface CategoryQueryService {

    List<Category> handle(GetAllCategoriesQuery query);

    Optional<Category> handle(GetCategoryByIdQuery query);

    List<Category> handle(GetCategoriesByBusinessIdQuery query);
}
