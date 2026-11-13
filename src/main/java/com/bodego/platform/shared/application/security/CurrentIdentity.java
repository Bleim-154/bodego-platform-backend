package com.bodego.platform.shared.application.security;

public interface CurrentIdentity {
    AuthenticatedIdentity require();
}
