package com.bodego.platform.strategicoffers.application.internal.queryservices;

import com.bodego.platform.strategicoffers.domain.model.aggregates.*;
import com.bodego.platform.strategicoffers.domain.model.queries.*;
import com.bodego.platform.strategicoffers.domain.services.*;
import com.bodego.platform.strategicoffers.infrastructure.persistence.jpa.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class RecommendationQueryServiceImpl implements RecommendationQueryService {

    private final RecommendationRepository repository;

    public RecommendationQueryServiceImpl(
            RecommendationRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public Optional<Recommendation> handle(
            GetRecommendationByIdQuery query
    ) {
        if (query == null) {
            throw new IllegalArgumentException("Query is required");
        }

        return repository.findById(query.recommendationId());
    }

    @Override
    public List<Recommendation> handle(
            GetRecommendationsQuery query
    ) {
        if (query == null) {
            throw new IllegalArgumentException("Query is required");
        }

        return repository.findByFilters(query.businessId(), query.productId(), query.type(), query.dismissed());
    }
}
