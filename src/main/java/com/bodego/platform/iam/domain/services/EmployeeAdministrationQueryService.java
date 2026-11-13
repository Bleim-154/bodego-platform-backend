package com.bodego.platform.iam.domain.services;

import com.bodego.platform.iam.domain.model.aggregates.User;
import com.bodego.platform.iam.domain.model.queries.*;
import com.bodego.platform.iam.domain.model.valueobjects.UserDirectoryPage;

public interface EmployeeAdministrationQueryService {
    User handle(GetManagedUserByIdQuery query);
    UserDirectoryPage handle(ListBusinessUsersQuery query);
}
