package com.bodego.platform.operations.domain.services;

import com.bodego.platform.operations.domain.model.commands.RegisterWasteCommand;

public interface WasteCommandService {

    Long handle(RegisterWasteCommand command);
}
