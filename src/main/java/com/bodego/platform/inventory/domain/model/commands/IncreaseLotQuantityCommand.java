package com.bodego.platform.inventory.domain.model.commands;

public record IncreaseLotQuantityCommand(
        Long lotId,
        Integer amount
) {}
