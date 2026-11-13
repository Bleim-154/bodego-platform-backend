package com.bodego.platform.iam.application.internal.queryservices;

import com.bodego.platform.iam.domain.model.aggregates.Business;
import com.bodego.platform.iam.domain.model.queries.GetBusinessByIdQuery;
import com.bodego.platform.iam.domain.model.queries.IsBusinessActiveQuery;
import com.bodego.platform.iam.domain.model.valueobjects.IamValidation;
import com.bodego.platform.iam.domain.services.BusinessQueryService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.BusinessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class BusinessQueryServiceImpl implements BusinessQueryService {
    private final BusinessRepository businesses;

    public BusinessQueryServiceImpl(BusinessRepository businesses) { this.businesses = businesses; }

    @Override
    public Optional<Business> handle(GetBusinessByIdQuery query) {
        Objects.requireNonNull(query, "Business query is required");
        return businesses.findById(IamValidation.positiveId(query.businessId()));
    }

    @Override
    public boolean handle(IsBusinessActiveQuery query) {
        Objects.requireNonNull(query, "Business status query is required");
        return businesses.findById(IamValidation.positiveId(query.businessId()))
                .map(business -> Boolean.TRUE.equals(business.getActive())).orElse(false);
    }
}
