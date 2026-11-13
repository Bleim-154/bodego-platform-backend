package com.bodego.platform.iam.application.internal.queryservices;

import com.bodego.platform.iam.application.internal.authorization.AdminAuthorizationService;
import com.bodego.platform.iam.domain.exceptions.*;
import com.bodego.platform.iam.domain.model.aggregates.User;
import com.bodego.platform.iam.domain.model.queries.*;
import com.bodego.platform.iam.domain.model.valueobjects.*;
import com.bodego.platform.iam.domain.services.EmployeeAdministrationQueryService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class EmployeeAdministrationQueryServiceImpl implements EmployeeAdministrationQueryService {
    private final AdminAuthorizationService authorization;
    private final UserRepository users;
    public EmployeeAdministrationQueryServiceImpl(AdminAuthorizationService authorization,UserRepository users) {
        this.authorization=authorization; this.users=users;
    }
    @Override public User handle(GetManagedUserByIdQuery query) {
        Long businessId=authorization.requireAdmin(false);
        IamValidation.positiveId(query.userId());
        return users.findByIdAndBusinessId(query.userId(),businessId).orElseThrow(ManagedUserNotFoundException::new);
    }
    @Override public UserDirectoryPage handle(ListBusinessUsersQuery query) {
        Long businessId=authorization.requireAdmin(false);
        if(query.page()<0 || query.size()<1 || query.size()>100) throw new InvalidIamDataException();
        var slice=users.findAllByBusinessId(businessId,PageRequest.of(query.page(),query.size(),Sort.by("id")));
        return new UserDirectoryPage(slice.getContent(),query.page(),query.size(),slice.hasNext());
    }
}
