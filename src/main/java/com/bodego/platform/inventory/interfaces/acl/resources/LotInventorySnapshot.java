package com.bodego.platform.inventory.interfaces.acl.resources;

import java.time.LocalDate;

public record LotInventorySnapshot(
        Long id,
        Long businessId,
        Long productId,
        Integer quantity,
        LocalDate expirationDate
) {
}
