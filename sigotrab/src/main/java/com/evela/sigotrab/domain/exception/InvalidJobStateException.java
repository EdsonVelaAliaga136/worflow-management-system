package com.evela.sigotrab.domain.exception;

import java.util.UUID;

import com.evela.sigotrab.domain.model.JobStatus;


public class InvalidJobStateException extends RuntimeException {
    private final UUID jobOfferId;
    private final JobStatus status;
    
    public InvalidJobStateException(UUID jobOfferId, JobStatus status, String operation) {
        super(String.format("Operación no válida en ese estado - ID: %s, estado: %s, operación: %s", jobOfferId, status, operation));
        this.jobOfferId = jobOfferId;
        this.status = status;
    }

    public UUID getJobOfferId() {
        return jobOfferId;
    }

    public JobStatus getStatus() {
        return status;
    }
    
    
}
