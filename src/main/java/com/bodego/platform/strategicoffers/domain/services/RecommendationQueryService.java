package com.bodego.platform.strategicoffers.domain.services;

import com.bodego.platform.strategicoffers.domain.model.aggregates.Recommendation;
import com.bodego.platform.strategicoffers.domain.model.queries.*;

import java.util.*;

public interface RecommendationQueryService {

    Optional<Recommendation> handle(
            GetRecommendationByIdQuery query
    );

    List<Recommendation> handle(
            GetRecommendationsQuery query
    );
}
