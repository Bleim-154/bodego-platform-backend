package com.bodego.platform.iam.infrastructure.security;

import com.bodego.platform.shared.application.security.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class CurrentIdentityFromSecurityContext implements CurrentIdentity {
    @Override public AuthenticatedIdentity require() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedIdentity identity)) {
            throw new AuthenticationCredentialsNotFoundException("Unauthorized");
        }
        return identity;
    }
}
