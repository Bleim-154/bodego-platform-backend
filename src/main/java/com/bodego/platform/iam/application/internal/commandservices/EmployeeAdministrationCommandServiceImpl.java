package com.bodego.platform.iam.application.internal.commandservices;

import com.bodego.platform.iam.application.internal.authorization.AdminAuthorizationService;
import com.bodego.platform.iam.domain.exceptions.ManagedUserNotFoundException;
import com.bodego.platform.iam.domain.model.aggregates.User;
import com.bodego.platform.iam.domain.model.commands.*;
import com.bodego.platform.iam.domain.model.valueobjects.IamValidation;
import com.bodego.platform.iam.domain.services.*;
import com.bodego.platform.iam.infrastructure.persistence.jpa.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;

@Service
@Transactional
public class EmployeeAdministrationCommandServiceImpl implements EmployeeAdministrationCommandService {
    private final AdminAuthorizationService authorization;
    private final UserCommandService commands;
    private final UserRepository users;
    public EmployeeAdministrationCommandServiceImpl(AdminAuthorizationService authorization,UserCommandService commands,UserRepository users) {
        this.authorization=authorization; this.commands=commands; this.users=users;
    }
    @Override public User handle(RegisterEmployeeCommand command) {
        char[] password=command.password();
        try(command) {
            Long businessId=authorization.requireAdmin(true);
            try(var trusted=new CreateEmployeeCommand(businessId,command.fullName(),command.email(),password)) {
                Long id=commands.handle(trusted);
                return users.findByIdAndBusinessId(id,businessId).orElseThrow(ManagedUserNotFoundException::new);
            }
        } finally {Arrays.fill(password,'\0');}
    }
    @Override public User handle(ChangeEmployeeStatusCommand command) {
        Long businessId=authorization.requireAdmin(true);
        IamValidation.positiveId(command.userId());
        var user=users.findByIdAndBusinessId(command.userId(),businessId).orElseThrow(ManagedUserNotFoundException::new);
        user.changeEmployeeStatus(command.active());
        return users.saveAndFlush(user);
    }
}
