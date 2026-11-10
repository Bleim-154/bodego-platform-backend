package com.bodego.platform.operations.domain.model.commands;

import java.time.LocalDateTime;

public record CreatePhysicalCountCommand(Long lotId, Long userId, Integer physicalQuantity, LocalDateTime date, String observation) {
}
