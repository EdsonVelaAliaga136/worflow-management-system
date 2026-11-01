package com.evela.sigotrab.domain.exception;

import java.util.UUID;


 
public class UnauthorizedJobOperationException extends RuntimeException {
    private final UUID jobOfferId;
    private final UUID userId;

    public UnauthorizedJobOperationException(UUID jobOfferId, UUID userId) {
        super(String.format("El usuario no es el asignado: ID:%s Usuario:%s", jobOfferId, userId));
        this.jobOfferId = jobOfferId;
        this.userId = userId;
    }

    public UUID getJobOfferId() {
        return jobOfferId;
    }

    public UUID getUserId() {
        return userId;
    }

    
}
