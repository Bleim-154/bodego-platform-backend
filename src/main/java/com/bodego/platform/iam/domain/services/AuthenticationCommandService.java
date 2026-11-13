package com.bodego.platform.iam.domain.services;

import com.bodego.platform.iam.domain.model.commands.SignInCommand;
import com.bodego.platform.shared.application.security.AuthenticatedIdentity;

public interface AuthenticationCommandService {
    AuthenticatedIdentity handle(SignInCommand command);
}
