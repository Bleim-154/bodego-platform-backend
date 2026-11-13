package com.bodego.platform.shared.application.security;

/** Application policy, independent of HTTP and Spring Security. */
public enum BusinessPermission {
    CATALOG_READ(false), CATALOG_MANAGE(true), INVENTORY_READ(false), INVENTORY_MANAGE(true),
    MOVEMENT_REGISTER(false), COUNT_REGISTER(false), WASTE_REGISTER(false), CORRECTION_CREATE(false),
    OPERATIONS_READ(false), CORRECTION_REVIEW(true), STOCK_ADJUSTMENT(true),
    ALERT_READ(false), ALERT_MANAGE(true), AUDIT_READ(true), AUDIT_RECORD(false), STRATEGIC_OFFERS(true),
    DENIED(true);

    private final boolean adminOnly;
    BusinessPermission(boolean adminOnly) { this.adminOnly=adminOnly; }
    public boolean allows(String role) {
        return this!=DENIED && ("ADMIN".equals(role) || (!adminOnly && "EMPLOYEE".equals(role)));
    }
}
