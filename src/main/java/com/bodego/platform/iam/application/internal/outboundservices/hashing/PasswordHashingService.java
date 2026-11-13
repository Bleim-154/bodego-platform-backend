package com.bodego.platform.iam.application.internal.outboundservices.hashing;

import com.bodego.platform.iam.domain.model.valueobjects.PasswordHash;

public interface PasswordHashingService {
    PasswordHash hash(CharSequence rawPassword);
    boolean matches(CharSequence rawPassword, PasswordHash storedHash);
    boolean needsRehash(PasswordHash storedHash);
}
