package com.bodego.platform.iam.application.internal.commandservices;

import com.bodego.platform.iam.application.internal.outboundservices.hashing.PasswordHashingService;
import com.bodego.platform.iam.domain.exceptions.InvalidCredentialsException;
import com.bodego.platform.iam.domain.model.commands.SignInCommand;
import com.bodego.platform.iam.domain.model.valueobjects.*;
import com.bodego.platform.iam.domain.services.AuthenticationCommandService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.*;
import com.bodego.platform.shared.application.security.AuthenticatedIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.CharBuffer;
import java.util.Arrays;

@Service
@Transactional(readOnly = true)
public class AuthenticationCommandServiceImpl implements AuthenticationCommandService {
    private final UserRepository users;
    private final BusinessRepository businesses;
    private final PasswordHashingService hashing;
    private final PasswordHash dummyHash;

    public AuthenticationCommandServiceImpl(UserRepository users, BusinessRepository businesses,
                                             PasswordHashingService hashing) {
        this.users=users; this.businesses=businesses; this.hashing=hashing;
        // Timing equalization only; never a credential or a signing secret.
        this.dummyHash=hashing.hash(java.util.UUID.randomUUID().toString());
    }

    @Override public AuthenticatedIdentity handle(SignInCommand command) {
        char[] password=command.password();
        try(command) {
            var user=users.findByEmail(new EmailAddress(command.email()));
            boolean matches=user.map(u -> u.matchesPassword(CharBuffer.wrap(password), hashing::matches))
                    .orElseGet(() -> hashing.matches(CharBuffer.wrap(password), dummyHash));
            if(user.isEmpty() || !matches || !user.get().isEnabled()) throw new InvalidCredentialsException();
            var business=businesses.findById(user.get().getBusinessId());
            if(business.isEmpty() || !Boolean.TRUE.equals(business.get().getActive())) throw new InvalidCredentialsException();
            return new AuthenticatedIdentity(user.get().getId(), user.get().getBusinessId(), user.get().getRole().name());
        } finally { Arrays.fill(password, '\0'); }
    }
}
