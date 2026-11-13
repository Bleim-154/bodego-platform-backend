package com.bodego.platform.iam.domain.services;

import com.bodego.platform.iam.domain.model.aggregates.Business;
import com.bodego.platform.iam.domain.model.commands.CreateBusinessCommand;
import com.bodego.platform.iam.domain.model.commands.UpdateBusinessCommand;
import java.util.Optional;

public interface BusinessCommandService {
    Long handle(CreateBusinessCommand command);
    Optional<Business> handle(UpdateBusinessCommand command);
}
