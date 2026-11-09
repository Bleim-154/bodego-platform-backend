package com.bodego.platform.catalog.domain.model.commands;

public record DecreaseLotQuantityCommand(
        Long lotId,
        Integer amount
) {}
