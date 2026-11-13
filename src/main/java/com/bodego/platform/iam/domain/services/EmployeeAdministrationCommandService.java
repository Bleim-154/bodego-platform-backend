package com.bodego.platform.iam.domain.services;

import com.bodego.platform.iam.domain.model.aggregates.User;
import com.bodego.platform.iam.domain.model.commands.*;

public interface EmployeeAdministrationCommandService {
    User handle(RegisterEmployeeCommand command);
    User handle(ChangeEmployeeStatusCommand command);
}
