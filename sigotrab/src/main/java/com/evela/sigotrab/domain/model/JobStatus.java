package com.evela.sigotrab.domain.model;

import java.util.Map;
import java.util.Set;

public enum JobStatus {
    REGISTERED,
    APPROVED,
    IN_PROGRESS,
    PUBLISHED,
    REJECTED;

    private static final Map<JobStatus, Set<JobStatus>> ALLOWED = Map.of(
        REGISTERED, Set.of(APPROVED, REJECTED),
        APPROVED, Set.of(IN_PROGRESS),
        IN_PROGRESS, Set.of(PUBLISHED, APPROVED),
        PUBLISHED, Set.of(),
        REJECTED, Set.of()
    );

    public boolean canTransitionTo(JobStatus target){
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
