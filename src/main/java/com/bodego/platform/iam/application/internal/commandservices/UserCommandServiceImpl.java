package com.bodego.platform.iam.application.internal.commandservices;

import com.bodego.platform.iam.application.internal.outboundservices.hashing.PasswordHashingService;
import com.bodego.platform.iam.domain.exceptions.*;
import com.bodego.platform.iam.domain.model.aggregates.User;
import com.bodego.platform.iam.domain.model.commands.CreateEmployeeCommand;
import com.bodego.platform.iam.domain.model.commands.ProvisionFirstAdminCommand;
import com.bodego.platform.iam.domain.model.valueobjects.*;
import com.bodego.platform.iam.domain.services.UserCommandService;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.BusinessRepository;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Objects;

@Service
@Transactional
public class UserCommandServiceImpl implements UserCommandService {
    private final BusinessRepository businesses;
    private final UserRepository users;
    private final PasswordHashingService hashing;

    public UserCommandServiceImpl(BusinessRepository businesses, UserRepository users, PasswordHashingService hashing) {
        this.businesses = businesses;
        this.users = users;
        this.hashing = hashing;
    }

    @Override
    public Long handle(ProvisionFirstAdminCommand command) {
        Objects.requireNonNull(command, "Provisioning command is required");
        try (command) {
            return create(command.businessId(), command.fullName(), command.email(), command.password(), UserRole.ADMIN);
        }
    }

    @Override
    public Long handle(CreateEmployeeCommand command) {
        Objects.requireNonNull(command, "Employee command is required");
        try (command) {
            return create(command.businessId(), command.fullName(), command.email(), command.password(), UserRole.EMPLOYEE);
        }
    }

    private Long create(Long businessId, String fullName, String rawEmail, char[] password, UserRole role) {
        try {
            IamValidation.positiveId(businessId);
            String name = IamValidation.requiredText(fullName, 150, "user full name");
            EmailAddress email = new EmailAddress(rawEmail);
            // Serializes bootstrap per Business, including across JVMs. Inactive admins count too.
            var business = businesses.findWithLockById(businessId).orElseThrow(BusinessNotFoundException::new);
            if (!Boolean.TRUE.equals(business.getActive())) throw new InactiveBusinessException();
            if (role == UserRole.ADMIN && users.existsByBusinessIdAndRole(businessId, UserRole.ADMIN)) {
                throw new FirstAdminAlreadyProvisionedException();
            }
            if (users.existsByEmail(email)) throw new EmailAlreadyExistsException();
            PasswordHash hash = hashing.hash(CharBuffer.wrap(password));
            return users.persist(new User(businessId, name, email, hash, role)).getId();
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
