package com.evela.sigotrab.domain.exception;

public class DuplicateJobOfferException extends RuntimeException{

    private final String sourceName;
    private final String externalReference;

    public DuplicateJobOfferException(String sourceName, String externalReference) {
        super(String.format("Ya existe una oferta registrada para la fuente %s con referencia %s", sourceName, externalReference));
        this.sourceName = sourceName;
        this.externalReference = externalReference;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getExternalReference() {
        return externalReference;
    }
}
