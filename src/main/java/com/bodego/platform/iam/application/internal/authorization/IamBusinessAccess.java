package com.bodego.platform.iam.application.internal.authorization;

import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.*;
import com.bodego.platform.iam.interfaces.acl.IamContextFacade;
import com.bodego.platform.shared.application.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class IamBusinessAccess implements BusinessAccess {
    private final CurrentIdentity current;
    private final IamContextFacade iam;
    private final BusinessRepository businesses;
    private final UserRepository users;
    public IamBusinessAccess(CurrentIdentity current,IamContextFacade iam,BusinessRepository businesses,UserRepository users) {
        this.current=current;this.iam=iam;this.businesses=businesses;this.users=users;
    }
    private AuthenticatedIdentity actor(BusinessPermission permission) {
        var identity=current.require();
        if(!permission.allows(identity.role())) throw new BusinessAccessDeniedException();
        return identity;
    }
    @Override @Transactional(readOnly=true)
    public AuthenticatedIdentity require(BusinessPermission permission) {
        var identity=actor(permission);
        var state=iam.getUserById(identity.businessId(),identity.userId());
        if(state.isEmpty() || !state.get().enabled() || !state.get().role().equals(identity.role())) throw new BusinessAccessDeniedException();
        return identity;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public AuthenticatedIdentity requireWrite(BusinessPermission permission) {
        if(!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new BusinessAccessDeniedException();
        var identity=actor(permission);
        // Same lock order as IAM administration: Business, then User. Retained until caller commit/rollback.
        boolean metadata=permission==BusinessPermission.CATALOG_MANAGE || permission==BusinessPermission.INVENTORY_MANAGE;
        var business=metadata?businesses.findWithLockById(identity.businessId()):businesses.findWithReadLockById(identity.businessId());
        var user=users.findWithReadLockByIdAndBusinessId(identity.userId(),identity.businessId());
        if(business.isEmpty() || !Boolean.TRUE.equals(business.get().getActive()) || user.isEmpty()
                || !user.get().isEnabled() || !user.get().getRole().name().equals(identity.role())) throw new BusinessAccessDeniedException();
        return identity;
    }
}
