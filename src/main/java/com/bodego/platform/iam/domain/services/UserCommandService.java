package com.bodego.platform.iam.domain.services;

import com.bodego.platform.iam.domain.model.commands.CreateEmployeeCommand;
import com.bodego.platform.iam.domain.model.commands.ProvisionFirstAdminCommand;

/** Trusted internal callers only. No registration API or automatic bootstrap. */
public interface UserCommandService {
    Long handle(ProvisionFirstAdminCommand command);
    Long handle(CreateEmployeeCommand command);
}
