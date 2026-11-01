package com.evela.sigotrab.domain.exception;

import java.util.UUID;

 
public class RejectionReasonRequiredException  extends RuntimeException{
    private final UUID jobOfferId;
    public RejectionReasonRequiredException(UUID jobOfferId) {
        super(String.format("Rechazo sin motivo con ID: %s", jobOfferId));
        this.jobOfferId = jobOfferId;
    }
    public UUID getJobOfferId() {
        return jobOfferId;
    }

    
}
