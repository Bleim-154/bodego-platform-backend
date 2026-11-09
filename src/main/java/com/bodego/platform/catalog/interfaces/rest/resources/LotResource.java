
package com.bodego.platform.catalog.interfaces.rest.resources;

import java.time.LocalDate;

public record LotResource(
        Long id,
        Long businessId,
        Long productId,
        String batchNumber,
        Integer quantity,
        LocalDate expirationDate
) {}
