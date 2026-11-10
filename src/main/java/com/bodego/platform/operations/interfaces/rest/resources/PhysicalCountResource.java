package com.bodego.platform.operations.interfaces.rest.resources;

import java.time.LocalDateTime;

public record PhysicalCountResource(Long id, Long businessId, Long lotId, Long userId, Integer systemQuantity, Integer physicalQuantity, Integer difference, LocalDateTime date, String observation) {
}
