package com.bodego.platform.catalog.domain.model.commands;

public record IncreaseLotQuantityCommand(
        Long lotId,
        Integer amount
) {}
