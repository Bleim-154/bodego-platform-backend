package com.bodego.platform.iam.domain.model.commands;

public record UpdateBusinessCommand(Long businessId, String name, String address, String phone) {}
