package com.bodego.platform.catalog.interfaces.rest.resources;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record UpdateLotResource(
        @NotBlank @Size(max = 100) String batchNumber,
        LocalDate expirationDate
) {}
