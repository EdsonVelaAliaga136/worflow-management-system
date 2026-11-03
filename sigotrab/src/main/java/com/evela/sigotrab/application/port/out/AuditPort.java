package com.evela.sigotrab.application.port.out;

import com.evela.sigotrab.domain.model.AuditRecord;

public interface AuditPort {
    void record(AuditRecord event);
}
