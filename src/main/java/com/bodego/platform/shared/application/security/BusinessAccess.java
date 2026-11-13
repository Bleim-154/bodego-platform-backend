package com.bodego.platform.shared.application.security;

public interface BusinessAccess {
    AuthenticatedIdentity require(BusinessPermission permission);
    /** Must join the caller's writable transaction and keep actor/business locks until its completion. */
    AuthenticatedIdentity requireWrite(BusinessPermission permission);

    default Long businessId(BusinessPermission permission,Long claimedBusinessId) {
        var identity=require(permission);
        verifyClaim(identity,claimedBusinessId);
        return identity.businessId();
    }
    default AuthenticatedIdentity write(BusinessPermission permission,Long claimedBusinessId) {
        var identity=requireWrite(permission);
        verifyClaim(identity,claimedBusinessId);
        return identity;
    }
    static void verifyClaim(AuthenticatedIdentity identity,Long claimedBusinessId) {
        if(claimedBusinessId!=null && claimedBusinessId<=0) throw new IllegalArgumentException("Invalid business ID");
        if(claimedBusinessId!=null && !identity.businessId().equals(claimedBusinessId)) throw new BusinessAccessDeniedException();
    }
}
