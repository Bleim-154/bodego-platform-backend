package com.bodego.platform.shared.application.security;

/** Explicit unit fixture only. Integration tests always use the real IAM adapter. */
public final class TestBusinessAccess {
    private TestBusinessAccess() {}
    public static BusinessAccess admin() {
        return new BusinessAccess() {
            @Override public AuthenticatedIdentity require(BusinessPermission permission) {
                if(!permission.allows("ADMIN")) throw new BusinessAccessDeniedException();
                return new AuthenticatedIdentity(99L,1L,"ADMIN");
            }
            @Override public AuthenticatedIdentity requireWrite(BusinessPermission permission) { return require(permission); }
        };
    }
}
