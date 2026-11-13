package com.bodego.platform.iam.interfaces.acl.resources;

/** Internal role value is copied as text to keep the ACL independent of IAM entities/value objects. */
public record UserAccessSnapshot(Long id, Long businessId, String role, boolean userActive, boolean businessActive) {
    public boolean enabled() { return userActive && businessActive; }
}
