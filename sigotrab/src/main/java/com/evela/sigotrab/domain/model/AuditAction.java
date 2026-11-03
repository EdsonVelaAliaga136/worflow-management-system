package com.evela.sigotrab.domain.model;

public enum AuditAction {
    CREATE, UPDATE, DELETE, LOGIN, LOGOUT,
    APPROVE, REJECT,
    ACQUIRE, RELEASE, TIMEOUT_RELEASE,
    PUBLISH, ASSIGN, REASSIGN
}
