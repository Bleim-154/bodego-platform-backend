package com.bodego.platform.operations.domain.model.commands;

import com.bodego.platform.operations.domain.model.valueobjects.WasteReason;

public record RegisterWasteCommand(
        Long businessId,
        Long lotId,
        Integer quantity,
        WasteReason reason,
        String observation
) {
}
