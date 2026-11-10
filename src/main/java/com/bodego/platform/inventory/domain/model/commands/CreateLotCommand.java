package com.bodego.platform.inventory.domain.model.commands;

import java.time.LocalDate;

public record CreateLotCommand(
        Long businessId,
        Long productId,
        String batchNumber,
        Integer quantity,
        LocalDate expirationDate
) {}
