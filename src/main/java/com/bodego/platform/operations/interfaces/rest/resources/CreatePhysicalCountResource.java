package com.bodego.platform.operations.interfaces.rest.resources;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record CreatePhysicalCountResource(@NotNull @Positive Long lotId, @NotNull @Positive Long userId, Integer systemQuantity, @NotNull @Min(0) Integer physicalQuantity, Integer difference, @NotNull LocalDateTime date, @Size(max = 255) String observation) { }
