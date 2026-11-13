package com.bodego.platform.iam.interfaces.acl;

import com.bodego.platform.iam.domain.model.queries.GetBusinessByIdQuery;
import com.bodego.platform.iam.domain.model.queries.GetUserByIdQuery;
import com.bodego.platform.iam.domain.services.BusinessQueryService;
import com.bodego.platform.iam.domain.services.UserQueryService;
import com.bodego.platform.iam.interfaces.acl.resources.BusinessSnapshot;
import com.bodego.platform.iam.interfaces.acl.resources.UserAccessSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

/** Read-only collaboration, not authentication or authorization. No JPA entities or secrets escape. */
@Service
@Transactional(readOnly = true)
public class IamContextFacade {
    private final BusinessQueryService businesses;
    private final UserQueryService users;

    public IamContextFacade(BusinessQueryService businesses, UserQueryService users) {
        this.businesses = businesses;
        this.users = users;
    }

    public Optional<BusinessSnapshot> getBusinessById(Long businessId) {
        return businesses.handle(new GetBusinessByIdQuery(businessId))
                .map(b -> new BusinessSnapshot(b.getId(), b.getName(), b.getAddress(), b.getPhone(), Boolean.TRUE.equals(b.getActive())));
    }

    public Optional<UserAccessSnapshot> getUserById(Long businessId, Long userId) {
        var user = users.handle(new GetUserByIdQuery(businessId, userId));
        if (user.isEmpty()) return Optional.empty();
        return businesses.handle(new GetBusinessByIdQuery(businessId))
                .map(b -> new UserAccessSnapshot(user.get().getId(), businessId, user.get().getRole().name(),
                        user.get().isEnabled(), Boolean.TRUE.equals(b.getActive())));
    }
}
