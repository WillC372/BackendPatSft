package com.poolguard.auth;

/**
 * Role-Based Access Control (RBAC) roles.
 */
public enum Role {
    ADMIN,    // full access: thresholds, maintenance, audit, reports
    OPERATOR  // read-only monitoring + alerts
}
