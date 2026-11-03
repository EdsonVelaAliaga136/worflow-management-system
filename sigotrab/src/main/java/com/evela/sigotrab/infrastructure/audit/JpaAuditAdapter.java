package com.evela.sigotrab.infrastructure.audit;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.evela.sigotrab.application.port.out.AuditPort;
import com.evela.sigotrab.domain.model.AuditRecord;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository 
public class JpaAuditAdapter implements AuditPort {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JpaAuditAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void record(AuditRecord e) {
        jdbc.update("""
                insert into audit_event
                    (user_id, username, action, entity_type, entity_id,
                     old_values, new_values, ip_address, success, error_code, reason)
                values (?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb),
                        cast(? as inet), ?, ?, ?)
                """,
                e.actor() == null ? null : e.actor().userId(),
                e.actor() == null ? null : e.actor().username(),
                e.action().name(),
                e.entityType(),
                e.entityId(),
                toJson(e.oldValues()),
                toJson(e.newValues()),
                e.actor() == null ? null : e.actor().ipAddress(),
                e.success(),
                e.errorCode(),
                e.reason());
    }

    private String toJson(Map<String, Object> values) {
        if (values == null) return null;
        try {
            return json.writeValueAsString(values);
        } catch (JacksonException ex) {
            throw new IllegalStateException("No se pudo serializar la auditoría", ex);
        }
    }
}

