package com.bodego.platform.iam.application.internal.authorization;

import com.bodego.platform.iam.domain.exceptions.IamAccessDeniedException;
import com.bodego.platform.iam.domain.model.valueobjects.UserRole;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.*;
import com.bodego.platform.shared.application.security.CurrentIdentity;
import org.springframework.stereotype.Service;

/** Called inside the application service's transaction, including for non-HTTP callers. */
@Service
public class AdminAuthorizationService {
    private final CurrentIdentity identity;
    private final UserRepository users;
    private final BusinessRepository businesses;
    public AdminAuthorizationService(CurrentIdentity identity, UserRepository users, BusinessRepository businesses) {
        this.identity=identity; this.users=users; this.businesses=businesses;
    }
    public Long requireAdmin(boolean forUpdate) {
        var actor=identity.require();
        if(!"ADMIN".equals(actor.role())) throw new IamAccessDeniedException();
        var business=forUpdate?businesses.findWithLockById(actor.businessId()):businesses.findById(actor.businessId());
        if(business.isEmpty() || !Boolean.TRUE.equals(business.get().getActive())) throw new IamAccessDeniedException();
        var user=users.findByIdAndBusinessId(actor.userId(),actor.businessId());
        if(user.isEmpty() || !user.get().isEnabled() || user.get().getRole()!=UserRole.ADMIN) throw new IamAccessDeniedException();
        return actor.businessId();
    }
}
