package com.bodego.platform.iam.application.internal.queryservices;

import com.bodego.platform.iam.domain.model.aggregates.User;
import com.bodego.platform.iam.domain.model.queries.GetUserByIdQuery;
import com.bodego.platform.iam.domain.model.queries.GetUserByEmailQuery;
import com.bodego.platform.iam.domain.model.valueobjects.EmailAddress;
import com.bodego.platform.iam.domain.model.valueobjects.IamValidation;
import com.bodego.platform.iam.domain.services.UserQueryService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserQueryServiceImpl implements UserQueryService {
    private final UserRepository users;

    public UserQueryServiceImpl(UserRepository users) { this.users = users; }

    @Override
    public Optional<User> handle(GetUserByIdQuery query) {
        Objects.requireNonNull(query, "User query is required");
        return users.findByIdAndBusinessId(IamValidation.positiveId(query.userId()),
                IamValidation.positiveId(query.businessId()));
    }

    @Override
    public Optional<User> handle(GetUserByEmailQuery query) {
        Objects.requireNonNull(query, "User email query is required");
        return users.findByBusinessIdAndEmail(IamValidation.positiveId(query.businessId()), new EmailAddress(query.email()));
    }
}
