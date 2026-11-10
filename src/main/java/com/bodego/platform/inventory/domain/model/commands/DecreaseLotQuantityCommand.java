package com.bodego.platform.inventory.domain.model.commands;

public record DecreaseLotQuantityCommand(
        Long lotId,
        Integer amount
) {}
