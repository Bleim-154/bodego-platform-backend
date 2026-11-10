package com.bodego.platform.operations.domain.model.commands;

import java.time.LocalDateTime;

public record CreatePhysicalCountCommand(Long businessId, Long lotId, Long userId, Integer systemQuantity, Integer physicalQuantity, LocalDateTime date, String observation) {
}
