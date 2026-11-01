package com.evela.sigotrab.domain.exception;

import java.util.UUID;


public class JobNotFoundException extends RuntimeException{
    private final UUID jobOfferId;

    public JobNotFoundException(UUID jobOfferId) {
        super(String.format("Oferta inexistente con ID: %s", jobOfferId));
        this.jobOfferId = jobOfferId;
    }

    public UUID getJobOfferId() {
        return jobOfferId;
    }
    
}
