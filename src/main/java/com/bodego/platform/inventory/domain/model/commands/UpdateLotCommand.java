
package com.bodego.platform.inventory.domain.model.commands;

import java.time.LocalDate;

public record UpdateLotCommand(
        Long lotId,
        String batchNumber,
        LocalDate expirationDate
) {}
