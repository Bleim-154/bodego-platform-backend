package com.bodego.platform.iam.domain.model.commands;

public record ChangeEmployeeStatusCommand(Long userId, boolean active) {}
