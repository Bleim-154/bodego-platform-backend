package com.bodego.platform.shared.application.security;

/** Trusted application identity; independent of HTTP, JPA and Spring Security. */
public record AuthenticatedIdentity(Long userId, Long businessId, String role) {}
