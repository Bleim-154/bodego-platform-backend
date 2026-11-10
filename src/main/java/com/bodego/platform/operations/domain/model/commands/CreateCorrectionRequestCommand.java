package com.bodego.platform.operations.domain.model.commands;

public record CreateCorrectionRequestCommand(
        Long businessId,
        Long physicalCountId,
        String reason
) {
}
