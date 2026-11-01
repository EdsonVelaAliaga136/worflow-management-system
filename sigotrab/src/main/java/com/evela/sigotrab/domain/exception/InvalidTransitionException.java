package com.evela.sigotrab.domain.exception;

import java.util.UUID;

import com.evela.sigotrab.domain.model.JobStatus;


public class InvalidTransitionException extends RuntimeException{
    private final UUID jobOfferId;
    private final JobStatus from;
    private final JobStatus to;

    public InvalidTransitionException(UUID jobOfferId, JobStatus from, JobStatus to) {
        super(String.format("Transición no permitida - ID: %s, de: %s, a: %s", jobOfferId, from, to));
        this.jobOfferId = jobOfferId;
        this.from = from;
        this.to = to;
    }

    public UUID getJobOfferId() {
        return jobOfferId;
    }

    public JobStatus getFrom() {
        return from;
    }

    public JobStatus getTo() {
        return to;
    }
    
}
