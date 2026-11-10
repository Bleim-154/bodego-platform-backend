package com.bodego.platform.strategicoffers.domain.model.valueobjects;

public final class StrategicOffersValidation {

    private StrategicOffersValidation() {
    }

    public static void positive(
            Long id
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID must be positive");
        }
    }

    public static void optionalPositive(
            Long id
    ) {
        if (id != null) {
            positive(id);
        }
    }
}
