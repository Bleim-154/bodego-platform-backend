package com.bodego.platform.iam.domain.services;

import com.bodego.platform.iam.domain.model.aggregates.User;
import com.bodego.platform.iam.domain.model.queries.GetUserByIdQuery;
import com.bodego.platform.iam.domain.model.queries.GetUserByEmailQuery;
import java.util.Optional;

public interface UserQueryService {
    Optional<User> handle(GetUserByIdQuery query);
    Optional<User> handle(GetUserByEmailQuery query);
}
