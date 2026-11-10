package com.bodego.platform.operations.domain.services;

import com.bodego.platform.operations.domain.model.commands.CreatePhysicalCountCommand;

public interface PhysicalCountCommandService {

    Long handle(CreatePhysicalCountCommand command);
}
