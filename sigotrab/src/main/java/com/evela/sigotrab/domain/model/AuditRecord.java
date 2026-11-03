package com.evela.sigotrab.domain.model;

import java.util.Map;

public record AuditRecord(
        AuditAction action,
        String entityType,
        String entityId,
        ActorInfo actor,
        boolean success,
        String errorCode,
        String reason,
        Map<String, Object> oldValues,
        Map<String, Object> newValues) 
{

    public static AuditRecord success(AuditAction action, String entityType,
            String entityId, ActorInfo actor) {
        return new AuditRecord(action, entityType, entityId, actor, true, null, null, null, null);
    }

    public static AuditRecord success(AuditAction action, String entityType,
            String entityId, ActorInfo actor,
            Map<String, Object> newValues) {
        return new AuditRecord(action, entityType, entityId, actor, true, null, null, null, newValues);
    }

    public static AuditRecord failure(AuditAction action, String entityType,
            String entityId, ActorInfo actor, String errorCode) {
        return new AuditRecord(action, entityType, entityId, actor, false, errorCode, null, null, null);
    }

    public static AuditRecord system(AuditAction action, String entityType, String entityId) {
        return new AuditRecord(action, entityType, entityId, ActorInfo.system(), true, null, null, null, null);
    }
}
