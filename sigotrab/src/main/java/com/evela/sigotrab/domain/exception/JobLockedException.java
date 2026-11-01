package com.evela.sigotrab.domain.exception;

import java.util.UUID;


public class JobLockedException extends RuntimeException{
    private final UUID jobOfferId;

    public JobLockedException(UUID jobOfferId) {
        super(String.format("Conflicto de adquisición de bloqueo - ID: %s", jobOfferId));
        this.jobOfferId = jobOfferId;
    }

    public UUID getJobOfferId() {
        return jobOfferId;
    }
    
}
