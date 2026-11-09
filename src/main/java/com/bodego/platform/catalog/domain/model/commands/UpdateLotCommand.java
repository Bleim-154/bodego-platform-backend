
package com.bodego.platform.catalog.domain.model.commands;

import java.time.LocalDate;

public record UpdateLotCommand(
        Long lotId,
        String batchNumber,
        LocalDate expirationDate
) {}
