package com.evela.sigotrab.domain.model;

import java.time.Instant;

public record NewJobOffer(
    String title,
    String company,
    String description,
    String requirements,
    String location,
    String salary,
    String employmentType,
    String originalSourceUrl,
    String sourceName,
    String externalReference,
    Instant extractedAt
) {
}
