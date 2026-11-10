package com.bodego.platform.operations.domain.model.commands;

public record CreatePhysicalCountCommand(
        Long businessId,
        Long lotId,
        Integer countedQuantity,
        String observation
) {
}
