
package com.bodego.platform.catalog.application.internal.queryservices;

import com.bodego.platform.shared.application.security.*;

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
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class CategoryQueryServiceImpl implements CategoryQueryService {
    private final BusinessAccess access;

    private final CategoryRepository categoryRepository;

    public CategoryQueryServiceImpl(CategoryRepository categoryRepository, BusinessAccess access) {
        this.access = access;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public List<Category> handle(GetAllCategoriesQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.CATALOG_READ);

        return categoryRepository.findByBusinessId(actor.businessId());
    }

    @Override
    public Optional<Category> handle(GetCategoryByIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.CATALOG_READ);

        return categoryRepository.findByIdAndBusinessId(query.categoryId(), actor.businessId());
    }

    @Override
    public List<Category> handle(GetCategoriesByBusinessIdQuery query) {
        if (query == null) throw new IllegalArgumentException("Query is required");
        var actor = access.require(BusinessPermission.CATALOG_READ);
        BusinessAccess.verifyClaim(actor, query.businessId());

        return categoryRepository.findByBusinessId(actor.businessId());
    }
}
