package com.bodego.platform.strategicoffers.domain.services;

import com.bodego.platform.strategicoffers.domain.model.aggregates.Recommendation;
import com.bodego.platform.strategicoffers.domain.model.commands.*;

import java.util.*;

public interface RecommendationCommandService {

    List<Recommendation> handle(
            GenerateRecommendationsCommand command
    );

    Optional<Recommendation> handle(
            DismissRecommendationCommand command
    );
}
