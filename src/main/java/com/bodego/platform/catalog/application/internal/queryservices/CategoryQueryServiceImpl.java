
package com.bodego.platform.catalog.application.internal.queryservices;

import com.bodego.platform.catalog.domain.model.aggregates.Category;
import com.bodego.platform.catalog.domain.model.queries.GetAllCategoriesQuery;
import com.bodego.platform.catalog.domain.model.queries.GetCategoryByIdQuery;
import com.bodego.platform.catalog.domain.model.queries.GetCategoriesByBusinessIdQuery;
import com.bodego.platform.catalog.domain.services.CategoryQueryService;
import com.bodego.platform.catalog.infrastructure.persistence.jpa.repositories.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CategoryQueryServiceImpl implements CategoryQueryService {

    private final CategoryRepository categoryRepository;

    public CategoryQueryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public List<Category> handle(GetAllCategoriesQuery query) {
        return categoryRepository.findAll();
    }

    @Override
    public Optional<Category> handle(GetCategoryByIdQuery query) {
        return categoryRepository.findById(query.categoryId());
    }

    @Override
    public List<Category> handle(GetCategoriesByBusinessIdQuery query) {
        return categoryRepository.findByBusinessId(query.businessId());
    }
}
